package jobtracker.integration.gmail;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
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

	@Test
	void dateFilter_firstSync_usesInitialWindow() {
		String filter = GmailService.imapDateFilter(Optional.empty(), LocalDate.of(2026, 10, 25), 90);

		assertThat(filter).isEqualTo("after:2026/07/27");
	}

	@Test
	void dateFilter_withStoredEmails_startsFromNewestWithMargin() {
		Instant newest = Instant.parse("2026-09-17T13:45:00Z");

		String filter = GmailService.imapDateFilter(Optional.of(newest), LocalDate.of(2026, 10, 25), 90);

		assertThat(filter).isEqualTo("after:2026/09/15");
	}

	@Test
	void fullImapQuery_staysPlainAscii() {
		String query = GmailService.SEARCH_QUERY_INBOUND_IMAP + " "
			+ GmailService.imapDateFilter(Optional.empty(), LocalDate.of(2026, 10, 25), 90);

		assertThat(StandardCharsets.US_ASCII.newEncoder().canEncode(query)).isTrue();
		assertThat(query).doesNotContain("\"").doesNotContain("label:sent");
	}
}
