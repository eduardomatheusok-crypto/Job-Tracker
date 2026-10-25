package jobtracker.integration.gmail;

import jakarta.mail.Address;
import jakarta.mail.FetchProfile;
import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.Store;
import jakarta.mail.internet.InternetAddress;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.function.Predicate;
import jobtracker.entity.EmailDirection;
import org.eclipse.angus.mail.gimap.GmailFolder;
import org.eclipse.angus.mail.gimap.GmailMessage;
import org.eclipse.angus.mail.gimap.GmailRawSearchTerm;
import org.eclipse.angus.mail.imap.IMAPFolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Acesso ao Gmail via IMAP autenticado com senha de app.
 *
 * <p>Usa as extensões do Gmail: {@code X-GM-RAW} aceita a mesma sintaxe de busca da Gmail API
 * e {@code X-GM-MSGID} em hexadecimal é o mesmo id de mensagem da API, então e-mails já
 * sincronizados via OAuth não são duplicados.
 */
@Component
public class GmailImapClient {

	private static final Logger log = LoggerFactory.getLogger(GmailImapClient.class);

	private static final String HOST = "imap.gmail.com";
	private static final int SNIPPET_LENGTH = 200;

	public record FetchedEmail(
		String messageId,
		EmailDirection direction,
		String subject,
		String fromAddress,
		String toAddress,
		String snippet,
		String rawContent,
		Instant receivedAt
	) {}

	/** Apenas autentica; lança {@link jakarta.mail.AuthenticationFailedException} se a senha for inválida. */
	public void verify(String address, String appPassword) throws MessagingException {
		try (Store store = connect(address, appPassword)) {
			log.debug("Gmail IMAP credentials verified for {}", address);
		}
	}

	/**
	 * Busca os e-mails que casam com {@code inboundQuery} ou {@code sentQuery} e ainda não são conhecidos.
	 *
	 * @param isKnown retorna true para ids já persistidos (o conteúdo deles não é baixado)
	 */
	public List<FetchedEmail> fetchNew(
		String address,
		String appPassword,
		String inboundQuery,
		String sentQuery,
		Predicate<String> isKnown
	) throws MessagingException {
		try (Store store = connect(address, appPassword)) {
			GmailFolder allMail = findAllMailFolder(store);
			allMail.open(Folder.READ_ONLY);
			try {
				Message[] inbound = allMail.search(new GmailRawSearchTerm(inboundQuery));
				Message[] sent = allMail.search(new GmailRawSearchTerm(sentQuery));

				FetchProfile idProfile = new FetchProfile();
				idProfile.add(GmailFolder.FetchProfileItem.MSGID);
				allMail.fetch(inbound, idProfile);
				allMail.fetch(sent, idProfile);

				Set<String> sentIds = new HashSet<>();
				Map<String, Message> candidates = new LinkedHashMap<>();
				for (Message m : sent) {
					String id = messageId(m);
					sentIds.add(id);
					candidates.putIfAbsent(id, m);
				}
				for (Message m : inbound) {
					candidates.putIfAbsent(messageId(m), m);
				}
				candidates.keySet().removeIf(isKnown);

				Message[] toFetch = candidates.values().toArray(Message[]::new);
				FetchProfile contentProfile = new FetchProfile();
				contentProfile.add(FetchProfile.Item.ENVELOPE);
				allMail.fetch(toFetch, contentProfile);

				List<FetchedEmail> result = new ArrayList<>();
				for (Map.Entry<String, Message> entry : candidates.entrySet()) {
					try {
						result.add(toFetchedEmail(entry.getKey(), entry.getValue(), sentIds.contains(entry.getKey())));
					} catch (MessagingException | IOException | RuntimeException e) {
						log.warn("Gmail IMAP: failed reading message {}", entry.getKey(), e);
					}
				}
				return result;
			} finally {
				allMail.close(false);
			}
		}
	}

	private Store connect(String address, String appPassword) throws MessagingException {
		Properties props = new Properties();
		props.put("mail.gimaps.connectiontimeout", "15000");
		props.put("mail.gimaps.timeout", "60000");
		Store store = Session.getInstance(props).getStore("gimaps");
		// Senhas de app são exibidas em blocos ("abcd efgh ..."); o Google aceita sem os espaços
		store.connect(HOST, address, appPassword.replaceAll("\\s+", ""));
		return store;
	}

	/** "Todos os e-mails" tem nome localizado; o atributo especial \All é estável. */
	private GmailFolder findAllMailFolder(Store store) throws MessagingException {
		for (Folder folder : store.getDefaultFolder().list("*")) {
			if (folder instanceof IMAPFolder imapFolder
				&& Arrays.asList(imapFolder.getAttributes()).contains("\\All")) {
				return (GmailFolder) folder;
			}
		}
		throw new MessagingException("Pasta \"Todos os e-mails\" não encontrada. Verifique se ela está visível no IMAP do Gmail.");
	}

	private String messageId(Message message) throws MessagingException {
		return Long.toHexString(((GmailMessage) message).getMsgId());
	}

	private FetchedEmail toFetchedEmail(String id, Message message, boolean sent) throws MessagingException, IOException {
		String rawContent = extractText(message);
		String snippet = rawContent.replaceAll("\\s+", " ").trim();
		if (snippet.length() > SNIPPET_LENGTH) {
			snippet = snippet.substring(0, SNIPPET_LENGTH);
		}
		Instant receivedAt = message.getReceivedDate() != null
			? message.getReceivedDate().toInstant()
			: Instant.now();

		return new FetchedEmail(
			id,
			sent ? EmailDirection.SENT : EmailDirection.INBOUND,
			message.getSubject() != null ? message.getSubject() : "",
			formatAddresses(message.getFrom()),
			formatAddresses(message.getRecipients(Message.RecipientType.TO)),
			snippet,
			rawContent,
			receivedAt
		);
	}

	private String formatAddresses(Address[] addresses) {
		if (addresses == null || addresses.length == 0) {
			return "";
		}
		return InternetAddress.toUnicodeString(addresses);
	}

	private String extractText(Part part) throws MessagingException, IOException {
		if (part.isMimeType("text/plain")) {
			return String.valueOf(part.getContent());
		}
		if (part.isMimeType("text/html")) {
			return GmailService.stripHtml(String.valueOf(part.getContent()));
		}
		if (part.isMimeType("multipart/*")) {
			Multipart multipart = (Multipart) part.getContent();
			StringBuilder bodyBuilder = new StringBuilder();
			for (int i = 0; i < multipart.getCount(); i++) {
				String content = extractText(multipart.getBodyPart(i));
				if (!content.isBlank()) {
					bodyBuilder.append(content).append("\n");
				}
			}
			return bodyBuilder.toString().trim();
		}
		return "";
	}
}
