package jobtracker.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import jobtracker.ia.TipoEmail;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Marca um e-mail do Gmail como já classificado, para a sincronização não reenviá-lo ao LLM. */
@Entity
@Table(name = "email_processado")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmailProcessado {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "gmail_id", nullable = false, unique = true, length = 255)
	private String gmailId;

	@Column(name = "user_id", nullable = false)
	private Long userId;

	/** Null para e-mails tratados pelo parser antigo, antes da classificação por IA. */
	@Enumerated(EnumType.STRING)
	@Column(name = "tipo_email", length = 30)
	private TipoEmail tipoEmail;

	@Column(name = "processado_em", nullable = false)
	private Instant processadoEm;

	@PrePersist
	void prePersist() {
		if (processadoEm == null) {
			processadoEm = Instant.now();
		}
	}
}
