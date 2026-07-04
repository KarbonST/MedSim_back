package ru.vstu.medsim.session;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import ru.vstu.medsim.player.domain.GameSession;
import ru.vstu.medsim.player.domain.GameSessionStatus;
import ru.vstu.medsim.player.domain.SessionTimerStatus;
import ru.vstu.medsim.player.repository.GameSessionRepository;

import java.time.LocalDateTime;

@Service
public class GameSessionRuntimeStateService {

    private static final Logger log = LoggerFactory.getLogger(GameSessionRuntimeStateService.class);

    private final GameSessionRepository gameSessionRepository;

    public GameSessionRuntimeStateService(GameSessionRepository gameSessionRepository) {
        this.gameSessionRepository = gameSessionRepository;
    }

    public void syncExpiredTimer(GameSession session) {
        if (session.getStatus() != GameSessionStatus.IN_PROGRESS) {
            return;
        }

        if (session.getTimerStatus() != SessionTimerStatus.RUNNING) {
            return;
        }

        if (session.getRemainingSecondsAt(LocalDateTime.now()) > 0) {
            return;
        }

        session.pause();
        gameSessionRepository.save(session);
        log.info(
                "Game session auto-paused after timer elapsed: sessionCode={}, activeStageNumber={}",
                session.getCode(),
                session.getActiveStageNumber()
        );
    }
}
