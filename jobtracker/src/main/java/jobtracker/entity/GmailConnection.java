package jobtracker.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "gmail_connections")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GmailConnection {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false, unique = true)
	private User user;

	@Column(nullable = false, length = 255)
	private String gmailAddress;

	@Column(length = 2000)
	private String encryptedAccessToken;

	@Column(length = 2000)
	private String encryptedRefreshToken;

	@Column
	private Instant tokenExpiresAt;

	@Column(nullable = false, updatable = false)
	private Instant connectedAt;

	@Column
	private Instant lastSyncedAt;

	@Column(nullable = false)
	private boolean active;

	@PrePersist
	void prePersist() {
		final Instant now = Instant.now();
		connectedAt = now;
		active = true;
	}

	@PreUpdate
	void preUpdate() {
		lastSyncedAt = Instant.now();
	}
}
