package jobtracker.integration.gmail;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import jobtracker.dto.application.ApplicationRequest;
import jobtracker.dto.application.UpdateApplicationRequest;
import jobtracker.entity.Application;
import jobtracker.entity.ApplicationStatus;
import jobtracker.entity.Email;
import jobtracker.entity.EmailDirection;
import jobtracker.repository.ApplicationRepository;
import jobtracker.service.ApplicationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class EmailParserService {

	private static final int NOTES_MAX_LENGTH = 2000;

	/** Domínios de serviços/plataformas que nunca devem virar candidatura. */
	private static final List<String> NON_RECRUITING_DOMAINS = List.of(
		"gmail", "yahoo", "outlook", "hotmail", "gupy", "greenhouse", "linkedin",
		"render", "vercel", "netlify", "github", "gitlab", "bitbucket", "digitalocean",
		"heroku", "aws.amazon", "cloudflare", "sendgrid", "mailchimp", "statuspage"
	);

	/** Sinais de que o e-mail realmente trata de recrutamento (usado no fallback por domínio). */
	private static final List<String> RECRUITING_SIGNALS = List.of(
		"candidatura", "candidatar", "candidatar-se",
		"vaga", "vagas", "processo seletivo", "entrevista", "entrevistas",
		"recrutamento", "recruiting", "inscrição", "inscricao",
		"pré-selecionado", "pré selecionado", "pré-seleção", "pré seleção",
		"hiring", "talent", "career", "job offer", "offer letter", "carta de oferta",
		"proposta", "contratação", "contratacao", "join our team",
		"thank you for applying", "you applied", "your application",
		"application received", "application at", "application for",
		"submitted your application", "welcome to the hiring process",
		"selection process", "job posting", "position",
		"opportunity", "emprego"
	);

	/** Captura palavras com unicode (acentos), números e os caracteres típicos de cargo. */
	private static final String TOKEN = "[\\p{L}\\p{N}_\\-\\/#\\+]+";
	/** Palavras de ligação que indicam o fim do cargo ("vaga de Backend aberta na Acme"...). */
	private static final String STOPWORDS =
		"na|no|em|de|da|do|para|com|por|aberta|aberto|remoto|presencial|hibrido|híbrido";
	/** Captura o cargo parando na primeira palavra de ligação (limite de 6 palavras). */
	private static final String POSITION_GROUP =
		"(" + TOKEN + "(?:\\s+(?!" + STOPWORDS + "\\b)" + TOKEN + "){0,5})";

	private final ApplicationRepository applicationRepository;
	private final ApplicationService applicationService;

	public void parseAndProcess(Email email) {
		String subject = email.getSubject() != null ? email.getSubject() : "";
		String body = email.getRawContent() != null ? email.getRawContent() : "";
		String fullText = (subject + " " + body).toLowerCase(Locale.ROOT);

		boolean sent = email.getDirection() == EmailDirection.SENT;
		String contactAddress = sent ? email.getToAddress() : email.getFromAddress();

		String companyName = parseCompanyName(subject, body, contactAddress);
		if (companyName == null || companyName.isBlank()) {
			return;
		}

		String position = parsePosition(subject, body);
		ApplicationStatus status = parseStatus(fullText);

		Optional<Application> existingAppOpt = applicationRepository
			.findFirstByUserIdAndCompanyNameIgnoreCaseOrderByCreatedAtDesc(email.getUser().getId(), companyName);

		if (existingAppOpt.isPresent()) {
			Application existingApp = existingAppOpt.get();
			if (status != existingApp.getStatus() && status != ApplicationStatus.SAVED) {
				String autoNote = "[Auto-Sync] Status atualizado baseado no e-mail: " + subject;
				String combined = StringUtils.hasText(existingApp.getNotes())
					? existingApp.getNotes() + "\n" + autoNote
					: autoNote;
				String newNotes = combined.length() > NOTES_MAX_LENGTH
					? combined.substring(0, NOTES_MAX_LENGTH - 3) + "..."
					: combined;

				UpdateApplicationRequest updateRequest = UpdateApplicationRequest.builder()
					.status(status)
					.notes(newNotes)
					.build();
				applicationService.updateApplication(existingApp.getId(), updateRequest, email.getUser());
				email.setApplication(existingApp);
			} else {
				email.setApplication(existingApp);
			}
		} else {
			ApplicationRequest createRequest = ApplicationRequest.builder()
				.companyName(companyName)
				.position(position)
				.status(status)
				.notes("[Auto-Sync] Candidatura criada automaticamente baseado no e-mail: " + subject)
				.build();
			var newAppResponse = applicationService.createApplication(createRequest, email.getUser());

			Application newApp = applicationRepository.getReferenceById(newAppResponse.getId());
			email.setApplication(newApp);
		}
	}

	private String parseCompanyName(String subject, String body, String contactAddress) {
		String[] subjectPatterns = {
			"(?i)candidatura na\\s+([A-Za-z0-9\\s_\\-]+)",
			"(?i)vaga na\\s+([A-Za-z0-9\\s_\\-]+)",
			"(?i)processo seletivo\\s+([A-Za-z0-9\\s_\\-]+)",
			"(?i)inscrição confirmada na\\s+([A-Za-z0-9\\s_\\-]+)",
			"(?i)candidatura recebida\\s*\\-\\s*([A-Za-z0-9\\s_\\-]+)",
			"(?i)greenhouse application\\s*\\-\\s*([A-Za-z0-9\\s_\\-]+)",
			"(?i)application at\\s+([A-Za-z0-9\\s_\\-]+)",
			"(?i)thank you for applying to\\s+([A-Za-z0-9\\s_\\-]+)"
		};

		for (String regex : subjectPatterns) {
			Pattern pattern = Pattern.compile(regex);
			Matcher matcher = pattern.matcher(subject);
			if (matcher.find()) {
				return matcher.group(1).trim();
			}
		}

		if (contactAddress != null && contactAddress.contains("@")) {
			String domain = contactAddress.substring(contactAddress.indexOf("@") + 1);
			domain = domain.replaceAll("(?i)\\.(com|com\\.br|net|org|io|co|tech|dev|ai)$", "").trim();
			String effectiveDomain = domain.toLowerCase(Locale.ROOT);
			boolean blocked = NON_RECRUITING_DOMAINS.stream().anyMatch(d -> effectiveDomain.startsWith(d));
			if (!blocked && hasRecruitingSignal(subject, body)) {
				return capitalize(domain);
			}
		}

		String[] bodyPatterns = {
			"(?i)obrigado por se candidatar na\\s+([A-Za-z0-9\\s_\\-]+)",
			"(?i)sua candidatura para\\s+([A-Za-z0-9\\s_\\-]+)",
			"(?i)welcome to the hiring process at\\s+([A-Za-z0-9\\s_\\-]+)"
		};

		for (String regex : bodyPatterns) {
			Pattern pattern = Pattern.compile(regex);
			Matcher matcher = pattern.matcher(body);
			if (matcher.find()) {
				return matcher.group(1).trim();
			}
		}

		return null;
	}

	private String parsePosition(String subject, String body) {
		String[] positionPatterns = {
			"(?i)vaga de\\s+" + POSITION_GROUP,
			"(?i)vaga para\\s+" + POSITION_GROUP,
			"(?i)cargo de\\s+" + POSITION_GROUP,
			"(?i)candidatura para\\s+" + POSITION_GROUP,
			"(?i)application for\\s+" + POSITION_GROUP,
			"(?i)position:\\s*" + POSITION_GROUP
		};

		for (String regex : positionPatterns) {
			Pattern pattern = Pattern.compile(regex);
			Matcher matcher = pattern.matcher(subject);
			if (matcher.find()) {
				return matcher.group(1).trim();
			}
		}

		for (String regex : positionPatterns) {
			Pattern pattern = Pattern.compile(regex);
			Matcher matcher = pattern.matcher(body);
			if (matcher.find()) {
				return matcher.group(1).trim();
			}
		}

		return "Software Engineer";
	}

	private ApplicationStatus parseStatus(String text) {
		if (text.contains("reprovado") || text.contains("infelizmente") || text.contains("não seguiremos")
			|| text.contains("agradecemos seu interesse") || text.contains("não foi selecionado")
			|| text.contains("no longer under consideration") || text.contains("not moving forward")
			|| text.contains("will not be moving forward") || text.contains("decided to pursue other")) {
			return ApplicationStatus.REJECTED;
		}

		if (text.contains("proposta") || text.contains("carta de oferta") || text.contains("parabéns você foi aprovado")
			|| text.contains("gostaríamos de te contratar") || text.contains("job offer") || text.contains("offer letter")
			|| text.contains("congratulations on your offer")) {
			return ApplicationStatus.ACCEPTED;
		}

		if (text.contains("entrevista") || text.contains("conversar") || text.contains("agendar")
			|| text.contains("schedule a chat") || text.contains("interview") || text.contains("hiring manager")
			|| text.contains("video call") || text.contains("link da reunião")) {
			return ApplicationStatus.INTERVIEW;
		}

		return ApplicationStatus.APPLIED;
	}

	private boolean hasRecruitingSignal(String subject, String body) {
		String fullText = (subject + " " + body).toLowerCase(Locale.ROOT);
		return RECRUITING_SIGNALS.stream().anyMatch(fullText::contains);
	}

	private String capitalize(String str) {
		if (str == null || str.isEmpty()) {
			return str;
		}
		return str.substring(0, 1).toUpperCase() + str.substring(1);
	}
}
