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
 * Processa <b>um</b> e-mail em uma transação própria (REQUIRES_NEW).
 *
 * <p>Isola a falha por mensagem: se o parsing de um e-mail lançar uma exceção,
 * apenas aquele e-mail é revertido e o restante da sincronização continua.
 */
@Service
@RequiredArgsConstructor
public class EmailSyncProcessor {

	private final EmailRepository emailRepository;
	private final UserRepository userRepository;
	private final EmailParserService emailParserService;

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

		Email savedEmail = emailRepository.save(email);
		emailParserService.parseAndProcess(savedEmail);
		savedEmail.setProcessedAt(Instant.now());
		emailRepository.save(savedEmail);
	}
}