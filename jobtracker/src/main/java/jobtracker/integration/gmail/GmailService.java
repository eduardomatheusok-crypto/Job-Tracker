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
import jakarta.mail.AuthenticationFailedException;
import jakarta.mail.FolderNotFoundException;
import jakarta.mail.MessagingException;
import jakarta.persistence.EntityNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import jobtracker.entity.Email;
import jobtracker.entity.EmailDirection;
import jobtracker.entity.GmailConnection;
import jobtracker.entity.User;
import jobtracker.ia.ProcessadorEmailIAService;
import jobtracker.repository.EmailRepository;
import jobtracker.repository.GmailConnectionRepository;
import jobtracker.service.UserService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.google.api.client.auth.oauth2.TokenResponseException;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GmailService {

	private static final Logger log = LoggerFactory.getLogger(GmailService.class);

	private static final String SEARCH_QUERY_INBOUND =
		"(candidatura OR vaga OR vagas OR \"processo seletivo\" OR entrevista OR entrevistas OR \"application\" OR interview OR \"inscrição\" OR \"inscricao\" OR \"oportunidade\" OR \"recrutamento\" OR \"recruiting\" OR \"hiring\") "
			+ "-from:(render.com OR vercel.com OR netlify.com OR github.com OR gitlab.com OR digitalocean.com OR "
			+ "heroku.com OR aws.amazon.com OR mailchimp.com OR sendgrid.net OR statuspage.io)";

	private static final String SEARCH_QUERY_SENT = "label:sent";

	/**
	 * Mesma busca de {@link #SEARCH_QUERY_INBOUND} para o IMAP (X-GM-RAW), só com ASCII e sem aspas: com acentos
	 * o cliente envia "CHARSET UTF-8" + literal, e aspas internas exigem escape; o Gmail responde
	 * "BAD Could not parse command". A busca do Gmail ignora acentos, então "inscricao" também acha "inscrição".
	 */
	static final String SEARCH_QUERY_INBOUND_IMAP =
		"(candidatura OR vaga OR vagas OR (processo seletivo) OR entrevista OR entrevistas OR application OR interview "
			+ "OR inscricao OR oportunidade OR recrutamento OR recruiting OR hiring) "
			+ "-from:(render.com OR vercel.com OR netlify.com OR github.com OR gitlab.com OR digitalocean.com OR "
			+ "heroku.com OR aws.amazon.com OR mailchimp.com OR sendgrid.net OR statuspage.io)";

	@Value("${google.client-id}")
	private String clientId;

	@Value("${google.client-secret}")
	private String clientSecret;

	/** Limita chamadas ao Gemini por sincronização (cota do plano gratuito); o restante fica para a próxima. */
	@Value("${app.ia.max-emails-por-sync:30}")
	private int maxEmailsPerSync;

	/** Primeira sincronização IMAP (sem e-mails salvos): quantos dias para trás buscar. */
	@Value("${app.gmail.sync.initial-window-days:90}")
	private int initialWindowDays;

	/** Máximo de e-mails baixados por sincronização IMAP; o restante vem nas próximas. */
	@Value("${app.gmail.sync.max-downloads:100}")
	private int maxDownloadsPerSync;

	private static final int IMAP_WINDOW_MARGIN_DAYS = 2;
	private static final java.time.format.DateTimeFormatter IMAP_DATE_FORMAT = java.time.format.DateTimeFormatter.ofPattern("yyyy/MM/dd");

	private final GmailOAuthService gmailOAuthService;
	private final GmailConnectionRepository gmailConnectionRepository;
	private final UserService userService;
	private final EncryptionService encryptionService;
	private final EmailRepository emailRepository;
	private final EmailSyncProcessor emailSyncProcessor;
	private final GmailImapClient gmailImapClient;
	private final ProcessadorEmailIAService processadorEmailIAService;

	/**
	 * Usuários com sincronização em andamento. Evita que o agendador, o auto-sync da página e o clique
	 * manual rodem juntos e salvem/classifiquem os mesmos e-mails em paralelo. Em memória: vale para uma
	 * única instância do backend (o caso do Render).
	 */
	private final Set<Long> syncsInProgress = ConcurrentHashMap.newKeySet();

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
		} else {
			// Sem refresh token novo, o antigo pode estar revogado; reaproveitá-lo causaria invalid_grant em ~1h
			log.warn("Gmail connect: Google did not return a refresh token for user {}", user.getId());
		}
		if (response.getExpiresInSeconds() != null) {
			connection.setTokenExpiresAt(Instant.now().plusSeconds(response.getExpiresInSeconds()));
		} else {
			connection.setTokenExpiresAt(Instant.now().plusSeconds(3600));
		}
		connection.setEncryptedAppPassword(null);
		connection.setActive(true);

		return gmailConnectionRepository.save(connection);
	}

	/**
	 * Conecta via IMAP com senha de app. Valida as credenciais no Gmail antes de salvar.
	 *
	 * @throws IllegalArgumentException se o e-mail ou a senha de app forem recusados pelo Google
	 */
	public GmailConnection connectWithAppPassword(User user, String gmailAddress, String appPassword) {
		String address = gmailAddress.trim().toLowerCase(java.util.Locale.ROOT);
		try {
			gmailImapClient.verify(address, appPassword);
		} catch (AuthenticationFailedException e) {
			throw new IllegalArgumentException("O Google recusou o e-mail ou a senha de app. Confira os dados e se a verificação em 2 etapas está ativa.", e);
		} catch (MessagingException e) {
			log.warn("Gmail IMAP: could not verify credentials for user {}", user.getId(), e);
			throw new GmailSyncException("Não foi possível falar com o Gmail agora. Tente novamente em instantes.", e);
		}

		GmailConnection connection = gmailConnectionRepository.findByUserId(user.getId())
			.orElseGet(() -> GmailConnection.builder().user(user).build());
		connection.setGmailAddress(address);
		connection.setEncryptedAppPassword(encryptionService.encrypt(appPassword.replaceAll("\\s+", "")));
		connection.setEncryptedAccessToken(null);
		connection.setEncryptedRefreshToken(null);
		connection.setTokenExpiresAt(null);
		connection.setActive(true);

		return gmailConnectionRepository.save(connection);
	}

	public java.util.Optional<GmailConnection> findConnectionByUserId(Long userId) {
		return gmailConnectionRepository.findByUserId(userId);
	}

	@Transactional
	public void disconnectUser(Long userId) {
		gmailConnectionRepository.findByUserId(userId).ifPresent(conn -> {
			if (conn.getUser() != null) {
				conn.getUser().setGmailConnection(null);
			}
		});
		gmailConnectionRepository.deleteByUserId(userId);
	}

	/**
	 * Sincroniza os e-mails de um usuário. O trabalho de rede (Gmail API) acontece
	 * <b>fora</b> de qualquer transação de banco; cada e-mail é persistido/parsado na
	 * própria transação via {@link EmailSyncProcessor}, de modo que a falha de uma
	 * mensagem não derruba o restante do lote.
	 */
	public int syncEmails(Long userId) throws IOException {
		if (!syncsInProgress.add(userId)) {
			throw new GmailSyncInProgressException(userId);
		}
		try {
			return doSyncEmails(userId);
		} finally {
			syncsInProgress.remove(userId);
		}
	}

	private int doSyncEmails(Long userId) throws IOException {
		GmailConnection connection = gmailConnectionRepository.findByUserId(userId)
			.orElseThrow(() -> new EntityNotFoundException("Gmail integration not active for user: " + userId));

		if (!connection.isActive()) {
			return 0;
		}

		int fetched = connection.getEncryptedAppPassword() != null
			? syncViaImap(connection, userId)
			: syncViaApi(connection, userId);

		int classified = classifyPendingEmails(userId);

		connection.setLastSyncedAt(Instant.now());
		gmailConnectionRepository.save(connection);
		log.info("Gmail sync done for user {}: fetched={}, classified={}", userId, fetched, classified);

		return classified;
	}

	/**
	 * Classifica com IA os e-mails recebidos ainda não processados, do mais novo ao mais antigo (o
	 * {@link ProcessadorEmailIAService} não deixa um e-mail antigo regredir um status mais novo). Falhas do
	 * LLM não interrompem o lote: o e-mail fica pendente e é tentado de novo na próxima sincronização.
	 */
	private int classifyPendingEmails(Long userId) {
		List<Email> pending = emailRepository.findPendentesDeClassificacao(userId, PageRequest.of(0, maxEmailsPerSync));
		int classified = 0;
		int failed = 0;
		for (Email email : pending) {
			try {
				ProcessadorEmailIAService.Resultado resultado = processadorEmailIAService.processar(email);
				if (resultado == ProcessadorEmailIAService.Resultado.FALHA) {
					failed++;
				} else if (resultado != ProcessadorEmailIAService.Resultado.JA_PROCESSADO) {
					classified++;
				}
			} catch (Exception e) {
				failed++;
				log.warn("Gmail sync: failed applying AI classification to email {} for user {}", email.getId(), userId, e);
			}
		}
		if (failed > 0) {
			log.warn("Gmail sync: {} email(s) left pending classification for user {}", failed, userId);
		}
		return classified;
	}

	private int syncViaImap(GmailConnection connection, Long userId) {
		String appPassword = encryptionService.decrypt(connection.getEncryptedAppPassword());

		List<GmailImapClient.FetchedEmail> emails;
		try {
			emails = gmailImapClient.fetchNew(
				connection.getGmailAddress(),
				appPassword,
				SEARCH_QUERY_INBOUND_IMAP + " " + imapDateFilter(newestInboundReceivedAt(userId), LocalDate.now(ZoneOffset.UTC), initialWindowDays),
				messageId -> emailRepository.existsByMessageIdAndUserId(messageId, userId),
				maxDownloadsPerSync
			);
		} catch (AuthenticationFailedException e) {
			log.warn("Gmail IMAP: authentication failed for user {}: {}", userId, e.getMessage());
			deactivate(connection);
			throw new IllegalStateException("A senha de app foi revogada ou é inválida. Gere uma nova senha de app e conecte novamente.", e);
		} catch (FolderNotFoundException e) {
			log.warn("Gmail IMAP: All Mail folder not visible for user {}", userId);
			throw new GmailSyncException("A pasta \"Todos os e-mails\" está oculta no IMAP. No Gmail, vá em Configurações → Marcadores e marque \"Mostrar no IMAP\" em \"Todos os e-mails\".", e);
		} catch (MessagingException e) {
			log.warn("Gmail IMAP: sync failed for user {}", userId, e);
			throw new GmailSyncException("Não foi possível acessar o Gmail agora. Tente novamente em instantes.", e);
		}

		int processed = 0;
		for (GmailImapClient.FetchedEmail email : emails) {
			try {
				emailSyncProcessor.process(
					userId,
					email.messageId(),
					email.direction(),
					email.subject(),
					email.fromAddress(),
					email.toAddress(),
					email.snippet(),
					email.rawContent(),
					email.receivedAt()
				);
				processed++;
			} catch (Exception e) {
				log.warn("Gmail sync: failed processing message {} for user {}", email.messageId(), userId, e);
			}
		}
		return processed;
	}

	private Optional<Instant> newestInboundReceivedAt(Long userId) {
		return emailRepository.findFirstByUserIdAndDirectionOrderByReceivedAtDesc(userId, EmailDirection.INBOUND)
			.map(Email::getReceivedAt);
	}

	/**
	 * Janela de datas da busca IMAP: a partir do e-mail recebido mais recente já salvo (com margem, pois o
	 * "after:" do Gmail trabalha com datas inteiras e fuso do Gmail) ou, na primeira sincronização, os últimos
	 * {@code initialWindowDays} dias. Formato só com ASCII, exigido pelo X-GM-RAW.
	 */
	static String imapDateFilter(Optional<Instant> newestReceivedAt, LocalDate today, int initialWindowDays) {
		LocalDate start = newestReceivedAt
			.map(instant -> LocalDate.ofInstant(instant, ZoneOffset.UTC).minusDays(IMAP_WINDOW_MARGIN_DAYS))
			.orElse(today.minusDays(initialWindowDays));
		return "after:" + start.format(IMAP_DATE_FORMAT);
	}

	private int syncViaApi(GmailConnection connection, Long userId) throws IOException {
		Gmail client;
		try {
			client = getGmailClient(connection);
		} catch (IllegalStateException | GmailSyncException e) {
			throw e;
		} catch (Exception e) {
			// Falha de rede/infra ao renovar o token: não invalida a conexão
			log.warn("Gmail sync: could not build Gmail client for user {}", userId, e);
			throw new GmailSyncException("Não foi possível falar com o Google agora. Tente novamente em instantes.", e);
		}

		List<Message> messages;
		try {
			messages = fetchAllMatchingMessages(client);
		} catch (GoogleJsonResponseException e) {
			String reason = googleErrorReason(e);
			log.warn("Gmail sync: Google API rejected request (status={}, reason={}, message={}) for user {}",
				e.getStatusCode(), reason, e.getDetails() != null ? e.getDetails().getMessage() : e.getMessage(), userId);

			// 401 aqui significa que o Credential já tentou renovar o token e não conseguiu
			if (e.getStatusCode() == 401) {
				deactivate(connection);
				throw new IllegalStateException("Sua autorização com o Gmail expirou ou foi revogada. Por favor, reconecte sua conta.", e);
			}
			if ("insufficientPermissions".equals(reason) || "ACCESS_TOKEN_SCOPE_INSUFFICIENT".equals(reason)) {
				deactivate(connection);
				throw new IllegalStateException("A permissão de leitura do Gmail não foi concedida. Reconecte sua conta e marque a opção de acesso aos e-mails.", e);
			}
			if ("accessNotConfigured".equals(reason) || "SERVICE_DISABLED".equals(reason)) {
				throw new GmailSyncException("A Gmail API não está ativada no projeto do Google Cloud. Ative-a no console e tente novamente.", e);
			}
			if ("rateLimitExceeded".equals(reason) || "userRateLimitExceeded".equals(reason) || e.getStatusCode() == 429) {
				throw new GmailSyncException("Limite de requisições do Gmail atingido. Aguarde alguns minutos e tente novamente.", e);
			}
			throw new GmailSyncException("O Google recusou a requisição (status " + e.getStatusCode() + "). Tente novamente em instantes.", e);
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
		if (failed > 0) {
			log.warn("Gmail sync: {} message(s) failed for user {}", failed, userId);
		}
		return processed;
	}

	public int syncAllConnections() {
		int total = 0;
		for (GmailConnection connection : gmailConnectionRepository.findAllByActiveTrue()) {
			Long userId = connection.getUser().getId();
			try {
				total += syncEmails(userId);
			} catch (GmailSyncInProgressException e) {
				log.info("Gmail scheduled sync skipped for user {}: another sync is running", userId);
			} catch (Exception e) {
				log.warn("Gmail sync failed for user {}", userId, e);
			}
		}
		return total;
	}

	private List<Message> fetchAllMatchingMessages(Gmail client) throws IOException {
		List<Message> allMessages = new ArrayList<>();
		allMessages.addAll(fetchByQuery(client, SEARCH_QUERY_INBOUND));
		allMessages.addAll(fetchByQuery(client, SEARCH_QUERY_SENT));

		// Deduplica por messageId (um e-mail pode casar com as duas queries)
		return allMessages.stream()
			.collect(java.util.stream.Collectors.toMap(
				Message::getId,
				m -> m,
				(a, b) -> a
			))
			.values()
			.stream()
			.toList();
	}

	private List<Message> fetchByQuery(Gmail client, String query) throws IOException {
		List<Message> messages = new ArrayList<>();
		String pageToken = null;

		do {
			Gmail.Users.Messages.List request = client.users().messages().list("me")
				.setQ(query);
			if (pageToken != null) {
				request.setPageToken(pageToken);
			}
			ListMessagesResponse response = request.execute();
			if (response.getMessages() != null) {
				messages.addAll(response.getMessages());
			}
			pageToken = response.getNextPageToken();
		} while (pageToken != null);

		return messages;
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
			if (refreshToken == null) {
				log.warn("Gmail token expired and no refresh token stored for user {}", connection.getUser().getId());
				deactivate(connection);
				throw new IllegalStateException("Sua autorização com o Gmail expirou. Por favor, reconecte sua conta.");
			}
			try {
				credential.refreshToken();
				connection.setEncryptedAccessToken(encryptionService.encrypt(credential.getAccessToken()));
				if (credential.getExpiresInSeconds() != null) {
					connection.setTokenExpiresAt(Instant.now().plusSeconds(credential.getExpiresInSeconds()));
				} else {
					connection.setTokenExpiresAt(Instant.now().plusSeconds(3600));
				}
				gmailConnectionRepository.save(connection);
			} catch (TokenResponseException e) {
				String error = e.getDetails() != null ? e.getDetails().getError() : null;
				log.warn("Gmail token refresh failed for user {}: error={}, description={}", connection.getUser().getId(),
					error, e.getDetails() != null ? e.getDetails().getErrorDescription() : e.getMessage());
				// invalid_grant = refresh token revogado/expirado (ex.: app OAuth em modo "Testing" expira em 7 dias)
				if ("invalid_grant".equals(error)) {
					deactivate(connection);
					throw new IllegalStateException("Sua autorização com o Gmail expirou ou foi revogada. Por favor, reconecte sua conta.", e);
				}
				// invalid_client/unauthorized_client etc. são problemas de configuração do servidor, não da conta
				throw new GmailSyncException("Falha ao renovar o acesso ao Gmail (" + error + "). Verifique GOOGLE_CLIENT_ID/GOOGLE_CLIENT_SECRET.", e);
			}
		}

		return new Gmail.Builder(
			new NetHttpTransport(),
			GsonFactory.getDefaultInstance(),
			credential
		)
		.setApplicationName("jobtracker")
		.build();
	}

	private void deactivate(GmailConnection connection) {
		connection.setActive(false);
		gmailConnectionRepository.save(connection);
	}

	private String googleErrorReason(GoogleJsonResponseException e) {
		if (e.getDetails() == null) {
			return null;
		}
		if (e.getDetails().getErrors() != null && !e.getDetails().getErrors().isEmpty()) {
			return e.getDetails().getErrors().get(0).getReason();
		}
		Object status = e.getDetails().get("status");
		return status != null ? status.toString() : null;
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

	static String stripHtml(String html) {
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