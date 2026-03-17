package ru.vstu.medsim.session;

import java.util.List;
import java.util.Set;

public final class GameRoleCatalog {

    public static final List<String> MANDATORY_LEADERSHIP_ROLES = List.of(
            "Главный врач",
            "Главная медсестра",
            "Главный инженер"
    );

    public static final List<String> EXECUTOR_ROLES = List.of(
            "Сестра поликлинического отделения",
            "Сестра диагностического отделения",
            "Заместитель главного инженера по медтехнике",
            "Заместитель главного инженера по АХЧ"
    );

    public static final Set<String> INVENTORY_ACCESS_ROLES = Set.copyOf(MANDATORY_LEADERSHIP_ROLES);

    private GameRoleCatalog() {
    }
}
