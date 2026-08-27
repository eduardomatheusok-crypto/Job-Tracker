package jobtracker.security;

import java.util.Collection;
import java.util.List;
import jobtracker.entity.User;
import jobtracker.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserDetailsConfig implements UserDetailsService {

	private final UserRepository userRepository;

	@Override
	public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
		User user = userRepository.findByEmailIgnoreCase(username.trim())
			.orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));

		return new org.springframework.security.core.userdetails.User(
			user.getEmail(),
			user.getPassword(),
			authorities(user)
		);
	}

	private Collection<? extends GrantedAuthority> authorities(User user) {
		return List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
	}
}
