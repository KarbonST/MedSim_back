package ru.vstu.medsim.chat;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ru.vstu.medsim.chat.domain.TeamChatMessage;
import ru.vstu.medsim.chat.dto.FacilitatorTeamChatThread;
import ru.vstu.medsim.chat.dto.FacilitatorTeamChatsResponse;
import ru.vstu.medsim.chat.dto.PlayerTeamChatResponse;
import ru.vstu.medsim.chat.dto.TeamChatMessageItem;
import ru.vstu.medsim.chat.repository.TeamChatMessageRepository;
import ru.vstu.medsim.player.domain.GameSession;
import ru.vstu.medsim.player.domain.GameSessionStatus;
import ru.vstu.medsim.player.domain.SessionParticipant;
import ru.vstu.medsim.player.repository.SessionParticipantRepository;
import ru.vstu.medsim.session.GameSessionQueryService;
import ru.vstu.medsim.session.domain.SessionTeam;
import ru.vstu.medsim.session.repository.SessionTeamRepository;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class TeamChatService {

    private final GameSessionQueryService gameSessionQueryService;
    private final SessionParticipantRepository sessionParticipantRepository;
    private final SessionTeamRepository sessionTeamRepository;
    private final TeamChatMessageRepository teamChatMessageRepository;
    private final UserDetailsService userDetailsService;
    private final PasswordEncoder passwordEncoder;

    public TeamChatService(
            GameSessionQueryService gameSessionQueryService,
            SessionParticipantRepository sessionParticipantRepository,
            SessionTeamRepository sessionTeamRepository,
            TeamChatMessageRepository teamChatMessageRepository,
            UserDetailsService userDetailsService,
            PasswordEncoder passwordEncoder
    ) {
        this.gameSessionQueryService = gameSessionQueryService;
        this.sessionParticipantRepository = sessionParticipantRepository;
        this.sessionTeamRepository = sessionTeamRepository;
        this.teamChatMessageRepository = teamChatMessageRepository;
        this.userDetailsService = userDetailsService;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public PlayerTeamChatResponse getPlayerChat(String sessionCode, Long participantId) {
        PlayerChatAccess access = resolvePlayerConnection(sessionCode, participantId);
        List<TeamChatMessageItem> messages = teamChatMessageRepository
                .findAllByGameSessionIdAndTeamIdOrderByCreatedAtAscIdAsc(access.session().getId(), access.team().getId())
                .stream()
                .map(this::toItem)
                .toList();

        return new PlayerTeamChatResponse(access.team().getId(), access.team().getName(), messages);
    }

    @Transactional(readOnly = true)
    public FacilitatorTeamChatsResponse getFacilitatorChats(String sessionCode) {
        FacilitatorChatAccess access = resolveFacilitatorConnection(sessionCode, null);
        List<SessionTeam> teams = sessionTeamRepository.findAllByGameSessionIdOrderBySortOrderAscIdAsc(access.session().getId());
        Map<Long, List<TeamChatMessageItem>> messagesByTeamId = teamChatMessageRepository
                .findAllByGameSessionIdOrderByCreatedAtAscIdAsc(access.session().getId())
                .stream()
                .map(this::toItem)
                .collect(Collectors.groupingBy(TeamChatMessageItem::teamId));

        List<FacilitatorTeamChatThread> teamChats = teams.stream()
                .map(team -> new FacilitatorTeamChatThread(
                        team.getId(),
                        team.getName(),
                        team.getSortOrder(),
                        messagesByTeamId.getOrDefault(team.getId(), List.of())
                ))
                .toList();

        return new FacilitatorTeamChatsResponse(access.session().getCode(), access.session().getName(), teamChats);
    }

    @Transactional
    public TeamChatMessageItem postPlayerMessage(String sessionCode, Long participantId, String rawMessageText) {
        PlayerChatAccess access = resolvePlayerConnection(sessionCode, participantId);
        validateMessageAllowed(access.session());

        String messageText = normalizeMessage(rawMessageText);
        TeamChatMessage message = teamChatMessageRepository.save(
                new TeamChatMessage(access.session(), access.team(), access.participant(), messageText)
        );

        return toItem(message);
    }

    @Transactional(readOnly = true)
    public PlayerChatAccess resolvePlayerConnection(String sessionCode, Long participantId) {
        GameSession session = gameSessionQueryService.getSessionOrThrow(sessionCode);
        SessionParticipant participant = sessionParticipantRepository.findByIdAndGameSessionId(participantId, session.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Участник не найден в указанной сессии."));

        if (participant.getTeam() == null) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Чат команды станет доступен после распределения участника по командам."
            );
        }

        if (session.getStatus() == GameSessionStatus.LOBBY) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Командный чат станет доступен после старта игры."
            );
        }

        return new PlayerChatAccess(session, participant, participant.getTeam());
    }

    @Transactional(readOnly = true)
    public FacilitatorChatAccess resolveFacilitatorConnection(String sessionCode, String encodedCredentials) {
        GameSession session = gameSessionQueryService.getSessionOrThrow(sessionCode);

        if (encodedCredentials != null) {
            AuthCredentials credentials = parseEncodedCredentials(encodedCredentials);
            UserDetails user = userDetailsService.loadUserByUsername(credentials.login());
            if (!passwordEncoder.matches(credentials.password(), user.getPassword())) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Неверные учётные данные ведущего.");
            }
            boolean facilitator = user.getAuthorities().stream()
                    .anyMatch(authority -> "FACILITATOR".equals(authority.getAuthority()));
            if (!facilitator) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Доступ к чатам команд разрешён только ведущему.");
            }
        }

        return new FacilitatorChatAccess(session);
    }

    private void validateMessageAllowed(GameSession session) {
        if (session.getStatus() == GameSessionStatus.LOBBY) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Командный чат станет доступен после старта игры.");
        }
        if (session.getStatus() == GameSessionStatus.FINISHED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Игра уже завершена. Отправка сообщений недоступна.");
        }
    }

    private String normalizeMessage(String rawMessageText) {
        String messageText = rawMessageText == null ? "" : rawMessageText.trim();
        if (messageText.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Введите текст сообщения.");
        }
        if (messageText.length() > 1000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Сообщение не должно превышать 1000 символов.");
        }
        return messageText;
    }

    private AuthCredentials parseEncodedCredentials(String encodedCredentials) {
        String base64Value = encodedCredentials == null ? "" : encodedCredentials.trim();
        if (base64Value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Не удалось распознать учётные данные ведущего.");
        }

        String decodedValue;
        try {
            decodedValue = new String(Base64.getDecoder().decode(base64Value), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Не удалось распознать учётные данные ведущего.", exception);
        }

        int separatorIndex = decodedValue.indexOf(':');
        if (separatorIndex <= 0) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Не удалось распознать учётные данные ведущего.");
        }

        return new AuthCredentials(
                decodedValue.substring(0, separatorIndex),
                decodedValue.substring(separatorIndex + 1)
        );
    }

    private TeamChatMessageItem toItem(TeamChatMessage message) {
        return new TeamChatMessageItem(
                message.getId(),
                message.getTeam().getId(),
                message.getTeam().getName(),
                message.getParticipant().getId(),
                message.getParticipant().getPlayer().getDisplayName(),
                message.getMessageText(),
                message.getCreatedAt()
        );
    }

    public record PlayerChatAccess(GameSession session, SessionParticipant participant, SessionTeam team) {
    }

    public record FacilitatorChatAccess(GameSession session) {
    }

    private record AuthCredentials(String login, String password) {
    }
}
