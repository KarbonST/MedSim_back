package ru.vstu.medsim.player.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.vstu.medsim.player.domain.SessionParticipant;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SessionParticipantRepository extends JpaRepository<SessionParticipant, Long> {

    Optional<SessionParticipant> findByGameSessionIdAndPlayerId(Long gameSessionId, Long playerId);

    Optional<SessionParticipant> findByIdAndGameSessionId(Long id, Long gameSessionId);

    List<SessionParticipant> findAllByGameSessionIdOrderByJoinedAtAscIdAsc(Long gameSessionId);

    long countByGameSessionId(Long gameSessionId);

    long countByPlayerIdIn(Collection<Long> playerIds);

    boolean existsByPlayerId(Long playerId);

    void deleteAllByGameSessionId(Long gameSessionId);
}
