package ru.vstu.medsim.session.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.vstu.medsim.session.domain.SessionStageSetting;

import java.util.List;

public interface SessionStageSettingRepository extends JpaRepository<SessionStageSetting, Long> {

    List<SessionStageSetting> findAllByGameSessionIdOrderByStageNumberAsc(Long gameSessionId);

    long countByGameSessionId(Long gameSessionId);

    void deleteAllByGameSessionId(Long gameSessionId);
}
