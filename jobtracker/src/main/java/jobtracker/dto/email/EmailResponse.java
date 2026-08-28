package jobtracker.dto.email;

import java.time.Instant;
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
public class EmailResponse {

	private Long id;
	private Long userId;
	private Long applicationId;
	private String messageId;
	private String subject;
	private String fromAddress;
	private String snippet;
	private String rawContent;
	private Instant receivedAt;
	private Instant processedAt;
	private Instant createdAt;
}