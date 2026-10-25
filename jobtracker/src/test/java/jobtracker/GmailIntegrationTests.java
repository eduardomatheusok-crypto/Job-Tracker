package jobtracker;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jobtracker.entity.GmailConnection;
import jobtracker.entity.User;
import jobtracker.integration.gmail.EmailParserService;
import jobtracker.integration.gmail.GmailOAuthService;
import jobtracker.integration.gmail.GmailService;
import jobtracker.integration.gmail.GmailSyncException;
import jobtracker.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.Optional;

@SpringBootTest
class GmailIntegrationTests {

	private MockMvc mockMvc;

	@Autowired
	private WebApplicationContext webApplicationContext;

	// Instância local de ObjectMapper — não precisa de bean do contexto
	private final ObjectMapper objectMapper = new ObjectMapper();

	// Repositórios usados para setup/limpeza
	@Autowired
	private UserRepository userRepository;

	@Autowired
	private ApplicationRepository applicationRepository;

	@Autowired
	private ApplicationHistoryRepository applicationHistoryRepository;

	@Autowired
	private GmailConnectionRepository gmailConnectionRepository;

	@Autowired
	private EmailRepository emailRepository;

	// Mocks das camadas que chamam APIs externas
	@MockitoBean
	private GmailOAuthService gmailOAuthService;

	@MockitoBean
	private GmailService gmailService;

	@MockitoBean
	private EmailParserService emailParserService;

	// -----------------------------------------------------------------------
	// Setup
	// -----------------------------------------------------------------------

	@BeforeEach
	void setUp() {
		emailRepository.deleteAll();
		applicationHistoryRepository.deleteAll();
		applicationRepository.deleteAll();
		gmailConnectionRepository.deleteAll();
		userRepository.deleteAll();

		mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
			.apply(springSecurity())
			.build();
	}

	// -----------------------------------------------------------------------
	// Helpers
	// -----------------------------------------------------------------------

	private void register(String name, String email, String password) throws Exception {
		String body = """
			{"name":"%s","email":"%s","password":"%s"}
			""".formatted(name, email, password);
		mockMvc.perform(post("/api/auth/register")
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
			.andExpect(status().isCreated());
	}

	private String loginAndGetToken(String email, String password) throws Exception {
		String body = """
			{"email":"%s","password":"%s"}
			""".formatted(email, password);
		MvcResult result = mockMvc.perform(post("/api/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
			.andExpect(status().isOk())
			.andReturn();
		JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
		return node.get("token").asText();
	}

	// -----------------------------------------------------------------------
	// Teste 1: GET /api/gmail/auth-url com JWT – deve retornar URL de auth
	// -----------------------------------------------------------------------
	@Test
	void getAuthUrl_withValidJwt_returnsAuthorizationUrl() throws Exception {
		register("Alice Gmail", "alice.gmail@example.com", "password123");
		String token = loginAndGetToken("alice.gmail@example.com", "password123");

		String fakeUrl = "https://accounts.google.com/o/oauth2/auth?client_id=fake&scope=gmail";
		when(gmailOAuthService.getAuthorizationUrl(anyString())).thenReturn(fakeUrl);

		mockMvc.perform(get("/api/gmail/auth-url")
				.header("Authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.authUrl").value(fakeUrl));

		verify(gmailOAuthService).getAuthorizationUrl("alice.gmail@example.com");
	}

	// -----------------------------------------------------------------------
	// Teste 2: GET /api/gmail/auth-url sem JWT – deve retornar 401
	// -----------------------------------------------------------------------
	@Test
	void getAuthUrl_withoutJwt_returnsUnauthorized() throws Exception {
		mockMvc.perform(get("/api/gmail/auth-url"))
			.andExpect(status().isUnauthorized());
	}

	// -----------------------------------------------------------------------
	// Teste 3: GET /api/gmail/callback com code e state válidos – conecta usuário
	// -----------------------------------------------------------------------
	@Test
	void callback_withValidCodeAndState_connectsUserAndReturnsHtml() throws Exception {
		register("Bob Gmail", "bob.gmail@example.com", "password123");
		User bob = userRepository.findByEmailIgnoreCase("bob.gmail@example.com").orElseThrow();

		GmailConnection fakeConnection = GmailConnection.builder()
			.user(bob)
			.gmailAddress("bob.gmail@example.com")
			.active(true)
			.build();

		when(gmailService.connectUser(eq("valid-code"), eq("encrypted-state")))
			.thenReturn(fakeConnection);

		mockMvc.perform(get("/api/gmail/callback")
				.param("code", "valid-code")
				.param("state", "encrypted-state"))
			.andExpect(status().isOk())
			.andExpect(content().string(org.hamcrest.Matchers.containsString("Gmail conectado com sucesso")));

		verify(gmailService).connectUser("valid-code", "encrypted-state");
	}

	// -----------------------------------------------------------------------
	// Teste 4: GET /api/gmail/callback com erro – retorna 400
	// -----------------------------------------------------------------------
	@Test
	void callback_withInvalidCode_returnsBadRequest() throws Exception {
		when(gmailService.connectUser(anyString(), anyString()))
			.thenThrow(new RuntimeException("invalid_grant"));

		mockMvc.perform(get("/api/gmail/callback")
				.param("code", "bad-code")
				.param("state", "some-state"))
			.andExpect(status().isBadRequest())
			.andExpect(content().string(org.hamcrest.Matchers.containsString("Erro ao conectar Gmail")));
	}

	// -----------------------------------------------------------------------
	// Teste 5: POST /api/gmail/sync com JWT – retorna quantidade de e-mails
	// -----------------------------------------------------------------------
	@Test
	void syncEmails_withValidJwt_returnsEmailCount() throws Exception {
		register("Carol Gmail", "carol.gmail@example.com", "password123");
		String token = loginAndGetToken("carol.gmail@example.com", "password123");

		when(gmailService.syncEmails(anyLong())).thenReturn(7);

		mockMvc.perform(post("/api/gmail/sync")
				.header("Authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.emailsProcessed").value(7))
			.andExpect(jsonPath("$.message").value("Sincronização concluída"));
	}

	// -----------------------------------------------------------------------
	// Teste 6: GET /api/gmail/status – conectado
	// -----------------------------------------------------------------------
	@Test
	void getStatus_whenConnected_returnsConnectedTrue() throws Exception {
		register("Dan Gmail", "dan.gmail@example.com", "password123");
		String token = loginAndGetToken("dan.gmail@example.com", "password123");

		User dan = userRepository.findByEmailIgnoreCase("dan.gmail@example.com").orElseThrow();
		GmailConnection conn = GmailConnection.builder()
			.user(dan)
			.gmailAddress("dan.gmail@example.com")
			.active(true)
			.build();

		when(gmailService.findConnectionByUserId(dan.getId())).thenReturn(Optional.of(conn));

		mockMvc.perform(get("/api/gmail/status")
				.header("Authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.connected").value(true))
			.andExpect(jsonPath("$.gmailAddress").value("dan.gmail@example.com"));
	}

	// -----------------------------------------------------------------------
	// Teste 7: GET /api/gmail/status – desconectado
	// -----------------------------------------------------------------------
	@Test
	void getStatus_whenNotConnected_returnsConnectedFalse() throws Exception {
		register("Eve Gmail", "eve.gmail@example.com", "password123");
		String token = loginAndGetToken("eve.gmail@example.com", "password123");

		when(gmailService.findConnectionByUserId(anyLong())).thenReturn(Optional.empty());

		mockMvc.perform(get("/api/gmail/status")
				.header("Authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.connected").value(false));
	}

	// -----------------------------------------------------------------------
	// Teste 8: POST /api/gmail/disconnect com JWT – desconecta com sucesso
	// -----------------------------------------------------------------------
	@Test
	void disconnect_withValidJwt_returnsSuccessMessage() throws Exception {
		register("Frank Gmail", "frank.gmail@example.com", "password123");
		String token = loginAndGetToken("frank.gmail@example.com", "password123");

		doNothing().when(gmailService).disconnectUser(anyLong());

		mockMvc.perform(post("/api/gmail/disconnect")
				.header("Authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.message").value("Gmail desconectado com sucesso."));

		verify(gmailService).disconnectUser(anyLong());
	}

	// -----------------------------------------------------------------------
	// Teste: POST /api/gmail/connect-app-password – credenciais válidas
	// -----------------------------------------------------------------------
	@Test
	void connectAppPassword_withValidCredentials_returnsConnectedAddress() throws Exception {
		register("Eve Gmail", "eve.gmail@example.com", "password123");
		String token = loginAndGetToken("eve.gmail@example.com", "password123");
		User eve = userRepository.findByEmailIgnoreCase("eve.gmail@example.com").orElseThrow();

		GmailConnection conn = GmailConnection.builder()
			.user(eve)
			.gmailAddress("eve@gmail.com")
			.active(true)
			.build();
		when(gmailService.connectWithAppPassword(any(User.class), eq("eve@gmail.com"), eq("abcd efgh ijkl mnop")))
			.thenReturn(conn);

		mockMvc.perform(post("/api/gmail/connect-app-password")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"email":"eve@gmail.com","appPassword":"abcd efgh ijkl mnop"}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.gmailAddress").value("eve@gmail.com"));
	}

	// -----------------------------------------------------------------------
	// Teste: POST /api/gmail/connect-app-password – senha recusada pelo Google
	// -----------------------------------------------------------------------
	@Test
	void connectAppPassword_withRejectedCredentials_returnsBadRequest() throws Exception {
		register("Fay Gmail", "fay.gmail@example.com", "password123");
		String token = loginAndGetToken("fay.gmail@example.com", "password123");

		when(gmailService.connectWithAppPassword(any(User.class), anyString(), anyString()))
			.thenThrow(new IllegalArgumentException("O Google recusou o e-mail ou a senha de app."));

		mockMvc.perform(post("/api/gmail/connect-app-password")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"email":"fay@gmail.com","appPassword":"wrong"}
					"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("O Google recusou o e-mail ou a senha de app."));
	}

	// -----------------------------------------------------------------------
	// Teste: POST /api/gmail/sync – falha temporária não marca como desconectado
	// -----------------------------------------------------------------------
	@Test
	void syncEmails_withTransientFailure_returnsBadGatewayWithoutDisconnecting() throws Exception {
		register("Gus Gmail", "gus.gmail@example.com", "password123");
		String token = loginAndGetToken("gus.gmail@example.com", "password123");

		when(gmailService.syncEmails(anyLong()))
			.thenThrow(new GmailSyncException("Não foi possível acessar o Gmail agora.", null));

		mockMvc.perform(post("/api/gmail/sync")
				.header("Authorization", "Bearer " + token))
			.andExpect(status().isBadGateway())
			.andExpect(jsonPath("$.message").value("Não foi possível acessar o Gmail agora."))
			.andExpect(jsonPath("$.connected").doesNotExist());
	}
}
