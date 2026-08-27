package jobtracker.integration.gmail;

import java.io.IOException;
import java.util.Map;
import jobtracker.entity.User;
import jobtracker.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

/**
 * Controller que expõe os endpoints REST da integração Gmail.
 *
 * <p>Rotas:
 * <ul>
 *   <li>GET  /api/gmail/auth-url    – retorna URL de autorização Google (JWT obrigatório)
 *   <li>GET  /api/gmail/callback    – callback OAuth público; troca o código e salva conexão
 *   <li>POST /api/gmail/sync        – sincroniza e-mails do usuário logado (JWT obrigatório)
 *   <li>GET  /api/gmail/status      – retorna status da conexão ativa (JWT obrigatório)
 *   <li>POST /api/gmail/disconnect  – remove a conexão do usuário logado (JWT obrigatório)
 * </ul>
 *
 * <p>O callback é stateless: o e-mail do usuário viaja criptografado no parâmetro {@code state}
 * gerado em {@code getAuthorizationUrl} e descriptografado aqui pelo {@link GmailOAuthService}.
 *
 * <p>Para obter o usuário autenticado, usa o mesmo padrão do {@code ApplicationService}:
 * lê {@link SecurityContextHolder} e resolve o e-mail via {@link UserService}.
 */
@RestController
@RequestMapping("/api/gmail")
@RequiredArgsConstructor
public class GmailController {

	private final GmailOAuthService gmailOAuthService;
	private final GmailService gmailService;
	private final UserService userService;

	/** URL do frontend para redirecionar após o callback (configurável por ambiente). */
	@Value("${app.frontend-url:http://localhost:3000}")
	private String frontendUrl;

	// -----------------------------------------------------------------------
	// Helpers
	// -----------------------------------------------------------------------

	/** Obtém o usuário autenticado a partir do SecurityContext (mesmo padrão do ApplicationService). */
	private User currentUser() {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		return userService.findByEmailOrThrow(auth.getName());
	}

	// -----------------------------------------------------------------------
	// GET /api/gmail/auth-url
	// Requer JWT. Retorna a URL de autorização Google com state criptografado.
	// -----------------------------------------------------------------------
	@GetMapping("/auth-url")
	public ResponseEntity<Map<String, String>> getAuthUrl() throws IOException {
		String url = gmailOAuthService.getAuthorizationUrl(currentUser().getEmail());
		return ResponseEntity.ok(Map.of("authUrl", url));
	}

	// -----------------------------------------------------------------------
	// GET /api/gmail/callback   (rota pública – sem JWT)
	// Recebido diretamente do Google após o usuário conceder permissão.
	// Troca o code pelo token, persiste a conexão e retorna HTML de confirmação.
	// -----------------------------------------------------------------------
	@GetMapping("/callback")
	public ResponseEntity<String> handleCallback(
		@RequestParam String code,
		@RequestParam String state
	) {
		try {
			gmailService.connectUser(code, state);
			String html = """
				<!DOCTYPE html>
				<html lang="pt-BR">
				<head><meta charset="UTF-8"><title>Gmail conectado</title></head>
				<body>
				  <h2>✅ Gmail conectado com sucesso!</h2>
				  <p>Você já pode fechar esta janela e voltar ao JobTracker.</p>
				  <script>
				    setTimeout(() => window.close(), 3000);
				  </script>
				</body>
				</html>
				""";
			return ResponseEntity.ok()
				.header("Content-Type", "text/html; charset=UTF-8")
				.body(html);
		} catch (Exception e) {
			return ResponseEntity.badRequest().body("Erro ao conectar Gmail: " + e.getMessage());
		}
	}

	// -----------------------------------------------------------------------
	// POST /api/gmail/sync
	// Requer JWT. Sincroniza e-mails e retorna o número de e-mails processados.
	// -----------------------------------------------------------------------
	@PostMapping("/sync")
	public ResponseEntity<Map<String, Object>> syncEmails() throws IOException {
		int count = gmailService.syncEmails(currentUser().getId());
		return ResponseEntity.ok(Map.of(
			"message", "Sincronização concluída",
			"emailsProcessed", count
		));
	}

	// -----------------------------------------------------------------------
	// GET /api/gmail/status
	// Requer JWT. Retorna se a conexão Gmail está ativa e o e-mail vinculado.
	// -----------------------------------------------------------------------
	@GetMapping("/status")
	public ResponseEntity<Map<String, Object>> getStatus() {
		return gmailService.findConnectionByUserId(currentUser().getId())
			.map(conn -> ResponseEntity.ok(Map.<String, Object>of(
				"connected", conn.isActive(),
				"gmailAddress", conn.getGmailAddress() != null ? conn.getGmailAddress() : ""
			)))
			.orElse(ResponseEntity.ok(Map.of("connected", false, "gmailAddress", "")));
	}

	// -----------------------------------------------------------------------
	// POST /api/gmail/disconnect
	// Requer JWT. Remove a conexão Gmail do usuário logado.
	// -----------------------------------------------------------------------
	@PostMapping("/disconnect")
	public ResponseEntity<Map<String, String>> disconnect() {
		gmailService.disconnectUser(currentUser().getId());
		return ResponseEntity.ok(Map.of("message", "Gmail desconectado com sucesso."));
	}
}
