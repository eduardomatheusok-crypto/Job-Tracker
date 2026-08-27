package jobtracker.repository;

import java.util.List;
import jobtracker.entity.Email;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EmailRepository extends JpaRepository<Email, Long> {

	List<Email> findAllByUserId(Long userId);

	boolean existsByMessageIdAndUserId(String messageId, Long userId);
}
