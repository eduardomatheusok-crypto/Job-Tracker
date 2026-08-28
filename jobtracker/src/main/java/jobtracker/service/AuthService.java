package jobtracker.service;

import jobtracker.dto.auth.AuthResponse;
import jobtracker.dto.auth.LoginRequest;
import jobtracker.dto.auth.RegisterRequest;
import jobtracker.entity.User;
import jobtracker.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Transactional
public class AuthService {

	private final UserService userService;
	private final AuthenticationManager authenticationManager;
	private final JwtService jwtService;

	public AuthResponse register(RegisterRequest request) {
		String email = request.getEmail().trim().toLowerCase();
		if (userService.existsByEmail(email)) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already registered");
		}

		User user = userService.createUser(
			request.getName(),
			email,
			request.getPassword(),
			null
		);

		return AuthResponse.builder()
			.token(jwtService.generateToken(user.getEmail()))
			.tokenType("Bearer")
			.user(userService.toResponse(user))
			.build();
	}

	@Transactional(readOnly = true)
	public AuthResponse login(LoginRequest request) {
		String email = request.getEmail().trim().toLowerCase();
		try {
			authenticationManager.authenticate(
				new UsernamePasswordAuthenticationToken(email, request.getPassword())
			);
		} catch (AuthenticationException ex) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
		}

		User user = userService.findByEmailOrThrow(email);

		return AuthResponse.builder()
			.token(jwtService.generateToken(user.getEmail()))
			.tokenType("Bearer")
			.user(userService.toResponse(user))
			.build();
	}
}
