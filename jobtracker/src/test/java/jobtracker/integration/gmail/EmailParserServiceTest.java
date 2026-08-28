package jobtracker.integration.gmail;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import jobtracker.dto.application.ApplicationRequest;
import jobtracker.dto.application.ApplicationResponse;
import jobtracker.dto.application.UpdateApplicationRequest;
import jobtracker.entity.Application;
import jobtracker.entity.ApplicationStatus;
import jobtracker.entity.Email;
import jobtracker.entity.User;
import jobtracker.repository.ApplicationRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import jobtracker.service.ApplicationService;

class EmailParserServiceTest {

	private final ApplicationRepository applicationRepository = mock(ApplicationRepository.class);
	private final ApplicationService applicationService = mock(ApplicationService.class);
	private final EmailParserService emailParserService = new EmailParserService(applicationRepository, applicationService);

	@Test
	void parseAndProcess_createsApplicationWhenNoMatchExists() {
		User user = mock(User.class);
		when(user.getId()).thenReturn(1L);
		when(applicationRepository.findAllByUserIdOrderByCreatedAtDesc(1L)).thenReturn(List.of());

		when(applicationService.createApplication(any(ApplicationRequest.class), eq(user)))
			.thenReturn(ApplicationResponse.builder().id(10L).build());

		Application newApp = Application.builder().id(10L).build();
		when(applicationRepository.getReferenceById(10L)).thenReturn(newApp);

		Email email = Email.builder()
			.user(user)
			.subject("Candidatura na Acme Corp")
			.rawContent("Vaga de Backend")
			.build();

		emailParserService.parseAndProcess(email);

		ArgumentCaptor<ApplicationRequest> captor = ArgumentCaptor.forClass(ApplicationRequest.class);
		verify(applicationService).createApplication(captor.capture(), eq(user));
		assertEquals("Acme Corp", captor.getValue().getCompanyName());
		assertEquals("Backend", captor.getValue().getPosition());
		assertEquals(ApplicationStatus.APPLIED, captor.getValue().getStatus());
		verify(applicationService, never())
			.updateApplication(anyLong(), any(UpdateApplicationRequest.class), any(User.class));
		assertNotNull(email.getApplication());
		assertEquals(10L, email.getApplication().getId());
	}

	@Test
	void parseAndProcess_positionStopsAtFillerWords() {
		User user = mock(User.class);
		when(user.getId()).thenReturn(1L);
		when(applicationRepository.findAllByUserIdOrderByCreatedAtDesc(1L)).thenReturn(List.of());

		when(applicationService.createApplication(any(ApplicationRequest.class), eq(user)))
			.thenReturn(ApplicationResponse.builder().id(11L).build());

		when(applicationRepository.getReferenceById(11L)).thenReturn(Application.builder().id(11L).build());

		Email email = Email.builder()
			.user(user)
			.subject("Candidatura na Acme Corp")
			.rawContent("Vaga de Backend aberta na Acme Corp empresa XYZ")
			.build();

		emailParserService.parseAndProcess(email);

		ArgumentCaptor<ApplicationRequest> captor = ArgumentCaptor.forClass(ApplicationRequest.class);
		verify(applicationService).createApplication(captor.capture(), eq(user));
		assertEquals("Backend", captor.getValue().getPosition());
		assertNotNull(email.getApplication());
	}

	@Test
	void parseAndProcess_updatesExistingApplicationOnRejection() {
		User user = mock(User.class);
		when(user.getId()).thenReturn(1L);

		Application existingApp = mock(Application.class);
		when(existingApp.getId()).thenReturn(5L);
		when(existingApp.getCompanyName()).thenReturn("Acme Corp");
		when(existingApp.getStatus()).thenReturn(ApplicationStatus.APPLIED);
		when(existingApp.getNotes()).thenReturn("");

		when(applicationRepository.findAllByUserIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(existingApp));

		Email email = Email.builder()
			.user(user)
			.subject("Candidatura na Acme Corp")
			.rawContent("Vaga de Backend na Acme Corp. Infelizmente, não seguiremos com seu processo. Agradecemos seu interesse.")
			.build();

		emailParserService.parseAndProcess(email);

		ArgumentCaptor<UpdateApplicationRequest> captor = ArgumentCaptor.forClass(UpdateApplicationRequest.class);
		verify(applicationService).updateApplication(eq(5L), captor.capture(), eq(user));
		assertEquals(ApplicationStatus.REJECTED, captor.getValue().getStatus());
		verify(applicationService, never())
			.createApplication(any(ApplicationRequest.class), any(User.class));
		assertNotNull(email.getApplication());
		assertEquals(5L, email.getApplication().getId());
	}
}