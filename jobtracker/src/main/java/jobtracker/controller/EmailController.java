package jobtracker.controller;

import java.util.List;
import jobtracker.dto.email.EmailResponse;
import jobtracker.service.EmailService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/emails")
@RequiredArgsConstructor
public class EmailController {

	private final EmailService emailService;

	@GetMapping
	public ResponseEntity<Page<EmailResponse>> getEmails(
		@RequestParam(required = false) Long applicationId,
		@PageableDefault(size = 20, sort = {"receivedAt", "id"}, direction = Sort.Direction.DESC) Pageable pageable
	) {
		return ResponseEntity.ok(emailService.getEmails(applicationId, pageable));
	}

	@GetMapping("/{id}")
	public ResponseEntity<EmailResponse> getEmailById(@PathVariable Long id) {
		return ResponseEntity.ok(emailService.getEmailById(id));
	}
}