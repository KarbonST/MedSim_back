package ru.vstu.medsim.chat.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.vstu.medsim.chat.domain.TeamChatMessage;

import java.util.List;

public interface TeamChatMessageRepository extends JpaRepository<TeamChatMessage, Long> {

    List<TeamChatMessage> findAllByGameSessionIdAndTeamIdOrderByCreatedAtAscIdAsc(Long gameSessionId, Long teamId);

    List<TeamChatMessage> findAllByGameSessionIdOrderByCreatedAtAscIdAsc(Long gameSessionId);

    void deleteAllByGameSessionId(Long gameSessionId);
}
