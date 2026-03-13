package ru.vstu.medsim.player.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.vstu.medsim.player.domain.SessionParticipant;

import java.util.Optional;

public interface SessionParticipantRepository extends JpaRepository<SessionParticipant, Long> {

    Optional<SessionParticipant> findByGameSessionIdAndPlayerId(Long gameSessionId, Long playerId);
}
