package jobtracker.integration.gmail;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.gmail.sync.enabled", havingValue = "true", matchIfMissing = true)
public class GmailSyncScheduler {

	private final GmailService gmailService;

	@Scheduled(
		initialDelayString = "${app.gmail.sync.initial-delay-ms:60000}",
		fixedDelayString = "${app.gmail.sync.fixed-delay-ms:3600000}"
	)
	public void syncAll() {
		gmailService.syncAllConnections();
	}
}