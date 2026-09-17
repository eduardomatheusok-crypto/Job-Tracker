package jobtracker.integration.gmail;

import java.time.Instant;
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
import jobtracker.repository.EmailRepository;
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
		"candidatura", "candidatar", "candidatar-se", "candidato", "candidata",
		"vaga", "vagas", "processo seletivo", "entrevista", "entrevistas",
		"recrutamento", "recruiting", "inscrição", "inscricao", "inscrito",
		"pré-selecionado", "pré selecionado", "pré-seleção", "pré seleção",
		"hiring", "talent", "talentos", "career", "careers", "carreira", "carreiras",
		"job offer", "offer letter", "carta de oferta", "proposta", "contratação",
		"contratacao", "join our team", "thank you for applying", "you applied",
		"your application", "application received", "application at", "application for",
		"submitted your application", "welcome to the hiring process", "selection process",
		"job posting", "position", "opportunity", "oportunidade", "emprego", "currículo",
		"curriculo", "cv", "gupy", "greenhouse", "lever", "workable", "etapa", "feedback"
	);

	/** Captura palavras com unicode (acentos), números e os caracteres típicos de cargo. */
	private static final String TOKEN = "[\\p{L}\\p{N}_\\-\\/#\\+]+";
	/** Palavras de ligação que indicam o fim do cargo ("vaga de Backend aberta na Acme"...). */
	private static final String STOPWORDS =
		"na|no|em|de|da|do|para|com|por|aberta|aberto|remoto|presencial|hibrido|híbrido";
	/** Captura o cargo parando na primeira palavra de ligação (limite de 6 palavras). */
	private static final String POSITION_GROUP =
		"(" + TOKEN + "(?:\\s+(?!" + STOPWORDS + "\\b)" + TOKEN + "){0,5})";

	/** Palavras de ligação que indicam o fim do nome da empresa ("vaga da vaga PROCESSO DE SELE"...). */
	private static final String COMPANY_STOPWORDS =
		"de|da|do|das|dos|na|no|em|para|com|por|vaga|vacante|open|remote|remoto|aberta|aberto|position|cargo";
	/** Captura o nome da empresa parando em palavras de ligação (limite de 4 palavras). */
	private static final String COMPANY_GROUP =
		"(" + TOKEN + "(?:\\s+(?!" + COMPANY_STOPWORDS + "\\b)" + TOKEN + "){0,3})";

	private final ApplicationRepository applicationRepository;
	private final EmailRepository emailRepository;
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
			backfillAppliedAt(existingApp);
		} else {
			ApplicationRequest createRequest = ApplicationRequest.builder()
				.companyName(companyName)
				.position(position)
				.status(status)
				.appliedAt(email.getReceivedAt())
				.notes("[Auto-Sync] Candidatura criada automaticamente baseado no e-mail: " + subject)
				.build();
			var newAppResponse = applicationService.createApplication(createRequest, email.getUser());

			Application newApp = applicationRepository.getReferenceById(newAppResponse.getId());
			email.setApplication(newApp);
		}
	}

	/**
	 * Ajusta {@link Application#getAppliedAt()} para a data do e-mail mais antigo
	 * vinculado à candidatura, caso ela ainda não tenha sido definida (ex.: quando
	 * a candidatura foi criada por um e-mail com status diferente de APPLIED).
	 * Só faz backfill se o valor atual for nulo ou posterior a algum e-mail vinculado.
	 */
	private void backfillAppliedAt(Application application) {
		if (application.getId() == null) {
			return;
		}
		Instant earliest = emailRepository.findReceivedAtOrderedAsc(application.getId())
			.stream()
			.findFirst()
			.orElse(null);
		if (earliest == null) {
			return;
		}
		if (application.getAppliedAt() == null || earliest.isBefore(application.getAppliedAt())) {
			application.setAppliedAt(earliest);
		}
	}

	private String parseCompanyName(String subject, String body, String contactAddress) {
		String[] subjectPatterns = {
			"(?i)(?:candidatura|vaga|inscrição|inscricao|oportunidade|seleção|selecao)\\s+(?:na|no|em|pela|at)\\s+" + COMPANY_GROUP,
			"(?i)processo seletivo\\s+(?:da|do|de|na|no|em|at)?\\s*" + COMPANY_GROUP,
			"(?i)(?:sua )?inscrição (?:foi )?(?:confirmada|recebida|realizada)\\s+(?:na|no|em|pela|at)\\s+" + COMPANY_GROUP,
			"(?i)(?:obrigado por se candidatar|obrigado pelo interesse)\\s+(?:na|no|em|à|ao|da|do|at)\\s+" + COMPANY_GROUP,
			"(?i)candidatura recebida\\s*[-:]\\s*" + COMPANY_GROUP,
			"(?i)greenhouse application\\s*[-:]\\s*" + COMPANY_GROUP,
			"(?i)application (?:at|to)\\s+" + COMPANY_GROUP,
			"(?i)thank you for applying (?:to|at)\\s+" + COMPANY_GROUP,
			"(?i)\\[([^\\]]+)\\]\\s*(?:sua candidatura|inscrição|processo seletivo|vaga|obrigado|recebemos)"
		};

		for (String regex : subjectPatterns) {
			Pattern pattern = Pattern.compile(regex);
			Matcher matcher = pattern.matcher(subject);
			if (matcher.find()) {
				String extracted = matcher.group(1).trim();
				if (!extracted.isBlank() && extracted.length() >= 2) {
					return extracted;
				}
			}
		}

		String companyFromDisplayName = extractCompanyFromDisplayName(contactAddress);
		if (companyFromDisplayName != null && hasRecruitingSignal(subject, body)) {
			return companyFromDisplayName;
		}

		if (contactAddress != null && contactAddress.contains("@")) {
			String emailDomain = contactAddress.substring(contactAddress.lastIndexOf("@") + 1).replace(">", "").trim();
			String companyFromDomain = extractCompanyFromDomain(emailDomain);
			if (companyFromDomain != null && hasRecruitingSignal(subject, body)) {
				return companyFromDomain;
			}
		}

		String[] bodyPatterns = {
			"(?i)obrigado por se candidatar (?:na|no|em|para|à|ao|da|do)\\s+" + COMPANY_GROUP,
			"(?i)sua candidatura para\\s+" + COMPANY_GROUP,
			"(?i)welcome to the hiring process at\\s+" + COMPANY_GROUP,
			"(?i)inscrição realizada com sucesso (?:na|no|em|para)\\s+" + COMPANY_GROUP,
			"(?i)equipe de recrutamento (?:da|do|de)\\s+" + COMPANY_GROUP
		};

		for (String regex : bodyPatterns) {
			Pattern pattern = Pattern.compile(regex);
			Matcher matcher = pattern.matcher(body);
			if (matcher.find()) {
				String extracted = matcher.group(1).trim();
				if (!extracted.isBlank() && extracted.length() >= 2) {
					return extracted;
				}
			}
		}

		return null;
	}

	private String extractCompanyFromDisplayName(String contactAddress) {
		if (contactAddress == null || !contactAddress.contains("<")) {
			return null;
		}
		String namePart = contactAddress.substring(0, contactAddress.indexOf("<")).trim();
		namePart = namePart.replaceAll("^[\"']+|[\"']+$", "").trim();

		if (namePart.isBlank()) {
			return null;
		}

		// Remove sufixos como " via Gupy", " via Greenhouse", " - Recrutamento", " Careers", etc.
		String cleaned = namePart.replaceAll("(?i)\\s*(?:via\\s+(?:gupy|greenhouse|lever|workable|jobscore)|careers|recrutamento|recruiting|talent(?:s)? acquisition|rh|hr|jobs|team|equipe|notificações|notificacoes|notificacao).*$", "").trim();
		cleaned = cleaned.replaceAll("^[\"'\\[\\(]+|[\"'\\)\\]]+$", "").trim();

		String lower = cleaned.toLowerCase(Locale.ROOT);
		boolean blocked = NON_RECRUITING_DOMAINS.stream().anyMatch(d -> lower.equals(d) || lower.startsWith(d));
		if (blocked || cleaned.length() < 2 || cleaned.length() > 50) {
			return null;
		}

		return capitalize(cleaned);
	}

	private String parsePosition(String subject, String body) {
		String[] positionPatterns = {
			"(?i)(?:vaga|cargo|oportunidade|candidatura|posição|posicao)\\s+(?:de|para|for|as)\\s+" + POSITION_GROUP,
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

	/**
	 * Extrai um nome de empresa coerente a partir do domínio do remetente.
	 * Remove subdomínios comuns (mail., hr., jobs., recruiting., careers., info., no-reply...)
	 * e a extensão de TLD, capitalizando o nome principal.
	 * Ex.: "recrutamento@hr.acme.com.br" → "Acme", "noreply@jobs.acme.io" → "Acme".
	 */
	private String extractCompanyFromDomain(String domain) {
		if (domain == null || domain.isBlank()) {
			return null;
		}
		String effectiveDomain = domain.toLowerCase(Locale.ROOT);

		String[] parts = effectiveDomain.split("\\.");

		// Encontra o índice do nome principal: pula subdomínios, ignora a extensão (último segmento).
		int tldIndex = parts.length - 1;
		int nameIndex;
		// Se houver segundo nível como "com" (ex.: acme.com.br), ele é o nome antes do TLD de país.
		if (parts.length >= 3
			&& (parts[tldIndex - 1].equals("com") || parts[tldIndex - 1].equals("co")
				|| parts[tldIndex - 1].equals("gov") || parts[tldIndex - 1].equals("org") || parts[tldIndex - 1].equals("net"))) {
			nameIndex = tldIndex - 2;
		} else {
			nameIndex = tldIndex - 1;
		}

		if (nameIndex < 0) {
			return null;
		}

		String name = parts[nameIndex];

		// Bloqueia plataformas de recrutamento/infraestrutura conhecidas.
		String lowerName = name.toLowerCase(Locale.ROOT);
		boolean blocked = NON_RECRUITING_DOMAINS.stream()
			.anyMatch(d -> lowerName.equals(d) || lowerName.startsWith(d));
		if (blocked) {
			return null;
		}

		// Ignora nomes muito curtos ou genéricos que não representam uma empresa real.
		if (name.isBlank() || name.length() < 3) {
			return null;
		}

		return capitalize(name);
	}

	private String capitalize(String str) {
		if (str == null || str.isEmpty()) {
			return str;
		}
		return str.substring(0, 1).toUpperCase() + str.substring(1);
	}
}
