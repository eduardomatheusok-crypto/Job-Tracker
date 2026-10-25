package jobtracker.integration.gmail;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class GmailImapQueryTest {

	// O Gmail recusa o X-GM-RAW com "BAD Could not parse command" quando há acentos (CHARSET + literal)
	// ou aspas internas; a busca IMAP precisa ser ASCII puro e sem aspas
	@Test
	void imapQuery_isPlainAsciiWithoutQuotes() {
		String query = GmailService.SEARCH_QUERY_INBOUND_IMAP;

		assertThat(StandardCharsets.US_ASCII.newEncoder().canEncode(query)).isTrue();
		assertThat(query).doesNotContain("\"").doesNotContain("\\");
	}
}
