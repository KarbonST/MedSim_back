package ru.vstu.medsim.session.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.vstu.medsim.session.domain.TeamInventoryItem;

import java.util.List;

public interface TeamInventoryItemRepository extends JpaRepository<TeamInventoryItem, Long> {

    List<TeamInventoryItem> findAllByTeamIdOrderByItemNameAsc(Long teamId);

    long countByTeamId(Long teamId);
}
