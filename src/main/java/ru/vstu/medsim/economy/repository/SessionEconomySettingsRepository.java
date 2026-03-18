package ru.vstu.medsim.economy.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.vstu.medsim.economy.domain.SessionEconomySettings;

import java.util.Optional;

public interface SessionEconomySettingsRepository extends JpaRepository<SessionEconomySettings, Long> {

    Optional<SessionEconomySettings> findByGameSessionId(Long gameSessionId);
}
