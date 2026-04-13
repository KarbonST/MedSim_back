package ru.vstu.medsim.session.domain;

public enum StageInteractionMode {
    CHAT_WITH_PROBLEMS,
    CHAT_AND_KANBAN;

    public boolean hasProblemWorkflow() {
        return true;
    }
}
