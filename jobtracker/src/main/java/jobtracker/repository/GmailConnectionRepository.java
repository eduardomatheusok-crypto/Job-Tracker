package jobtracker.repository;

import java.util.List;
import java.util.Optional;
import jobtracker.entity.GmailConnection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface GmailConnectionRepository extends JpaRepository<GmailConnection, Long> {

	Optional<GmailConnection> findByUserId(Long userId);

	boolean existsByUserId(Long userId);

	List<GmailConnection> findAllByActiveTrue();

	@Modifying
	@Transactional
	@Query("DELETE FROM GmailConnection g WHERE g.user.id = :userId")
	void deleteByUserId(@Param("userId") Long userId);
}

