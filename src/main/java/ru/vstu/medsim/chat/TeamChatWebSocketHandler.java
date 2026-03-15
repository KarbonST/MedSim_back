package ru.vstu.medsim.chat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.util.UriComponentsBuilder;
import ru.vstu.medsim.chat.dto.TeamChatMessageItem;

import java.io.IOException;
import java.net.URI;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class TeamChatWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(TeamChatWebSocketHandler.class);

    private final TeamChatService teamChatService;
    private final ObjectMapper objectMapper;
    private final Map<String, Set<WebSocketSession>> playerSessionsByTeam = new ConcurrentHashMap<>();
    private final Map<Long, Set<WebSocketSession>> facilitatorSessionsByGame = new ConcurrentHashMap<>();
    private final Map<String, ChatConnection> connectionsBySessionId = new ConcurrentHashMap<>();

    public TeamChatWebSocketHandler(TeamChatService teamChatService, ObjectMapper objectMapper) {
        this.teamChatService = teamChatService;
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        try {
            ChatConnection connection = resolveConnection(session);
            connectionsBySessionId.put(session.getId(), connection);
            log.info("Team chat websocket connected: sessionCode={}, facilitator={}, participantId={}, teamId={}",
                    connection.sessionCode(), connection.facilitator(), connection.participantId(), connection.teamId());

            if (connection.facilitator()) {
                facilitatorSessionsByGame
                        .computeIfAbsent(connection.gameSessionId(), ignored -> ConcurrentHashMap.newKeySet())
                        .add(session);
            } else {
                playerSessionsByTeam
                        .computeIfAbsent(teamKey(connection.gameSessionId(), connection.teamId()), ignored -> ConcurrentHashMap.newKeySet())
                        .add(session);
            }
        } catch (ResponseStatusException exception) {
            log.warn("Team chat websocket rejected: status={}, reason={}", exception.getStatusCode(), exception.getReason());
            session.close(new CloseStatus(closeCodeFor(exception.getStatusCode().value()), sanitizeReason(exception.getReason())));
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        ChatConnection connection = connectionsBySessionId.get(session.getId());
        if (connection == null || connection.facilitator()) {
            return;
        }

        try {
            JsonNode payload = objectMapper.readTree(message.getPayload());
            String messageText = payload.path("messageText").asText("");
            TeamChatMessageItem item = teamChatService.postPlayerMessage(connection.sessionCode(), connection.participantId(), messageText);
            broadcastMessage(item, connection.gameSessionId());
        } catch (JsonProcessingException exception) {
            sendError(session, "Не удалось обработать сообщение чата.");
        } catch (ResponseStatusException exception) {
            sendError(session, exception.getReason() == null ? "Не удалось отправить сообщение." : exception.getReason());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        log.info("Team chat websocket closed: sessionId={}, code={}, reason={}", session.getId(), status.getCode(), status.getReason());
        removeSession(session);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.warn("Team chat websocket transport error: sessionId={}, message={}", session.getId(), exception.getMessage());
        removeSession(session);
        if (session.isOpen()) {
            session.close(CloseStatus.SERVER_ERROR);
        }
    }

    private ChatConnection resolveConnection(WebSocketSession session) {
        URI uri = session.getUri();
        if (uri == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Не удалось определить параметры подключения к чату.");
        }

        var queryParams = UriComponentsBuilder.fromUri(uri).build().getQueryParams();
        String sessionCode = queryParams.getFirst("sessionCode");
        String participantIdRaw = queryParams.getFirst("participantId");
        String credentials = queryParams.getFirst("credentials");

        if (participantIdRaw != null && !participantIdRaw.isBlank()) {
            Long participantId;
            try {
                participantId = Long.valueOf(participantIdRaw);
            } catch (NumberFormatException exception) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Некорректный идентификатор участника.", exception);
            }
            TeamChatService.PlayerChatAccess access = teamChatService.resolvePlayerConnection(sessionCode, participantId);
            return new ChatConnection(
                    access.session().getId(),
                    access.session().getCode(),
                    access.team().getId(),
                    access.participant().getId(),
                    false
            );
        }

        TeamChatService.FacilitatorChatAccess access = teamChatService.resolveFacilitatorConnection(sessionCode, credentials);
        return new ChatConnection(access.session().getId(), access.session().getCode(), null, null, true);
    }

    private void broadcastMessage(TeamChatMessageItem item, Long gameSessionId) throws IOException {
        String payload = objectMapper.writeValueAsString(new TeamChatSocketEvent("TEAM_CHAT_MESSAGE", item, null));
        TextMessage textMessage = new TextMessage(payload);

        for (WebSocketSession session : playerSessionsByTeam.getOrDefault(teamKey(gameSessionId, item.teamId()), Set.of())) {
            sendIfOpen(session, textMessage);
        }

        for (WebSocketSession session : facilitatorSessionsByGame.getOrDefault(gameSessionId, Set.of())) {
            sendIfOpen(session, textMessage);
        }
    }

    private void sendError(WebSocketSession session, String errorMessage) throws IOException {
        if (!session.isOpen()) {
            return;
        }
        String payload = objectMapper.writeValueAsString(new TeamChatSocketEvent("TEAM_CHAT_ERROR", null, errorMessage));
        session.sendMessage(new TextMessage(payload));
    }

    private void sendIfOpen(WebSocketSession session, TextMessage message) throws IOException {
        if (session.isOpen()) {
            session.sendMessage(message);
        }
    }

    private void removeSession(WebSocketSession session) {
        ChatConnection connection = connectionsBySessionId.remove(session.getId());
        if (connection == null) {
            return;
        }

        if (connection.facilitator()) {
            Set<WebSocketSession> sessions = facilitatorSessionsByGame.get(connection.gameSessionId());
            if (sessions != null) {
                sessions.remove(session);
                if (sessions.isEmpty()) {
                    facilitatorSessionsByGame.remove(connection.gameSessionId());
                }
            }
            return;
        }

        Set<WebSocketSession> sessions = playerSessionsByTeam.get(teamKey(connection.gameSessionId(), connection.teamId()));
        if (sessions != null) {
            sessions.remove(session);
            if (sessions.isEmpty()) {
                playerSessionsByTeam.remove(teamKey(connection.gameSessionId(), connection.teamId()));
            }
        }
    }

    private String teamKey(Long gameSessionId, Long teamId) {
        return gameSessionId + ":" + teamId;
    }

    private int closeCodeFor(int statusCode) {
        return switch (statusCode) {
            case 401 -> 4401;
            case 403 -> 4403;
            case 404 -> 4404;
            case 409 -> 4409;
            default -> 4400;
        };
    }

    private String sanitizeReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return "Чат недоступен.";
        }
        return reason.length() > 120 ? reason.substring(0, 120) : reason;
    }

    private record ChatConnection(
            Long gameSessionId,
            String sessionCode,
            Long teamId,
            Long participantId,
            boolean facilitator
    ) {
    }

    private record TeamChatSocketEvent(String type, TeamChatMessageItem message, String errorMessage) {
    }
}
