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
import static org.hamcrest.Matchers.matchesPattern;
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
        jdbcTemplate.update("DELETE FROM session_teams");
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
    void shouldCreateGameSessionWithGeneratedCodeAndTeams() throws Exception {
        String sessionCode = createSession("Тестовая смена", 3);

        assertThat(sessionCode).matches("[A-Z]{4}-\\d{2}");
        assertThat(count("game_sessions")).isEqualTo(1);
        assertThat(count("session_teams")).isEqualTo(3);

        List<String> teamNames = jdbcTemplate.queryForList(
                "SELECT name FROM session_teams ORDER BY sort_order",
                String.class
        );

        assertThat(teamNames).hasSize(3).doesNotHaveDuplicates();
        assertThat(teamNames).allMatch(name -> !name.isBlank());
    }

    @Test
    void shouldRenameGameSessionForFacilitator() throws Exception {
        String sessionCode = createSession("Тестовая смена", 2);

        mockMvc.perform(patch("/api/game-sessions/{sessionCode}/name", sessionCode)
                        .with(auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("sessionName", "Обновлённая смена"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionCode").value(sessionCode))
                .andExpect(jsonPath("$.sessionName").value("Обновлённая смена"));
    }

    @Test
    void shouldRenameSessionTeam() throws Exception {
        String sessionCode = createSession("Тестовая смена", 2);
        Long teamId = firstTeamId(sessionCode);

        mockMvc.perform(patch("/api/game-sessions/{sessionCode}/teams/{teamId}/name", sessionCode, teamId)
                        .with(auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("teamName", "Гроза Термометров"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.teams[0].teamName").value("Гроза Термометров"));
    }

    @Test
    void shouldReturnAvailableLobbySessionsForPlayers() throws Exception {
        String lobbySessionCode = createSession("Приёмное отделение", 2);
        String startedSessionCode = createSession("Инженерный штаб", 2);

        mockMvc.perform(patch("/api/game-sessions/{sessionCode}/start", startedSessionCode)
                        .with(auth()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/player-sessions/available"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].sessionCode").value(lobbySessionCode));
    }

    @Test
    void shouldJoinPlayerToExistingSessionAndPersistData() throws Exception {
        String sessionCode = createSession("Тестовая смена", 2);

        var request = Map.of(
                "displayName", "Анна Петрова",
                "hospitalPosition", "Главная медсестра",
                "sessionCode", " " + sessionCode.toLowerCase() + " "
        );

        mockMvc.perform(post("/api/player-sessions/join")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionCode").value(sessionCode))
                .andExpect(jsonPath("$.sessionName").value("Тестовая смена"));

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
    }

    @Test
    void shouldRejectJoinWhenSessionAlreadyStarted() throws Exception {
        String sessionCode = createSession("Тестовая смена", 2);
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
        String sessionCode = createSession("Инженерная сессия", 2);

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
        assertThat(count("session_participants")).isEqualTo(1);
    }

    @Test
    void shouldAutoAssignParticipantsToTeamsEvenly() throws Exception {
        String sessionCode = createSession("Командная сессия", 3);
        joinPlayer("Анна Петрова", "Главная медсестра", sessionCode);
        joinPlayer("Иван Сидоров", "Главный инженер", sessionCode);
        joinPlayer("Павел Орлов", "Главный врач", sessionCode);
        joinPlayer("Ольга Смирнова", "Сестра поликлинического отделения", sessionCode);
        joinPlayer("Елена Миронова", "Сестра диагностического отделения", sessionCode);

        mockMvc.perform(post("/api/game-sessions/{sessionCode}/teams/auto-assign", sessionCode)
                        .with(auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.teams.length()").value(3));

        List<Integer> teamSizes = jdbcTemplate.queryForList(
                """
                SELECT COUNT(sp.id)
                FROM session_teams st
                LEFT JOIN session_participants sp ON sp.team_id = st.id
                WHERE st.game_session_id = (SELECT id FROM game_sessions WHERE code = ?)
                GROUP BY st.id
                ORDER BY st.sort_order
                """,
                Integer.class,
                sessionCode
        );

        assertThat(teamSizes).hasSize(3);
        assertThat(teamSizes.getLast() - teamSizes.getFirst()).isLessThanOrEqualTo(1);
    }

    @Test
    void shouldRespectAlreadyAssignedTeamsDuringAutoDistribution() throws Exception {
        String sessionCode = createSession("Командная сессия", 2);
        joinPlayer("Анна Петрова", "Главная медсестра", sessionCode);
        joinPlayer("Иван Сидоров", "Главный инженер", sessionCode);
        joinPlayer("Павел Орлов", "Главный врач", sessionCode);

        Long participantId = firstParticipantId(sessionCode);
        Long firstTeamId = firstTeamId(sessionCode);
        assignParticipantToTeam(sessionCode, participantId, firstTeamId);

        mockMvc.perform(post("/api/game-sessions/{sessionCode}/teams/auto-assign", sessionCode)
                        .with(auth()))
                .andExpect(status().isOk());

        Long preservedTeamId = jdbcTemplate.queryForObject(
                "SELECT team_id FROM session_participants WHERE id = ?",
                Long.class,
                participantId
        );

        assertThat(preservedTeamId).isEqualTo(firstTeamId);
    }

    @Test
    void shouldAllowManualParticipantTeamAssignment() throws Exception {
        String sessionCode = createSession("Командная сессия", 2);
        joinPlayer("Анна Петрова", "Главная медсестра", sessionCode);
        Long participantId = firstParticipantId(sessionCode);
        Long teamId = firstTeamId(sessionCode);

        mockMvc.perform(patch("/api/game-sessions/{sessionCode}/participants/{participantId}/team", sessionCode, participantId)
                        .with(auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("teamId", teamId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.participants[0].teamId").value(teamId));
    }

    @Test
    void shouldRequireTeamBeforeManualRoleAssignment() throws Exception {
        String sessionCode = createSession("Тестовая смена", 2);
        joinPlayer("Анна Петрова", "Главная медсестра", sessionCode);
        Long participantId = firstParticipantId(sessionCode);

        mockMvc.perform(patch("/api/game-sessions/{sessionCode}/participants/{participantId}/role", sessionCode, participantId)
                        .with(auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("gameRole", "Координатор снабжения"))))
                .andExpect(status().isConflict());
    }

    @Test
    void shouldAssignRandomRolesOnlyForParticipantsWithTeams() throws Exception {
        String sessionCode = createSession("Тестовая смена", 2);
        joinPlayer("Анна Петрова", "Главная медсестра", sessionCode);
        joinPlayer("Иван Сидоров", "Главный инженер", sessionCode);
        joinPlayer("Павел Орлов", "Главный врач", sessionCode);
        mockMvc.perform(post("/api/game-sessions/{sessionCode}/teams/auto-assign", sessionCode)
                        .with(auth()))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/game-sessions/{sessionCode}/roles/random", sessionCode)
                        .with(auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.participants.length()").value(3));

        List<String> assignedRoles = jdbcTemplate.queryForList(
                "SELECT game_role FROM session_participants ORDER BY id",
                String.class
        );

        assertThat(assignedRoles).contains("Главный врач", "Главная медсестра", "Главный инженер");
    }

    @Test
    void shouldSaveSessionStageSettingsAndExposeThemInSessionView() throws Exception {
        String sessionCode = createSession("Тестовая смена", 2);
        joinPlayer("Анна Петрова", "Главная медсестра", sessionCode);

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
                .andExpect(jsonPath("$.stages.length()").value(2));
    }

    @Test
    void shouldReturnSessionParticipantsWithTeamsForFacilitatorView() throws Exception {
        String sessionCode = createSession("Тестовая смена", 2);
        joinPlayer("Анна Петрова", "Главная медсестра", sessionCode);
        joinPlayer("Иван Сидоров", "Главный инженер", sessionCode);
        mockMvc.perform(post("/api/game-sessions/{sessionCode}/teams/auto-assign", sessionCode)
                        .with(auth()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/game-sessions/{sessionCode}/participants", sessionCode)
                        .with(auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.teams.length()").value(2))
                .andExpect(jsonPath("$.participants[0].teamName").isNotEmpty());
    }

    @Test
    void shouldReturnSessionsOverviewForFacilitatorDashboard() throws Exception {
        String firstSessionCode = createSession("Приёмное отделение", 3);
        String secondSessionCode = createSession("Инженерный штаб", 2);
        joinPlayer("Анна Петрова", "Главная медсестра", firstSessionCode);
        joinPlayer("Павел Орлов", "Главный врач", secondSessionCode);

        mockMvc.perform(get("/api/game-sessions")
                        .with(auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].teamCount").value(2))
                .andExpect(jsonPath("$[1].teamCount").value(3));
    }

    @Test
    void shouldStartAndFinishSession() throws Exception {
        String sessionCode = createSession("Тестовая смена", 2);
        joinPlayer("Анна Петрова", "Главная медсестра", sessionCode);

        mockMvc.perform(patch("/api/game-sessions/{sessionCode}/start", sessionCode)
                        .with(auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionStatus").value("IN_PROGRESS"));

        mockMvc.perform(patch("/api/game-sessions/{sessionCode}/finish", sessionCode)
                        .with(auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionStatus").value("FINISHED"));
    }

    @Test
    void shouldDeleteSessionAndExclusivePlayers() throws Exception {
        String firstSessionCode = createSession("Приёмное отделение", 2);
        String secondSessionCode = createSession("Инженерный штаб", 2);
        joinPlayer("Анна Петрова", "Главная медсестра", firstSessionCode);
        joinPlayer("Иван Сидоров", "Главный инженер", firstSessionCode);
        joinPlayer("Иван Сидоров", "Главный инженер", secondSessionCode);

        mockMvc.perform(delete("/api/game-sessions/{sessionCode}", firstSessionCode)
                        .with(auth()))
                .andExpect(status().isNoContent());

        assertThat(count("game_sessions")).isEqualTo(1);
        assertThat(count("session_teams")).isEqualTo(2);
        assertThat(count("session_participants")).isEqualTo(1);
        assertThat(count("players")).isEqualTo(1);
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
    }

    private RequestPostProcessor auth() {
        return httpBasic("facilitator", "medsim123");
    }

    private String createSession(String sessionName, int teamCount) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/game-sessions")
                        .with(auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "sessionName", sessionName,
                                "teamCount", teamCount
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionCode").value(matchesPattern("[A-Z]{4}-\\d{2}")))
                .andReturn();

        JsonNode payload = objectMapper.readTree(result.getResponse().getContentAsString());
        return payload.get("sessionCode").asText();
    }

    private void joinPlayer(String displayName, String hospitalPosition, String sessionCode) throws Exception {
        mockMvc.perform(post("/api/player-sessions/join")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "displayName", displayName,
                                "hospitalPosition", hospitalPosition,
                                "sessionCode", sessionCode
                        ))))
                .andExpect(status().isOk());
    }

    private Long firstTeamId(String sessionCode) {
        return jdbcTemplate.queryForObject(
                """
                SELECT st.id
                FROM session_teams st
                JOIN game_sessions gs ON gs.id = st.game_session_id
                WHERE gs.code = ?
                ORDER BY st.sort_order
                LIMIT 1
                """,
                Long.class,
                sessionCode
        );
    }

    private Long firstParticipantId(String sessionCode) {
        return jdbcTemplate.queryForObject(
                """
                SELECT sp.id
                FROM session_participants sp
                JOIN game_sessions gs ON gs.id = sp.game_session_id
                WHERE gs.code = ?
                ORDER BY sp.id
                LIMIT 1
                """,
                Long.class,
                sessionCode
        );
    }

    private void assignParticipantToTeam(String sessionCode, Long participantId, Long teamId) throws Exception {
        mockMvc.perform(patch("/api/game-sessions/{sessionCode}/participants/{participantId}/team", sessionCode, participantId)
                        .with(auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("teamId", teamId))))
                .andExpect(status().isOk());
    }

    private long count(String tableName) {
        Long value = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + tableName, Long.class);
        return value == null ? 0L : value;
    }
}
