package jobtracker.integration.gmail;

import java.time.Instant;
import jobtracker.entity.Email;
import jobtracker.entity.EmailDirection;
import jobtracker.repository.EmailRepository;
import jobtracker.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persiste <b>um</b> e-mail em uma transação própria (REQUIRES_NEW), isolando falhas por mensagem.
 *
 * <p>A classificação (Gemini) acontece depois, em {@link jobtracker.ia.ProcessadorEmailIAService}.
 */
@Service
@RequiredArgsConstructor
public class EmailSyncProcessor {

	private final EmailRepository emailRepository;
	private final UserRepository userRepository;

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void process(
		Long userId,
		String messageId,
		EmailDirection direction,
		String subject,
		String fromAddress,
		String toAddress,
		String snippet,
		String rawContent,
		Instant receivedAt
	) {
		Email email = Email.builder()
			.user(userRepository.getReferenceById(userId))
			.messageId(messageId)
			.direction(direction)
			.subject(subject)
			.fromAddress(fromAddress)
			.toAddress(toAddress)
			.snippet(snippet != null ? snippet : "")
			.rawContent(rawContent)
			.receivedAt(receivedAt)
			.build();

		emailRepository.save(email);
	}
}