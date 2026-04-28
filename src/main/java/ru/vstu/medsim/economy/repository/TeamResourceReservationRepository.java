package ru.vstu.medsim.economy.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.vstu.medsim.economy.domain.ResourceReservationStatus;
import ru.vstu.medsim.economy.domain.TeamResourceReservation;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TeamResourceReservationRepository extends JpaRepository<TeamResourceReservation, Long> {

    Optional<TeamResourceReservation> findFirstByKanbanCardIdAndStatusOrderByUpdatedAtDescIdDesc(
            Long kanbanCardId,
            ResourceReservationStatus status
    );

    Optional<TeamResourceReservation> findFirstByKanbanCardIdAndStatusInOrderByUpdatedAtDescIdDesc(
            Long kanbanCardId,
            Collection<ResourceReservationStatus> statuses
    );

    List<TeamResourceReservation> findAllByTeamIdAndStatusOrderByCreatedAtAscIdAsc(
            Long teamId,
            ResourceReservationStatus status
    );

    List<TeamResourceReservation> findAllByTeamGameSessionIdAndStatusOrderByTeamSortOrderAscCreatedAtAscIdAsc(
            Long gameSessionId,
            ResourceReservationStatus status
    );
}
