package ru.vstu.medsim.kanban.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.vstu.medsim.kanban.domain.KanbanCardStatus;
import ru.vstu.medsim.kanban.domain.TeamKanbanCard;

import java.util.List;
import java.util.Optional;

public interface TeamKanbanCardRepository extends JpaRepository<TeamKanbanCard, Long> {

    @Query("""
            select card
            from TeamKanbanCard card
            join fetch card.team team
            join fetch card.problemState problemState
            join fetch problemState.teamRoomState roomState
            join fetch roomState.clinicRoom room
            join fetch problemState.problemTemplate problemTemplate
            left join fetch card.assignee assignee
            left join fetch assignee.player assigneePlayer
            where team.id = :teamId
            order by room.sortOrder asc, problemTemplate.problemNumber asc, card.id asc
            """)
    List<TeamKanbanCard> findAllCardsForTeam(@Param("teamId") Long teamId);

    @Query("""
            select card
            from TeamKanbanCard card
            join fetch card.team team
            join fetch card.problemState problemState
            join fetch problemState.teamRoomState roomState
            join fetch roomState.clinicRoom room
            join fetch problemState.problemTemplate problemTemplate
            left join fetch card.assignee assignee
            left join fetch assignee.player assigneePlayer
            where team.gameSession.id = :gameSessionId
            order by team.sortOrder asc, room.sortOrder asc, problemTemplate.problemNumber asc, card.id asc
            """)
    List<TeamKanbanCard> findAllByGameSessionId(@Param("gameSessionId") Long gameSessionId);

    @Query("""
            select card.problemState.id
            from TeamKanbanCard card
            where card.team.id = :teamId
            """)
    List<Long> findProblemStateIdsByTeamId(@Param("teamId") Long teamId);

    @Query("""
            select card
            from TeamKanbanCard card
            join fetch card.team team
            join fetch card.problemState problemState
            join fetch problemState.teamRoomState roomState
            join fetch roomState.clinicRoom room
            join fetch problemState.problemTemplate problemTemplate
            left join fetch card.assignee assignee
            left join fetch assignee.player assigneePlayer
            where card.id = :cardId
              and team.id = :teamId
              and team.gameSession.id = :gameSessionId
            """)
    Optional<TeamKanbanCard> findCardForTeamSession(
            @Param("cardId") Long cardId,
            @Param("teamId") Long teamId,
            @Param("gameSessionId") Long gameSessionId
    );

    @Query("""
            select card
            from TeamKanbanCard card
            join fetch card.team team
            join fetch card.problemState problemState
            join fetch problemState.teamRoomState roomState
            join fetch roomState.clinicRoom room
            join fetch problemState.problemTemplate problemTemplate
            left join fetch card.assignee assignee
            left join fetch assignee.player assigneePlayer
            where team.gameSession.id = :gameSessionId
              and card.status = :status
            order by team.sortOrder asc, room.sortOrder asc, problemTemplate.problemNumber asc, card.id asc
            """)
    List<TeamKanbanCard> findAllByGameSessionIdAndStatus(
            @Param("gameSessionId") Long gameSessionId,
            @Param("status") KanbanCardStatus status
    );
}
