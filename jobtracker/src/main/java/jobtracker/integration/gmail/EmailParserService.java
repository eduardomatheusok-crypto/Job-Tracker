package jobtracker.integration.gmail;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import jobtracker.dto.application.ApplicationRequest;
import jobtracker.dto.application.UpdateApplicationRequest;
import jobtracker.entity.Application;
import jobtracker.entity.ApplicationStatus;
import jobtracker.entity.Email;
import jobtracker.repository.ApplicationRepository;
import jobtracker.service.ApplicationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmailParserService {

	private final ApplicationRepository applicationRepository;
	private final ApplicationService applicationService;

	public void parseAndProcess(Email email) {
		String subject = email.getSubject() != null ? email.getSubject() : "";
		String body = email.getRawContent() != null ? email.getRawContent() : "";
		String fullText = (subject + " " + body).toLowerCase();

		String companyName = parseCompanyName(subject, body, email.getFromAddress());
		if (companyName == null || companyName.isBlank()) {
			return;
		}

		String position = parsePosition(subject, body);
		ApplicationStatus status = parseStatus(fullText);

		Optional<Application> existingAppOpt = applicationRepository.findAllByUserIdOrderByCreatedAtDesc(email.getUser().getId())
			.stream()
			.filter(app -> app.getCompanyName().equalsIgnoreCase(companyName))
			.findFirst();

		if (existingAppOpt.isPresent()) {
			Application existingApp = existingAppOpt.get();
			if (status != existingApp.getStatus() && status != ApplicationStatus.SAVED) {
				UpdateApplicationRequest updateRequest = UpdateApplicationRequest.builder()
					.status(status)
					.notes(existingApp.getNotes() + "\n[Auto-Sync] Status atualizado baseado no e-mail: " + subject)
					.build();
				applicationService.updateApplication(existingApp.getId(), updateRequest);
				email.setApplication(existingApp);
			} else {
				email.setApplication(existingApp);
			}
		} else {
			if (status != ApplicationStatus.REJECTED) {
				ApplicationRequest createRequest = ApplicationRequest.builder()
					.companyName(companyName)
					.position(position)
					.status(status)
					.notes("[Auto-Sync] Candidatura criada automaticamente baseado no e-mail: " + subject)
					.build();
				var newAppResponse = applicationService.createApplication(createRequest);
				
				Application newApp = applicationRepository.findById(newAppResponse.getId()).orElse(null);
				email.setApplication(newApp);
			}
		}
	}

	private String parseCompanyName(String subject, String body, String fromAddress) {
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

		if (fromAddress != null && fromAddress.contains("@")) {
			String domain = fromAddress.substring(fromAddress.indexOf("@") + 1);
			domain = domain.replaceAll("(?i)\\.(com|com\\.br|net|org|io|co|tech|dev|ai)$", "").trim();
			if (!domain.equalsIgnoreCase("gmail") && !domain.equalsIgnoreCase("yahoo") && !domain.equalsIgnoreCase("outlook")
				&& !domain.equalsIgnoreCase("gupy") && !domain.equalsIgnoreCase("greenhouse") && !domain.equalsIgnoreCase("linkedin")) {
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
			"(?i)vaga de\\s+([A-Za-z0-9\\s_\\-\\/#\\+]+)",
			"(?i)vaga para\\s+([A-Za-z0-9\\s_\\-\\/#\\+]+)",
			"(?i)cargo de\\s+([A-Za-z0-9\\s_\\-\\/#\\+]+)",
			"(?i)candidatura para\\s+([A-Za-z0-9\\s_\\-\\/#\\+]+)",
			"(?i)application for\\s+([A-Za-z0-9\\s_\\-\\/#\\+]+)",
			"(?i)position:\\s*([A-Za-z0-9\\s_\\-\\/#\\+]+)"
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

	private String capitalize(String str) {
		if (str == null || str.isEmpty()) {
			return str;
		}
		return str.substring(0, 1).toUpperCase() + str.substring(1);
	}
}
