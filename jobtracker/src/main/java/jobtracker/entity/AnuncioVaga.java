package jobtracker.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Vaga divulgada por e-mail (alerta, recrutador) que ainda não é uma candidatura. */
@Entity
@Table(name = "anuncio_vaga")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnuncioVaga {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "email_id")
	private Email email;

	@Column(length = 150)
	private String empresa;

	@Column(length = 150)
	private String cargo;

	@Column(length = 2000)
	private String justificativa;

	@Column(name = "criado_em", nullable = false, updatable = false)
	private Instant criadoEm;

	@PrePersist
	void prePersist() {
		criadoEm = Instant.now();
	}
}
