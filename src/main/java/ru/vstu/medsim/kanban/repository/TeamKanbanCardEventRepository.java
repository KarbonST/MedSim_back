package ru.vstu.medsim.kanban.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.vstu.medsim.kanban.domain.TeamKanbanCardEvent;

import java.util.List;

public interface TeamKanbanCardEventRepository extends JpaRepository<TeamKanbanCardEvent, Long> {

    @Query("""
            select event
            from TeamKanbanCardEvent event
            left join fetch event.actor actor
            left join fetch actor.player actorPlayer
            left join fetch event.targetParticipant targetParticipant
            left join fetch targetParticipant.player targetPlayer
            where event.card.id in :cardIds
            order by event.card.id asc, event.createdAt asc, event.id asc
            """)
    List<TeamKanbanCardEvent> findAllForCardIds(@Param("cardIds") List<Long> cardIds);

    @Query("""
            select event
            from TeamKanbanCardEvent event
            join fetch event.card card
            join fetch card.problemState problemState
            join fetch problemState.teamRoomState roomState
            join fetch roomState.clinicRoom room
            join fetch problemState.problemTemplate problemTemplate
            left join fetch event.actor actor
            left join fetch actor.player actorPlayer
            left join fetch event.targetParticipant targetParticipant
            left join fetch targetParticipant.player targetPlayer
            where targetParticipant.id = :participantId
            order by event.createdAt desc, event.id desc
            """)
    List<TeamKanbanCardEvent> findRecentTargetedEvents(
            @Param("participantId") Long participantId,
            Pageable pageable
    );
}
