package jobtracker.ia;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

/** Classifica e-mails com o Gemini (modelo, temperatura e resposta JSON vêm do application.properties). */
@Service
public class ClassificadorEmailService {

	/** Limita o tamanho do corpo enviado ao modelo (custo/latência); o início do e-mail concentra a informação útil. */
	static final int MAX_CORPO = 8000;

	private final ChatClient chatClient;

	public ClassificadorEmailService(ChatClient.Builder chatClientBuilder) {
		this.chatClient = chatClientBuilder
			.defaultSystem(PromptsIA.SISTEMA)
			.build();
	}

	public ClassificacaoEmail classificar(String remetente, String assunto, String corpo) {
		return chatClient.prompt()
			.user(u -> u.text(PromptsIA.USUARIO)
				.param("remetente", valorOuVazio(remetente))
				.param("assunto", valorOuVazio(assunto))
				.param("corpo", truncar(valorOuVazio(corpo))))
			.call()
			.entity(ClassificacaoEmail.class);
	}

	private static String valorOuVazio(String valor) {
		return valor != null ? valor : "";
	}

	private static String truncar(String texto) {
		return texto.length() > MAX_CORPO ? texto.substring(0, MAX_CORPO) : texto;
	}
}
