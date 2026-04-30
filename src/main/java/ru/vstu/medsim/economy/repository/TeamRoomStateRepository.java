package ru.vstu.medsim.economy.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.vstu.medsim.economy.domain.TeamRoomState;

import java.util.List;

public interface TeamRoomStateRepository extends JpaRepository<TeamRoomState, Long> {

    List<TeamRoomState> findAllByTeamIdInOrderByTeamSortOrderAscClinicRoomSortOrderAscIdAsc(List<Long> teamIds);

    @Modifying
    @Query("""
            delete from TeamRoomState roomState
            where roomState.team.gameSession.id = :gameSessionId
            """)
    void deleteAllByTeamGameSessionId(@Param("gameSessionId") Long gameSessionId);
}
