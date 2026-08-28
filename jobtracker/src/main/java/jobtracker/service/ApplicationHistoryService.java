package jobtracker.service;

import java.util.ArrayList;
import java.util.List;
import jobtracker.dto.history.ApplicationHistoryResponse;
import jobtracker.entity.Application;
import jobtracker.entity.ApplicationHistory;
import jobtracker.entity.ApplicationStatus;
import jobtracker.entity.User;
import jobtracker.repository.ApplicationRepository;
import jobtracker.repository.ApplicationHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class ApplicationHistoryService {

	private final ApplicationRepository applicationRepository;
	private final ApplicationHistoryRepository applicationHistoryRepository;

	public ApplicationHistory recordCreation(Application application, User actor) {
		return saveHistory(application, actor, null, application.getStatus(), "created", "Application created");
	}

	public ApplicationHistory recordUpdate(
		Application application,
		User actor,
		ApplicationStatus previousStatus,
		ApplicationStatus newStatus,
		List<String> changedFields
	) {
		String note = changedFields == null || changedFields.isEmpty()
			? "Application updated"
			: "Updated fields: " + String.join(", ", changedFields);
		return saveHistory(application, actor, previousStatus, newStatus, joinFields(changedFields), note);
	}

	public ApplicationHistory recordDeletion(Application application, User actor) {
		return saveHistory(application, actor, application.getStatus(), application.getStatus(), "deleted", "Application deleted");
	}

	@Transactional(readOnly = true)
	public List<ApplicationHistory> getHistoryByApplicationId(Long applicationId) {
		return applicationHistoryRepository.findByApplicationIdOrderByChangedAtDesc(applicationId);
	}

	@Transactional(readOnly = true)
	public List<ApplicationHistoryResponse> getHistoryResponsesByApplicationId(Long applicationId) {
		return getHistoryByApplicationId(applicationId)
			.stream()
			.map(this::toResponse)
			.toList();
	}

	@Transactional(readOnly = true)
	public ApplicationHistoryResponse toResponse(ApplicationHistory history) {
		return ApplicationHistoryResponse.builder()
			.id(history.getId())
			.applicationId(history.getApplication().getId())
			.changedByUserId(history.getChangedByUser() != null ? history.getChangedByUser().getId() : null)
			.previousStatus(history.getPreviousStatus())
			.newStatus(history.getNewStatus())
			.changedField(history.getChangedField())
			.note(history.getNote())
			.changedAt(history.getChangedAt())
			.build();
	}

	public void deleteByApplicationId(Long applicationId) {
		applicationHistoryRepository.deleteByApplicationId(applicationId);
	}

	private ApplicationHistory saveHistory(
		Application application,
		User actor,
		ApplicationStatus previousStatus,
		ApplicationStatus newStatus,
		String changedField,
		String note
	) {
		Application managedApplication = applicationRepository.getReferenceById(application.getId());
		ApplicationHistory history = ApplicationHistory.builder()
			.application(managedApplication)
			.changedByUser(actor)
			.previousStatus(previousStatus)
			.newStatus(newStatus)
			.changedField(changedField)
			.note(note)
			.build();

		return applicationHistoryRepository.save(history);
	}

	private String joinFields(List<String> changedFields) {
		if (changedFields == null || changedFields.isEmpty()) {
			return null;
		}
		return String.join(",", new ArrayList<>(changedFields));
	}
}