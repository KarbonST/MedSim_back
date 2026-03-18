package ru.vstu.medsim.economy.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.vstu.medsim.economy.domain.ClinicRoomTemplate;

import java.util.List;

public interface ClinicRoomTemplateRepository extends JpaRepository<ClinicRoomTemplate, Long> {

    List<ClinicRoomTemplate> findAllByOrderBySortOrderAscIdAsc();
}
