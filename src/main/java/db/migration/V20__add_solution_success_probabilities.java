package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class V20__add_solution_success_probabilities extends BaseJavaMigration {

    private static final Pattern BASE_PROBABILITY_PATTERN = Pattern.compile(
            "Вероятность по методике:\\s*(\\d+(?:[.,]\\d+)?)%",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );
    private static final Pattern ENGINEERING_MULTIPLIER_PATTERN = Pattern.compile(
            "инженер\\s+(\\d+(?:[.,]\\d+)?)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );
    private static final Pattern NURSING_MULTIPLIER_PATTERN = Pattern.compile(
            "медсестра\\s+(\\d+(?:[.,]\\d+)?)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        addProbabilityColumnIfMissing(connection, "base_success_probability");
        addProbabilityColumnIfMissing(connection, "nursing_success_multiplier");
        addProbabilityColumnIfMissing(connection, "engineering_success_multiplier");
        seedProbabilityValues(connection);
    }

    private void addProbabilityColumnIfMissing(Connection connection, String columnName) throws SQLException {
        if (columnExists(connection, columnName)) {
            return;
        }

        try (Statement statement = connection.createStatement()) {
            statement.execute(
                    "ALTER TABLE kanban_solution_options ADD COLUMN "
                            + columnName
                            + " NUMERIC(5, 2) NOT NULL DEFAULT 1.00"
            );
        }
    }

    private boolean columnExists(Connection connection, String columnName) throws SQLException {
        String sql = """
                SELECT COUNT(*)
                FROM information_schema.columns
                WHERE LOWER(table_name) = 'kanban_solution_options'
                  AND LOWER(column_name) = ?
                """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, columnName.toLowerCase(Locale.ROOT));
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() && resultSet.getInt(1) > 0;
            }
        }
    }

    private void seedProbabilityValues(Connection connection) throws SQLException {
        String selectSql = "SELECT id, description FROM kanban_solution_options";
        String updateSql = """
                UPDATE kanban_solution_options
                SET base_success_probability = ?,
                    nursing_success_multiplier = ?,
                    engineering_success_multiplier = ?
                WHERE id = ?
                """;

        try (
                PreparedStatement selectStatement = connection.prepareStatement(selectSql);
                ResultSet resultSet = selectStatement.executeQuery();
                PreparedStatement updateStatement = connection.prepareStatement(updateSql)
        ) {
            while (resultSet.next()) {
                String description = resultSet.getString("description");
                updateStatement.setBigDecimal(1, parseProbability(description));
                updateStatement.setBigDecimal(2, parseMultiplier(description, NURSING_MULTIPLIER_PATTERN));
                updateStatement.setBigDecimal(3, parseMultiplier(description, ENGINEERING_MULTIPLIER_PATTERN));
                updateStatement.setLong(4, resultSet.getLong("id"));
                updateStatement.addBatch();
            }

            updateStatement.executeBatch();
        }
    }

    private BigDecimal parseProbability(String description) {
        Matcher matcher = BASE_PROBABILITY_PATTERN.matcher(description != null ? description : "");
        if (!matcher.find()) {
            return BigDecimal.ONE.setScale(2);
        }

        return parseDecimal(matcher.group(1))
                .movePointLeft(2)
                .setScale(2);
    }

    private BigDecimal parseMultiplier(String description, Pattern pattern) {
        Matcher matcher = pattern.matcher(description != null ? description : "");
        if (!matcher.find()) {
            return BigDecimal.ONE.setScale(2);
        }

        return parseDecimal(matcher.group(1)).setScale(2);
    }

    private BigDecimal parseDecimal(String value) {
        return new BigDecimal(value.replace(',', '.'));
    }
}
