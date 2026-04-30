package ru.vstu.medsim.economy.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.vstu.medsim.economy.domain.TeamEconomyEvent;
import ru.vstu.medsim.economy.domain.TeamEconomyEventType;

import java.util.List;

public interface TeamEconomyEventRepository extends JpaRepository<TeamEconomyEvent, Long> {

    @Modifying
    @Query("""
            delete from TeamEconomyEvent event
            where event.team.gameSession.id = :gameSessionId
            """)
    void deleteAllByTeamGameSessionId(@Param("gameSessionId") Long gameSessionId);

    boolean existsByTeamIdAndStageNumberAndEventType(Long teamId, Integer stageNumber, TeamEconomyEventType eventType);

    List<TeamEconomyEvent> findAllByTeamIdAndEventTypeOrderByStageNumberDescCreatedAtDescIdDesc(
            Long teamId,
            TeamEconomyEventType eventType
    );

    @Query("""
            select event
            from TeamEconomyEvent event
            where event.team.id = :teamId
            order by event.createdAt desc, event.id desc
            """)
    List<TeamEconomyEvent> findRecentForTeam(@Param("teamId") Long teamId, Pageable pageable);

    @Query("""
            select event
            from TeamEconomyEvent event
            join fetch event.team team
            left join fetch event.actor actor
            left join fetch actor.player actorPlayer
            left join fetch event.kanbanCard kanbanCard
            where team.gameSession.id = :gameSessionId
            order by team.sortOrder asc, event.createdAt asc, event.id asc
            """)
    List<TeamEconomyEvent> findAllByGameSessionId(@Param("gameSessionId") Long gameSessionId);
}
