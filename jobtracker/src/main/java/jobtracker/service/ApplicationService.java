package jobtracker.service;

import jakarta.persistence.EntityNotFoundException;
import java.util.ArrayList;
import java.util.List;
import jobtracker.dto.application.ApplicationRequest;
import jobtracker.dto.application.ApplicationResponse;
import jobtracker.dto.application.UpdateApplicationRequest;
import jobtracker.entity.Application;
import jobtracker.entity.ApplicationStatus;
import jobtracker.entity.User;
import jobtracker.repository.ApplicationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class ApplicationService {

	private final ApplicationRepository applicationRepository;
	private final UserService userService;
	private final ApplicationHistoryService applicationHistoryService;

	@Transactional(readOnly = true)
	public List<ApplicationResponse> getApplications() {
		User currentUser = getAuthenticatedUser();
		return applicationRepository.findAllByUserIdOrderByCreatedAtDesc(currentUser.getId())
			.stream()
			.map(this::toResponse)
			.toList();
	}

	@Transactional(readOnly = true)
	public ApplicationResponse getApplicationById(Long id) {
		return toResponse(findOwnedApplication(id));
	}

	public ApplicationResponse createApplication(ApplicationRequest request) {
		User currentUser = getAuthenticatedUser();

		Application application = Application.builder()
			.user(currentUser)
			.companyName(request.getCompanyName())
			.position(request.getPosition())
			.location(request.getLocation())
			.jobUrl(request.getJobUrl())
			.notes(request.getNotes())
			.status(request.getStatus() != null ? request.getStatus() : ApplicationStatus.SAVED)
			.build();

		Application savedApplication = applicationRepository.save(application);
		applicationHistoryService.recordCreation(savedApplication, currentUser);
		return toResponse(savedApplication);
	}

	public ApplicationResponse updateApplication(Long id, UpdateApplicationRequest request) {
		Application application = findOwnedApplication(id);
		User currentUser = getAuthenticatedUser();
		ApplicationStatus previousStatus = application.getStatus();
		List<String> changedFields = new ArrayList<>();

		if (request.getCompanyName() != null) {
			application.setCompanyName(request.getCompanyName());
			changedFields.add("companyName");
		}
		if (request.getPosition() != null) {
			application.setPosition(request.getPosition());
			changedFields.add("position");
		}
		if (request.getLocation() != null) {
			application.setLocation(request.getLocation());
			changedFields.add("location");
		}
		if (request.getJobUrl() != null) {
			application.setJobUrl(request.getJobUrl());
			changedFields.add("jobUrl");
		}
		if (request.getNotes() != null) {
			application.setNotes(request.getNotes());
			changedFields.add("notes");
		}
		if (request.getStatus() != null) {
			application.setStatus(request.getStatus());
			changedFields.add("status");
		}

		Application savedApplication = applicationRepository.save(application);
		if (!changedFields.isEmpty()) {
			applicationHistoryService.recordUpdate(
				savedApplication,
				currentUser,
				previousStatus,
				savedApplication.getStatus(),
				changedFields
			);
		}
		return toResponse(savedApplication);
	}

	public void deleteApplication(Long id) {
		Application application = findOwnedApplication(id);
		applicationHistoryService.deleteByApplicationId(application.getId());
		applicationRepository.delete(application);
	}

	@Transactional(readOnly = true)
	public ApplicationResponse toResponse(Application application) {
		return ApplicationResponse.builder()
			.id(application.getId())
			.userId(application.getUser().getId())
			.companyName(application.getCompanyName())
			.position(application.getPosition())
			.location(application.getLocation())
			.jobUrl(application.getJobUrl())
			.notes(application.getNotes())
			.status(application.getStatus())
			.appliedAt(application.getAppliedAt())
			.createdAt(application.getCreatedAt())
			.updatedAt(application.getUpdatedAt())
			.build();
	}

	@Transactional(readOnly = true)
	protected User getAuthenticatedUser() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null || authentication.getName() == null) {
			throw new EntityNotFoundException("Authenticated user not found");
		}

		return userService.findByEmailOrThrow(authentication.getName());
	}

	@Transactional(readOnly = true)
	protected Application findOwnedApplication(Long id) {
		User currentUser = getAuthenticatedUser();
		return applicationRepository.findByIdAndUserId(id, currentUser.getId())
			.orElseThrow(() -> new EntityNotFoundException("Application not found: " + id));
	}
}
