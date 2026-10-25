package jobtracker.repository;

import jobtracker.entity.AnuncioVaga;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnuncioVagaRepository extends JpaRepository<AnuncioVaga, Long> {
}
