package jobtracker.integration.gmail;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class EncryptionServiceTest {

	private static final String KEY = "some-strong-encryption-secret";

	@Test
	void encryptDecrypt_roundTrips() {
		EncryptionService encryptionService = new EncryptionService(KEY);

		String encrypted = encryptionService.encrypt("very-secret-token");

		assertNotEquals("very-secret-token", encrypted);
		assertEquals("very-secret-token", encryptionService.decrypt(encrypted));
	}

	@Test
	void encrypt_usesFreshIvEachTime() {
		EncryptionService encryptionService = new EncryptionService(KEY);

		String first = encryptionService.encrypt("same-value");
		String second = encryptionService.encrypt("same-value");

		assertNotEquals(first, second);
	}

	@Test
	void encrypt_null_returnsNull() {
		EncryptionService encryptionService = new EncryptionService(KEY);

		assertNull(encryptionService.encrypt(null));
	}

	@Test
	void blankKey_isRejectedAtStartup() {
		assertThrows(IllegalStateException.class, () -> new EncryptionService("  "));
	}

	@Test
	void wrongKey_cannotDecrypt() {
		EncryptionService serviceA = new EncryptionService(KEY);
		EncryptionService serviceB = new EncryptionService("a-different-encryption-secret");

		String encrypted = serviceA.encrypt("oauth-token");

		assertThrows(RuntimeException.class, () -> serviceB.decrypt(encrypted));
	}
}