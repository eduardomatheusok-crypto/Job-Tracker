package jobtracker.ia;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import jobtracker.entity.ApplicationStatus;

/**
 * Saída estruturada do classificador. A ordem dos campos importa: a justificativa vem primeiro
 * para o modelo raciocinar antes de decidir a categoria e o status.
 */
public record ClassificacaoEmail(
	@JsonPropertyDescription("Explicação curta, em português, dos trechos do e-mail que levaram à classificação")
	String justificativa,

	@JsonPropertyDescription("Categoria do e-mail: NAO_RELACIONADO, ANUNCIO_VAGA ou ATUALIZACAO_CANDIDATURA")
	TipoEmail tipoEmail,

	@JsonPropertyDescription("Novo status da candidatura. Preencher somente quando tipoEmail = ATUALIZACAO_CANDIDATURA; caso contrário, null")
	ApplicationStatus novoStatus,

	@JsonPropertyDescription("Nome da empresa contratante (não a plataforma de recrutamento, como Gupy ou LinkedIn); null se não identificada")
	String empresa,

	@JsonPropertyDescription("Cargo/vaga mencionado no e-mail; null se não identificado")
	String cargo,

	@JsonPropertyDescription("Confiança na classificação, de 0.0 (nenhuma) a 1.0 (total)")
	Double confianca
) {}
