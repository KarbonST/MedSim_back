package ru.vstu.medsim.session.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.vstu.medsim.session.domain.SessionTeam;

import java.util.List;
import java.util.Optional;

public interface SessionTeamRepository extends JpaRepository<SessionTeam, Long> {

    List<SessionTeam> findAllByGameSessionIdOrderBySortOrderAscIdAsc(Long gameSessionId);

    Optional<SessionTeam> findByIdAndGameSessionId(Long teamId, Long gameSessionId);

    long countByGameSessionId(Long gameSessionId);
}
