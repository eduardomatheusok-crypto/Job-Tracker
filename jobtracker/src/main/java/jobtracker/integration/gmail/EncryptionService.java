package jobtracker.integration.gmail;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class EncryptionService {

	private static final String TRANSFORMATION = "AES/GCM/NoPadding";
	private static final int IV_LENGTH_BYTES = 12;
	private static final int TAG_LENGTH_BITS = 128;

	private final SecretKeySpec secretKey;
	private final SecureRandom secureRandom = new SecureRandom();

	public EncryptionService(@Value("${security.encryption-key}") String rawKey) {
		if (rawKey == null || rawKey.isBlank()) {
			throw new IllegalStateException("Encryption key is not configured");
		}
		this.secretKey = buildKey(rawKey);
	}

	public String encrypt(String value) {
		if (value == null) {
			return null;
		}
		try {
			byte[] iv = new byte[IV_LENGTH_BYTES];
			secureRandom.nextBytes(iv);
			Cipher cipher = Cipher.getInstance(TRANSFORMATION);
			cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
			byte[] ciphertext = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
			byte[] combined = new byte[iv.length + ciphertext.length];
			System.arraycopy(iv, 0, combined, 0, iv.length);
			System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);
			return Base64.getEncoder().encodeToString(combined);
		} catch (Exception e) {
			throw new RuntimeException("Error during encryption", e);
		}
	}

	public String decrypt(String encryptedValue) {
		if (encryptedValue == null) {
			return null;
		}
		try {
			byte[] combined = Base64.getDecoder().decode(encryptedValue);
			if (combined.length <= IV_LENGTH_BYTES) {
				throw new IllegalArgumentException("Invalid encrypted payload");
			}
			byte[] iv = new byte[IV_LENGTH_BYTES];
			System.arraycopy(combined, 0, iv, 0, iv.length);
			byte[] ciphertext = new byte[combined.length - iv.length];
			System.arraycopy(combined, iv.length, ciphertext, 0, ciphertext.length);
			Cipher cipher = Cipher.getInstance(TRANSFORMATION);
			cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
			byte[] decrypted = cipher.doFinal(ciphertext);
			return new String(decrypted, StandardCharsets.UTF_8);
		} catch (Exception e) {
			throw new RuntimeException("Error during decryption", e);
		}
	}

	private SecretKeySpec buildKey(String rawKey) {
		try {
			byte[] keyBytes = rawKey.getBytes(StandardCharsets.UTF_8);
			MessageDigest sha = MessageDigest.getInstance("SHA-256");
			keyBytes = sha.digest(keyBytes);
			return new SecretKeySpec(keyBytes, "AES");
		} catch (Exception e) {
			throw new RuntimeException("Error initializing encryption key", e);
		}
	}
}