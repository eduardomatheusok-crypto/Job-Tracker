package jobtracker;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jobtracker.entity.Application;
import jobtracker.entity.Email;
import jobtracker.entity.User;
import jobtracker.repository.ApplicationHistoryRepository;
import jobtracker.repository.ApplicationRepository;
import jobtracker.repository.EmailRepository;
import jobtracker.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
class HistoryAndEmailIntegrationTests {

	private MockMvc mockMvc;

	@Autowired
	private WebApplicationContext webApplicationContext;

	@Autowired
	private ApplicationHistoryRepository applicationHistoryRepository;

	@Autowired
	private ApplicationRepository applicationRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private EmailRepository emailRepository;

	private final ObjectMapper objectMapper = new ObjectMapper();

	@BeforeEach
	void setUp() {
		emailRepository.deleteAll();
		applicationHistoryRepository.deleteAll();
		applicationRepository.deleteAll();
		userRepository.deleteAll();

		mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
			.apply(springSecurity())
			.build();
	}

	@Test
	void historyEndpoint_returnsCreationAndUpdateRecordsForOwnedApplication() throws Exception {
		String token = registerAndLogin("alice.history@example.com");

		long applicationId = createApplication(token, """
			{
				"companyName":"Netflix",
				"position":"Developer",
				"status":"SAVED"
			}
			""");

		mockMvc.perform(put("/api/applications/" + applicationId)
				.header("Authorization", bearer(token))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
						"status":"INTERVIEW",
						"notes":"Scheduled interview"
					}
					"""))
			.andExpect(status().isOk());

		mockMvc.perform(get("/api/applications/" + applicationId + "/history")
				.header("Authorization", bearer(token)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$", hasSize(2)))
			.andExpect(jsonPath("$[0].previousStatus").value("SAVED"))
			.andExpect(jsonPath("$[0].newStatus").value("INTERVIEW"))
			.andExpect(jsonPath("$[0].applicationId").value(applicationId))
			.andExpect(jsonPath("$[1].newStatus").value("SAVED"))
			.andExpect(jsonPath("$[1].changedField").value("created"));
	}

	@Test
	void historyEndpoint_rejectsAnotherUsersApplication() throws Exception {
		String aliceToken = registerAndLogin("alice.history2@example.com");
		String bobToken = registerAndLogin("bob.history2@example.com");

		long applicationId = createApplication(aliceToken, """
			{
				"companyName":"Alpha Corp",
				"position":"Engineer"
			}
			""");

		mockMvc.perform(get("/api/applications/" + applicationId + "/history")
				.header("Authorization", bearer(bobToken)))
			.andExpect(status().isNotFound());
	}

	@Test
	void getEmails_returnsOwnedEmailsAndFiltersByApplication() throws Exception {
		String token = registerAndLogin("alice.email@example.com");
		User user = userRepository.findByEmailIgnoreCase("alice.email@example.com").orElseThrow();

		long applicationId = createApplication(token, """
			{
				"companyName":"Spotify",
				"position":"Backend",
				"status":"APPLIED"
			}
			""");
		Application application = applicationRepository.findById(applicationId).orElseThrow();

		emailRepository.save(Email.builder()
			.user(user)
			.application(application)
			.messageId("msg-001")
			.subject("Interview invitation - Spotify")
			.fromAddress("careers@spotify.com")
			.snippet("We would like to schedule an interview")
			.rawContent("Hello, we would like to schedule an interview")
			.build());

		mockMvc.perform(get("/api/emails")
				.header("Authorization", bearer(token)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content", hasSize(1)))
			.andExpect(jsonPath("$.content[0].subject").value("Interview invitation - Spotify"))
			.andExpect(jsonPath("$.content[0].applicationId").value(applicationId));

		mockMvc.perform(get("/api/emails")
				.param("applicationId", String.valueOf(applicationId))
				.header("Authorization", bearer(token)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content", hasSize(1)));

		mockMvc.perform(get("/api/emails?applicationId=99999")
				.header("Authorization", bearer(token)))
			.andExpect(status().isNotFound());
	}

	@Test
	void getEmailById_returnsOwnedEmailAndRejectsForeignEmail() throws Exception {
		String aliceToken = registerAndLogin("alice.email2@example.com");
		User alice = userRepository.findByEmailIgnoreCase("alice.email2@example.com").orElseThrow();

		Email email = emailRepository.save(Email.builder()
			.user(alice)
			.messageId("msg-002")
			.subject("Application received")
			.fromAddress("jobs@company.com")
			.build());

		mockMvc.perform(get("/api/emails/" + email.getId())
				.header("Authorization", bearer(aliceToken)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.messageId").value("msg-002"));

		String bobToken = registerAndLogin("bob.email2@example.com");

		mockMvc.perform(get("/api/emails/" + email.getId())
				.header("Authorization", bearer(bobToken)))
			.andExpect(status().isNotFound());

		mockMvc.perform(get("/api/emails")
				.header("Authorization", bearer(bobToken)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content", hasSize(0)));
	}

	@Test
	void summaryEndpoint_returnsCountsByStatusForCurrentUser() throws Exception {
		String aliceToken = registerAndLogin("alice.summary@example.com");
		String bobToken = registerAndLogin("bob.summary@example.com");

		createApplication(aliceToken, """
			{
				"companyName":"Acme",
				"position":"Engineer",
				"status":"APPLIED"
			}
			""");
		createApplication(aliceToken, """
			{
				"companyName":"Globex",
				"position":"Engineer",
				"status":"REJECTED"
			}
			""");
		createApplication(aliceToken, """
			{
				"companyName":"Initech",
				"position":"Engineer",
				"status":"INTERVIEW"
			}
			""");
		createApplication(bobToken, """
			{
				"companyName":"Hooli",
				"position":"Engineer",
				"status":"ACCEPTED"
			}
			""");

		mockMvc.perform(get("/api/applications/summary")
				.header("Authorization", bearer(aliceToken)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.total").value(3))
			.andExpect(jsonPath("$.byStatus.APPLIED").value(1))
			.andExpect(jsonPath("$.byStatus.REJECTED").value(1))
			.andExpect(jsonPath("$.byStatus.INTERVIEW").value(1))
			.andExpect(jsonPath("$.byStatus.ACCEPTED").value(0))
			.andExpect(jsonPath("$.byStatus.SAVED").value(0));

		mockMvc.perform(get("/api/applications/summary")
				.header("Authorization", bearer(bobToken)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.total").value(1))
			.andExpect(jsonPath("$.byStatus.ACCEPTED").value(1));
	}

	private String registerAndLogin(String email) throws Exception {
		String name = email.substring(0, email.indexOf('@'));
		mockMvc.perform(post("/api/auth/register")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
						"name":"%s",
						"email":"%s",
						"password":"password123"
					}
					""".formatted(name, email)))
			.andExpect(status().isCreated());

		return loginAndGetToken(email);
	}

	private String loginAndGetToken(String email) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
						"email":"%s",
						"password":"password123"
					}
					""".formatted(email)))
			.andExpect(status().isOk())
			.andReturn();

		JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
		return node.get("token").asText();
	}

	private long createApplication(String token, String jsonBody) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/applications")
				.header("Authorization", bearer(token))
				.contentType(MediaType.APPLICATION_JSON)
				.content(jsonBody))
			.andExpect(status().isCreated())
			.andReturn();

		JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
		return node.get("id").asLong();
	}

	private String bearer(String token) {
		return "Bearer " + token;
	}
}