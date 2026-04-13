package ru.vstu.medsim.session.domain;

public enum StageInteractionMode {
    CHAT_ONLY,
    CHAT_WITH_PROBLEMS,
    CHAT_AND_KANBAN;

    public boolean hasProblemWorkflow() {
        return this == CHAT_WITH_PROBLEMS || this == CHAT_AND_KANBAN;
    }
}
