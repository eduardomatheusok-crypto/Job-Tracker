package jobtracker.dto.history;

import java.time.Instant;
import jobtracker.entity.ApplicationStatus;
import jobtracker.entity.OrigemAlteracao;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApplicationHistoryResponse {

	private Long id;
	private Long applicationId;
	private Long changedByUserId;
	private ApplicationStatus previousStatus;
	private ApplicationStatus newStatus;
	private String changedField;
	private String note;
	private OrigemAlteracao origem;
	private Instant changedAt;
}