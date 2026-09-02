package jobtracker.integration.gmail;

import com.google.api.client.auth.oauth2.BearerToken;
import com.google.api.client.auth.oauth2.ClientParametersAuthentication;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.ListMessagesResponse;
import com.google.api.services.gmail.model.Message;
import com.google.api.services.gmail.model.MessagePart;
import com.google.api.services.gmail.model.MessagePartHeader;
import jakarta.persistence.EntityNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import jobtracker.entity.Email;
import jobtracker.entity.EmailDirection;
import jobtracker.entity.GmailConnection;
import jobtracker.entity.User;
import jobtracker.repository.EmailRepository;
import jobtracker.repository.GmailConnectionRepository;
import jobtracker.service.UserService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GmailService {

	private static final Logger log = LoggerFactory.getLogger(GmailService.class);

	private static final String SEARCH_QUERY =
		"subject:(candidatura OR vaga OR \"processo seletivo\" OR entrevista OR \"application\" OR interview) "
			+ "-from:(render.com OR vercel.com OR netlify.com OR github.com OR gitlab.com OR digitalocean.com OR "
			+ "heroku.com OR aws.amazon.com OR mailchimp.com OR sendgrid.net OR statuspage.io)";

	@Value("${google.client-id}")
	private String clientId;

	@Value("${google.client-secret}")
	private String clientSecret;

	private final GmailOAuthService gmailOAuthService;
	private final GmailConnectionRepository gmailConnectionRepository;
	private final UserService userService;
	private final EncryptionService encryptionService;
	private final EmailRepository emailRepository;
	private final EmailSyncProcessor emailSyncProcessor;

	public GmailConnection connectUser(String code, String state) throws IOException {
		String email = gmailOAuthService.decryptState(state);
		User user = userService.findByEmailOrThrow(email);

		GoogleTokenResponse response = gmailOAuthService.getTokens(code);

		GmailConnection connection = gmailConnectionRepository.findByUserId(user.getId())
			.orElseGet(() -> GmailConnection.builder().user(user).build());

		String grantedAddress = resolveGrantedGmailAddress(response);
		connection.setGmailAddress(grantedAddress != null && !grantedAddress.isBlank() ? grantedAddress : email);
		connection.setEncryptedAccessToken(encryptionService.encrypt(response.getAccessToken()));
		if (response.getRefreshToken() != null) {
			connection.setEncryptedRefreshToken(encryptionService.encrypt(response.getRefreshToken()));
		}
		if (response.getExpiresInSeconds() != null) {
			connection.setTokenExpiresAt(Instant.now().plusSeconds(response.getExpiresInSeconds()));
		} else {
			connection.setTokenExpiresAt(Instant.now().plusSeconds(3600));
		}
		connection.setActive(true);

		return gmailConnectionRepository.save(connection);
	}

	public java.util.Optional<GmailConnection> findConnectionByUserId(Long userId) {
		return gmailConnectionRepository.findByUserId(userId);
	}

	public void disconnectUser(Long userId) {
		GmailConnection connection = gmailConnectionRepository.findByUserId(userId)
			.orElseThrow(() -> new EntityNotFoundException("Gmail integration not found for user: " + userId));
		gmailConnectionRepository.delete(connection);
	}

	/**
	 * Sincroniza os e-mails de um usuário. O trabalho de rede (Gmail API) acontece
	 * <b>fora</b> de qualquer transação de banco; cada e-mail é persistido/parsado na
	 * própria transação via {@link EmailSyncProcessor}, de modo que a falha de uma
	 * mensagem não derruba o restante do lote.
	 */
	public int syncEmails(Long userId) throws IOException {
		GmailConnection connection = gmailConnectionRepository.findByUserId(userId)
			.orElseThrow(() -> new EntityNotFoundException("Gmail integration not active for user: " + userId));

		if (!connection.isActive()) {
			return 0;
		}

		Gmail client = getGmailClient(connection);

		List<Message> messages = fetchAllMatchingMessages(client);

		if (messages.isEmpty()) {
			connection.setLastSyncedAt(Instant.now());
			gmailConnectionRepository.save(connection);
			return 0;
		}

		int processed = 0;
		int failed = 0;
		for (Message messageSummary : messages) {
			String messageId = messageSummary.getId();
			if (emailRepository.existsByMessageIdAndUserId(messageId, userId)) {
				continue;
			}

			try {
				Message msg = client.users().messages().get("me", messageId).setFormat("full").execute();

				String subject = getHeader(msg, "Subject");
				String from = getHeader(msg, "From");
				String to = getHeader(msg, "To");
				boolean sent = msg.getLabelIds() != null && msg.getLabelIds().contains("SENT");
				Instant receivedAt = msg.getInternalDate() != null
					? Instant.ofEpochMilli(msg.getInternalDate())
					: Instant.now();
				String rawContent = extractTextFromBody(msg.getPayload());

				emailSyncProcessor.process(
					userId,
					messageId,
					sent ? EmailDirection.SENT : EmailDirection.INBOUND,
					subject,
					from,
					to,
					msg.getSnippet(),
					rawContent,
					receivedAt
				);
				processed++;
			} catch (Exception e) {
				failed++;
				log.warn("Gmail sync: failed processing message {} for user {}", messageId, userId, e);
			}
		}

		connection.setLastSyncedAt(Instant.now());
		gmailConnectionRepository.save(connection);
		log.info("Gmail sync done for user {}: processed={}, failed={}", userId, processed, failed);

		return processed;
	}

	public int syncAllConnections() {
		int total = 0;
		for (GmailConnection connection : gmailConnectionRepository.findAllByActiveTrue()) {
			Long userId = connection.getUser().getId();
			try {
				total += syncEmails(userId);
			} catch (Exception e) {
				log.warn("Gmail sync failed for user {}", userId, e);
			}
		}
		return total;
	}

	private List<Message> fetchAllMatchingMessages(Gmail client) throws IOException {
		List<Message> allMessages = new ArrayList<>();
		String pageToken = null;

		do {
			Gmail.Users.Messages.List request = client.users().messages().list("me")
				.setQ(SEARCH_QUERY);
			if (pageToken != null) {
				request.setPageToken(pageToken);
			}
			ListMessagesResponse response = request.execute();
			if (response.getMessages() != null) {
				allMessages.addAll(response.getMessages());
			}
			pageToken = response.getNextPageToken();
		} while (pageToken != null);

		return allMessages;
	}

	private String resolveGrantedGmailAddress(GoogleTokenResponse response) {
		try {
			Credential credential = new Credential.Builder(BearerToken.authorizationHeaderAccessMethod())
				.setTransport(new NetHttpTransport())
				.setJsonFactory(GsonFactory.getDefaultInstance())
				.setTokenServerUrl(new GenericUrl("https://oauth2.googleapis.com/token"))
				.setClientAuthentication(new ClientParametersAuthentication(clientId, clientSecret))
				.build();
			credential.setAccessToken(response.getAccessToken());
			credential.setRefreshToken(response.getRefreshToken());

			Gmail client = new Gmail.Builder(
				new NetHttpTransport(),
				GsonFactory.getDefaultInstance(),
				credential
			)
			.setApplicationName("jobtracker")
			.build();

			return client.users().getProfile("me").execute().getEmailAddress();
		} catch (IOException e) {
			return null;
		}
	}

	private Gmail getGmailClient(GmailConnection connection) throws IOException {
		String accessToken = encryptionService.decrypt(connection.getEncryptedAccessToken());
		String refreshToken = encryptionService.decrypt(connection.getEncryptedRefreshToken());

		Credential credential = new Credential.Builder(BearerToken.authorizationHeaderAccessMethod())
			.setTransport(new NetHttpTransport())
			.setJsonFactory(GsonFactory.getDefaultInstance())
			.setTokenServerUrl(new GenericUrl("https://oauth2.googleapis.com/token"))
			.setClientAuthentication(new ClientParametersAuthentication(clientId, clientSecret))
			.build();

		credential.setAccessToken(accessToken);
		credential.setRefreshToken(refreshToken);

		// Força refresh do token se estiver expirado ou perto de expirar (1 minuto de margem)
		if (connection.getTokenExpiresAt() == null || connection.getTokenExpiresAt().isBefore(Instant.now().plusSeconds(60))) {
			credential.refreshToken();
			connection.setEncryptedAccessToken(encryptionService.encrypt(credential.getAccessToken()));
			if (credential.getExpiresInSeconds() != null) {
				connection.setTokenExpiresAt(Instant.now().plusSeconds(credential.getExpiresInSeconds()));
			} else {
				connection.setTokenExpiresAt(Instant.now().plusSeconds(3600));
			}
			gmailConnectionRepository.save(connection);
		}

		return new Gmail.Builder(
			new NetHttpTransport(),
			GsonFactory.getDefaultInstance(),
			credential
		)
		.setApplicationName("jobtracker")
		.build();
	}

	private String getHeader(Message message, String name) {
		if (message.getPayload() == null || message.getPayload().getHeaders() == null) {
			return "";
		}
		return message.getPayload().getHeaders().stream()
			.filter(h -> h.getName().equalsIgnoreCase(name))
			.map(MessagePartHeader::getValue)
			.findFirst()
			.orElse("");
	}

	private String extractTextFromBody(MessagePart part) {
		if (part == null) {
			return "";
		}
		if (part.getMimeType().equalsIgnoreCase("text/plain") && part.getBody() != null && part.getBody().getData() != null) {
			return new String(Base64.getUrlDecoder().decode(part.getBody().getData()), StandardCharsets.UTF_8);
		}
		if (part.getMimeType().equalsIgnoreCase("text/html") && part.getBody() != null && part.getBody().getData() != null) {
			String html = new String(Base64.getUrlDecoder().decode(part.getBody().getData()), StandardCharsets.UTF_8);
			return stripHtml(html);
		}
		if (part.getParts() != null) {
			StringBuilder bodyBuilder = new StringBuilder();
			for (MessagePart subpart : part.getParts()) {
				String content = extractTextFromBody(subpart);
				if (!content.isBlank()) {
					bodyBuilder.append(content).append("\n");
				}
			}
			return bodyBuilder.toString().trim();
		}
		return "";
	}

	private String stripHtml(String html) {
		String text = html.replaceAll("<br\\s*/?>", "\n")
			.replaceAll("<[^>]+>", " ")
			.replaceAll("\\s+", " ")
			.trim();
		return text
			.replace("&amp;", "&")
			.replace("&lt;", "<")
			.replace("&gt;", ">")
			.replace("&quot;", "\"")
			.replace("&#39;", "'");
	}
}