package gj.cloud.ops.domain.preview.repository;

import gj.cloud.ops.domain.preview.entity.CustomScenarioRevisionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CustomScenarioRevisionRepository
        extends JpaRepository<CustomScenarioRevisionEntity, String> {

    Optional<CustomScenarioRevisionEntity> findTopByScenarioIdOrderByRevisionDesc(String scenarioId);

    List<CustomScenarioRevisionEntity> findAllByScenarioIdOrderByRevisionDesc(String scenarioId);
}
