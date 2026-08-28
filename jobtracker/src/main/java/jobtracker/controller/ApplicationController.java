package jobtracker.controller;

import jakarta.validation.Valid;
import java.util.List;
import jobtracker.dto.application.ApplicationRequest;
import jobtracker.dto.application.ApplicationResponse;
import jobtracker.dto.application.ApplicationSummaryResponse;
import jobtracker.dto.application.UpdateApplicationRequest;
import jobtracker.dto.history.ApplicationHistoryResponse;
import jobtracker.service.ApplicationHistoryService;
import jobtracker.service.ApplicationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
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

@RestController
@RequestMapping("/api/applications")
@RequiredArgsConstructor
public class ApplicationController {

	private final ApplicationService applicationService;
	private final ApplicationHistoryService applicationHistoryService;

	@PostMapping
	public ResponseEntity<ApplicationResponse> createApplication(
		@Valid @RequestBody ApplicationRequest request
	) {
		return ResponseEntity.status(HttpStatus.CREATED).body(applicationService.createApplication(request));
	}

	@GetMapping
	public ResponseEntity<Page<ApplicationResponse>> getApplications(
		@PageableDefault(size = 20, sort = {"createdAt", "id"}, direction = Sort.Direction.DESC) Pageable pageable
	) {
		return ResponseEntity.ok(applicationService.getApplications(pageable));
	}

	@GetMapping("/summary")
	public ResponseEntity<ApplicationSummaryResponse> getSummary() {
		return ResponseEntity.ok(applicationService.getSummary());
	}

	@GetMapping("/{id}")
	public ResponseEntity<ApplicationResponse> getApplicationById(@PathVariable Long id) {
		return ResponseEntity.ok(applicationService.getApplicationById(id));
	}

	@GetMapping("/{id}/history")
	public ResponseEntity<List<ApplicationHistoryResponse>> getApplicationHistory(@PathVariable Long id) {
		applicationService.getOwnedApplication(id);
		return ResponseEntity.ok(applicationHistoryService.getHistoryResponsesByApplicationId(id));
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
