package jobtracker.repository;

import java.util.Optional;
import jobtracker.entity.GmailConnection;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GmailConnectionRepository extends JpaRepository<GmailConnection, Long> {

	Optional<GmailConnection> findByUserId(Long userId);

	boolean existsByUserId(Long userId);
}
