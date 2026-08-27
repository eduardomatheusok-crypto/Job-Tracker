package jobtracker.controller;

import jobtracker.dto.user.UserResponse;
import jobtracker.entity.User;
import jobtracker.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
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

	/**
	 * Retorna as informações de perfil do usuário atualmente autenticado.
	 *
	 * @return ResponseEntity contendo o UserResponse do usuário logado
	 */
	@GetMapping("/me")
	public ResponseEntity<UserResponse> getMyProfile() {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		if (auth == null || auth.getName() == null) {
			return ResponseEntity.status(401).build();
		}
		
		User currentUser = userService.findByEmailOrThrow(auth.getName());
		return ResponseEntity.ok(userService.toResponse(currentUser));
	}
}
