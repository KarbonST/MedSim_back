package ru.vstu.medsim.kanban.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.vstu.medsim.kanban.domain.KanbanSolutionOption;

import java.util.List;
import java.util.Optional;

public interface KanbanSolutionOptionRepository extends JpaRepository<KanbanSolutionOption, Long> {

    List<KanbanSolutionOption> findAllByProblemTemplateIdAndActiveTrueOrderBySortOrderAscIdAsc(Long problemTemplateId);

    Optional<KanbanSolutionOption> findByIdAndProblemTemplateIdAndActiveTrue(Long id, Long problemTemplateId);
}
