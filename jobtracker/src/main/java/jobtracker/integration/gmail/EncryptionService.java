package jobtracker.integration.gmail;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class EncryptionService {

	private final SecretKeySpec secretKey;

	public EncryptionService(@Value("${security.encryption-key}") String rawKey) {
		this.secretKey = buildKey(rawKey);
	}

	public String encrypt(String value) {
		if (value == null) {
			return null;
		}
		try {
			Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
			cipher.init(Cipher.ENCRYPT_MODE, secretKey);
			byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
			return Base64.getEncoder().encodeToString(encrypted);
		} catch (Exception e) {
			throw new RuntimeException("Error during encryption", e);
		}
	}

	public String decrypt(String encryptedValue) {
		if (encryptedValue == null) {
			return null;
		}
		try {
			Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
			cipher.init(Cipher.DECRYPT_MODE, secretKey);
			byte[] decrypted = cipher.doFinal(Base64.getDecoder().decode(encryptedValue));
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
