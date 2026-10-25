package jobtracker.ia;

import java.time.Instant;
import java.util.Optional;
import jobtracker.entity.AnuncioVaga;
import jobtracker.entity.Application;
import jobtracker.entity.ApplicationStatus;
import jobtracker.entity.Email;
import jobtracker.entity.EmailProcessado;
import jobtracker.entity.SugestaoStatus;
import jobtracker.entity.User;
import jobtracker.repository.AnuncioVagaRepository;
import jobtracker.repository.ApplicationRepository;
import jobtracker.repository.EmailProcessadoRepository;
import jobtracker.repository.EmailRepository;
import jobtracker.repository.SugestaoStatusRepository;
import jobtracker.repository.UserRepository;
import jobtracker.service.ApplicationHistoryService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

/**
 * Classifica um e-mail com o {@link ClassificadorEmailService} e aplica a decisão:
 * <ul>
 *   <li>NAO_RELACIONADO: ignora;
 *   <li>ANUNCIO_VAGA: salva como anúncio;
 *   <li>ATUALIZACAO_CANDIDATURA com confiança &gt;= {@value #LIMIAR_CONFIANCA}: atualiza o status
 *       (histórico com origem AGENTE); abaixo disso, cria uma sugestão pendente.
 * </ul>
 *
 * <p>A chamada ao LLM fica fora da transação; só a gravação da decisão é transacional. Se o LLM
 * falhar (erro ou limite 429), o e-mail não é marcado como processado e volta na próxima sincronização.
 */
@Service
@RequiredArgsConstructor
public class ProcessadorEmailIAService {

	private static final Logger log = LoggerFactory.getLogger(ProcessadorEmailIAService.class);

	static final double LIMIAR_CONFIANCA = 0.7;
	private static final int MAX_TEXTO_CURTO = 150;
	private static final int MAX_TEXTO_LONGO = 2000;
	private static final String CARGO_NAO_INFORMADO = "Não informado";

	public enum Resultado {
		JA_PROCESSADO,
		IGNORADO,
		ANUNCIO_SALVO,
		STATUS_ATUALIZADO,
		SUGESTAO_CRIADA,
		FALHA
	}

	private final ClassificadorEmailService classificador;
	private final EmailProcessadoRepository emailProcessadoRepository;
	private final EmailRepository emailRepository;
	private final UserRepository userRepository;
	private final ApplicationRepository applicationRepository;
	private final AnuncioVagaRepository anuncioVagaRepository;
	private final SugestaoStatusRepository sugestaoStatusRepository;
	private final ApplicationHistoryService applicationHistoryService;
	private final TransactionTemplate transactionTemplate;

	public Resultado processar(Email email) {
		if (emailProcessadoRepository.existsByGmailId(email.getMessageId())) {
			return Resultado.JA_PROCESSADO;
		}

		ClassificacaoEmail classificacao;
		try {
			classificacao = classificador.classificar(email.getFromAddress(), email.getSubject(), email.getRawContent());
		} catch (Exception e) {
			// O Spring AI embrulha o erro do Gemini em "Failed to generate content"; o motivo real está na causa raiz
			String causa = descreverCausaRaiz(e);
			boolean limite = causa.contains("429") || causa.contains("RESOURCE_EXHAUSTED");
			log.warn("IA: falha ao classificar e-mail {} ({}): {}",
				email.getMessageId(), limite ? "limite de requisições" : "erro", causa);
			return Resultado.FALHA;
		}

		if (classificacao == null || classificacao.tipoEmail() == null) {
			log.warn("IA: resposta sem tipoEmail para o e-mail {}", email.getMessageId());
			return Resultado.FALHA;
		}

		Long emailId = email.getId();
		Long userId = email.getUser().getId();
		Resultado resultado = transactionTemplate.execute(status -> aplicar(emailId, userId, classificacao));
		log.info("IA: e-mail {} classificado como {} (status={}, confiança={}) -> {}",
			email.getMessageId(), classificacao.tipoEmail(), classificacao.novoStatus(), classificacao.confianca(), resultado);
		return resultado;
	}

	private Resultado aplicar(Long emailId, Long userId, ClassificacaoEmail c) {
		Email email = emailRepository.findById(emailId).orElseThrow();
		User user = userRepository.getReferenceById(userId);

		Resultado resultado = switch (c.tipoEmail()) {
			case NAO_RELACIONADO -> Resultado.IGNORADO;
			case ANUNCIO_VAGA -> {
				anuncioVagaRepository.save(AnuncioVaga.builder()
					.user(user)
					.email(email)
					.empresa(curto(c.empresa()))
					.cargo(curto(c.cargo()))
					.justificativa(longo(c.justificativa()))
					.build());
				yield Resultado.ANUNCIO_SALVO;
			}
			case ATUALIZACAO_CANDIDATURA -> aplicarAtualizacao(email, user, c);
		};

		email.setProcessedAt(Instant.now());
		emailRepository.save(email);
		emailProcessadoRepository.save(EmailProcessado.builder()
			.gmailId(email.getMessageId())
			.userId(userId)
			.tipoEmail(c.tipoEmail())
			.build());
		return resultado;
	}

	private Resultado aplicarAtualizacao(Email email, User user, ClassificacaoEmail c) {
		String empresa = curto(c.empresa());
		Optional<Application> existente = empresa != null
			? applicationRepository.findFirstByUserIdAndCompanyNameIgnoreCaseOrderByCreatedAtDesc(user.getId(), empresa)
			: Optional.empty();

		boolean confiavel = c.confianca() != null
			&& c.confianca() >= LIMIAR_CONFIANCA
			&& c.novoStatus() != null
			&& empresa != null;

		if (!confiavel) {
			sugestaoStatusRepository.save(SugestaoStatus.builder()
				.user(user)
				.email(email)
				.application(existente.orElse(null))
				.empresa(empresa)
				.cargo(curto(c.cargo()))
				.novoStatus(c.novoStatus())
				.confianca(c.confianca())
				.justificativa(longo(c.justificativa()))
				.build());
			return Resultado.SUGESTAO_CRIADA;
		}

		String nota = longo("[Agente IA] " + c.justificativa() + " (e-mail: " + email.getSubject() + ")");
		Application application = existente.orElseGet(() -> criarCandidatura(user, email, c, empresa, nota));
		email.setApplication(application);

		ApplicationStatus anterior = application.getStatus();
		// E-mails são classificados do mais novo ao mais antigo: um e-mail antigo só vincula, sem regredir o status
		boolean existeEmailMaisNovo = existente.isPresent()
			&& emailRepository.existsByApplicationIdAndReceivedAtAfter(application.getId(), email.getReceivedAt());
		if (existente.isPresent() && anterior != c.novoStatus() && !existeEmailMaisNovo) {
			application.setStatus(c.novoStatus());
			applicationRepository.save(application);
			applicationHistoryService.recordAgentChange(application, anterior, c.novoStatus(), "status", nota);
		}
		return Resultado.STATUS_ATUALIZADO;
	}

	private Application criarCandidatura(User user, Email email, ClassificacaoEmail c, String empresa, String nota) {
		String cargo = curto(c.cargo());
		Application application = applicationRepository.save(Application.builder()
			.user(user)
			.companyName(empresa)
			.position(cargo != null ? cargo : CARGO_NAO_INFORMADO)
			.status(c.novoStatus())
			.appliedAt(email.getReceivedAt())
			.notes(nota)
			.build());
		applicationHistoryService.recordAgentChange(application, null, c.novoStatus(), "created", nota);
		return application;
	}

	static String descreverCausaRaiz(Throwable erro) {
		Throwable raiz = erro;
		while (raiz.getCause() != null && raiz.getCause() != raiz) {
			raiz = raiz.getCause();
		}
		String descricao = raiz.getClass().getSimpleName() + ": " + raiz.getMessage();
		return raiz == erro ? descricao : erro.getMessage() + " <- " + descricao;
	}

		private static String curto(String valor) {
		return limitar(valor, MAX_TEXTO_CURTO);
	}

	private static String longo(String valor) {
		return limitar(valor, MAX_TEXTO_LONGO);
	}

	private static String limitar(String valor, int max) {
		if (!StringUtils.hasText(valor)) {
			return null;
		}
		String texto = valor.trim();
		return texto.length() > max ? texto.substring(0, max - 3) + "..." : texto;
	}
}
