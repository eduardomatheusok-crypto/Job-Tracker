package jobtracker.integration.gmail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.persistence.EntityNotFoundException;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import jobtracker.ia.ProcessadorEmailIAService;
import jobtracker.repository.EmailRepository;
import jobtracker.repository.GmailConnectionRepository;
import jobtracker.service.UserService;
import org.junit.jupiter.api.Test;

class GmailSyncLockTest {

	private final GmailConnectionRepository gmailConnectionRepository = mock(GmailConnectionRepository.class);

	private final GmailService gmailService = new GmailService(
		mock(GmailOAuthService.class), gmailConnectionRepository, mock(UserService.class), mock(EncryptionService.class),
		mock(EmailRepository.class), mock(EmailSyncProcessor.class), mock(GmailImapClient.class),
		mock(ProcessadorEmailIAService.class));

	@Test
	void secondSyncForSameUser_isRejectedWhileFirstIsRunning_andLockIsReleasedAfter() throws Exception {
		CountDownLatch firstStarted = new CountDownLatch(1);
		CountDownLatch releaseFirst = new CountDownLatch(1);
		when(gmailConnectionRepository.findByUserId(1L)).thenAnswer(inv -> {
			firstStarted.countDown();
			releaseFirst.await(5, TimeUnit.SECONDS);
			return Optional.empty();
		});

		CompletableFuture<Throwable> first = CompletableFuture.supplyAsync(() -> {
			try {
				gmailService.syncEmails(1L);
				return null;
			} catch (Throwable t) {
				return t;
			}
		});
		assertThat(firstStarted.await(5, TimeUnit.SECONDS)).isTrue();

		// Enquanto a primeira está rodando, a segunda é descartada
		assertThatThrownBy(() -> gmailService.syncEmails(1L)).isInstanceOf(GmailSyncInProgressException.class);

		releaseFirst.countDown();
		assertThat(first.get(5, TimeUnit.SECONDS)).isInstanceOf(EntityNotFoundException.class);

		// Terminada (mesmo com erro), a trava é liberada e uma nova sincronização pode rodar
		assertThatThrownBy(() -> gmailService.syncEmails(1L)).isInstanceOf(EntityNotFoundException.class);
	}
}
