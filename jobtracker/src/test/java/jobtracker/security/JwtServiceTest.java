package jobtracker.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

	private static final String SECRET = "test-secret-key-that-is-at-least-32-bytes-long!!";

	@Test
	void generateAndValidateToken_roundTrips() {
		JwtService jwtService = new JwtService(SECRET, 86400000);

		String token = jwtService.generateToken("alice@example.com");

		assertEquals("alice@example.com", jwtService.extractSubject(token));
		assertTrue(jwtService.isTokenValid(token, "alice@example.com"));
		assertFalse(jwtService.isTokenValid(token, "bob@example.com"));
	}

	@Test
	void generateToken_withExtraClaims_exposesClaim() {
		JwtService jwtService = new JwtService(SECRET, 86400000);

		String token = jwtService.generateToken(Map.of("userId", 42L), "alice@example.com");

		Long userId = jwtService.extractClaim(token, claims -> claims.get("userId", Long.class));

		assertEquals(42L, userId);
	}

	@Test
	void expiredToken_isRejected() throws InterruptedException {
		JwtService jwtService = new JwtService(SECRET, 50);

		String token = jwtService.generateToken("alice@example.com");
		Thread.sleep(120);

		assertThrows(Exception.class, () -> jwtService.extractSubject(token));
		assertThrows(Exception.class, () -> jwtService.isTokenValid(token, "alice@example.com"));
	}

	@Test
	void tokenSignedWithDifferentSecret_isRejected() {
		JwtService signer = new JwtService(SECRET, 86400000);
		JwtService verifier = new JwtService("a-completely-different-secret-key-for-verification!", 86400000);

		String token = signer.generateToken("alice@example.com");

		assertThrows(Exception.class, () -> verifier.extractSubject(token));
	}

	@Test
	void blankSecret_isRejectedAtStartup() {
		assertThrows(IllegalStateException.class, () -> new JwtService("   ", 86400000));
	}

	@Test
	void shortSecret_isRejectedAtStartup() {
		assertThrows(IllegalStateException.class, () -> new JwtService("too-short", 86400000));
	}
}