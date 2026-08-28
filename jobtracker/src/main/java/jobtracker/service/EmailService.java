package jobtracker.service;

import jakarta.persistence.EntityNotFoundException;
import jobtracker.dto.email.EmailResponse;
import jobtracker.entity.Email;
import jobtracker.entity.User;
import jobtracker.repository.EmailRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EmailService {

	private final EmailRepository emailRepository;
	private final ApplicationService applicationService;
	private final CurrentUserService currentUserService;

	public Page<EmailResponse> getEmails(Long applicationId, Pageable pageable) {
		User currentUser = currentUserService.get();
		Page<Email> emails;
		if (applicationId != null) {
			applicationService.getOwnedApplication(applicationId);
			emails = emailRepository.findByUserIdAndApplicationId(currentUser.getId(), applicationId, pageable);
		} else {
			emails = emailRepository.findByUserId(currentUser.getId(), pageable);
		}
		return emails.map(this::toResponse);
	}

	public EmailResponse getEmailById(Long id) {
		User currentUser = currentUserService.get();
		Email email = emailRepository.findByIdAndUserId(id, currentUser.getId())
			.orElseThrow(() -> new EntityNotFoundException("Email not found: " + id));
		return toResponse(email);
	}

	private EmailResponse toResponse(Email email) {
		return EmailResponse.builder()
			.id(email.getId())
			.userId(email.getUser().getId())
			.applicationId(email.getApplication() != null ? email.getApplication().getId() : null)
			.messageId(email.getMessageId())
			.subject(email.getSubject())
			.fromAddress(email.getFromAddress())
			.snippet(email.getSnippet())
			.rawContent(email.getRawContent())
			.receivedAt(email.getReceivedAt())
			.processedAt(email.getProcessedAt())
			.createdAt(email.getCreatedAt())
			.build();
	}
}