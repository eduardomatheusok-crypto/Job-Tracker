package jobtracker.controller;

import jobtracker.dto.user.UserResponse;
import jobtracker.service.CurrentUserService;
import jobtracker.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller REST para operações relacionadas a usuários.
 *
 * <p>Provê o endpoint {@code GET /api/users/me} para consultar o perfil
 * do usuário atualmente autenticado na sessão STATELESS via token JWT.
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

	private final UserService userService;
	private final CurrentUserService currentUserService;

	/**
	 * Retorna as informações de perfil do usuário atualmente autenticado.
	 *
	 * @return ResponseEntity contendo o UserResponse do usuário logado
	 */
	@GetMapping("/me")
	public ResponseEntity<UserResponse> getMyProfile() {
		return ResponseEntity.ok(userService.toResponse(currentUserService.get()));
	}
}
