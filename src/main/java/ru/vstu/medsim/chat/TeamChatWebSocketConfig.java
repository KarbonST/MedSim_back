package ru.vstu.medsim.chat;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class TeamChatWebSocketConfig implements WebSocketConfigurer {

    private final TeamChatWebSocketHandler teamChatWebSocketHandler;

    public TeamChatWebSocketConfig(TeamChatWebSocketHandler teamChatWebSocketHandler) {
        this.teamChatWebSocketHandler = teamChatWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(teamChatWebSocketHandler, "/ws/team-chat")
                .setAllowedOriginPatterns(
                        "http://localhost:*",
                        "http://127.0.0.1:*",
                        "https://*.cloudpub.ru",
                        "http://*.cloudpub.ru"
                );
    }
}
