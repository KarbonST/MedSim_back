package ru.vstu.medsim.session;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Component
public class TeamInventoryCatalog {

    private static final List<InventoryTemplate> INVENTORY_TEMPLATES = List.of(
            new InventoryTemplate("Антисептик", 0),
            new InventoryTemplate("Ведра для отходов", 2),
            new InventoryTemplate("Влажные салфетки", 22),
            new InventoryTemplate("Градусники", 15),
            new InventoryTemplate("Инвалидное кресло", 0),
            new InventoryTemplate("Лампы основного освещения", 2),
            new InventoryTemplate("Лекарства", 73),
            new InventoryTemplate("Мыло", 4),
            new InventoryTemplate("Одноразовые перчатки", 17),
            new InventoryTemplate("Простыни", 10),
            new InventoryTemplate("Свинцовые накидки", 0),
            new InventoryTemplate("Средство для мытья полов", 3),
            new InventoryTemplate("Средство для удаления грибка", 5),
            new InventoryTemplate("Стулья", 0),
            new InventoryTemplate("Туалетная бумага", 12),
            new InventoryTemplate("УФ лампы", 0),
            new InventoryTemplate("Футляры для градусников", 23)
    );

    public List<InventorySeed> generateInitialInventory() {
        List<InventoryTemplate> templates = new ArrayList<>(INVENTORY_TEMPLATES);
        Collections.shuffle(templates, ThreadLocalRandom.current());

        return templates.stream()
                .map(template -> new InventorySeed(template.itemName(), randomQuantity(template.baseQuantity())))
                .toList();
    }

    public List<String> getAllItemNames() {
        return INVENTORY_TEMPLATES.stream()
                .map(InventoryTemplate::itemName)
                .toList();
    }

    private int randomQuantity(int baseQuantity) {
        int upperBound = Math.max(1, Math.min(Math.max(baseQuantity, 1), 5));
        return ThreadLocalRandom.current().nextInt(0, upperBound + 1);
    }

    private record InventoryTemplate(String itemName, int baseQuantity) {
    }

    public record InventorySeed(String itemName, int quantity) {
    }
}
