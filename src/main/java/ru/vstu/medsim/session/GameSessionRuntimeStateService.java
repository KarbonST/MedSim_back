package ru.vstu.medsim.session;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import ru.vstu.medsim.economy.SessionEconomyService;
import ru.vstu.medsim.kanban.KanbanService;
import ru.vstu.medsim.player.domain.GameSession;
import ru.vstu.medsim.player.domain.GameSessionStatus;
import ru.vstu.medsim.player.domain.SessionTimerStatus;
import ru.vstu.medsim.player.repository.GameSessionRepository;
import ru.vstu.medsim.session.domain.SessionStageSetting;
import ru.vstu.medsim.session.repository.SessionStageSettingRepository;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class GameSessionRuntimeStateService {

    private static final Logger log = LoggerFactory.getLogger(GameSessionRuntimeStateService.class);

    private final GameSessionRepository gameSessionRepository;
    private final SessionStageSettingRepository sessionStageSettingRepository;
    private final SessionEconomyService sessionEconomyService;
    private final KanbanService kanbanService;

    public GameSessionRuntimeStateService(
            GameSessionRepository gameSessionRepository,
            SessionStageSettingRepository sessionStageSettingRepository,
            @Lazy SessionEconomyService sessionEconomyService,
            @Lazy KanbanService kanbanService
    ) {
        this.gameSessionRepository = gameSessionRepository;
        this.sessionStageSettingRepository = sessionStageSettingRepository;
        this.sessionEconomyService = sessionEconomyService;
        this.kanbanService = kanbanService;
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

        List<SessionStageSetting> stages = sessionStageSettingRepository
                .findAllByGameSessionIdOrderByStageNumberAsc(session.getId());
        SessionStageSetting activeStage = resolveActiveStage(session, stages);

        if (isFinalStage(session, stages)) {
            finishExpiredFinalStage(session, activeStage);
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

    private void finishExpiredFinalStage(GameSession session, SessionStageSetting activeStage) {
        if (activeStage != null) {
            List<Long> activatedProblemStateIds = sessionEconomyService.activateFinalStageCrisisIfNeeded(session);
            kanbanService.recordStageCrisisEscalations(session, activatedProblemStateIds);

            if (activeStage.getInteractionMode().hasProblemWorkflow()) {
                sessionEconomyService.settleStageForSession(session, activeStage.getStageNumber());
            }
        }

        session.finish();
        gameSessionRepository.save(session);
        log.info(
                "Game session auto-finished after final stage timer elapsed: sessionCode={}, activeStageNumber={}",
                session.getCode(),
                session.getActiveStageNumber()
        );
    }

    private boolean isFinalStage(GameSession session, List<SessionStageSetting> stages) {
        if (session.getActiveStageNumber() == null || stages.isEmpty()) {
            return false;
        }

        int lastStageNumber = stages.get(stages.size() - 1).getStageNumber();
        return session.getActiveStageNumber() == lastStageNumber;
    }

    private SessionStageSetting resolveActiveStage(GameSession session, List<SessionStageSetting> stages) {
        if (session.getActiveStageNumber() == null) {
            return null;
        }

        return stages.stream()
                .filter(stage -> stage.getStageNumber().equals(session.getActiveStageNumber()))
                .findFirst()
                .orElse(null);
    }
}
