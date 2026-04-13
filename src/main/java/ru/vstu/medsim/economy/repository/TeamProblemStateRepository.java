package ru.vstu.medsim.economy.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.vstu.medsim.economy.domain.TeamProblemState;

import java.util.List;

public interface TeamProblemStateRepository extends JpaRepository<TeamProblemState, Long> {

    List<TeamProblemState> findAllByTeamRoomStateIdInOrderByTeamRoomStateClinicRoomSortOrderAscProblemTemplateProblemNumberAscIdAsc(List<Long> teamRoomStateIds);

    List<TeamProblemState> findAllByTeamRoomStateTeamIdOrderByTeamRoomStateClinicRoomSortOrderAscProblemTemplateProblemNumberAscIdAsc(Long teamId);
}
