package com.dopamin.omok.game.application.service;

import com.dopamin.omok.game.application.dto.RoomResponse;
import com.dopamin.omok.global.common.response.ApiResponse;
import com.dopamin.omok.game.application.port.in.*;
import com.dopamin.omok.game.application.port.in.ReadyGameUseCase;
import com.dopamin.omok.game.application.port.in.StartGameUseCase;
import com.dopamin.omok.game.application.port.out.*;
import com.dopamin.omok.game.domain.*;
import com.dopamin.omok.global.common.exception.ErrorCode;
import com.dopamin.omok.global.common.exception.OmokException;
import com.dopamin.omok.shop.application.port.out.LoadUserActiveItemPort;
import com.dopamin.omok.shop.domain.ItemType;
import com.dopamin.omok.user.application.port.out.LoadUserPort;
import com.dopamin.omok.user.domain.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RoomService implements CreateRoomUseCase, JoinRoomUseCase, SpectateRoomUseCase,
        LeaveRoomUseCase, RequestRematchUseCase, GetRoomUseCase, ReadyGameUseCase, StartGameUseCase {

    private final com.dopamin.omok.global.websocket.DisconnectGraceManager disconnectGraceManager;
    private final LoadRoomPort loadRoomPort;
    private final SaveRoomPort saveRoomPort;
    private final LoadGamePlayerPort loadGamePlayerPort;
    private final SaveGamePlayerPort saveGamePlayerPort;
    private final DeleteGamePlayerPort deleteGamePlayerPort;
    private final LoadGamePort loadGamePort;
    private final SaveGamePort saveGamePort;
    private final LoadUserPort loadUserPort;
    private final LoadUserActiveItemPort loadUserActiveItemPort;
    private final SimpMessagingTemplate messagingTemplate;

    // 리매치 요청 추적 (roomCode → 요청한 userId 집합)
    private final Map<String, Set<Long>> rematchRequests = new ConcurrentHashMap<>();

    @Override
    @Transactional
    public RoomResponse createRoom(Long userId, GameType gameType, TimeLimit timeLimit, ByoyomiOption byoyomiOption) {
        User user = findUserById(userId);
        String roomCode = generateUniqueRoomCode();
        Room room = Room.create(user, roomCode, gameType, timeLimit, byoyomiOption);
        saveRoomPort.save(room);

        GamePlayer host = GamePlayer.createHost(room, user);
        saveGamePlayerPort.save(host);

        return buildRoomResponse(room);
    }

    @Override
    @Transactional
    public RoomResponse joinRoom(String roomCode, Long userId) {
        Room room = findRoom(roomCode);

        if (room.isClosed()) throw new OmokException(ErrorCode.ROOM_ALREADY_CLOSED);

        // 이미 플레이어/방장으로 참가 중이면 현재 방 정보 반환 (재입장)
        GamePlayer existing = loadGamePlayerPort.findByRoomIdAndUserId(room.getId(), userId).orElse(null);
        if (existing != null && !existing.isSpectator()) {
            return buildRoomResponse(room);
        }

        boolean hasPlayer = !loadGamePlayerPort.findByRoomIdAndRole(room.getId(), PlayerRole.PLAYER).isEmpty();
        if (hasPlayer) throw new OmokException(ErrorCode.ROOM_ALREADY_FULL);
        if (!room.isWaiting()) throw new OmokException(ErrorCode.ROOM_ALREADY_FULL);

        User user = findUserById(userId);

        // 관전자로 있었다면 제거하고 재참여
        loadGamePlayerPort.findByRoomIdAndUserId(room.getId(), userId)
                .ifPresent(deleteGamePlayerPort::delete);

        GamePlayer player = GamePlayer.createPlayer(room, user);
        saveGamePlayerPort.save(player);

        RoomResponse response = buildRoomResponse(room);
        messagingTemplate.convertAndSend("/topic/room/" + roomCode + "/status",
                ApiResponse.success(response));
        return response;
    }

    @Override
    @Transactional
    public RoomResponse spectateRoom(String roomCode, Long userId) {
        Room room = findRoom(roomCode);

        if (room.isClosed()) throw new OmokException(ErrorCode.ROOM_ALREADY_CLOSED);

        loadGamePlayerPort.findByRoomIdAndUserId(room.getId(), userId).ifPresent(gp -> {
            throw new OmokException(ErrorCode.ALREADY_IN_ROOM);
        });

        if (loadGamePlayerPort.countSpectatorsByRoomId(room.getId()) >= room.getMaxSpectators()) {
            throw new OmokException(ErrorCode.ROOM_SPECTATOR_FULL);
        }

        User user = findUserById(userId);
        GamePlayer spectator = GamePlayer.createSpectator(room, user);
        saveGamePlayerPort.save(spectator);

        return buildRoomResponse(room);
    }

    @Override
    @Transactional
    public void leaveRoom(String roomCode, Long userId) {
        Room room = findRoom(roomCode);

        GamePlayer gamePlayer = loadGamePlayerPort.findByRoomIdAndUserId(room.getId(), userId)
                .orElseThrow(() -> new OmokException(ErrorCode.NOT_IN_ROOM));

        if (gamePlayer.isHost()) {
            // 방장 퇴장 → 방 폐쇄
            closeRoomByHost(room, roomCode);
        } else if (gamePlayer.isPlayer() && room.isInProgress()) {
            // 대국 중 참가자 퇴장 → 자동 패배
            handlePlayerDisconnectDuringGame(room, userId, roomCode);
        } else {
            // 관전자 또는 대기 중 참가자 퇴장
            deleteGamePlayerPort.delete(gamePlayer);
            if (gamePlayer.isPlayer()) {
                room.waitForNextGame();
                saveRoomPort.save(room);
            }
            RoomResponse response = buildRoomResponse(room);
            messagingTemplate.convertAndSend("/topic/room/" + roomCode + "/status",
                    ApiResponse.success(response));
        }
    }

    @Override
    @Transactional
    public RoomResponse requestRematch(String roomCode, Long userId) {
        Room room = findRoom(roomCode);

        if (room.isClosed()) throw new OmokException(ErrorCode.ROOM_ALREADY_CLOSED);
        if (room.isInProgress()) throw new OmokException(ErrorCode.REMATCH_NOT_AVAILABLE);

        loadGamePlayerPort.findByRoomIdAndUserId(room.getId(), userId)
                .filter(GamePlayer::isParticipant)
                .orElseThrow(() -> new OmokException(ErrorCode.NOT_GAME_PARTICIPANT));

        Set<Long> requests = rematchRequests.computeIfAbsent(roomCode, k -> ConcurrentHashMap.newKeySet());
        requests.add(userId);

        List<GamePlayer> participants = loadGamePlayerPort.findByRoomId(room.getId()).stream()
                .filter(GamePlayer::isParticipant).toList();

        boolean bothRequested = participants.stream().allMatch(gp -> requests.contains(gp.getUser().getId()));

        if (bothRequested) {
            rematchRequests.remove(roomCode);
            return startRematch(room, participants);
        }

        return buildRoomResponse(room);
    }

    @Override
    @Transactional
    public RoomResponse readyGame(String roomCode, Long userId) {
        Room room = findRoom(roomCode);
        GamePlayer player = loadGamePlayerPort.findByRoomIdAndUserId(room.getId(), userId)
                .orElseThrow(() -> new OmokException(ErrorCode.NOT_IN_ROOM));

        if (!player.isPlayer()) throw new OmokException(ErrorCode.NOT_GAME_PARTICIPANT);

        player.toggleReady();
        saveGamePlayerPort.save(player);

        RoomResponse response = buildRoomResponse(room);
        messagingTemplate.convertAndSend("/topic/room/" + roomCode + "/status",
                ApiResponse.success(response));
        return response;
    }

    @Override
    @Transactional
    public RoomResponse startGame(String roomCode, Long userId) {
        Room room = findRoom(roomCode);

        if (!room.isHost(userId)) throw new OmokException(ErrorCode.ROOM_NOT_HOST);
        if (!room.isWaiting()) throw new OmokException(ErrorCode.ROOM_ALREADY_FULL);

        List<GamePlayer> players = loadGamePlayerPort.findByRoomIdAndRole(room.getId(), PlayerRole.PLAYER);
        if (players.isEmpty()) throw new OmokException(ErrorCode.NOT_ENOUGH_PLAYERS);

        GamePlayer player = players.get(0);
        if (!player.isReady()) throw new OmokException(ErrorCode.PLAYER_NOT_READY);

        GamePlayer host = loadGamePlayerPort.findByRoomIdAndRole(room.getId(), PlayerRole.HOST)
                .stream().findFirst().orElseThrow();

        player.resetReady();
        saveGamePlayerPort.save(player);

        room.startGame();
        saveRoomPort.save(room);

        Game game = Game.start(room, host.getUser(), player.getUser());
        saveGamePort.save(game);

        RoomResponse response = buildRoomResponse(room, game);
        messagingTemplate.convertAndSend("/topic/room/" + roomCode + "/status",
                ApiResponse.success(response));
        return response;
    }

    @Override
    public RoomResponse getRoom(String roomCode) {
        Room room = findRoom(roomCode);
        Game currentGame = loadGamePort.findActiveGameByRoomCode(roomCode)
                .orElseGet(() -> loadGamePort.findLatestGameByRoomCode(roomCode).orElse(null));
        return buildRoomResponse(room, currentGame);
    }

    @Override
    public Page<RoomResponse> getWaitingRooms(Pageable pageable) {
        return loadRoomPort.findByStatus(RoomStatus.WAITING, pageable)
                .map(room -> buildRoomResponse(room));
    }

    @Transactional
    public void handleDisconnect(String roomCode, Long userId) {
        Room room = loadRoomPort.findByRoomCode(roomCode).orElse(null);
        if (room == null || room.isClosed()) return;

        GamePlayer gp = loadGamePlayerPort.findByRoomIdAndUserId(room.getId(), userId).orElse(null);
        if (gp == null) return;

        try {
            if (room.isWaiting()) {
                // 게임 미시작: 즉시 처리
                if (gp.isHost()) {
                    closeRoomByHost(room, roomCode);
                } else if (gp.isPlayer()) {
                    deleteGamePlayerPort.delete(gp);
                    messagingTemplate.convertAndSend("/topic/room/" + roomCode + "/status",
                            ApiResponse.success(buildRoomResponse(room)));
                }
            } else if (room.isInProgress()) {
                // 게임 중: 30초 유예, 최대 2번
                String nickname = gp.getUser().getNickname();
                messagingTemplate.convertAndSend("/topic/room/" + roomCode + "/notice",
                        Map.of("message", nickname + "님의 연결이 끊겼습니다. 30초 내 재접속하지 않으면 패배 처리됩니다."));
                disconnectGraceManager.scheduleGrace(roomCode, userId, () ->
                        loadRoomPort.findByRoomCode(roomCode).ifPresent(r -> {
                            if (r.isClosed() || !r.isInProgress()) return;
                            loadGamePlayerPort.findByRoomIdAndUserId(r.getId(), userId).ifPresent(p -> {
                                if (p.isHost()) closeRoomByHost(r, roomCode);
                                else if (p.isPlayer()) handlePlayerDisconnectDuringGame(r, userId, roomCode);
                            });
                        })
                );
            }
        } catch (Exception e) {
            log.error("Disconnect handling error for room={} user={}", roomCode, userId, e);
        }
    }

    private RoomResponse startRematch(Room room, List<GamePlayer> participants) {
        // 색상 교체
        participants.forEach(gp -> {
            gp.swapColor();
            gp.resetTimeForRematch(room.getTimeLimit().getSeconds());
            saveGamePlayerPort.save(gp);
        });

        room.startGame();
        saveRoomPort.save(room);

        GamePlayer blackPlayer = participants.stream()
                .filter(gp -> gp.getColor() == StoneColor.BLACK).findFirst().orElseThrow();
        GamePlayer whitePlayer = participants.stream()
                .filter(gp -> gp.getColor() == StoneColor.WHITE).findFirst().orElseThrow();

        Game newGame = Game.start(room, blackPlayer.getUser(), whitePlayer.getUser());
        saveGamePort.save(newGame);

        RoomResponse response = buildRoomResponse(room, newGame);
        messagingTemplate.convertAndSend("/topic/room/" + room.getRoomCode() + "/status",
                ApiResponse.success(response));
        return response;
    }

    private void closeRoomByHost(Room room, String roomCode) {
        room.close();
        saveRoomPort.save(room);

        loadGamePort.findActiveGameByRoomCode(roomCode).ifPresent(game -> {
            if (game.isInProgress()) {
                // 게임 중 호스트 퇴장 → 상대방 승리 처리
                Long hostId = room.getHost().getId();
                User winner = game.getOpponent(hostId);
                User loser = game.getBlackPlayer().getId().equals(hostId)
                        ? game.getBlackPlayer() : game.getWhitePlayer();
                game.finish(winner);
                winner.recordWin();
                loser.recordLoss();
            } else {
                game.abandon();
            }
            saveGamePort.save(game);
        });

        deleteGamePlayerPort.deleteByRoomId(room.getId());
        rematchRequests.remove(roomCode);

        messagingTemplate.convertAndSend("/topic/room/" + roomCode + "/closed",
                Map.of("message", "방장이 방을 나갔습니다."));
    }

    private void handlePlayerDisconnectDuringGame(Room room, Long userId, String roomCode) {
        loadGamePort.findActiveGameByRoomCode(roomCode).ifPresent(game -> {
            User loser = game.getBlackPlayer().getId().equals(userId)
                    ? game.getBlackPlayer() : game.getWhitePlayer();
            User winner = game.getOpponent(loser.getId());

            game.finish(winner);
            winner.recordWin();
            loser.recordLoss();
            saveGamePort.save(game);

            // 패배한 플레이어를 방에서 제거
            loadGamePlayerPort.findByRoomIdAndUserId(room.getId(), userId)
                    .ifPresent(deleteGamePlayerPort::delete);

            room.waitForNextGame();
            saveRoomPort.save(room);

            RoomResponse response = buildRoomResponse(room, game);
            messagingTemplate.convertAndSend("/topic/room/" + roomCode + "/status",
                    ApiResponse.success(response));
        });
    }

    private RoomResponse buildRoomResponse(Room room) {
        List<GamePlayer> players = loadGamePlayerPort.findByRoomId(room.getId());
        return RoomResponse.of(room, players);
    }

    private RoomResponse buildRoomResponse(Room room, Game game) {
        List<GamePlayer> players = loadGamePlayerPort.findByRoomId(room.getId());
        String defeatMessage = null;
        if (game != null && game.getWinner() != null) {
            defeatMessage = loadUserActiveItemPort
                    .findActiveItemNameByUserIdAndType(game.getWinner().getId(), ItemType.DEFEAT_MESSAGE)
                    .orElse(null);
        }
        return RoomResponse.of(room, players, game, defeatMessage);
    }

    private Room findRoom(String roomCode) {
        return loadRoomPort.findByRoomCode(roomCode)
                .orElseThrow(() -> new OmokException(ErrorCode.ROOM_NOT_FOUND));
    }

    private User findUserById(Long userId) {
        return loadUserPort.findById(userId)
                .orElseThrow(() -> new OmokException(ErrorCode.USER_NOT_FOUND));
    }

    private String generateUniqueRoomCode() {
        String code;
        do {
            code = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        } while (loadRoomPort.existsByRoomCode(code));
        return code;
    }
}
