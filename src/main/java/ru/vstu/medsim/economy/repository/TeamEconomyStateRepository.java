package ru.vstu.medsim.economy.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.vstu.medsim.economy.domain.TeamEconomyState;

import java.util.List;

public interface TeamEconomyStateRepository extends JpaRepository<TeamEconomyState, Long> {

    List<TeamEconomyState> findAllByTeamGameSessionIdOrderByTeamSortOrderAscIdAsc(Long gameSessionId);
}
