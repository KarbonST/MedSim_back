package ru.vstu.medsim.economy.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.vstu.medsim.economy.domain.TeamEconomyEvent;
import ru.vstu.medsim.economy.domain.TeamEconomyEventType;

import java.util.List;

public interface TeamEconomyEventRepository extends JpaRepository<TeamEconomyEvent, Long> {

    boolean existsByTeamIdAndStageNumberAndEventType(Long teamId, Integer stageNumber, TeamEconomyEventType eventType);

    @Query("""
            select event
            from TeamEconomyEvent event
            where event.team.id = :teamId
            order by event.createdAt desc, event.id desc
            """)
    List<TeamEconomyEvent> findRecentForTeam(@Param("teamId") Long teamId, Pageable pageable);
}
