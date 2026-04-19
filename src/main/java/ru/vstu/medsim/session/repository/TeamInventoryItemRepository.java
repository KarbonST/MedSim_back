package ru.vstu.medsim.session.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.vstu.medsim.session.domain.TeamInventoryItem;

import java.util.List;
import java.util.Optional;

public interface TeamInventoryItemRepository extends JpaRepository<TeamInventoryItem, Long> {

    List<TeamInventoryItem> findAllByTeamIdOrderByItemNameAsc(Long teamId);

    Optional<TeamInventoryItem> findByTeamIdAndItemNameIgnoreCase(Long teamId, String itemName);

    long countByTeamId(Long teamId);

    @Modifying
    @Query("""
            DELETE FROM TeamInventoryItem item
            WHERE item.team.id IN (
                SELECT team.id
                FROM SessionTeam team
                WHERE team.gameSession.id = :gameSessionId
            )
            """)
    void deleteAllByTeamGameSessionId(@Param("gameSessionId") Long gameSessionId);
}
