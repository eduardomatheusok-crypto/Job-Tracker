package jobtracker.repository;

import java.util.List;
import java.util.Optional;
import jobtracker.entity.Application;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApplicationRepository extends JpaRepository<Application, Long> {

	List<Application> findAllByUserIdOrderByCreatedAtDesc(Long userId);

	Optional<Application> findByIdAndUserId(Long id, Long userId);
}
