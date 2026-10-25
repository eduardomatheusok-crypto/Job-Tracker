package jobtracker.ia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import jobtracker.entity.AnuncioVaga;
import jobtracker.entity.Application;
import jobtracker.entity.ApplicationStatus;
import jobtracker.entity.Email;
import jobtracker.entity.EmailProcessado;
import jobtracker.entity.SituacaoSugestao;
import jobtracker.entity.SugestaoStatus;
import jobtracker.entity.User;
import jobtracker.repository.AnuncioVagaRepository;
import jobtracker.repository.ApplicationRepository;
import jobtracker.repository.EmailProcessadoRepository;
import jobtracker.repository.EmailRepository;
import jobtracker.repository.SugestaoStatusRepository;
import jobtracker.repository.UserRepository;
import jobtracker.service.ApplicationHistoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class ProcessadorEmailIAServiceTest {

	private static final Long USER_ID = 1L;
	private static final Long EMAIL_ID = 10L;
	private static final String GMAIL_ID = "18c2f0a1b2c3d4e5";

	private final ClassificadorEmailService classificador = mock(ClassificadorEmailService.class);
	private final EmailProcessadoRepository emailProcessadoRepository = mock(EmailProcessadoRepository.class);
	private final EmailRepository emailRepository = mock(EmailRepository.class);
	private final UserRepository userRepository = mock(UserRepository.class);
	private final ApplicationRepository applicationRepository = mock(ApplicationRepository.class);
	private final AnuncioVagaRepository anuncioVagaRepository = mock(AnuncioVagaRepository.class);
	private final SugestaoStatusRepository sugestaoStatusRepository = mock(SugestaoStatusRepository.class);
	private final ApplicationHistoryService applicationHistoryService = mock(ApplicationHistoryService.class);

	// Executa o callback de verdade; o gerenciador de transação mockado não faz nada
	private final TransactionTemplate transactionTemplate = new TransactionTemplate(mock(PlatformTransactionManager.class));

	private final ProcessadorEmailIAService processador = new ProcessadorEmailIAService(
		classificador, emailProcessadoRepository, emailRepository, userRepository, applicationRepository,
		anuncioVagaRepository, sugestaoStatusRepository, applicationHistoryService, transactionTemplate);

	private User user;
	private Email email;

	@BeforeEach
	void setUp() {
		user = User.builder().id(USER_ID).email("eu@example.com").build();
		email = Email.builder()
			.id(EMAIL_ID)
			.user(user)
			.messageId(GMAIL_ID)
			.subject("Atualização sobre sua candidatura")
			.fromAddress("talentos@acme.com")
			.rawContent("Olá! Agradecemos seu interesse, mas seguimos com outros perfis.")
			.receivedAt(Instant.parse("2026-10-20T12:00:00Z"))
			.build();

		when(emailProcessadoRepository.existsByGmailId(GMAIL_ID)).thenReturn(false);
		when(emailRepository.findById(EMAIL_ID)).thenReturn(Optional.of(email));
		when(userRepository.getReferenceById(USER_ID)).thenReturn(user);
		when(applicationRepository.save(any(Application.class))).thenAnswer(inv -> inv.getArgument(0));
	}

	private void classificarComo(TipoEmail tipo, ApplicationStatus status, String empresa, double confianca) {
		when(classificador.classificar(anyString(), anyString(), anyString()))
			.thenReturn(new ClassificacaoEmail("justificativa do teste", tipo, status, empresa, "Dev Backend", confianca));
	}

	private Application candidaturaExistente(ApplicationStatus status) {
		Application app = Application.builder().id(5L).user(user).companyName("Acme").position("Dev Backend").status(status).build();
		when(applicationRepository.findFirstByUserIdAndCompanyNameIgnoreCaseOrderByCreatedAtDesc(USER_ID, "Acme"))
			.thenReturn(Optional.of(app));
		return app;
	}

	private EmailProcessado marcacaoSalva() {
		ArgumentCaptor<EmailProcessado> captor = ArgumentCaptor.forClass(EmailProcessado.class);
		verify(emailProcessadoRepository).save(captor.capture());
		return captor.getValue();
	}

	@Test
	void naoRelacionado_ignoraEMarcaComoProcessado() {
		classificarComo(TipoEmail.NAO_RELACIONADO, null, null, 0.95);

		assertThat(processador.processar(email)).isEqualTo(ProcessadorEmailIAService.Resultado.IGNORADO);

		verifyNoInteractions(anuncioVagaRepository, sugestaoStatusRepository, applicationHistoryService);
		verify(applicationRepository, never()).save(any());
		assertThat(marcacaoSalva().getTipoEmail()).isEqualTo(TipoEmail.NAO_RELACIONADO);
		assertThat(email.getProcessedAt()).isNotNull();
	}

	@Test
	void anuncioVaga_salvaAnuncio() {
		classificarComo(TipoEmail.ANUNCIO_VAGA, null, "Acme", 0.9);

		assertThat(processador.processar(email)).isEqualTo(ProcessadorEmailIAService.Resultado.ANUNCIO_SALVO);

		ArgumentCaptor<AnuncioVaga> captor = ArgumentCaptor.forClass(AnuncioVaga.class);
		verify(anuncioVagaRepository).save(captor.capture());
		assertThat(captor.getValue().getEmpresa()).isEqualTo("Acme");
		assertThat(captor.getValue().getCargo()).isEqualTo("Dev Backend");
		verify(applicationRepository, never()).save(any());
		assertThat(marcacaoSalva().getGmailId()).isEqualTo(GMAIL_ID);
	}

	@Test
	void atualizacaoComConfiancaAlta_atualizaStatusERegistraHistoricoDoAgente() {
		Application app = candidaturaExistente(ApplicationStatus.APPLIED);
		classificarComo(TipoEmail.ATUALIZACAO_CANDIDATURA, ApplicationStatus.REJECTED, "Acme", 0.92);

		assertThat(processador.processar(email)).isEqualTo(ProcessadorEmailIAService.Resultado.STATUS_ATUALIZADO);

		assertThat(app.getStatus()).isEqualTo(ApplicationStatus.REJECTED);
		assertThat(email.getApplication()).isSameAs(app);
		verify(applicationHistoryService).recordAgentChange(
			eq(app), eq(ApplicationStatus.APPLIED), eq(ApplicationStatus.REJECTED), eq("status"), anyString());
		verifyNoInteractions(sugestaoStatusRepository);
		marcacaoSalva();
	}

	@Test
	void atualizacaoNoLimiarDeConfianca_atualizaStatus() {
		Application app = candidaturaExistente(ApplicationStatus.APPLIED);
		classificarComo(TipoEmail.ATUALIZACAO_CANDIDATURA, ApplicationStatus.INTERVIEW, "Acme", 0.7);

		assertThat(processador.processar(email)).isEqualTo(ProcessadorEmailIAService.Resultado.STATUS_ATUALIZADO);
		assertThat(app.getStatus()).isEqualTo(ApplicationStatus.INTERVIEW);
	}

	@Test
	void atualizacaoComConfiancaBaixa_criaSugestaoPendenteSemAlterarStatus() {
		Application app = candidaturaExistente(ApplicationStatus.APPLIED);
		classificarComo(TipoEmail.ATUALIZACAO_CANDIDATURA, ApplicationStatus.REJECTED, "Acme", 0.55);

		assertThat(processador.processar(email)).isEqualTo(ProcessadorEmailIAService.Resultado.SUGESTAO_CRIADA);

		assertThat(app.getStatus()).isEqualTo(ApplicationStatus.APPLIED);
		ArgumentCaptor<SugestaoStatus> captor = ArgumentCaptor.forClass(SugestaoStatus.class);
		verify(sugestaoStatusRepository).save(captor.capture());
		SugestaoStatus sugestao = captor.getValue();
		assertThat(sugestao.getSituacao()).isEqualTo(SituacaoSugestao.PENDENTE);
		assertThat(sugestao.getNovoStatus()).isEqualTo(ApplicationStatus.REJECTED);
		assertThat(sugestao.getApplication()).isSameAs(app);
		assertThat(sugestao.getConfianca()).isEqualTo(0.55);
		verifyNoInteractions(applicationHistoryService);
		marcacaoSalva();
	}

	@Test
	void atualizacaoConfiavelSemCandidaturaExistente_criaCandidaturaPeloAgente() {
		when(applicationRepository.findFirstByUserIdAndCompanyNameIgnoreCaseOrderByCreatedAtDesc(USER_ID, "Acme"))
			.thenReturn(Optional.empty());
		classificarComo(TipoEmail.ATUALIZACAO_CANDIDATURA, ApplicationStatus.APPLIED, "Acme", 0.95);

		assertThat(processador.processar(email)).isEqualTo(ProcessadorEmailIAService.Resultado.STATUS_ATUALIZADO);

		ArgumentCaptor<Application> captor = ArgumentCaptor.forClass(Application.class);
		verify(applicationRepository).save(captor.capture());
		Application criada = captor.getValue();
		assertThat(criada.getCompanyName()).isEqualTo("Acme");
		assertThat(criada.getStatus()).isEqualTo(ApplicationStatus.APPLIED);
		assertThat(criada.getAppliedAt()).isEqualTo(email.getReceivedAt());
		verify(applicationHistoryService).recordAgentChange(
			eq(criada), eq(null), eq(ApplicationStatus.APPLIED), eq("created"), anyString());
	}

	@Test
	void atualizacaoSemEmpresa_criaSugestaoMesmoComConfiancaAlta() {
		classificarComo(TipoEmail.ATUALIZACAO_CANDIDATURA, ApplicationStatus.REJECTED, null, 0.95);

		assertThat(processador.processar(email)).isEqualTo(ProcessadorEmailIAService.Resultado.SUGESTAO_CRIADA);
		verify(applicationRepository, never()).save(any());
	}

	@Test
	void falhaDoGemini_naoMarcaComoProcessado() {
		when(classificador.classificar(anyString(), anyString(), anyString()))
			.thenThrow(new RuntimeException("429 Too Many Requests: quota exceeded"));

		assertThat(processador.processar(email)).isEqualTo(ProcessadorEmailIAService.Resultado.FALHA);

		verify(emailProcessadoRepository, never()).save(any());
		verifyNoInteractions(anuncioVagaRepository, sugestaoStatusRepository, applicationHistoryService);
		assertThat(email.getProcessedAt()).isNull();
	}

	@Test
	void respostaSemTipo_naoMarcaComoProcessado() {
		when(classificador.classificar(anyString(), anyString(), anyString()))
			.thenReturn(new ClassificacaoEmail("?", null, null, null, null, 0.1));

		assertThat(processador.processar(email)).isEqualTo(ProcessadorEmailIAService.Resultado.FALHA);
		verify(emailProcessadoRepository, never()).save(any());
	}

	@Test
	void emailJaProcessado_naoChamaOGemini() {
		when(emailProcessadoRepository.existsByGmailId(GMAIL_ID)).thenReturn(true);

		assertThat(processador.processar(email)).isEqualTo(ProcessadorEmailIAService.Resultado.JA_PROCESSADO);

		verifyNoInteractions(classificador);
		verify(emailProcessadoRepository, never()).save(any());
	}
}
