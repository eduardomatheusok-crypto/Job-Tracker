package jobtracker.controller;

import jakarta.validation.Valid;
import jobtracker.dto.application.ApplicationRequest;
import jobtracker.dto.application.ApplicationResponse;
import jobtracker.dto.application.UpdateApplicationRequest;
import jobtracker.service.ApplicationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/applications")
@RequiredArgsConstructor
public class ApplicationController {

	private final ApplicationService applicationService;

	@PostMapping
	public ResponseEntity<ApplicationResponse> createApplication(
		@Valid @RequestBody ApplicationRequest request
	) {
		return ResponseEntity.status(HttpStatus.CREATED).body(applicationService.createApplication(request));
	}

	@GetMapping
	public ResponseEntity<List<ApplicationResponse>> getApplications() {
		return ResponseEntity.ok(applicationService.getApplications());
	}

	@GetMapping("/{id}")
	public ResponseEntity<ApplicationResponse> getApplicationById(@PathVariable Long id) {
		return ResponseEntity.ok(applicationService.getApplicationById(id));
	}

	@PutMapping("/{id}")
	public ResponseEntity<ApplicationResponse> updateApplication(
		@PathVariable Long id,
		@Valid @RequestBody UpdateApplicationRequest request
	) {
		return ResponseEntity.ok(applicationService.updateApplication(id, request));
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> deleteApplication(@PathVariable Long id) {
		applicationService.deleteApplication(id);
		return ResponseEntity.noContent().build();
	}
}
