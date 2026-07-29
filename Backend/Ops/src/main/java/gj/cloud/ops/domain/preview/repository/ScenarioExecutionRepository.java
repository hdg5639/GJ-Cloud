package gj.cloud.ops.domain.preview.repository;

import gj.cloud.ops.domain.preview.entity.ScenarioExecutionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ScenarioExecutionRepository extends JpaRepository<ScenarioExecutionEntity, String> {

    List<ScenarioExecutionEntity> findAllBySuiteRunIdOrderByStartedAtAsc(String suiteRunId);
}
