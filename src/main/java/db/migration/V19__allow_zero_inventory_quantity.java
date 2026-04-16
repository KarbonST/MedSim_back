package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class V19__allow_zero_inventory_quantity extends BaseJavaMigration {

    private static final String TABLE_NAME = "team_inventory_items";
    private static final String NON_NEGATIVE_CONSTRAINT = "chk_team_inventory_items_quantity_non_negative";

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();

        for (String constraintName : findPositiveQuantityConstraints(connection)) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("ALTER TABLE " + TABLE_NAME + " DROP CONSTRAINT " + quoteIdentifier(constraintName));
            }
        }

        if (!constraintExists(connection, NON_NEGATIVE_CONSTRAINT)) {
            try (Statement statement = connection.createStatement()) {
                statement.execute(
                        "ALTER TABLE " + TABLE_NAME
                                + " ADD CONSTRAINT " + NON_NEGATIVE_CONSTRAINT
                                + " CHECK (quantity >= 0)"
                );
            }
        }
    }

    private List<String> findPositiveQuantityConstraints(Connection connection) throws SQLException {
        List<String> constraintNames = new ArrayList<>();
        String sql = """
                SELECT tc.constraint_name, cc.check_clause
                FROM information_schema.table_constraints tc
                JOIN information_schema.check_constraints cc
                  ON cc.constraint_catalog = tc.constraint_catalog
                 AND cc.constraint_schema = tc.constraint_schema
                 AND cc.constraint_name = tc.constraint_name
                WHERE LOWER(tc.table_name) = ?
                  AND tc.constraint_type = 'CHECK'
                """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, TABLE_NAME);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    String checkClause = normalizeCheckClause(resultSet.getString("check_clause"));
                    if (checkClause.contains("quantity>0") && !checkClause.contains("quantity>=0")) {
                        constraintNames.add(resultSet.getString("constraint_name"));
                    }
                }
            }
        }

        return constraintNames;
    }

    private boolean constraintExists(Connection connection, String constraintName) throws SQLException {
        String sql = """
                SELECT COUNT(*)
                FROM information_schema.table_constraints
                WHERE LOWER(table_name) = ?
                  AND LOWER(constraint_name) = ?
                """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, TABLE_NAME);
            statement.setString(2, constraintName.toLowerCase(Locale.ROOT));
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() && resultSet.getInt(1) > 0;
            }
        }
    }

    private String normalizeCheckClause(String checkClause) {
        if (checkClause == null) {
            return "";
        }

        return checkClause
                .toLowerCase(Locale.ROOT)
                .replace("\"", "")
                .replace("(", "")
                .replace(")", "")
                .replace(" ", "");
    }

    private String quoteIdentifier(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }
}
