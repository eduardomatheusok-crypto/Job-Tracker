package jobtracker.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.function.Function;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

	private final Key signingKey;
	private final long expirationMillis;

	public JwtService(
		@Value("${jwt.secret}") String secret,
		@Value("${jwt.expiration-ms:86400000}") long expirationMillis
	) {
		this.signingKey = buildSigningKey(secret);
		this.expirationMillis = expirationMillis;
	}

	public String generateToken(String subject) {
		return generateToken(Map.of(), subject);
	}

	public String generateToken(Map<String, Object> extraClaims, String subject) {
		Instant now = Instant.now();
		Instant expiry = now.plusMillis(expirationMillis);

		return Jwts.builder()
			.claims(extraClaims)
			.subject(subject)
			.issuedAt(Date.from(now))
			.expiration(Date.from(expiry))
			.signWith(signingKey)
			.compact();
	}

	public String extractSubject(String token) {
		return extractClaim(token, Claims::getSubject);
	}

	public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
		Claims claims = extractAllClaims(token);
		return claimsResolver.apply(claims);
	}

	public boolean isTokenValid(String token, String subject) {
		return subject != null
			&& subject.equalsIgnoreCase(extractSubject(token))
			&& !isTokenExpired(token);
	}

	public boolean isTokenExpired(String token) {
		return extractExpiration(token).before(new Date());
	}

	private Date extractExpiration(String token) {
		return extractClaim(token, Claims::getExpiration);
	}

	private Claims extractAllClaims(String token) {
		return Jwts.parser()
			.verifyWith((javax.crypto.SecretKey) signingKey)
			.build()
			.parseSignedClaims(token)
			.getPayload();
	}

	private Key buildSigningKey(String secret) {
		String normalizedSecret = secret == null ? "" : secret.trim();
		if (normalizedSecret.isBlank()) {
			throw new IllegalStateException("JWT secret is not configured");
		}

		byte[] keyBytes = normalizedSecret.getBytes(StandardCharsets.UTF_8);
		if (keyBytes.length < 32) {
			throw new IllegalStateException("JWT secret must be at least 32 bytes");
		}

		return Keys.hmacShaKeyFor(keyBytes);
	}
}
