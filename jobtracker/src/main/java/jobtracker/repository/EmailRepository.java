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

	/** Há e-mail vinculado à candidatura mais recente que {@code receivedAt}? (evita regredir o status) */
	boolean existsByApplicationIdAndReceivedAtAfter(Long applicationId, java.time.Instant receivedAt);

	Optional<Email> findFirstByUserIdAndDirectionOrderByReceivedAtDesc(Long userId, jobtracker.entity.EmailDirection direction);

	@Query("SELECT e.receivedAt FROM Email e WHERE e.application.id = :applicationId ORDER BY e.receivedAt ASC")
	java.util.List<java.time.Instant> findReceivedAtOrderedAsc(@Param("applicationId") Long applicationId);

	java.util.List<Email> findAllByUserIdAndApplicationIsNull(Long userId);

	/** E-mails recebidos ainda não classificados pela IA, do mais novo ao mais antigo. */
	@Query("SELECT e FROM Email e WHERE e.user.id = :userId AND e.direction = jobtracker.entity.EmailDirection.INBOUND "
		+ "AND NOT EXISTS (SELECT 1 FROM EmailProcessado p WHERE p.gmailId = e.messageId) ORDER BY e.receivedAt DESC")
	java.util.List<Email> findPendentesDeClassificacao(@Param("userId") Long userId, org.springframework.data.domain.Pageable limite);
}