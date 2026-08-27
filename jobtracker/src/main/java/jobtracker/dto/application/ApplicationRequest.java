package jobtracker.dto.application;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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
public class ApplicationRequest {

	@NotBlank
	@Size(max = 150)
	private String companyName;

	@NotBlank
	@Size(max = 150)
	private String position;

	@Size(max = 150)
	private String location;

	@Size(max = 500)
	private String jobUrl;

	@Size(max = 2000)
	private String notes;

	private ApplicationStatus status;
}
