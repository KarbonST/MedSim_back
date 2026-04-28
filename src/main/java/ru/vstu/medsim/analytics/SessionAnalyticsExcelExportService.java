package ru.vstu.medsim.analytics;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.vstu.medsim.analytics.dto.GameSessionAnalyticsResponse;
import ru.vstu.medsim.analytics.dto.SessionAnalyticsStageItem;
import ru.vstu.medsim.analytics.dto.TeamAnalyticsCardItem;
import ru.vstu.medsim.analytics.dto.TeamAnalyticsItem;
import ru.vstu.medsim.analytics.dto.TeamAnalyticsStageItem;
import ru.vstu.medsim.analytics.dto.TeamParticipantAnalyticsItem;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;

@Service
public class SessionAnalyticsExcelExportService {

    private final SessionAnalyticsService sessionAnalyticsService;

    public SessionAnalyticsExcelExportService(SessionAnalyticsService sessionAnalyticsService) {
        this.sessionAnalyticsService = sessionAnalyticsService;
    }

    @Transactional(readOnly = true)
    public byte[] exportSessionAnalytics(String sessionCode) {
        GameSessionAnalyticsResponse analytics = sessionAnalyticsService.getSessionAnalytics(sessionCode);

        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            CellStyle titleStyle = createTitleStyle(workbook);
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle dateTimeStyle = createDateTimeStyle(workbook);
            CellStyle integerStyle = createIntegerStyle(workbook);
            CellStyle decimalStyle = createDecimalStyle(workbook);

            buildSummarySheet(workbook, analytics, titleStyle, headerStyle, dateTimeStyle, integerStyle, decimalStyle);
            buildTeamsSheet(workbook, analytics, titleStyle, headerStyle, integerStyle, decimalStyle);
            buildStagesSheet(workbook, analytics, titleStyle, headerStyle, integerStyle, decimalStyle);
            buildParticipantsSheet(workbook, analytics, titleStyle, headerStyle, integerStyle);
            buildCardsSheet(workbook, analytics, titleStyle, headerStyle, integerStyle);

            workbook.write(outputStream);
            return outputStream.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Не удалось подготовить Excel-отчёт по аналитике сессии.", exception);
        }
    }

    public String buildExportFilename(GameSessionAnalyticsResponse analytics) {
        return "medsim-analytics-%s.xlsx".formatted(analytics.sessionCode().toLowerCase());
    }

    @Transactional(readOnly = true)
    public String buildExportFilename(String sessionCode) {
        return buildExportFilename(sessionAnalyticsService.getSessionAnalytics(sessionCode));
    }

    private void buildSummarySheet(
            XSSFWorkbook workbook,
            GameSessionAnalyticsResponse analytics,
            CellStyle titleStyle,
            CellStyle headerStyle,
            CellStyle dateTimeStyle,
            CellStyle integerStyle,
            CellStyle decimalStyle
    ) {
        Sheet sheet = workbook.createSheet("Сводка");
        int rowIndex = 0;

        Row titleRow = sheet.createRow(rowIndex++);
        writeCell(titleRow, 0, "Послеигровая аналитика MedSim", titleStyle);

        rowIndex = writeKeyValue(sheet, rowIndex, "Сессия", analytics.sessionName(), null);
        rowIndex = writeKeyValue(sheet, rowIndex, "Код сессии", analytics.sessionCode(), null);
        rowIndex = writeKeyValue(sheet, rowIndex, "Статус", analytics.sessionStatus(), null);
        rowIndex = writeKeyValue(sheet, rowIndex, "Общий кризис 3 этапа", valueOrDash(analytics.finalStageCrisisType()), null);
        rowIndex = writeKeyValue(sheet, rowIndex, "Начало игры", analytics.startedAt(), dateTimeStyle);
        rowIndex = writeKeyValue(sheet, rowIndex, "Окончание игры", analytics.finishedAt(), dateTimeStyle);
        rowIndex = writeKeyValue(sheet, rowIndex, "Команд", analytics.teamCount(), integerStyle);
        rowIndex = writeKeyValue(sheet, rowIndex, "Участников", analytics.participantCount(), integerStyle);
        rowIndex = writeKeyValue(sheet, rowIndex, "Всего карточек", analytics.totalProblemCount(), integerStyle);
        rowIndex = writeKeyValue(sheet, rowIndex, "Закрыто карточек", analytics.resolvedProblemCount(), integerStyle);
        rowIndex = writeKeyValue(sheet, rowIndex, "Незакрытых карточек", analytics.unresolvedProblemCount(), integerStyle);
        rowIndex = writeKeyValue(sheet, rowIndex, "Возвратов", analytics.totalReturnCount(), integerStyle);
        rowIndex = writeKeyValue(sheet, rowIndex, "Hold-задач", analytics.totalHoldCount(), integerStyle);
        rowIndex = writeKeyValue(sheet, rowIndex, "Эскалаций", analytics.totalEscalatedProblemCount(), integerStyle);

        rowIndex += 1;
        Row teamHeader = sheet.createRow(rowIndex++);
        writeCell(teamHeader, 0, "Команда", headerStyle);
        writeCell(teamHeader, 1, "Место", headerStyle);
        writeCell(teamHeader, 2, "Баланс", headerStyle);
        writeCell(teamHeader, 3, "Закрыто", headerStyle);
        writeCell(teamHeader, 4, "Незакрыто", headerStyle);
        writeCell(teamHeader, 5, "Возвраты", headerStyle);
        writeCell(teamHeader, 6, "Узкое место", headerStyle);

        for (TeamAnalyticsItem team : analytics.teams()) {
            Row row = sheet.createRow(rowIndex++);
            writeCell(row, 0, team.teamName(), null);
            writeCell(row, 1, team.rank(), integerStyle);
            writeCell(row, 2, team.currentBalance(), decimalStyle);
            writeCell(row, 3, team.resolvedProblemCount(), integerStyle);
            writeCell(row, 4, team.unresolvedProblemCount(), integerStyle);
            writeCell(row, 5, team.returnCount(), integerStyle);
            writeCell(row, 6, team.bottleneckLabel(), null);
        }

        setSheetWidths(sheet, 22, 12, 14, 12, 14, 12, 28);
    }

    private void buildTeamsSheet(
            XSSFWorkbook workbook,
            GameSessionAnalyticsResponse analytics,
            CellStyle titleStyle,
            CellStyle headerStyle,
            CellStyle integerStyle,
            CellStyle decimalStyle
    ) {
        Sheet sheet = workbook.createSheet("Команды");
        int rowIndex = 0;

        Row titleRow = sheet.createRow(rowIndex++);
        writeCell(titleRow, 0, "Сводка по командам", titleStyle);

        Row headerRow = sheet.createRow(rowIndex++);
        writeCell(headerRow, 0, "Команда", headerStyle);
        writeCell(headerRow, 1, "Место", headerStyle);
        writeCell(headerRow, 2, "Участников", headerStyle);
        writeCell(headerRow, 3, "Всего карточек", headerStyle);
        writeCell(headerRow, 4, "Закрыто", headerStyle);
        writeCell(headerRow, 5, "Незакрыто", headerStyle);
        writeCell(headerRow, 6, "Возвраты", headerStyle);
        writeCell(headerRow, 7, "Hold", headerStyle);
        writeCell(headerRow, 8, "Эскалации", headerStyle);
        writeCell(headerRow, 9, "Активные эскалации", headerStyle);
        writeCell(headerRow, 10, "Баланс", headerStyle);
        writeCell(headerRow, 11, "Доступно", headerStyle);
        writeCell(headerRow, 12, "Доход", headerStyle);
        writeCell(headerRow, 13, "Расход", headerStyle);
        writeCell(headerRow, 14, "Штрафы", headerStyle);
        writeCell(headerRow, 15, "Бонусы", headerStyle);
        writeCell(headerRow, 16, "Среднее время цикла", headerStyle);
        writeCell(headerRow, 17, "Узкое место", headerStyle);

        for (TeamAnalyticsItem team : analytics.teams()) {
            Row row = sheet.createRow(rowIndex++);
            writeCell(row, 0, team.teamName(), null);
            writeCell(row, 1, team.rank(), integerStyle);
            writeCell(row, 2, team.participantCount(), integerStyle);
            writeCell(row, 3, team.totalProblemCount(), integerStyle);
            writeCell(row, 4, team.resolvedProblemCount(), integerStyle);
            writeCell(row, 5, team.unresolvedProblemCount(), integerStyle);
            writeCell(row, 6, team.returnCount(), integerStyle);
            writeCell(row, 7, team.holdCount(), integerStyle);
            writeCell(row, 8, team.escalatedProblemCount(), integerStyle);
            writeCell(row, 9, team.activeEscalationCount(), integerStyle);
            writeCell(row, 10, team.currentBalance(), decimalStyle);
            writeCell(row, 11, team.availableBalance(), decimalStyle);
            writeCell(row, 12, team.totalIncome(), decimalStyle);
            writeCell(row, 13, team.totalExpenses(), decimalStyle);
            writeCell(row, 14, team.totalPenalties(), decimalStyle);
            writeCell(row, 15, team.totalBonuses(), decimalStyle);
            writeCell(row, 16, formatDuration(team.avgFullCycleSeconds()), null);
            writeCell(row, 17, team.bottleneckLabel(), null);
        }

        setSheetWidths(sheet, 22, 10, 12, 14, 10, 12, 10, 10, 12, 16, 12, 12, 12, 12, 12, 12, 20, 28);
    }

    private void buildStagesSheet(
            XSSFWorkbook workbook,
            GameSessionAnalyticsResponse analytics,
            CellStyle titleStyle,
            CellStyle headerStyle,
            CellStyle integerStyle,
            CellStyle decimalStyle
    ) {
        Sheet sheet = workbook.createSheet("Этапы");
        int rowIndex = 0;

        Row titleRow = sheet.createRow(rowIndex++);
        writeCell(titleRow, 0, "Итоги по этапам", titleStyle);

        Row headerRow = sheet.createRow(rowIndex++);
        writeCell(headerRow, 0, "Срез", headerStyle);
        writeCell(headerRow, 1, "Команда", headerStyle);
        writeCell(headerRow, 2, "Этап", headerStyle);
        writeCell(headerRow, 3, "Режим", headerStyle);
        writeCell(headerRow, 4, "Минут", headerStyle);
        writeCell(headerRow, 5, "Всего карточек", headerStyle);
        writeCell(headerRow, 6, "Закрыто", headerStyle);
        writeCell(headerRow, 7, "Незакрыто", headerStyle);
        writeCell(headerRow, 8, "Возвраты", headerStyle);
        writeCell(headerRow, 9, "Hold", headerStyle);
        writeCell(headerRow, 10, "Эскалации", headerStyle);
        writeCell(headerRow, 11, "Активные эскалации", headerStyle);
        writeCell(headerRow, 12, "Итог этапа", headerStyle);

        for (SessionAnalyticsStageItem stage : analytics.stages()) {
            Row row = sheet.createRow(rowIndex++);
            writeCell(row, 0, "Все команды", null);
            writeCell(row, 1, "—", null);
            writeCell(row, 2, stage.stageNumber(), integerStyle);
            writeCell(row, 3, stage.interactionMode(), null);
            writeCell(row, 4, stage.durationMinutes(), integerStyle);
            writeCell(row, 5, stage.totalProblemCount(), integerStyle);
            writeCell(row, 6, stage.resolvedProblemCount(), integerStyle);
            writeCell(row, 7, stage.unresolvedProblemCount(), integerStyle);
            writeCell(row, 8, stage.returnCount(), integerStyle);
            writeCell(row, 9, stage.holdCount(), integerStyle);
            writeCell(row, 10, stage.escalatedProblemCount(), integerStyle);
            writeCell(row, 11, stage.activeEscalationCount(), integerStyle);
            writeCell(row, 12, stage.netAmount(), decimalStyle);
        }

        for (TeamAnalyticsItem team : analytics.teams()) {
            for (TeamAnalyticsStageItem stage : team.stages()) {
                Row row = sheet.createRow(rowIndex++);
                writeCell(row, 0, "Команда", null);
                writeCell(row, 1, team.teamName(), null);
                writeCell(row, 2, stage.stageNumber(), integerStyle);
                writeCell(row, 3, stage.interactionMode(), null);
                writeCell(row, 4, stage.durationMinutes(), integerStyle);
                writeCell(row, 5, stage.totalProblemCount(), integerStyle);
                writeCell(row, 6, stage.resolvedProblemCount(), integerStyle);
                writeCell(row, 7, stage.unresolvedProblemCount(), integerStyle);
                writeCell(row, 8, stage.returnCount(), integerStyle);
                writeCell(row, 9, stage.holdCount(), integerStyle);
                writeCell(row, 10, stage.escalatedProblemCount(), integerStyle);
                writeCell(row, 11, stage.activeEscalationCount(), integerStyle);
                writeCell(row, 12, stage.netAmount(), decimalStyle);
            }
        }

        setSheetWidths(sheet, 16, 22, 10, 22, 10, 14, 12, 14, 10, 10, 12, 18, 14);
    }

    private void buildParticipantsSheet(
            XSSFWorkbook workbook,
            GameSessionAnalyticsResponse analytics,
            CellStyle titleStyle,
            CellStyle headerStyle,
            CellStyle integerStyle
    ) {
        Sheet sheet = workbook.createSheet("Участники");
        int rowIndex = 0;

        Row titleRow = sheet.createRow(rowIndex++);
        writeCell(titleRow, 0, "Активность участников", titleStyle);

        Row headerRow = sheet.createRow(rowIndex++);
        writeCell(headerRow, 0, "Команда", headerStyle);
        writeCell(headerRow, 1, "Участник", headerStyle);
        writeCell(headerRow, 2, "Игровая роль", headerStyle);
        writeCell(headerRow, 3, "Назначено", headerStyle);
        writeCell(headerRow, 4, "Взято в работу", headerStyle);
        writeCell(headerRow, 5, "Отправлено на согласование", headerStyle);
        writeCell(headerRow, 6, "Закрыто как исполнителем", headerStyle);
        writeCell(headerRow, 7, "Согласований подразделения", headerStyle);
        writeCell(headerRow, 8, "Финальных согласований", headerStyle);
        writeCell(headerRow, 9, "Возвраты", headerStyle);
        writeCell(headerRow, 10, "Hold", headerStyle);

        for (TeamAnalyticsItem team : analytics.teams()) {
            for (TeamParticipantAnalyticsItem participant : team.participants()) {
                Row row = sheet.createRow(rowIndex++);
                writeCell(row, 0, team.teamName(), null);
                writeCell(row, 1, participant.displayName(), null);
                writeCell(row, 2, valueOrDash(participant.gameRole()), null);
                writeCell(row, 3, participant.tasksAssignedCount(), integerStyle);
                writeCell(row, 4, participant.tasksStartedCount(), integerStyle);
                writeCell(row, 5, participant.tasksSentToReviewCount(), integerStyle);
                writeCell(row, 6, participant.tasksClosedAsExecutorCount(), integerStyle);
                writeCell(row, 7, participant.departmentApprovalsCount(), integerStyle);
                writeCell(row, 8, participant.finalApprovalsCount(), integerStyle);
                writeCell(row, 9, participant.returnsTriggeredCount(), integerStyle);
                writeCell(row, 10, participant.holdsTriggeredCount(), integerStyle);
            }
        }

        setSheetWidths(sheet, 22, 24, 20, 12, 16, 22, 24, 22, 22, 12, 10);
    }

    private void buildCardsSheet(
            XSSFWorkbook workbook,
            GameSessionAnalyticsResponse analytics,
            CellStyle titleStyle,
            CellStyle headerStyle,
            CellStyle integerStyle
    ) {
        Sheet sheet = workbook.createSheet("Карточки");
        int rowIndex = 0;

        Row titleRow = sheet.createRow(rowIndex++);
        writeCell(titleRow, 0, "Карточки и узкие места", titleStyle);

        Row headerRow = sheet.createRow(rowIndex++);
        writeCell(headerRow, 0, "Команда", headerStyle);
        writeCell(headerRow, 1, "Проблема", headerStyle);
        writeCell(headerRow, 2, "Кабинет", headerStyle);
        writeCell(headerRow, 3, "Название", headerStyle);
        writeCell(headerRow, 4, "Этап", headerStyle);
        writeCell(headerRow, 5, "Статус", headerStyle);
        writeCell(headerRow, 6, "Приоритет", headerStyle);
        writeCell(headerRow, 7, "Подразделение", headerStyle);
        writeCell(headerRow, 8, "Исполнитель", headerStyle);
        writeCell(headerRow, 9, "Закрыта", headerStyle);
        writeCell(headerRow, 10, "Эскалация", headerStyle);
        writeCell(headerRow, 11, "Возвраты", headerStyle);
        writeCell(headerRow, 12, "Hold", headerStyle);
        writeCell(headerRow, 13, "Распределение", headerStyle);
        writeCell(headerRow, 14, "Реакция", headerStyle);
        writeCell(headerRow, 15, "Выполнение", headerStyle);
        writeCell(headerRow, 16, "Согласование подразделения", headerStyle);
        writeCell(headerRow, 17, "Финальное согласование", headerStyle);
        writeCell(headerRow, 18, "Полный цикл", headerStyle);

        for (TeamAnalyticsItem team : analytics.teams()) {
            for (TeamAnalyticsCardItem card : team.cards()) {
                Row row = sheet.createRow(rowIndex++);
                writeCell(row, 0, team.teamName(), null);
                writeCell(row, 1, card.problemNumber(), integerStyle);
                writeCell(row, 2, "%s %s".formatted(card.roomCode(), card.roomName()), null);
                writeCell(row, 3, card.title(), null);
                writeCell(row, 4, card.stageNumber(), integerStyle);
                writeCell(row, 5, card.status(), null);
                writeCell(row, 6, valueOrDash(card.priority()), null);
                writeCell(row, 7, valueOrDash(card.responsibleDepartment()), null);
                writeCell(row, 8, valueOrDash(card.assigneeName()), null);
                writeCell(row, 9, card.resolved() ? "Да" : "Нет", null);
                writeCell(row, 10, card.escalated() ? "Да" : "Нет", null);
                writeCell(row, 11, card.returnCount(), integerStyle);
                writeCell(row, 12, card.holdCount(), integerStyle);
                writeCell(row, 13, formatDuration(card.distributionSeconds()), null);
                writeCell(row, 14, formatDuration(card.reactionSeconds()), null);
                writeCell(row, 15, formatDuration(card.workSeconds()), null);
                writeCell(row, 16, formatDuration(card.departmentReviewSeconds()), null);
                writeCell(row, 17, formatDuration(card.chiefReviewSeconds()), null);
                writeCell(row, 18, formatDuration(card.fullCycleSeconds()), null);
            }
        }

        setSheetWidths(sheet, 20, 10, 24, 42, 10, 16, 12, 18, 24, 10, 12, 10, 10, 14, 12, 14, 26, 24, 14);
    }

    private int writeKeyValue(Sheet sheet, int rowIndex, String key, Object value, CellStyle valueStyle) {
        Row row = sheet.createRow(rowIndex);
        writeCell(row, 0, key, null);
        writeCell(row, 1, value, valueStyle);
        return rowIndex + 1;
    }

    private void writeCell(Row row, int columnIndex, Object value, CellStyle style) {
        Cell cell = row.createCell(columnIndex);
        if (value instanceof Number number) {
            cell.setCellValue(number.doubleValue());
        } else if (value instanceof BigDecimal decimal) {
            cell.setCellValue(decimal.doubleValue());
        } else if (value instanceof LocalDateTime dateTime) {
            cell.setCellValue(dateTime);
        } else {
            cell.setCellValue(value != null ? value.toString() : "");
        }

        if (style != null) {
            cell.setCellStyle(style);
        }
    }

    private void setSheetWidths(Sheet sheet, int... widths) {
        for (int index = 0; index < widths.length; index += 1) {
            sheet.setColumnWidth(index, widths[index] * 256);
        }
    }

    private String formatDuration(Long seconds) {
        if (seconds == null) {
            return "—";
        }
        long totalSeconds = Math.max(seconds, 0L);
        long hours = Duration.ofSeconds(totalSeconds).toHours();
        long minutes = Duration.ofSeconds(totalSeconds).toMinutesPart();
        long secs = Duration.ofSeconds(totalSeconds).toSecondsPart();

        if (hours > 0) {
            return "%d:%02d:%02d".formatted(hours, minutes, secs);
        }

        return "%02d:%02d".formatted(minutes, secs);
    }

    private String valueOrDash(String value) {
        return value == null || value.isBlank() ? "—" : value;
    }

    private CellStyle createTitleStyle(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 14);
        style.setFont(font);
        return style;
    }

    private CellStyle createHeaderStyle(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        return style;
    }

    private CellStyle createDateTimeStyle(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setDataFormat(workbook.getCreationHelper().createDataFormat().getFormat("dd.mm.yyyy hh:mm"));
        return style;
    }

    private CellStyle createIntegerStyle(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setDataFormat(workbook.getCreationHelper().createDataFormat().getFormat("0"));
        return style;
    }

    private CellStyle createDecimalStyle(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setDataFormat(workbook.getCreationHelper().createDataFormat().getFormat("0.00"));
        return style;
    }
}
