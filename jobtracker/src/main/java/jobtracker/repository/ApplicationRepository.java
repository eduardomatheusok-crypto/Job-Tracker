package jobtracker.repository;

import java.util.List;
import java.util.Optional;
import jobtracker.entity.Application;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ApplicationRepository extends JpaRepository<Application, Long> {

	List<Application> findAllByUserIdOrderByCreatedAtDesc(Long userId);

	@EntityGraph(attributePaths = "user")
	Page<Application> findByUserId(Long userId, Pageable pageable);

	Optional<Application> findByIdAndUserId(Long id, Long userId);

	Optional<Application> findFirstByUserIdAndCompanyNameIgnoreCaseOrderByCreatedAtDesc(Long userId, String companyName);

	@Query("SELECT a.status, COUNT(a) FROM Application a WHERE a.user.id = :userId GROUP BY a.status")
	List<Object[]> countApplicationsByStatus(@Param("userId") Long userId);
}