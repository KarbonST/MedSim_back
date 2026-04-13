package ru.vstu.medsim.kanban.domain;

public enum KanbanCardStatus {
    REGISTERED,
    ASSIGNED,
    READY_FOR_WORK,
    IN_PROGRESS,
    DEPARTMENT_REVIEW,
    CHIEF_DOCTOR_REVIEW,
    REWORK,
    DONE
}
