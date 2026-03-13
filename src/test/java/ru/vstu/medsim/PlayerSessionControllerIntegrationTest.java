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

    private long count(String tableName) {
        Long value = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + tableName, Long.class);
        return value == null ? 0L : value;
    }
}
