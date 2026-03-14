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
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PlayerSessionControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clearDatabase() {
        jdbcTemplate.update("DELETE FROM session_stage_settings");
        jdbcTemplate.update("DELETE FROM session_participants");
        jdbcTemplate.update("DELETE FROM players");
        jdbcTemplate.update("DELETE FROM game_sessions");
        jdbcTemplate.update("DELETE FROM users WHERE login <> 'facilitator'");
    }

    @Test
    void shouldReturnAuthenticatedFacilitatorProfile() throws Exception {
        mockMvc.perform(get("/api/auth/me").with(auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.login").value("facilitator"))
                .andExpect(jsonPath("$.systemRole").value("FACILITATOR"));
    }

    @Test
    void shouldRejectInvalidFacilitatorCredentials() throws Exception {
        mockMvc.perform(get("/api/auth/me")
                        .with(httpBasic("facilitator", "wrong-password")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldCreateGameSessionForFacilitator() throws Exception {
        createSession("WARD-12", "Тестовая смена");

        assertThat(count("game_sessions")).isEqualTo(1);

        mockMvc.perform(get("/api/game-sessions")
                        .with(auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].sessionCode").value("WARD-12"))
                .andExpect(jsonPath("$[0].sessionName").value("Тестовая смена"))
                .andExpect(jsonPath("$[0].sessionStatus").value("LOBBY"));
    }

    @Test
    void shouldRenameGameSessionForFacilitator() throws Exception {
        createSession("WARD-12", "Тестовая смена");

        mockMvc.perform(patch("/api/game-sessions/{sessionCode}/name", "WARD-12")
                        .with(auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("sessionName", "Обновлённая смена"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionCode").value("WARD-12"))
                .andExpect(jsonPath("$.sessionName").value("Обновлённая смена"));

        String sessionName = jdbcTemplate.queryForObject(
                "SELECT name FROM game_sessions WHERE code = ?",
                String.class,
                "WARD-12"
        );

        assertThat(sessionName).isEqualTo("Обновлённая смена");
    }

    @Test
    void shouldReturnAvailableLobbySessionsForPlayers() throws Exception {
        createSession("WARD-12", "Приёмное отделение");
        createSession("ENG-01", "Инженерный штаб");

        mockMvc.perform(patch("/api/game-sessions/{sessionCode}/start", "ENG-01")
                        .with(auth()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/player-sessions/available"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].sessionCode").value("WARD-12"))
                .andExpect(jsonPath("$[0].sessionName").value("Приёмное отделение"));
    }

    @Test
    void shouldJoinPlayerToExistingSessionAndPersistData() throws Exception {
        createSession("WARD-12", "Тестовая смена");

        var request = Map.of(
                "displayName", "Анна Петрова",
                "hospitalPosition", "Главная медсестра",
                "sessionCode", " ward-12 "
        );

        mockMvc.perform(post("/api/player-sessions/join")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Анна Петрова"))
                .andExpect(jsonPath("$.hospitalPosition").value("Главная медсестра"))
                .andExpect(jsonPath("$.sessionCode").value("WARD-12"))
                .andExpect(jsonPath("$.sessionName").value("Тестовая смена"))
                .andExpect(jsonPath("$.sessionStatus").value("LOBBY"))
                .andExpect(jsonPath("$.participantId").isNumber())
                .andExpect(jsonPath("$.sessionId").isNumber())
                .andExpect(jsonPath("$.playerId").isNumber());

        assertThat(count("players")).isEqualTo(1);
        assertThat(count("game_sessions")).isEqualTo(1);
        assertThat(count("session_participants")).isEqualTo(1);
    }

    @Test
    void shouldRejectJoinWhenSessionDoesNotExist() throws Exception {
        var request = Map.of(
                "displayName", "Анна Петрова",
                "hospitalPosition", "Главная медсестра",
                "sessionCode", "ward-12"
        );

        mockMvc.perform(post("/api/player-sessions/join")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());

        assertThat(count("players")).isZero();
        assertThat(count("game_sessions")).isZero();
        assertThat(count("session_participants")).isZero();
    }

    @Test
    void shouldRejectJoinWhenSessionAlreadyStarted() throws Exception {
        createSession("WARD-12", "Тестовая смена");
        mockMvc.perform(patch("/api/game-sessions/{sessionCode}/start", "WARD-12")
                        .with(auth()))
                .andExpect(status().isOk());

        var request = Map.of(
                "displayName", "Анна Петрова",
                "hospitalPosition", "Главная медсестра",
                "sessionCode", "WARD-12"
        );

        mockMvc.perform(post("/api/player-sessions/join")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    @Test
    void shouldReuseExistingParticipantForSamePlayerAndSession() throws Exception {
        createSession("ENG-01", "Инженерная сессия");

        var request = Map.of(
                "displayName", "Иван Сидоров",
                "hospitalPosition", "Главный инженер",
                "sessionCode", "eng-01"
        );

        mockMvc.perform(post("/api/player-sessions/join")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/player-sessions/join")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionCode").value("ENG-01"));

        assertThat(count("players")).isEqualTo(1);
        assertThat(count("game_sessions")).isEqualTo(1);
        assertThat(count("session_participants")).isEqualTo(1);
    }

    @Test
    void shouldSaveSessionStageSettingsAndExposeThemInSessionView() throws Exception {
        createSession("WARD-12", "Тестовая смена");
        joinPlayer("Анна Петрова", "Главная медсестра", "ward-12");

        var request = Map.of(
                "stages", List.of(
                        Map.of(
                                "stageNumber", 1,
                                "durationMinutes", 12,
                                "interactionMode", "CHAT_ONLY"
                        ),
                        Map.of(
                                "stageNumber", 2,
                                "durationMinutes", 20,
                                "interactionMode", "CHAT_AND_KANBAN"
                        )
                )
        );

        mockMvc.perform(put("/api/game-sessions/{sessionCode}/stages", "ward-12")
                        .with(auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionCode").value("WARD-12"))
                .andExpect(jsonPath("$.stages.length()").value(2))
                .andExpect(jsonPath("$.stages[0].interactionMode").value("CHAT_ONLY"))
                .andExpect(jsonPath("$.stages[1].interactionMode").value("CHAT_AND_KANBAN"));

        assertThat(count("session_stage_settings")).isEqualTo(2);
    }

    @Test
    void shouldAssignRandomRolesWithoutMatchingHospitalPositions() throws Exception {
        createSession("WARD-12", "Тестовая смена");
        joinPlayer("Анна Петрова", "Главная медсестра", "ward-12");
        joinPlayer("Иван Сидоров", "Главный инженер", "ward-12");
        joinPlayer("Павел Орлов", "Главный врач", "ward-12");
        joinPlayer("Ольга Смирнова", "Сестра поликлинического отделения", "ward-12");

        mockMvc.perform(post("/api/game-sessions/{sessionCode}/roles/random", "ward-12")
                        .with(auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.participants.length()").value(4));

        List<String> assignedRoles = jdbcTemplate.queryForList(
                """
                SELECT game_role
                FROM session_participants sp
                JOIN game_sessions gs ON gs.id = sp.game_session_id
                WHERE gs.code = 'WARD-12'
                ORDER BY sp.id
                """,
                String.class
        );

        List<Long> conflicts = jdbcTemplate.queryForList(
                """
                SELECT COUNT(*)
                FROM session_participants sp
                JOIN players p ON p.id = sp.player_id
                JOIN game_sessions gs ON gs.id = sp.game_session_id
                WHERE gs.code = 'WARD-12'
                  AND LOWER(p.hospital_position) = LOWER(sp.game_role)
                """,
                Long.class
        );

        assertThat(assignedRoles).contains("Главный врач", "Главная медсестра", "Главный инженер");
        assertThat(assignedRoles).allMatch(Set.of(
                "Главный врач",
                "Главная медсестра",
                "Главный инженер",
                "Сестра поликлинического отделения",
                "Сестра диагностического отделения",
                "Заместитель главного инженера по медтехнике",
                "Заместитель главного инженера по АХЧ"
        )::contains);
        assertThat(conflicts.getFirst()).isZero();
    }

    @Test
    void shouldAllowManualRoleAssignmentForAnyCustomRole() throws Exception {
        createSession("WARD-12", "Тестовая смена");
        joinPlayer("Анна Петрова", "Главная медсестра", "ward-12");
        Long participantId = jdbcTemplate.queryForObject(
                """
                SELECT sp.id
                FROM session_participants sp
                JOIN game_sessions gs ON gs.id = sp.game_session_id
                WHERE gs.code = 'WARD-12'
                """,
                Long.class
        );

        var request = Map.of("gameRole", "Координатор снабжения");

        mockMvc.perform(patch("/api/game-sessions/{sessionCode}/participants/{participantId}/role", "ward-12", participantId)
                        .with(auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.participantId").value(participantId))
                .andExpect(jsonPath("$.gameRole").value("Координатор снабжения"));

        String gameRole = jdbcTemplate.queryForObject(
                "SELECT game_role FROM session_participants WHERE id = ?",
                String.class,
                participantId
        );

        assertThat(gameRole).isEqualTo("Координатор снабжения");
    }

    @Test
    void shouldReturnSessionParticipantsForFacilitatorView() throws Exception {
        createSession("WARD-12", "Тестовая смена");
        joinPlayer("Анна Петрова", "Главная медсестра", "ward-12");
        joinPlayer("Иван Сидоров", "Главный инженер", "WARD-12");

        mockMvc.perform(get("/api/game-sessions/{sessionCode}/participants", "ward-12")
                        .with(auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionCode").value("WARD-12"))
                .andExpect(jsonPath("$.sessionStatus").value("LOBBY"))
                .andExpect(jsonPath("$.participants.length()").value(2))
                .andExpect(jsonPath("$.participants[0].displayName").value("Анна Петрова"))
                .andExpect(jsonPath("$.participants[0].hospitalPosition").value("Главная медсестра"))
                .andExpect(jsonPath("$.participants[0].gameRole").isEmpty())
                .andExpect(jsonPath("$.participants[1].displayName").value("Иван Сидоров"));
    }

    @Test
    void shouldReturnSessionsOverviewForFacilitatorDashboard() throws Exception {
        createSession("WARD-12", "Приёмное отделение");
        createSession("ENG-01", "Инженерный штаб");
        joinPlayer("Анна Петрова", "Главная медсестра", "ward-12");
        joinPlayer("Иван Сидоров", "Главный инженер", "ward-12");
        joinPlayer("Павел Орлов", "Главный врач", "eng-01");

        mockMvc.perform(get("/api/game-sessions")
                        .with(auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].sessionCode").value("ENG-01"))
                .andExpect(jsonPath("$[0].participantCount").value(1))
                .andExpect(jsonPath("$[1].sessionCode").value("WARD-12"))
                .andExpect(jsonPath("$[1].participantCount").value(2));
    }

    @Test
    void shouldStartSessionFromLobby() throws Exception {
        createSession("WARD-12", "Тестовая смена");
        joinPlayer("Анна Петрова", "Главная медсестра", "ward-12");

        mockMvc.perform(patch("/api/game-sessions/{sessionCode}/start", "ward-12")
                        .with(auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionCode").value("WARD-12"))
                .andExpect(jsonPath("$.sessionStatus").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.participantCount").value(1));

        String status = jdbcTemplate.queryForObject(
                "SELECT status FROM game_sessions WHERE code = 'WARD-12'",
                String.class
        );

        assertThat(status).isEqualTo("IN_PROGRESS");
    }

    @Test
    void shouldFinishSessionInProgress() throws Exception {
        createSession("WARD-12", "Тестовая смена");
        joinPlayer("Анна Петрова", "Главная медсестра", "ward-12");
        mockMvc.perform(patch("/api/game-sessions/{sessionCode}/start", "ward-12")
                        .with(auth()))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/game-sessions/{sessionCode}/finish", "ward-12")
                        .with(auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionCode").value("WARD-12"))
                .andExpect(jsonPath("$.sessionStatus").value("FINISHED"))
                .andExpect(jsonPath("$.participantCount").value(1));

        String status = jdbcTemplate.queryForObject(
                "SELECT status FROM game_sessions WHERE code = 'WARD-12'",
                String.class
        );

        assertThat(status).isEqualTo("FINISHED");
    }

    @Test
    void shouldDeleteSessionAndExclusivePlayers() throws Exception {
        createSession("WARD-12", "Приёмное отделение");
        createSession("ENG-01", "Инженерный штаб");
        joinPlayer("Анна Петрова", "Главная медсестра", "ward-12");
        joinPlayer("Иван Сидоров", "Главный инженер", "ward-12");
        joinPlayer("Иван Сидоров", "Главный инженер", "eng-01");

        mockMvc.perform(delete("/api/game-sessions/{sessionCode}", "ward-12")
                        .with(auth()))
                .andExpect(status().isNoContent());

        assertThat(count("game_sessions")).isEqualTo(1);
        assertThat(count("session_participants")).isEqualTo(1);
        assertThat(count("players")).isEqualTo(1);
        assertThat(countByCode("game_sessions", "ENG-01")).isEqualTo(1);
        assertThat(countByCode("game_sessions", "WARD-12")).isZero();
    }

    @Test
    void shouldReturnNotFoundWhenSessionDoesNotExist() throws Exception {
        mockMvc.perform(get("/api/game-sessions/{sessionCode}/participants", "missing-01")
                        .with(auth()))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldRejectBlankFields() throws Exception {
        var request = Map.of(
                "displayName", " ",
                "hospitalPosition", "",
                "sessionCode", "   "
        );

        mockMvc.perform(post("/api/player-sessions/join")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        assertThat(count("players")).isZero();
        assertThat(count("game_sessions")).isZero();
        assertThat(count("session_participants")).isZero();
    }

    private RequestPostProcessor auth() {
        return httpBasic("facilitator", "medsim123");
    }

    private void createSession(String sessionCode, String sessionName) throws Exception {
        var request = Map.of(
                "sessionCode", sessionCode,
                "sessionName", sessionName
        );

        mockMvc.perform(post("/api/game-sessions")
                        .with(auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    private void joinPlayer(String displayName, String hospitalPosition, String sessionCode) throws Exception {
        var request = Map.of(
                "displayName", displayName,
                "hospitalPosition", hospitalPosition,
                "sessionCode", sessionCode
        );

        mockMvc.perform(post("/api/player-sessions/join")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    private long count(String tableName) {
        Long value = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + tableName, Long.class);
        return value == null ? 0L : value;
    }

    private long countByCode(String tableName, String code) {
        Long value = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + tableName + " WHERE code = ?",
                Long.class,
                code
        );
        return value == null ? 0L : value;
    }
}
