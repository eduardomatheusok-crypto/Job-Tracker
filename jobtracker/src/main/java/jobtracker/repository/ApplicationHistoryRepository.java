package jobtracker.repository;

import java.util.List;
import jobtracker.entity.ApplicationHistory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApplicationHistoryRepository extends JpaRepository<ApplicationHistory, Long> {

	List<ApplicationHistory> findByApplicationIdOrderByChangedAtDesc(Long applicationId);

	List<ApplicationHistory> findByApplicationUserIdOrderByChangedAtDesc(Long userId);

	void deleteByApplicationId(Long applicationId);
}
