package com.dopamin.omok.game.application.service;

import com.dopamin.omok.game.application.dto.PlayerCosmetics;
import com.dopamin.omok.game.application.dto.RoomResponse;
import com.dopamin.omok.game.application.dto.StoneSkinResponse;
import com.dopamin.omok.global.common.response.ApiResponse;
import com.dopamin.omok.game.application.port.in.*;
import com.dopamin.omok.game.application.port.in.ReadyGameUseCase;
import com.dopamin.omok.game.application.port.in.StartGameUseCase;
import com.dopamin.omok.game.application.port.out.*;
import com.dopamin.omok.game.domain.*;
import com.dopamin.omok.game.physical.application.PhysicalGameEndedEvent;
import com.dopamin.omok.game.physical.application.PhysicalGameLifecycle;
import com.dopamin.omok.game.physical.application.dto.PhysicalReplayData;
import com.dopamin.omok.game.physical.application.port.out.SavePhysicalReplayPort;
import com.dopamin.omok.global.common.exception.ErrorCode;
import com.dopamin.omok.global.common.exception.OmokException;
import com.dopamin.omok.shop.application.port.out.LoadUserActiveItemPort;
import com.dopamin.omok.shop.domain.Item;
import com.dopamin.omok.shop.domain.ItemConfig;
import com.dopamin.omok.shop.domain.ItemType;
import com.dopamin.omok.shop.domain.UserActiveItem;
import com.dopamin.omok.user.application.port.out.LoadUserPort;
import com.dopamin.omok.user.domain.EloRating;
import com.dopamin.omok.user.domain.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
    private final PhysicalGameLifecycle physicalGameLifecycle;
    private final SavePhysicalReplayPort savePhysicalReplayPort;

    // 리매치 요청 추적 (roomCode → 요청한 userId 집합)
    private final Map<String, Set<Long>> rematchRequests = new ConcurrentHashMap<>();

    @Override
    @Transactional
    public RoomResponse createRoom(Long userId, GameType gameType, OmokRule omokRule,
                                   TimeLimit timeLimit, ByoyomiOption byoyomiOption) {
        User user = findUserById(userId);
        String roomCode = generateUniqueRoomCode();
        Room room = Room.create(user, roomCode, gameType, omokRule, timeLimit, byoyomiOption);
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

        // 피지컬 모드: 메모리 실시간 세션을 기동(클래식 Game 행은 결과 기록용으로 공유).
        if (room.getGameType() == GameType.PHYSICAL) {
            physicalGameLifecycle.start(room, game, List.of(host, player));
        }

        RoomResponse response = buildRoomResponse(room, game);
        messagingTemplate.convertAndSend("/topic/room/" + roomCode + "/status",
                ApiResponse.success(response));
        return response;
    }

    /**
     * 피지컬 오목이 '승자 확정'으로 종료(5목/기권)됐을 때 세션 매니저가 발행하는 이벤트를 수신해
     * 결과를 영속화한다. 연결 끊김/방장 퇴장은 아래 메서드들이 이미 직접 처리하므로 이 경로로 오지 않는다.
     */
    @EventListener
    @Transactional
    public void onPhysicalGameEnded(PhysicalGameEndedEvent event) {
        Game game = loadGamePort.findActiveGameByRoomCode(event.roomCode()).orElse(null);
        if (game == null || !game.isInProgress()) return;

        Long winnerId = event.winnerUserId();
        User winner = game.getBlackPlayer().getId().equals(winnerId)
                ? game.getBlackPlayer() : game.getWhitePlayer();
        User loser = game.getOpponent(winnerId);

        game.finish(winner);
        winner.recordWin();
        if (loser != null) {
            loser.recordLoss();
            EloRating.applyResult(winner, loser, true); // 피지컬 종료 이벤트 → 피지컬 레이팅
        }
        saveGamePort.save(game);
        if (event.replay() != null) savePhysicalReplayPort.save(event.replay()); // 리플레이 영속(게임당 1회)

        loadRoomPort.findByRoomCode(event.roomCode()).ifPresent(room -> {
            room.waitForNextGame();
            saveRoomPort.save(room);
            RoomResponse response = buildRoomResponse(room, game);
            messagingTemplate.convertAndSend("/topic/room/" + event.roomCode() + "/status",
                    ApiResponse.success(response));
        });
    }

    @Override
    public RoomResponse getRoom(String roomCode) {
        Room room = findRoom(roomCode);
        Game currentGame = loadGamePort.findActiveGameByRoomCode(roomCode)
                .orElseGet(() -> loadGamePort.findLatestGameByRoomCode(roomCode).orElse(null));
        return buildRoomResponse(room, currentGame);
    }

    /** 방장 레이팅 추천 밴드(±). 내 레이팅 기준 이 범위 안의 방장이 만든 방만 추천한다. */
    private static final int RATING_RECOMMEND_BAND = 150;

    @Override
    public Page<RoomResponse> getWaitingRooms(GameType gameType, Pageable pageable) {
        Page<Room> rooms = (gameType == null)
                ? loadRoomPort.findByStatus(RoomStatus.WAITING, pageable)
                : loadRoomPort.findByStatusAndGameType(RoomStatus.WAITING, gameType, pageable);
        return rooms.map(room -> buildRoomResponse(room));
    }

    @Override
    public Page<RoomResponse> getRecommendedRooms(Long userId, Pageable pageable) {
        User user = findUserById(userId);
        int c = user.getClassicRating();
        int p = user.getPhysicalRating();
        int band = RATING_RECOMMEND_BAND;
        return loadRoomPort.findRecommendedRooms(
                        RoomStatus.WAITING, c - band, c + band, p - band, p + band, pageable)
                .map(room -> buildRoomResponse(room));
    }

    @Override
    public Page<RoomResponse> getInProgressRooms(Pageable pageable) {
        return loadRoomPort.findByStatus(RoomStatus.IN_PROGRESS, pageable)
                .map(room -> {
                    Game game = loadGamePort.findActiveGameByRoomCode(room.getRoomCode()).orElse(null);
                    return buildRoomResponse(room, game);
                });
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
                EloRating.applyResult(winner, loser, room.getGameType() == GameType.PHYSICAL);
            } else {
                game.abandon();
            }
            saveGamePort.save(game);
        });

        deleteGamePlayerPort.deleteByRoomId(room.getId());
        rematchRequests.remove(roomCode);
        // 피지컬 메모리 세션 정리(클래식이면 null) + 그때까지의 리플레이 저장
        PhysicalReplayData replay = physicalGameLifecycle.stopSession(roomCode);
        if (replay != null) savePhysicalReplayPort.save(replay);

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
            EloRating.applyResult(winner, loser, room.getGameType() == GameType.PHYSICAL);
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
        // 피지컬 메모리 세션 정리(클래식이면 null) + 그때까지의 리플레이 저장
        PhysicalReplayData replay = physicalGameLifecycle.stopSession(roomCode);
        if (replay != null) savePhysicalReplayPort.save(replay);
    }

    private RoomResponse buildRoomResponse(Room room) {
        List<GamePlayer> players = loadGamePlayerPort.findByRoomId(room.getId());
        return RoomResponse.of(room, players);
    }

    private RoomResponse buildRoomResponse(Room room, Game game) {
        List<GamePlayer> players = loadGamePlayerPort.findByRoomId(room.getId());
        String defeatMessage = null;
        String defeatEffect = null;
        if (game != null && game.getWinner() != null) {
            Long winnerId = game.getWinner().getId();
            defeatMessage = loadUserActiveItemPort
                    .findActiveItemNameByUserIdAndType(winnerId, ItemType.DEFEAT_MESSAGE)
                    .orElse(null);
            // 승자가 패배 이펙트를 장착했다면 그 effect 키를 패자 화면 연출용으로 내려준다(미장착 null → 문구만).
            defeatEffect = loadUserActiveItemPort
                    .findByUserIdAndItemType(winnerId, ItemType.DEFEAT_EFFECT)
                    .map(UserActiveItem::getItem)
                    .map(Item::getItemConfig)
                    .map(ItemConfig::effect)
                    .filter(e -> e != null && !e.isBlank())
                    .orElse(null);
        }
        return RoomResponse.of(room, players, game, defeatMessage, defeatEffect, buildCosmetics(players));
    }

    /**
     * 참가자(색이 배정된 흑/백)별 코스메틱(바둑알 스킨 색 + 착수 효과)을 서버 권위로 해석해
     * 내부 userId(Long)로 매핑한다. 전적으로 user_active_items 에서 읽으므로 미보유자가 쓸 수 없다(유료재화 보호).
     * 착수 효과 우선순위: 장착한 STONE_EFFECT > 고급 STONE_SKIN이 번들한 effect.
     * 관전자(color == null)는 제외하며, 스킨/효과가 필요한 in-game 경로((room, game))에서만 호출한다.
     */
    private Map<Long, PlayerCosmetics> buildCosmetics(List<GamePlayer> players) {
        Map<Long, PlayerCosmetics> cosmetics = new HashMap<>();
        for (GamePlayer gp : players) {
            if (gp.getColor() == null) continue;        // 참가자(흑/백)만
            Long userId = gp.getUser().getId();
            if (cosmetics.containsKey(userId)) continue;

            Optional<ItemConfig> skinConfig = loadUserActiveItemPort
                    .findByUserIdAndItemType(userId, ItemType.STONE_SKIN)
                    .map(UserActiveItem::getItem)
                    .map(Item::getItemConfig);

            StoneSkinResponse skin = skinConfig
                    .map(ItemConfig::stone)
                    .map(StoneSkinResponse::from)
                    .orElse(null);

            // 장착한 착수 효과가 있으면 우선, 없으면 고급 스킨이 번들한 effect 사용
            String effect = loadUserActiveItemPort
                    .findByUserIdAndItemType(userId, ItemType.STONE_EFFECT)
                    .map(UserActiveItem::getItem)
                    .map(Item::getItemConfig)
                    .map(ItemConfig::effect)
                    .or(() -> skinConfig.map(ItemConfig::effect))
                    .filter(e -> e != null && !e.isBlank())
                    .orElse(null);

            if (skin != null || effect != null) {
                cosmetics.put(userId, new PlayerCosmetics(skin, effect));
            }
        }
        return cosmetics;
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
