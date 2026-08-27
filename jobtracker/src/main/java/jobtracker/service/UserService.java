package jobtracker.service;

import jobtracker.dto.user.UserRequest;
import jobtracker.dto.user.UserResponse;
import jobtracker.entity.Role;
import jobtracker.entity.User;
import jobtracker.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class UserService {

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;

	@Transactional(readOnly = true)
	public User findByIdOrThrow(Long id) {
		return userRepository.findById(id)
			.orElseThrow(() -> new EntityNotFoundException("User not found: " + id));
	}

	@Transactional(readOnly = true)
	public User findByEmailOrThrow(String email) {
		return userRepository.findByEmailIgnoreCase(normalizeEmail(email))
			.orElseThrow(() -> new EntityNotFoundException("User not found by email"));
	}

	@Transactional(readOnly = true)
	public boolean existsByEmail(String email) {
		return userRepository.existsByEmailIgnoreCase(normalizeEmail(email));
	}

	public User createUser(UserRequest request, String rawPassword) {
		User user = User.builder()
			.name(request.getName())
			.email(normalizeEmail(request.getEmail()))
			.password(passwordEncoder.encode(rawPassword))
			.role(request.getRole() != null ? request.getRole() : Role.USER)
			.build();

		return userRepository.save(user);
	}

	public User createUser(String name, String email, String rawPassword, Role role) {
		User user = User.builder()
			.name(name)
			.email(normalizeEmail(email))
			.password(passwordEncoder.encode(rawPassword))
			.role(role != null ? role : Role.USER)
			.build();

		return userRepository.save(user);
	}

	public User updateUser(Long id, UserRequest request) {
		User user = findByIdOrThrow(id);
		user.setName(request.getName());
		user.setEmail(normalizeEmail(request.getEmail()));
		if (request.getRole() != null) {
			user.setRole(request.getRole());
		}
		return userRepository.save(user);
	}

	@Transactional(readOnly = true)
	public UserResponse toResponse(User user) {
		return UserResponse.builder()
			.id(user.getId())
			.name(user.getName())
			.email(user.getEmail())
			.role(user.getRole())
			.createdAt(user.getCreatedAt())
			.updatedAt(user.getUpdatedAt())
			.build();
	}

	private String normalizeEmail(String email) {
		return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
	}
}
