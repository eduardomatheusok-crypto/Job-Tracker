package jobtracker.service;

import jakarta.persistence.EntityNotFoundException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import jobtracker.dto.application.ApplicationRequest;
import jobtracker.dto.application.ApplicationResponse;
import jobtracker.dto.application.ApplicationSummaryResponse;
import jobtracker.dto.application.UpdateApplicationRequest;
import jobtracker.entity.Application;
import jobtracker.entity.ApplicationStatus;
import jobtracker.entity.User;
import jobtracker.repository.ApplicationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class ApplicationService {

	private final ApplicationRepository applicationRepository;
	private final UserService userService;
	private final CurrentUserService currentUserService;
	private final ApplicationHistoryService applicationHistoryService;

	@Transactional(readOnly = true)
	public Page<ApplicationResponse> getApplications(Pageable pageable) {
		User currentUser = getAuthenticatedUser();
		return applicationRepository.findByUserId(currentUser.getId(), pageable)
			.map(this::toResponse);
	}

	@Transactional(readOnly = true)
	public ApplicationResponse getApplicationById(Long id) {
		return toResponse(findOwnedApplication(id));
	}

	public ApplicationResponse createApplication(ApplicationRequest request) {
		return createApplication(request, getAuthenticatedUser());
	}

	public ApplicationResponse createApplication(ApplicationRequest request, User owner) {
		Application application = Application.builder()
			.user(owner)
			.companyName(request.getCompanyName())
			.position(request.getPosition())
			.location(request.getLocation())
			.jobUrl(request.getJobUrl())
			.platform(request.getPlatform())
			.notes(request.getNotes())
			.status(request.getStatus() != null ? request.getStatus() : ApplicationStatus.SAVED)
			.build();

		if (application.getStatus() == ApplicationStatus.APPLIED && application.getAppliedAt() == null) {
			application.setAppliedAt(Instant.now());
		}

		Application savedApplication = applicationRepository.save(application);
		return toResponse(savedApplication);
	}

	public ApplicationResponse updateApplication(Long id, UpdateApplicationRequest request) {
		return updateApplication(id, request, getAuthenticatedUser());
	}

	public ApplicationResponse updateApplication(Long id, UpdateApplicationRequest request, User actor) {
		Application application = findOwnedApplication(id, actor.getId());
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
		if (request.getPlatform() != null) {
			application.setPlatform(request.getPlatform());
			changedFields.add("platform");
		}
		if (request.getNotes() != null) {
			application.setNotes(request.getNotes());
			changedFields.add("notes");
		}
		if (request.getStatus() != null) {
			application.setStatus(request.getStatus());
			changedFields.add("status");
		}

		if (application.getStatus() == ApplicationStatus.APPLIED && application.getAppliedAt() == null) {
			application.setAppliedAt(Instant.now());
		}

		Application savedApplication = applicationRepository.save(application);
		if (savedApplication.getStatus() != previousStatus) {
			applicationHistoryService.recordUpdate(
				savedApplication,
				actor,
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
			.platform(application.getPlatform())
			.notes(application.getNotes())
			.status(application.getStatus())
			.appliedAt(application.getAppliedAt())
			.createdAt(application.getCreatedAt())
			.updatedAt(application.getUpdatedAt())
			.build();
	}

	@Transactional(readOnly = true)
	public ApplicationSummaryResponse getSummary() {
		User currentUser = getAuthenticatedUser();
		Map<ApplicationStatus, Long> byStatus = new EnumMap<>(ApplicationStatus.class);
		for (ApplicationStatus status : ApplicationStatus.values()) {
			byStatus.put(status, 0L);
		}
		for (Object[] row : applicationRepository.countApplicationsByStatus(currentUser.getId())) {
			byStatus.put((ApplicationStatus) row[0], (Long) row[1]);
		}
		long total = byStatus.values().stream().mapToLong(Long::longValue).sum();
		return ApplicationSummaryResponse.builder()
			.total(total)
			.byStatus(byStatus)
			.build();
	}

	@Transactional(readOnly = true)
	public User getAuthenticatedUser() {
		return currentUserService.get();
	}

	@Transactional(readOnly = true)
	public Application getOwnedApplication(Long id) {
		return findOwnedApplication(id);
	}

	@Transactional(readOnly = true)
	protected Application findOwnedApplication(Long id) {
		return findOwnedApplication(id, getAuthenticatedUser().getId());
	}

	@Transactional(readOnly = true)
	protected Application findOwnedApplication(Long id, Long userId) {
		return applicationRepository.findByIdAndUserId(id, userId)
			.orElseThrow(() -> new EntityNotFoundException("Application not found: " + id));
	}
}
