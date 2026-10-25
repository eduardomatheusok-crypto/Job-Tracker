package jobtracker.integration.gmail;

import jakarta.mail.Address;
import jakarta.mail.FetchProfile;
import jakarta.mail.FolderNotFoundException;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
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
	 * Busca os e-mails recebidos que casam com {@code query} e ainda não são conhecidos, do mais novo
	 * ao mais antigo, baixando no máximo {@code maxDownloads}. Com muitos resultados, os mais antigos
	 * ficam de fora: o que importa para o status das candidaturas são os e-mails recentes.
	 *
	 * @param isKnown retorna true para ids já persistidos (o conteúdo deles não é baixado)
	 */
	public List<FetchedEmail> fetchNew(
		String address,
		String appPassword,
		String query,
		Predicate<String> isKnown,
		int maxDownloads
	) throws MessagingException {
		try (Store store = connect(address, appPassword)) {
			GmailFolder allMail = findAllMailFolder(store);
			allMail.open(Folder.READ_ONLY);
			try {
				// Resultados vêm em ordem de chegada (mais antigo primeiro); percorremos do fim para o início
				Message[] found = allMail.search(new GmailRawSearchTerm(query));

				FetchProfile idProfile = new FetchProfile();
				idProfile.add(GmailFolder.FetchProfileItem.MSGID);
				allMail.fetch(found, idProfile);

				Map<String, Message> candidates = new LinkedHashMap<>();
				for (int i = found.length - 1; i >= 0; i--) {
					Message m = found[i];
					String id = messageId(m);
					if (candidates.size() >= maxDownloads) {
						break;
					}
					if (!isKnown.test(id)) {
						candidates.putIfAbsent(id, m);
					}
				}
				log.info("Gmail IMAP: {} message(s) matched, {} new to download (limit {})",
					found.length, candidates.size(), maxDownloads);

				Message[] toFetch = candidates.values().toArray(Message[]::new);
				FetchProfile contentProfile = new FetchProfile();
				contentProfile.add(FetchProfile.Item.ENVELOPE);
				allMail.fetch(toFetch, contentProfile);

				List<FetchedEmail> result = new ArrayList<>();
				for (Map.Entry<String, Message> entry : candidates.entrySet()) {
					try {
						result.add(toFetchedEmail(entry.getKey(), entry.getValue()));
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
		throw new FolderNotFoundException(store.getDefaultFolder(), "Pasta \"Todos os e-mails\" (\\All) não encontrada no IMAP");
	}

	private String messageId(Message message) throws MessagingException {
		return Long.toHexString(((GmailMessage) message).getMsgId());
	}

	private FetchedEmail toFetchedEmail(String id, Message message) throws MessagingException, IOException {
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
			EmailDirection.INBOUND,
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
