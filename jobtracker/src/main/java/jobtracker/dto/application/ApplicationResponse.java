package jobtracker.dto.application;

import java.time.Instant;
import jobtracker.entity.ApplicationStatus;
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
public class ApplicationResponse {

	private Long id;
	private Long userId;
	private String companyName;
	private String position;
	private String location;
	private String jobUrl;
	private String notes;
	private ApplicationStatus status;
	private Instant appliedAt;
	private Instant createdAt;
	private Instant updatedAt;
}
