package ru.vstu.medsim.economy.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.vstu.medsim.economy.domain.ClinicRoomProblemTemplate;

import java.util.List;

public interface ClinicRoomProblemTemplateRepository extends JpaRepository<ClinicRoomProblemTemplate, Long> {

    List<ClinicRoomProblemTemplate> findAllByOrderByClinicRoomSortOrderAscProblemNumberAscIdAsc();
}
