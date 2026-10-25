package jobtracker.repository;

import jobtracker.entity.EmailProcessado;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailProcessadoRepository extends JpaRepository<EmailProcessado, Long> {

	boolean existsByGmailId(String gmailId);
}
