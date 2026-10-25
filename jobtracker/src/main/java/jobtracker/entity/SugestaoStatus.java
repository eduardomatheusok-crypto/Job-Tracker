package jobtracker.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

/** Mudança de status sugerida pelo agente com confiança baixa, aguardando revisão do usuário. */
@Entity
@Table(name = "sugestao_status")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SugestaoStatus {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "email_id")
	private Email email;

	/** Candidatura encontrada para a empresa do e-mail; null se nenhuma corresponder. */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "application_id")
	private Application application;

	@Column(length = 150)
	private String empresa;

	@Column(length = 150)
	private String cargo;

	@Enumerated(EnumType.STRING)
	@Column(name = "novo_status", length = 30)
	private ApplicationStatus novoStatus;

	@Column
	private Double confianca;

	@Column(length = 2000)
	private String justificativa;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	@Builder.Default
	private SituacaoSugestao situacao = SituacaoSugestao.PENDENTE;

	@Column(name = "criado_em", nullable = false, updatable = false)
	private Instant criadoEm;

	@PrePersist
	void prePersist() {
		criadoEm = Instant.now();
	}
}
