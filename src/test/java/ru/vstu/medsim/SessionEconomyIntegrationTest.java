package ru.vstu.medsim;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SessionEconomyIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clearDatabase() {
        jdbcTemplate.update("DELETE FROM team_problem_states");
        jdbcTemplate.update("DELETE FROM team_room_states");
        jdbcTemplate.update("DELETE FROM team_economy_states");
        jdbcTemplate.update("DELETE FROM session_economy_settings");
        jdbcTemplate.update("DELETE FROM team_inventory_items");
        jdbcTemplate.update("DELETE FROM session_stage_settings");
        jdbcTemplate.update("DELETE FROM session_participants");
        jdbcTemplate.update("DELETE FROM session_teams");
        jdbcTemplate.update("DELETE FROM players");
        jdbcTemplate.update("DELETE FROM game_sessions");
        jdbcTemplate.update("DELETE FROM users WHERE login <> 'facilitator'");
    }

    @Test
    void shouldInitializeEconomyForEveryCreatedSession() throws Exception {
        String sessionCode = createSession("Экономическая сессия", 2, new BigDecimal("15.00"), 15);

        Integer settingsCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM session_economy_settings ses JOIN game_sessions gs ON gs.id = ses.game_session_id WHERE gs.code = ?",
                Integer.class,
                sessionCode
        );
        Integer teamStateCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM team_economy_states tes JOIN session_teams st ON st.id = tes.team_id JOIN game_sessions gs ON gs.id = st.game_session_id WHERE gs.code = ?",
                Integer.class,
                sessionCode
        );
        Integer roomStateCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM team_room_states trs JOIN session_teams st ON st.id = trs.team_id JOIN game_sessions gs ON gs.id = st.game_session_id WHERE gs.code = ?",
                Integer.class,
                sessionCode
        );
        Integer problemStateCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM team_problem_states tps JOIN team_room_states trs ON trs.id = tps.team_room_state_id JOIN session_teams st ON st.id = trs.team_id JOIN game_sessions gs ON gs.id = st.game_session_id WHERE gs.code = ?",
                Integer.class,
                sessionCode
        );
        Integer activeSolutionOptionCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM kanban_solution_options WHERE active = TRUE",
                Integer.class
        );
        Integer activeStandardSolutionCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM kanban_solution_options WHERE active = TRUE AND title = 'Стандартное решение'",
                Integer.class
        );
        Integer sanitaryCheckSolutionCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM kanban_solution_options kso
                JOIN clinic_room_problem_templates crpt ON crpt.id = kso.problem_template_id
                JOIN clinic_room_templates crt ON crt.id = crpt.clinic_room_template_id
                WHERE crt.code = 'PROCEDURE'
                  AND crpt.problem_number = 28
                  AND kso.active = TRUE
                """,
                Integer.class
        );
        BigDecimal xrayInternalRepairBaseChance = jdbcTemplate.queryForObject(
                """
                SELECT kso.base_success_probability
                FROM kanban_solution_options kso
                JOIN clinic_room_problem_templates crpt ON crpt.id = kso.problem_template_id
                JOIN clinic_room_templates crt ON crt.id = crpt.clinic_room_template_id
                WHERE crt.code = 'XRAY'
                  AND crpt.problem_number = 1
                  AND kso.title = 'Устранение силами поликлиники'
                  AND kso.active = TRUE
                """,
                BigDecimal.class
        );
        BigDecimal xrayInternalRepairNursingMultiplier = jdbcTemplate.queryForObject(
                """
                SELECT kso.nursing_success_multiplier
                FROM kanban_solution_options kso
                JOIN clinic_room_problem_templates crpt ON crpt.id = kso.problem_template_id
                JOIN clinic_room_templates crt ON crt.id = crpt.clinic_room_template_id
                WHERE crt.code = 'XRAY'
                  AND crpt.problem_number = 1
                  AND kso.title = 'Устранение силами поликлиники'
                  AND kso.active = TRUE
                """,
                BigDecimal.class
        );
        BigDecimal xrayInternalRepairEngineeringMultiplier = jdbcTemplate.queryForObject(
                """
                SELECT kso.engineering_success_multiplier
                FROM kanban_solution_options kso
                JOIN clinic_room_problem_templates crpt ON crpt.id = kso.problem_template_id
                JOIN clinic_room_templates crt ON crt.id = crpt.clinic_room_template_id
                WHERE crt.code = 'XRAY'
                  AND crpt.problem_number = 1
                  AND kso.title = 'Устранение силами поликлиники'
                  AND kso.active = TRUE
                """,
                BigDecimal.class
        );

        List<BigDecimal> balances = jdbcTemplate.queryForList(
                "SELECT tes.current_balance FROM team_economy_states tes JOIN session_teams st ON st.id = tes.team_id JOIN game_sessions gs ON gs.id = st.game_session_id WHERE gs.code = ? ORDER BY st.sort_order",
                BigDecimal.class,
                sessionCode
        );
        List<Integer> stageTimeUnits = jdbcTemplate.queryForList(
                "SELECT tes.current_stage_time_units FROM team_economy_states tes JOIN session_teams st ON st.id = tes.team_id JOIN game_sessions gs ON gs.id = st.game_session_id WHERE gs.code = ? ORDER BY st.sort_order",
                Integer.class,
                sessionCode
        );

        assertThat(settingsCount).isEqualTo(1);
        assertThat(teamStateCount).isEqualTo(2);
        assertThat(roomStateCount).isEqualTo(20);
        assertThat(problemStateCount).isEqualTo(74);
        assertThat(activeSolutionOptionCount).isEqualTo(74);
        assertThat(activeStandardSolutionCount).isZero();
        assertThat(sanitaryCheckSolutionCount).isEqualTo(3);
        assertThat(xrayInternalRepairBaseChance).isEqualByComparingTo("0.70");
        assertThat(xrayInternalRepairNursingMultiplier).isEqualByComparingTo("1.00");
        assertThat(xrayInternalRepairEngineeringMultiplier).isEqualByComparingTo("0.70");
        assertThat(balances).containsExactly(new BigDecimal("15.00"), new BigDecimal("15.00"));
        assertThat(stageTimeUnits).containsExactly(15, 15);
    }

    @Test
    void shouldPersistCustomEconomySettingsForCreatedSession() throws Exception {
        String sessionCode = createSession("Кастомная экономическая сессия", 3, new BigDecimal("25.50"), 18);

        BigDecimal storedBudget = jdbcTemplate.queryForObject(
                "SELECT ses.starting_budget FROM session_economy_settings ses JOIN game_sessions gs ON gs.id = ses.game_session_id WHERE gs.code = ?",
                BigDecimal.class,
                sessionCode
        );
        Integer storedStageTimeUnits = jdbcTemplate.queryForObject(
                "SELECT ses.stage_time_units FROM session_economy_settings ses JOIN game_sessions gs ON gs.id = ses.game_session_id WHERE gs.code = ?",
                Integer.class,
                sessionCode
        );
        List<BigDecimal> balances = jdbcTemplate.queryForList(
                "SELECT tes.current_balance FROM team_economy_states tes JOIN session_teams st ON st.id = tes.team_id JOIN game_sessions gs ON gs.id = st.game_session_id WHERE gs.code = ? ORDER BY st.sort_order",
                BigDecimal.class,
                sessionCode
        );
        List<Integer> stageTimeUnits = jdbcTemplate.queryForList(
                "SELECT tes.current_stage_time_units FROM team_economy_states tes JOIN session_teams st ON st.id = tes.team_id JOIN game_sessions gs ON gs.id = st.game_session_id WHERE gs.code = ? ORDER BY st.sort_order",
                Integer.class,
                sessionCode
        );

        assertThat(storedBudget).isEqualByComparingTo(new BigDecimal("25.50"));
        assertThat(storedStageTimeUnits).isEqualTo(18);
        assertThat(balances).containsExactly(
                new BigDecimal("25.50"),
                new BigDecimal("25.50"),
                new BigDecimal("25.50")
        );
        assertThat(stageTimeUnits).containsExactly(18, 18, 18);

        mockMvc.perform(get("/api/game-sessions/{sessionCode}/economy", sessionCode)
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic("facilitator", "medsim123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.settings.startingBudget").value(25.50))
                .andExpect(jsonPath("$.settings.stageTimeUnits").value(18))
                .andExpect(jsonPath("$.teams", hasSize(3)))
                .andExpect(jsonPath("$.teams[0].currentBalance").value(25.50))
                .andExpect(jsonPath("$.teams[0].currentStageTimeUnits").value(18));
    }

    @Test
    void shouldAllowUpdatingEconomySettingsInLobby() throws Exception {
        String sessionCode = createSession("Обновляемая экономическая сессия", 2, new BigDecimal("15.00"), 15);

        mockMvc.perform(put("/api/game-sessions/{sessionCode}/economy/settings", sessionCode)
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic("facilitator", "medsim123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "startingBudget", new BigDecimal("31.25"),
                                "stageTimeUnits", 21
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.settings.startingBudget").value(31.25))
                .andExpect(jsonPath("$.settings.stageTimeUnits").value(21))
                .andExpect(jsonPath("$.teams", hasSize(2)))
                .andExpect(jsonPath("$.teams[0].currentBalance").value(31.25))
                .andExpect(jsonPath("$.teams[0].currentStageTimeUnits").value(21));

        BigDecimal storedBudget = jdbcTemplate.queryForObject(
                "SELECT ses.starting_budget FROM session_economy_settings ses JOIN game_sessions gs ON gs.id = ses.game_session_id WHERE gs.code = ?",
                BigDecimal.class,
                sessionCode
        );
        Integer storedStageTimeUnits = jdbcTemplate.queryForObject(
                "SELECT ses.stage_time_units FROM session_economy_settings ses JOIN game_sessions gs ON gs.id = ses.game_session_id WHERE gs.code = ?",
                Integer.class,
                sessionCode
        );
        List<BigDecimal> balances = jdbcTemplate.queryForList(
                "SELECT tes.current_balance FROM team_economy_states tes JOIN session_teams st ON st.id = tes.team_id JOIN game_sessions gs ON gs.id = st.game_session_id WHERE gs.code = ? ORDER BY st.sort_order",
                BigDecimal.class,
                sessionCode
        );
        List<Integer> stageTimeUnits = jdbcTemplate.queryForList(
                "SELECT tes.current_stage_time_units FROM team_economy_states tes JOIN session_teams st ON st.id = tes.team_id JOIN game_sessions gs ON gs.id = st.game_session_id WHERE gs.code = ? ORDER BY st.sort_order",
                Integer.class,
                sessionCode
        );

        assertThat(storedBudget).isEqualByComparingTo(new BigDecimal("31.25"));
        assertThat(storedStageTimeUnits).isEqualTo(21);
        assertThat(balances).containsExactly(new BigDecimal("31.25"), new BigDecimal("31.25"));
        assertThat(stageTimeUnits).containsExactly(21, 21);
    }

    @Test
    void shouldRejectUpdatingEconomySettingsAfterSessionStart() throws Exception {
        String sessionCode = createSession("Зафиксированная экономическая сессия", 2, new BigDecimal("15.00"), 15);

        jdbcTemplate.update(
                "UPDATE game_sessions SET status = 'IN_PROGRESS' WHERE code = ?",
                sessionCode
        );

        mockMvc.perform(put("/api/game-sessions/{sessionCode}/economy/settings", sessionCode)
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic("facilitator", "medsim123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "startingBudget", new BigDecimal("20.00"),
                                "stageTimeUnits", 20
                        ))))
                .andExpect(status().isConflict());
    }

    @Test
    void shouldExposeEconomyOverviewForFacilitator() throws Exception {
        String sessionCode = createSession("Экономическая сессия", 2, new BigDecimal("15.00"), 15);

        mockMvc.perform(get("/api/game-sessions/{sessionCode}/economy", sessionCode)
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic("facilitator", "medsim123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionCode").value(sessionCode))
                .andExpect(jsonPath("$.settings.stageTimeUnits").value(15))
                .andExpect(jsonPath("$.teams", hasSize(2)))
                .andExpect(jsonPath("$.teams[0].currentStageTimeUnits").value(15))
                .andExpect(jsonPath("$.teams[0].rooms", hasSize(10)))
                .andExpect(jsonPath("$.teams[0].rooms[0].roomName").value("Рентген"))
                .andExpect(jsonPath("$.teams[0].rooms[0].problems", hasSize(3)));
    }

    @Test
    void shouldResetTeamStageTimeUnitsWhenSelectingStage() throws Exception {
        String sessionCode = createSession("Экономическая сессия", 2, new BigDecimal("22.00"), 17);

        saveTwoStages(sessionCode);

        jdbcTemplate.update(
                "UPDATE team_economy_states SET current_stage_time_units = 1 WHERE team_id IN (SELECT st.id FROM session_teams st JOIN game_sessions gs ON gs.id = st.game_session_id WHERE gs.code = ?)",
                sessionCode
        );

        mockMvc.perform(patch("/api/game-sessions/{sessionCode}/runtime/stage", sessionCode)
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic("facilitator", "medsim123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("stageNumber", 2))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionRuntime.activeStageNumber").value(2));

        List<Integer> stageTimeUnits = jdbcTemplate.queryForList(
                "SELECT tes.current_stage_time_units FROM team_economy_states tes JOIN session_teams st ON st.id = tes.team_id JOIN game_sessions gs ON gs.id = st.game_session_id WHERE gs.code = ? ORDER BY st.sort_order",
                Integer.class,
                sessionCode
        );

        assertThat(stageTimeUnits).containsExactly(17, 17);
    }

    private String createSession(String sessionName, int teamCount, BigDecimal startingBudget, int stageTimeUnits) throws Exception {
        String response = mockMvc.perform(post("/api/game-sessions")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic("facilitator", "medsim123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "sessionName", sessionName,
                                "teamCount", teamCount,
                                "startingBudget", startingBudget,
                                "stageTimeUnits", stageTimeUnits
                        ))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return objectMapper.readTree(response).path("sessionCode").asText();
    }

    private void saveTwoStages(String sessionCode) throws Exception {
        mockMvc.perform(put("/api/game-sessions/{sessionCode}/stages", sessionCode)
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic("facilitator", "medsim123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "stages", List.of(
                                        Map.of(
                                                "stageNumber", 1,
                                                "durationMinutes", 10,
                                                "interactionMode", "CHAT_WITH_PROBLEMS"
                                        ),
                                        Map.of(
                                                "stageNumber", 2,
                                                "durationMinutes", 12,
                                                "interactionMode", "CHAT_AND_KANBAN"
                                        )
                                )
                        ))))
                .andExpect(status().isOk());
    }
}
