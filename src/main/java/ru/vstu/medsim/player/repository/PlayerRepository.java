package ru.vstu.medsim.player.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.vstu.medsim.player.domain.Player;

import java.util.Optional;

public interface PlayerRepository extends JpaRepository<Player, Long> {

    Optional<Player> findByDisplayNameIgnoreCaseAndHospitalPositionIgnoreCase(String displayName, String hospitalPosition);
}
