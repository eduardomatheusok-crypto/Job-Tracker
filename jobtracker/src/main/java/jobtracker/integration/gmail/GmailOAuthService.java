package jobtracker.integration.gmail;

import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.gmail.GmailScopes;
import java.io.IOException;
import java.util.Collections;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class GmailOAuthService {

	private final String clientId;
	private final String clientSecret;
	private final String redirectUri;
	private final EncryptionService encryptionService;

	public GmailOAuthService(
		@Value("${google.client-id}") String clientId,
		@Value("${google.client-secret}") String clientSecret,
		@Value("${google.redirect-uri}") String redirectUri,
		EncryptionService encryptionService
	) {
		this.clientId = clientId;
		this.clientSecret = clientSecret;
		this.redirectUri = redirectUri;
		this.encryptionService = encryptionService;
	}

	public GoogleAuthorizationCodeFlow getFlow() throws IOException {
		return new GoogleAuthorizationCodeFlow.Builder(
			new NetHttpTransport(),
			GsonFactory.getDefaultInstance(),
			clientId,
			clientSecret,
			Collections.singleton(GmailScopes.GMAIL_READONLY)
		)
		.setAccessType("offline")
		.setApprovalPrompt("force")
		.build();
	}

	public String getAuthorizationUrl(String email) throws IOException {
		String state = encryptionService.encrypt(email);
		return getFlow().newAuthorizationUrl()
			.setRedirectUri(redirectUri)
			.setState(state)
			.build();
	}

	public GoogleTokenResponse getTokens(String code) throws IOException {
		return getFlow().newTokenRequest(code)
			.setRedirectUri(redirectUri)
			.execute();
	}

	public String decryptState(String state) {
		return encryptionService.decrypt(state);
	}
}
