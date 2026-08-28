package jobtracker;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import jobtracker.entity.User;
import jobtracker.entity.ApplicationHistory;
import jobtracker.entity.ApplicationStatus;
import jobtracker.repository.UserRepository;
import jobtracker.repository.ApplicationRepository;
import jobtracker.repository.ApplicationHistoryRepository;

@SpringBootTest
class JobtrackerIntegrationTests {

	private MockMvc mockMvc;

	@Autowired
	private WebApplicationContext webApplicationContext;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private ApplicationRepository applicationRepository;

	@Autowired
	private ApplicationHistoryRepository applicationHistoryRepository;

	@BeforeEach
	void setUp() {
		applicationHistoryRepository.deleteAll();
		applicationRepository.deleteAll();
		userRepository.deleteAll();

		mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
			.apply(springSecurity())
			.build();
	}

	@Test
	void registerLoginJwtAndProtectedEndpointsShouldWork() throws Exception {
		register("Alice Example", "alice1@example.com", "password123");
		String aliceToken = loginAndGetToken("alice1@example.com", "password123");

		mockMvc.perform(get("/api/applications"))
			.andExpect(status().isUnauthorized());

		long applicationId = createApplicationAndGetId(aliceToken, """
			{
				"companyName":"OpenAI",
				"position":"Backend Engineer",
				"location":"Remote",
				"jobUrl":"https://example.com/jobs/1",
				"notes":"First application",
				"status":"APPLIED"
			}
			""");

		mockMvc.perform(get("/api/applications")
				.header("Authorization", bearer(aliceToken)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content", hasSize(1)))
			.andExpect(jsonPath("$.content[0].companyName").value("OpenAI"));

		mockMvc.perform(get("/api/applications/" + applicationId)
				.header("Authorization", bearer(aliceToken)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.companyName").value("OpenAI"));

		mockMvc.perform(put("/api/applications/" + applicationId)
				.header("Authorization", bearer(aliceToken))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
						"status":"INTERVIEW",
						"notes":"Updated note"
					}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("INTERVIEW"))
			.andExpect(jsonPath("$.notes").value("Updated note"));

		mockMvc.perform(delete("/api/applications/" + applicationId)
				.header("Authorization", bearer(aliceToken)))
			.andExpect(status().isNoContent());

		mockMvc.perform(get("/api/applications")
				.header("Authorization", bearer(aliceToken))
			)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content", hasSize(0)));
	}

	@Test
	void protectedEndpointsShouldRejectRequestsWithoutJwt() throws Exception {
		mockMvc.perform(get("/api/applications"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.error").value("Unauthorized"))
			.andExpect(jsonPath("$.message").value("Authentication required"));
	}

	@Test
	void errorResponses_useApiErrorEnvelope() throws Exception {
		register("Alice Error", "alice.error@example.com", "password123");
		register("Bob Error", "bob.error@example.com", "password123");
		String aliceToken = loginAndGetToken("alice.error@example.com", "password123");
		String bobToken = loginAndGetToken("bob.error@example.com", "password123");

		long applicationId = createApplicationAndGetId(aliceToken, """
			{
				"companyName":"OpenAI",
				"position":"Backend Engineer",
				"status":"APPLIED"
			}
			""");

		mockMvc.perform(post("/api/applications")
				.header("Authorization", bearer(aliceToken))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
						"companyName":"",
						"position":""
					}
					"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error").value("Validation Failed"))
			.andExpect(jsonPath("$.details", hasSize(2)));

		mockMvc.perform(get("/api/applications/" + applicationId)
				.header("Authorization", bearer(bobToken)))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error").value("Not Found"));

		mockMvc.perform(get("/api/emails/999999")
				.header("Authorization", bearer(bobToken)))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error").value("Not Found"));

		mockMvc.perform(post("/api/auth/register")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
						"name":"Alice Error",
						"email":"alice.error@example.com",
						"password":"password123"
					}
					"""))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.error").value("Conflict"));
	}

	@Test
	void oneUserShouldNotAccessAnotherUsersApplication() throws Exception {
		register("Alice Example", "alice2@example.com", "password123");
		register("Bob Example", "bob2@example.com", "password123");

		String aliceToken = loginAndGetToken("alice2@example.com", "password123");
		String bobToken = loginAndGetToken("bob2@example.com", "password123");

		long applicationId = createApplicationAndGetId(aliceToken, """
			{
				"companyName":"Alpha Corp",
				"position":"Engineer"
			}
			""");

		mockMvc.perform(get("/api/applications/" + applicationId)
				.header("Authorization", bearer(bobToken)))
			.andExpect(status().isNotFound());

		mockMvc.perform(put("/api/applications/" + applicationId)
				.header("Authorization", bearer(bobToken))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
						"notes":"Should not update"
					}
					"""))
			.andExpect(status().isNotFound());

		mockMvc.perform(delete("/api/applications/" + applicationId)
				.header("Authorization", bearer(bobToken)))
			.andExpect(status().isNotFound());
	}

	@Test
	void registerShouldFailWhenEmailAlreadyExists() throws Exception {
		register("Alice Example", "alice@example.com", "password123");

		mockMvc.perform(post("/api/auth/register")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
						"name":"Alice Second",
						"email":"alice@example.com",
						"password":"password321"
					}
					"""))
			.andExpect(status().isConflict());
	}

	@Test
	void loginShouldFailWithInvalidCredentials() throws Exception {
		register("Alice Example", "alice@example.com", "password123");

		// Senha incorreta
		mockMvc.perform(post("/api/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
						"email":"alice@example.com",
						"password":"wrongpassword"
					}
					"""))
			.andExpect(status().isUnauthorized());

		// E-mail não existente
		mockMvc.perform(post("/api/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
						"email":"nonexistent@example.com",
						"password":"password123"
					}
					"""))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void registerShouldFailWithInvalidData() throws Exception {
		// E-mail inválido
		mockMvc.perform(post("/api/auth/register")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
						"name":"Bob",
						"email":"invalid-email",
						"password":"password123"
					}
					"""))
			.andExpect(status().isBadRequest());

		// Senha curta (menos de 8 caracteres)
		mockMvc.perform(post("/api/auth/register")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
						"name":"Bob",
						"email":"bob@example.com",
						"password":"short"
					}
					"""))
			.andExpect(status().isBadRequest());

		// Nome em branco
		mockMvc.perform(post("/api/auth/register")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
						"name":"   ",
						"email":"bob@example.com",
						"password":"password123"
					}
					"""))
			.andExpect(status().isBadRequest());
	}

	@Test
	void createApplicationShouldFailWithInvalidData() throws Exception {
		register("Alice Example", "alice@example.com", "password123");
		String token = loginAndGetToken("alice@example.com", "password123");

		// Empresa em branco
		mockMvc.perform(post("/api/applications")
				.header("Authorization", bearer(token))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
						"companyName":"   ",
						"position":"Software Engineer"
					}
					"""))
			.andExpect(status().isBadRequest());

		// Cargo em branco
		mockMvc.perform(post("/api/applications")
				.header("Authorization", bearer(token))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
						"companyName":"Google",
						"position":""
					}
					"""))
			.andExpect(status().isBadRequest());
	}

	@Test
	void applicationHistoryShouldBeRecordedOnCreationAndUpdate() throws Exception {
		register("Alice Example", "alice@example.com", "password123");
		String token = loginAndGetToken("alice@example.com", "password123");

		// 1. Criação de candidatura
		long applicationId = createApplicationAndGetId(token, """
			{
				"companyName":"Netflix",
				"position":"Fullstack Developer",
				"status":"SAVED"
			}
			""");

		// Verifica que o histórico registrou a criação
		List<ApplicationHistory> historyAfterCreation = applicationHistoryRepository.findByApplicationIdOrderByChangedAtDesc(applicationId);
		assertEquals(1, historyAfterCreation.size());
		ApplicationHistory creationRecord = historyAfterCreation.get(0);
		assertEquals(ApplicationStatus.SAVED, creationRecord.getNewStatus());
		assertEquals("created", creationRecord.getChangedField());
		assertEquals("Application created", creationRecord.getNote());
		assertNotNull(creationRecord.getChangedByUser());
		User creationUser = userRepository.findById(creationRecord.getChangedByUser().getId()).orElseThrow();
		assertEquals("alice@example.com", creationUser.getEmail());

		// 2. Atualização de status da candidatura
		mockMvc.perform(put("/api/applications/" + applicationId)
				.header("Authorization", bearer(token))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
						"status":"INTERVIEW",
						"notes":"Got an interview scheduled"
					}
					"""))
			.andExpect(status().isOk());

		// Verifica que o histórico registrou a alteração
		List<ApplicationHistory> historyAfterUpdate = applicationHistoryRepository.findByApplicationIdOrderByChangedAtDesc(applicationId);
		assertEquals(2, historyAfterUpdate.size());
		
		// O registro mais recente (índice 0, ordenado descendente) deve ser o update
		ApplicationHistory updateRecord = historyAfterUpdate.get(0);
		assertEquals(ApplicationStatus.SAVED, updateRecord.getPreviousStatus());
		assertEquals(ApplicationStatus.INTERVIEW, updateRecord.getNewStatus());
		assertTrue(updateRecord.getChangedField().contains("status"));
		assertTrue(updateRecord.getChangedField().contains("notes"));
		assertNotNull(updateRecord.getChangedByUser());
		User updateUser = userRepository.findById(updateRecord.getChangedByUser().getId()).orElseThrow();
		assertEquals("alice@example.com", updateUser.getEmail());
	}

	@Test
	void getUserProfile_withValidToken_returnsUserProfile() throws Exception {
		register("Zack Example", "zack@example.com", "password123");
		String token = loginAndGetToken("zack@example.com", "password123");

		mockMvc.perform(get("/api/users/me")
				.header("Authorization", bearer(token)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.name").value("Zack Example"))
			.andExpect(jsonPath("$.email").value("zack@example.com"))
			.andExpect(jsonPath("$.role").value("USER"));
	}

	@Test
	void getUserProfile_withoutToken_returnsUnauthorized() throws Exception {
		mockMvc.perform(get("/api/users/me"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.error").value("Unauthorized"));
	}

	private void register(String name, String email, String password) throws Exception {
		mockMvc.perform(post("/api/auth/register")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
						"name":"%s",
						"email":"%s",
						"password":"%s"
					}
					""".formatted(name, email, password)))
			.andExpect(status().isCreated());
	}

	private String loginAndGetToken(String email, String password) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
						"email":"%s",
						"password":"%s"
					}
					""".formatted(email, password)))
			.andExpect(status().isOk())
			.andReturn();

		JsonNode node = new ObjectMapper().readTree(result.getResponse().getContentAsString());
		assertNotNull(node.get("token"));
		return node.get("token").asText();
	}

	private long createApplicationAndGetId(String token, String jsonBody) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/applications")
				.header("Authorization", bearer(token))
				.contentType(MediaType.APPLICATION_JSON)
				.content(jsonBody))
			.andExpect(status().isCreated())
			.andReturn();

		JsonNode node = new ObjectMapper().readTree(result.getResponse().getContentAsString());
		return node.get("id").asLong();
	}

	private String bearer(String token) {
		return "Bearer " + token;
	}
}
