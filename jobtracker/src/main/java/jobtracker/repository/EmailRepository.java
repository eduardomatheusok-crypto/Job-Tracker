package jobtracker.repository;

import java.util.Optional;
import jobtracker.entity.Email;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface EmailRepository extends JpaRepository<Email, Long> {

	@EntityGraph(attributePaths = {"user", "application"})
	Page<Email> findByUserId(Long userId, Pageable pageable);

	@EntityGraph(attributePaths = {"user", "application"})
	Page<Email> findByUserIdAndApplicationId(Long userId, Long applicationId, Pageable pageable);

	Optional<Email> findByIdAndUserId(Long id, Long userId);

	boolean existsByMessageIdAndUserId(String messageId, Long userId);

	@Query("SELECT e.receivedAt FROM Email e WHERE e.application.id = :applicationId ORDER BY e.receivedAt ASC")
	java.util.List<java.time.Instant> findReceivedAtOrderedAsc(@Param("applicationId") Long applicationId);
}