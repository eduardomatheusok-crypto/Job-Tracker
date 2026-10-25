package jobtracker.integration.gmail;

/**
 * Falha de sincronização que <b>não</b> invalida a conexão com o Gmail
 * (rede, limite de requisições, Gmail API desativada etc.). A conexão continua ativa
 * e o usuário pode tentar novamente sem reconectar a conta.
 */
public class GmailSyncException extends RuntimeException {

	public GmailSyncException(String message, Throwable cause) {
		super(message, cause);
	}
}
