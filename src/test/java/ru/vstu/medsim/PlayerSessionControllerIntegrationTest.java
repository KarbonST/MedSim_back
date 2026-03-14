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

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
        jdbcTemplate.update("DELETE FROM session_participants");
        jdbcTemplate.update("DELETE FROM players");
        jdbcTemplate.update("DELETE FROM game_sessions");
        jdbcTemplate.update("DELETE FROM users");
    }

    @Test
    void shouldJoinPlayerToSessionAndPersistData() throws Exception {
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
                .andExpect(jsonPath("$.sessionStatus").value("LOBBY"))
                .andExpect(jsonPath("$.participantId").isNumber())
                .andExpect(jsonPath("$.sessionId").isNumber())
                .andExpect(jsonPath("$.playerId").isNumber());

        assertThat(count("players")).isEqualTo(1);
        assertThat(count("game_sessions")).isEqualTo(1);
        assertThat(count("session_participants")).isEqualTo(1);
    }

    @Test
    void shouldReuseExistingParticipantForSamePlayerAndSession() throws Exception {
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
    void shouldReturnSessionParticipantsForFacilitatorView() throws Exception {
        joinPlayer("Анна Петрова", "Главная медсестра", "ward-12");
        joinPlayer("Иван Сидоров", "Главный инженер", "WARD-12");

        mockMvc.perform(get("/api/game-sessions/{sessionCode}/participants", "ward-12"))
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
        joinPlayer("Анна Петрова", "Главная медсестра", "ward-12");
        joinPlayer("Иван Сидоров", "Главный инженер", "ward-12");
        joinPlayer("Павел Орлов", "Главный врач", "eng-01");

        mockMvc.perform(get("/api/game-sessions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].sessionCode").value("ENG-01"))
                .andExpect(jsonPath("$[0].participantCount").value(1))
                .andExpect(jsonPath("$[1].sessionCode").value("WARD-12"))
                .andExpect(jsonPath("$[1].participantCount").value(2));
    }

    @Test
    void shouldStartSessionFromLobby() throws Exception {
        joinPlayer("Анна Петрова", "Главная медсестра", "ward-12");

        mockMvc.perform(patch("/api/game-sessions/{sessionCode}/start", "ward-12"))
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
        joinPlayer("Анна Петрова", "Главная медсестра", "ward-12");
        mockMvc.perform(patch("/api/game-sessions/{sessionCode}/start", "ward-12"))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/game-sessions/{sessionCode}/finish", "ward-12"))
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
        joinPlayer("Анна Петрова", "Главная медсестра", "ward-12");
        joinPlayer("Иван Сидоров", "Главный инженер", "ward-12");
        joinPlayer("Иван Сидоров", "Главный инженер", "eng-01");

        mockMvc.perform(delete("/api/game-sessions/{sessionCode}", "ward-12"))
                .andExpect(status().isNoContent());

        assertThat(count("game_sessions")).isEqualTo(1);
        assertThat(count("session_participants")).isEqualTo(1);
        assertThat(count("players")).isEqualTo(1);
        assertThat(countByCode("game_sessions", "ENG-01")).isEqualTo(1);
        assertThat(countByCode("game_sessions", "WARD-12")).isZero();
    }

    @Test
    void shouldReturnNotFoundWhenSessionDoesNotExist() throws Exception {
        mockMvc.perform(get("/api/game-sessions/{sessionCode}/participants", "missing-01"))
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
