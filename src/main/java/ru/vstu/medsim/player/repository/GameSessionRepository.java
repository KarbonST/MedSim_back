package ru.vstu.medsim.player.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.vstu.medsim.player.domain.GameSession;

import java.util.Optional;

public interface GameSessionRepository extends JpaRepository<GameSession, Long> {

    Optional<GameSession> findByCode(String code);
}
