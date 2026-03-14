package ru.vstu.medsim;

import com.fasterxml.jackson.databind.JsonNode;
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
import org.springframework.test.web.servlet.MvcResult;
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
        String sessionCode = createSession("Тестовая смена");

        assertThat(sessionCode).matches("[A-Z]{4}-\\d{2}");
        assertThat(count("game_sessions")).isEqualTo(1);

        Map<String, Object> session = jdbcTemplate.queryForMap(
                "SELECT code, name, status FROM game_sessions LIMIT 1"
        );

        assertThat(session.get("code")).isEqualTo(sessionCode);
        assertThat(session.get("name")).isEqualTo("Тестовая смена");
        assertThat(session.get("status")).isEqualTo("LOBBY");
    }

    @Test
    void shouldRenameGameSessionForFacilitator() throws Exception {
        String sessionCode = createSession("Тестовая смена");

        mockMvc.perform(patch("/api/game-sessions/{sessionCode}/name", sessionCode)
                        .with(auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("sessionName", "Обновлённая смена"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionCode").value(sessionCode))
                .andExpect(jsonPath("$.sessionName").value("Обновлённая смена"));

        String sessionName = jdbcTemplate.queryForObject(
                "SELECT name FROM game_sessions WHERE code = ?",
                String.class,
                sessionCode
        );

        assertThat(sessionName).isEqualTo("Обновлённая смена");
    }

    @Test
    void shouldReturnAvailableLobbySessionsForPlayers() throws Exception {
        String lobbySessionCode = createSession("Приёмное отделение");
        String startedSessionCode = createSession("Инженерный штаб");

        mockMvc.perform(patch("/api/game-sessions/{sessionCode}/start", startedSessionCode)
                        .with(auth()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/player-sessions/available"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].sessionCode").value(lobbySessionCode))
                .andExpect(jsonPath("$[0].sessionName").value("Приёмное отделение"));
    }

    @Test
    void shouldJoinPlayerToExistingSessionAndPersistData() throws Exception {
        String sessionCode = createSession("Тестовая смена");

        var request = Map.of(
                "displayName", "Анна Петрова",
                "hospitalPosition", "Главная медсестра",
                "sessionCode", " " + sessionCode.toLowerCase() + " "
        );

        mockMvc.perform(post("/api/player-sessions/join")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Анна Петрова"))
                .andExpect(jsonPath("$.hospitalPosition").value("Главная медсестра"))
                .andExpect(jsonPath("$.sessionCode").value(sessionCode))
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
        String sessionCode = createSession("Тестовая смена");
        mockMvc.perform(patch("/api/game-sessions/{sessionCode}/start", sessionCode)
                        .with(auth()))
                .andExpect(status().isOk());

        var request = Map.of(
                "displayName", "Анна Петрова",
                "hospitalPosition", "Главная медсестра",
                "sessionCode", sessionCode
        );

        mockMvc.perform(post("/api/player-sessions/join")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    @Test
    void shouldReuseExistingParticipantForSamePlayerAndSession() throws Exception {
        String sessionCode = createSession("Инженерная сессия");

        var request = Map.of(
                "displayName", "Иван Сидоров",
                "hospitalPosition", "Главный инженер",
                "sessionCode", sessionCode.toLowerCase()
        );

        mockMvc.perform(post("/api/player-sessions/join")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/player-sessions/join")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionCode").value(sessionCode));

        assertThat(count("players")).isEqualTo(1);
        assertThat(count("game_sessions")).isEqualTo(1);
        assertThat(count("session_participants")).isEqualTo(1);
    }

    @Test
    void shouldSaveSessionStageSettingsAndExposeThemInSessionView() throws Exception {
        String sessionCode = createSession("Тестовая смена");
        joinPlayer("Анна Петрова", "Главная медсестра", sessionCode.toLowerCase());

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

        mockMvc.perform(put("/api/game-sessions/{sessionCode}/stages", sessionCode)
                        .with(auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionCode").value(sessionCode))
                .andExpect(jsonPath("$.stages.length()").value(2))
                .andExpect(jsonPath("$.stages[0].interactionMode").value("CHAT_ONLY"))
                .andExpect(jsonPath("$.stages[1].interactionMode").value("CHAT_AND_KANBAN"));

        assertThat(count("session_stage_settings")).isEqualTo(2);
    }

    @Test
    void shouldAssignRandomRolesWithoutMatchingHospitalPositions() throws Exception {
        String sessionCode = createSession("Тестовая смена");
        joinPlayer("Анна Петрова", "Главная медсестра", sessionCode.toLowerCase());
        joinPlayer("Иван Сидоров", "Главный инженер", sessionCode.toLowerCase());
        joinPlayer("Павел Орлов", "Главный врач", sessionCode.toLowerCase());
        joinPlayer("Ольга Смирнова", "Сестра поликлинического отделения", sessionCode.toLowerCase());

        mockMvc.perform(post("/api/game-sessions/{sessionCode}/roles/random", sessionCode)
                        .with(auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.participants.length()").value(4));

        List<String> assignedRoles = jdbcTemplate.queryForList(
                """
                SELECT game_role
                FROM session_participants sp
                JOIN game_sessions gs ON gs.id = sp.game_session_id
                WHERE gs.code = ?
                ORDER BY sp.id
                """,
                String.class,
                sessionCode
        );

        Long conflictCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM session_participants sp
                JOIN players p ON p.id = sp.player_id
                JOIN game_sessions gs ON gs.id = sp.game_session_id
                WHERE gs.code = ?
                  AND LOWER(p.hospital_position) = LOWER(sp.game_role)
                """,
                Long.class,
                sessionCode
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
        assertThat(conflictCount).isZero();
    }

    @Test
    void shouldAllowManualRoleAssignmentForAnyCustomRole() throws Exception {
        String sessionCode = createSession("Тестовая смена");
        joinPlayer("Анна Петрова", "Главная медсестра", sessionCode.toLowerCase());
        Long participantId = jdbcTemplate.queryForObject(
                """
                SELECT sp.id
                FROM session_participants sp
                JOIN game_sessions gs ON gs.id = sp.game_session_id
                WHERE gs.code = ?
                """,
                Long.class,
                sessionCode
        );

        var request = Map.of("gameRole", "Координатор снабжения");

        mockMvc.perform(patch("/api/game-sessions/{sessionCode}/participants/{participantId}/role", sessionCode, participantId)
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
        String sessionCode = createSession("Тестовая смена");
        joinPlayer("Анна Петрова", "Главная медсестра", sessionCode.toLowerCase());
        joinPlayer("Иван Сидоров", "Главный инженер", sessionCode);

        mockMvc.perform(get("/api/game-sessions/{sessionCode}/participants", sessionCode)
                        .with(auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionCode").value(sessionCode))
                .andExpect(jsonPath("$.sessionStatus").value("LOBBY"))
                .andExpect(jsonPath("$.participants.length()").value(2))
                .andExpect(jsonPath("$.participants[0].displayName").value("Анна Петрова"))
                .andExpect(jsonPath("$.participants[0].hospitalPosition").value("Главная медсестра"))
                .andExpect(jsonPath("$.participants[0].gameRole").isEmpty())
                .andExpect(jsonPath("$.participants[1].displayName").value("Иван Сидоров"));
    }

    @Test
    void shouldReturnSessionsOverviewForFacilitatorDashboard() throws Exception {
        String firstSessionCode = createSession("Приёмное отделение");
        String secondSessionCode = createSession("Инженерный штаб");
        joinPlayer("Анна Петрова", "Главная медсестра", firstSessionCode.toLowerCase());
        joinPlayer("Иван Сидоров", "Главный инженер", firstSessionCode.toLowerCase());
        joinPlayer("Павел Орлов", "Главный врач", secondSessionCode.toLowerCase());

        mockMvc.perform(get("/api/game-sessions")
                        .with(auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].sessionCode").value(secondSessionCode))
                .andExpect(jsonPath("$[0].participantCount").value(1))
                .andExpect(jsonPath("$[1].sessionCode").value(firstSessionCode))
                .andExpect(jsonPath("$[1].participantCount").value(2));
    }

    @Test
    void shouldStartSessionFromLobby() throws Exception {
        String sessionCode = createSession("Тестовая смена");
        joinPlayer("Анна Петрова", "Главная медсестра", sessionCode.toLowerCase());

        mockMvc.perform(patch("/api/game-sessions/{sessionCode}/start", sessionCode)
                        .with(auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionCode").value(sessionCode))
                .andExpect(jsonPath("$.sessionStatus").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.participantCount").value(1));

        String status = jdbcTemplate.queryForObject(
                "SELECT status FROM game_sessions WHERE code = ?",
                String.class,
                sessionCode
        );

        assertThat(status).isEqualTo("IN_PROGRESS");
    }

    @Test
    void shouldFinishSessionInProgress() throws Exception {
        String sessionCode = createSession("Тестовая смена");
        joinPlayer("Анна Петрова", "Главная медсестра", sessionCode.toLowerCase());
        mockMvc.perform(patch("/api/game-sessions/{sessionCode}/start", sessionCode)
                        .with(auth()))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/game-sessions/{sessionCode}/finish", sessionCode)
                        .with(auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionCode").value(sessionCode))
                .andExpect(jsonPath("$.sessionStatus").value("FINISHED"))
                .andExpect(jsonPath("$.participantCount").value(1));

        String status = jdbcTemplate.queryForObject(
                "SELECT status FROM game_sessions WHERE code = ?",
                String.class,
                sessionCode
        );

        assertThat(status).isEqualTo("FINISHED");
    }

    @Test
    void shouldDeleteSessionAndExclusivePlayers() throws Exception {
        String firstSessionCode = createSession("Приёмное отделение");
        String secondSessionCode = createSession("Инженерный штаб");
        joinPlayer("Анна Петрова", "Главная медсестра", firstSessionCode.toLowerCase());
        joinPlayer("Иван Сидоров", "Главный инженер", firstSessionCode.toLowerCase());
        joinPlayer("Иван Сидоров", "Главный инженер", secondSessionCode.toLowerCase());

        mockMvc.perform(delete("/api/game-sessions/{sessionCode}", firstSessionCode)
                        .with(auth()))
                .andExpect(status().isNoContent());

        assertThat(count("game_sessions")).isEqualTo(1);
        assertThat(count("session_participants")).isEqualTo(1);
        assertThat(count("players")).isEqualTo(1);
        assertThat(countByCode("game_sessions", secondSessionCode)).isEqualTo(1);
        assertThat(countByCode("game_sessions", firstSessionCode)).isZero();
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

    private String createSession(String sessionName) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/game-sessions")
                        .with(auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("sessionName", sessionName))))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode payload = objectMapper.readTree(result.getResponse().getContentAsString());
        return payload.get("sessionCode").asText();
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
