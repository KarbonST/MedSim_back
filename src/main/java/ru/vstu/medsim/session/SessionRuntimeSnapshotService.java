package ru.vstu.medsim.session;

import org.springframework.stereotype.Service;
import ru.vstu.medsim.player.domain.GameSession;
import ru.vstu.medsim.session.domain.SessionStageSetting;
import ru.vstu.medsim.session.dto.SessionRuntimeItem;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class SessionRuntimeSnapshotService {

    public SessionRuntimeItem buildRuntime(GameSession session, List<SessionStageSetting> stages) {
        SessionStageSetting activeStage = resolveActiveStage(session, stages);

        if (activeStage == null) {
            return new SessionRuntimeItem(
                    null,
                    null,
                    null,
                    session.getTimerStatus().name(),
                    null,
                    null
            );
        }

        int remainingSeconds = session.getTimerRemainingSeconds() != null
                ? session.getRemainingSecondsAt(LocalDateTime.now())
                : activeStage.getDurationMinutes() * 60;

        return new SessionRuntimeItem(
                activeStage.getStageNumber(),
                activeStage.getDurationMinutes(),
                activeStage.getInteractionMode().name(),
                session.getTimerStatus().name(),
                remainingSeconds,
                session.getTimerEndsAt()
        );
    }

    private SessionStageSetting resolveActiveStage(GameSession session, List<SessionStageSetting> stages) {
        if (stages.isEmpty()) {
            return null;
        }

        if (session.getActiveStageNumber() == null) {
            return stages.get(0);
        }

        return stages.stream()
                .filter(stage -> stage.getStageNumber() == session.getActiveStageNumber())
                .findFirst()
                .orElse(stages.get(0));
    }
}
