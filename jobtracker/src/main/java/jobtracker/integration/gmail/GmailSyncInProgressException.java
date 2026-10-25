package jobtracker.integration.gmail;

/** Já existe uma sincronização rodando para o usuário; a nova chamada é descartada. */
public class GmailSyncInProgressException extends RuntimeException {

	public GmailSyncInProgressException(Long userId) {
		super("Sync already in progress for user " + userId);
	}
}
