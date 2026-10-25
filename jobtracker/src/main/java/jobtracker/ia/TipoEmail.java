package jobtracker.ia;

/** Categoria de um e-mail segundo o classificador de IA. */
public enum TipoEmail {
	/** Não tem relação com processos seletivos (newsletter, banco, notificação de serviço...). */
	NAO_RELACIONADO,
	/** Divulgação de vaga, alerta de vagas ou convite de recrutador para uma vaga ainda não aplicada. */
	ANUNCIO_VAGA,
	/** Notícia sobre uma candidatura do usuário (confirmação, etapa, entrevista, recusa, oferta). */
	ATUALIZACAO_CANDIDATURA
}
