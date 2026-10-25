package jobtracker.repository;

import jobtracker.entity.SugestaoStatus;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SugestaoStatusRepository extends JpaRepository<SugestaoStatus, Long> {
}
