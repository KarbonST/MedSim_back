package ru.vstu.medsim.chat;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.vstu.medsim.chat.dto.FacilitatorTeamChatsResponse;
import ru.vstu.medsim.chat.dto.PlayerTeamChatResponse;

@RestController
@RequestMapping("/api")
public class TeamChatController {

    private final TeamChatService teamChatService;

    public TeamChatController(TeamChatService teamChatService) {
        this.teamChatService = teamChatService;
    }

    @GetMapping("/player-sessions/{sessionCode}/participants/{participantId}/chat")
    public PlayerTeamChatResponse getPlayerChat(
            @PathVariable String sessionCode,
            @PathVariable Long participantId
    ) {
        return teamChatService.getPlayerChat(sessionCode, participantId);
    }

    @GetMapping("/game-sessions/{sessionCode}/team-chats")
    public FacilitatorTeamChatsResponse getFacilitatorChats(@PathVariable String sessionCode) {
        return teamChatService.getFacilitatorChats(sessionCode);
    }
}
