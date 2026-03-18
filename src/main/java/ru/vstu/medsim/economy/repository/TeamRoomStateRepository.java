package ru.vstu.medsim.economy.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.vstu.medsim.economy.domain.TeamRoomState;

import java.util.List;

public interface TeamRoomStateRepository extends JpaRepository<TeamRoomState, Long> {

    List<TeamRoomState> findAllByTeamIdInOrderByTeamSortOrderAscClinicRoomSortOrderAscIdAsc(List<Long> teamIds);
}
