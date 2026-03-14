package ru.vstu.medsim.player.repository;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.vstu.medsim.player.domain.GameSession;
import ru.vstu.medsim.player.domain.GameSessionStatus;

import java.util.List;
import java.util.Optional;

public interface GameSessionRepository extends JpaRepository<GameSession, Long> {

    Optional<GameSession> findByCode(String code);

    boolean existsByCode(String code);

    List<GameSession> findAll(Sort sort);

    List<GameSession> findAllByStatusOrderByCreatedAtDescIdDesc(GameSessionStatus status);
}
