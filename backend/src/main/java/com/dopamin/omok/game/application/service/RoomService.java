package com.dopamin.omok.game.application.service;

import com.dopamin.omok.game.application.dto.PlayerCosmetics;
import com.dopamin.omok.game.application.dto.RoomResponse;
import com.dopamin.omok.game.application.dto.StoneSkinResponse;
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
import com.dopamin.omok.shop.application.port.in.EquipItemUseCase;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
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
        LeaveRoomUseCase, RequestRematchUseCase, GetRoomUseCase, ReadyGameUseCase, StartGameUseCase,
        ChangeStoneSkinUseCase, SwapColorsUseCase, StartAiPracticeUseCase, StartPhysicalSandboxUseCase,
        CleanupStaleRoomsUseCase {

    /** 피지컬 AI 연습 상대 봇 계정 식별용 이메일(V33 마이그레이션으로 시드). */
    private static final String AI_BOT_EMAIL = "ai-practice-bot@dopamin.local";

    private final com.dopamin.omok.global.websocket.DisconnectGraceManager disconnectGraceManager;
    private final com.dopamin.omok.global.websocket.WebSocketSessionRegistry sessionRegistry;
    private final LoadRoomPort loadRoomPort;
    private final SaveRoomPort saveRoomPort;
    private final LoadGamePlayerPort loadGamePlayerPort;
    private final SaveGamePlayerPort saveGamePlayerPort;
    private final DeleteGamePlayerPort deleteGamePlayerPort;
    private final LoadGamePort loadGamePort;
    private final SaveGamePort saveGamePort;
    private final LoadUserPort loadUserPort;
    private final LoadUserActiveItemPort loadUserActiveItemPort;
    private final EquipItemUseCase equipItemUseCase;
    private final RoomEventPublisherPort roomEventPublisherPort;
    private final PhysicalGameLifecycle physicalGameLifecycle;
    private final SavePhysicalReplayPort savePhysicalReplayPort;

    // 리매치 요청 추적 (roomCode → 요청한 userId 집합)
    private final Map<String, Set<Long>> rematchRequests = new ConcurrentHashMap<>();

    @Override
    @Transactional
    public RoomResponse createRoom(Long userId, GameType gameType, OmokRule omokRule,
                                   TimeLimit timeLimit, ByoyomiOption byoyomiOption, boolean ranked) {
        User user = findUserById(userId);
        ensureCanHostNewRoom(userId);
        // 게스트(비회원)는 랭크전을 열 수 없다 — 항상 캐주얼로 강제한다(레이팅 어뷰징 방지).
        boolean effectiveRanked = ranked && !user.isGuest();
        String roomCode = generateUniqueRoomCode();
        Room room = Room.create(user, roomCode, gameType, omokRule, timeLimit, byoyomiOption, effectiveRanked);
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
        // 랭크 방은 회원만 — 게스트는 캐주얼 방만 참가할 수 있다(랭크전=회원 vs 회원 보장).
        if (room.isRanked() && user.isGuest()) {
            throw new OmokException(ErrorCode.ROOM_RANKED_MEMBERS_ONLY);
        }

        // 관전자로 있었다면 제거하고 재참여
        loadGamePlayerPort.findByRoomIdAndUserId(room.getId(), userId)
                .ifPresent(deleteGamePlayerPort::delete);

        // 방장이 흑백을 바꾼 뒤 참가자가 나갔다 새로 올 수 있으므로, 항상 방장 색의 반대를 배정한다.
        GamePlayer host = loadGamePlayerPort.findByRoomIdAndRole(room.getId(), PlayerRole.HOST)
                .stream().findFirst().orElseThrow();
        StoneColor joinColor = (host.getColor() == StoneColor.BLACK) ? StoneColor.WHITE : StoneColor.BLACK;
        GamePlayer player = GamePlayer.createPlayer(room, user, joinColor);
        saveGamePlayerPort.save(player);

        RoomResponse response = buildRoomResponse(room, null);
        roomEventPublisherPort.publishStatus(roomCode, response);
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

        // 대기 중인 방에만 관전자 입장을 실시간 브로드캐스트.
        // 대국 중에는 currentGame이 빠진 이 응답이 플레이어 화면의 게임 상태를 덮어쓸 수 있어 보내지 않는다.
        RoomResponse response = buildRoomResponse(room);
        if (room.isWaiting()) {
            roomEventPublisherPort.publishStatus(roomCode, response);
        }
        return response;
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
            RoomResponse response = buildRoomResponse(room, null);
            roomEventPublisherPort.publishStatus(roomCode, response);
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

        RoomResponse response = buildRoomResponse(room, null);
        roomEventPublisherPort.publishStatus(roomCode, response);
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

        ensureDifferentStoneSkins(host, player);

        // 봇은 항상 준비 상태 유지(연속 AI 연습 가능). 사람은 매 판 준비를 다시 눌러야 한다.
        if (!player.getUser().isBot()) {
            player.resetReady();
            saveGamePlayerPort.save(player);
        }

        // 새 판 시작 — 지난 판에서 누적된 연결 끊김 유예 상태(타이머·횟수)를 초기화한다.
        disconnectGraceManager.clearRoom(roomCode);

        room.startGame();
        saveRoomPort.save(room);

        // 흑/백은 GamePlayer.color 로 정한다 — 방장이 색을 바꿨을 수 있으므로 host=흑으로 가정하지 않는다.
        GamePlayer black = host.getColor() == StoneColor.BLACK ? host : player;
        GamePlayer white = (black == host) ? player : host;
        Game game = Game.start(room, black.getUser(), white.getUser());
        saveGamePort.save(game);

        // 피지컬 모드: 메모리 실시간 세션을 기동(클래식 Game 행은 결과 기록용으로 공유).
        if (room.getGameType() == GameType.PHYSICAL) {
            physicalGameLifecycle.start(room, game, List.of(host, player));
        }

        RoomResponse response = buildRoomResponse(room, game);
        roomEventPublisherPort.publishStatus(roomCode, response);
        return response;
    }

    @Override
    @Transactional
    public RoomResponse startAiPractice(Long userId) {
        // 게스트도 허용한다 — 게스트는 이미 사람과의 피지컬 캐주얼 대국이 가능하므로 '봇과의 연습'만 막을 이유가 없고,
        // 상대를 기다리지 않고 피지컬(이 서비스의 차별점)을 체험시키는 게 가입 전환에 유리하다.
        // 레이팅·전적은 Game.isRated 가 봇·게스트를 모두 제외하므로 어뷰징 위험도 없다.
        User user = findUserById(userId);
        ensureCanHostNewRoom(userId);
        User bot = loadUserPort.findByEmail(AI_BOT_EMAIL)
                .orElseThrow(() -> new OmokException(ErrorCode.USER_NOT_FOUND));

        // 피지컬 방을 만들고 빈자리를 '준비된' 봇으로 채운다. 게임은 자동 시작하지 않는다 —
        // 방장이 직접 '게임 시작'을 눌러야 시작하며, 그때 사람이 이미 접속해 있어 카운트다운(3·2·1)을 온전히 본다.
        // (이전엔 HTTP 응답 시점에 바로 시작해, 클라가 뒤늦게 접속하며 카운트다운이 들쭉날쭉했음.)
        String roomCode = generateUniqueRoomCode();
        Room room = Room.create(user, roomCode, GameType.PHYSICAL,
                OmokRule.FREESTYLE, TimeLimit.UNLIMITED, ByoyomiOption.NONE, false); // 연습=캐주얼(레이팅 미반영)
        saveRoomPort.save(room);

        GamePlayer host = GamePlayer.createHost(room, user);       // 흑(선) = 사람
        saveGamePlayerPort.save(host);
        GamePlayer botPlayer = GamePlayer.createPlayer(room, bot, StoneColor.WHITE);  // 백 = 봇
        botPlayer.markReady();                                      // 봇은 항상 준비 완료
        saveGamePlayerPort.save(botPlayer);

        return buildRoomResponse(room, null); // WAITING 방 반환 → 방장이 '게임 시작'으로 개시
    }

    @Override
    @Transactional
    public RoomResponse startPhysicalSandbox(Long userId) {
        User user = findUserById(userId);
        ensureCanHostNewRoom(userId);

        // 상대 없이 나 혼자 들어가는 피지컬 방(캐주얼=레이팅 미반영). 방장(흑) 한 명만 참가한다.
        String roomCode = generateUniqueRoomCode();
        Room room = Room.create(user, roomCode, GameType.PHYSICAL,
                OmokRule.FREESTYLE, TimeLimit.UNLIMITED, ByoyomiOption.NONE, false);
        saveRoomPort.save(room);

        GamePlayer host = GamePlayer.createHost(room, user); // 흑(선) = 나
        saveGamePlayerPort.save(host);

        // AI 연습과 달리 '게임 시작' 버튼을 누를 상대가 없으므로 여기서 바로 개시한다.
        room.startGame();
        saveRoomPort.save(room);

        Game game = Game.start(room, user, null); // 백(상대)은 없음 — white_player_id 는 nullable
        saveGamePort.save(game);

        physicalGameLifecycle.start(room, game, List.of(host)); // 참가자 1명으로 실시간 세션 기동

        RoomResponse response = buildRoomResponse(room, game);
        roomEventPublisherPort.publishStatus(roomCode, response);
        return response;
    }

    @Override
    @Transactional
    public RoomResponse changeStoneSkin(String roomCode, Long userId, Long itemId) {
        Room room = findRoom(roomCode);
        // 대기 중에만 변경 가능(게임 중 스킨 교체로 인한 혼동 방지)
        if (!room.isWaiting()) throw new OmokException(ErrorCode.STONE_SKIN_CHANGE_NOT_AVAILABLE);

        GamePlayer player = loadGamePlayerPort.findByRoomIdAndUserId(room.getId(), userId)
                .orElseThrow(() -> new OmokException(ErrorCode.NOT_IN_ROOM));
        if (!player.isParticipant()) throw new OmokException(ErrorCode.NOT_GAME_PARTICIPANT);

        if (itemId == null) {
            // 기본 스킨으로 되돌림(흑은 흑·백은 백 기본 외형)
            equipItemUseCase.unequip(userId, ItemType.STONE_SKIN);
        } else {
            // 보유·타입(STONE_SKIN) 검증은 샵 유스케이스가 수행한다(유료재화 보호).
            equipItemUseCase.equipStoneSkin(userId, itemId);
        }

        // 코스메틱 포함 방 상태를 양쪽 클라이언트에 즉시 반영
        RoomResponse response = buildRoomResponse(room, null);
        roomEventPublisherPort.publishStatus(roomCode, response);
        return response;
    }

    @Override
    @Transactional
    public RoomResponse swapColors(String roomCode, Long userId) {
        Room room = findRoom(roomCode);

        if (!room.isHost(userId)) throw new OmokException(ErrorCode.ROOM_NOT_HOST);
        // 대기 중에만 — 진행 중에 색이 바뀌면 착수 판정/기보/피지컬 세션이 전부 어긋난다.
        if (!room.isWaiting()) throw new OmokException(ErrorCode.COLOR_SWAP_NOT_AVAILABLE);

        List<GamePlayer> participants = loadGamePlayerPort.findByRoomId(room.getId()).stream()
                .filter(GamePlayer::isParticipant).toList();
        // 두 참가자가 모두 있을 때만 맞바꾼다 — 프론트도 이때만 버튼을 노출한다.
        if (participants.size() < 2) throw new OmokException(ErrorCode.NOT_ENOUGH_PLAYERS);

        participants.forEach(gp -> {
            gp.swapColor();
            saveGamePlayerPort.save(gp);
        });

        RoomResponse response = buildRoomResponse(room, null);
        roomEventPublisherPort.publishStatus(roomCode, response);
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
        // 회원 대 회원 대국만 레이팅·전적 반영(봇·게스트 대국은 기록만 남기고 미집계)
        if (game.isRated()) {
            winner.recordWin();
            if (loser != null) {
                loser.recordLoss();
                EloRating.applyResult(winner, loser, game.getGameType());
            }
        }
        saveGamePort.save(game);
        if (event.replay() != null) savePhysicalReplayPort.save(event.replay()); // 리플레이 영속(게임당 1회)

        loadRoomPort.findByRoomCode(event.roomCode()).ifPresent(room -> {
            room.waitForNextGame();
            saveRoomPort.save(room);
            RoomResponse response = buildRoomResponse(room, game);
            roomEventPublisherPort.publishStatus(event.roomCode(), response);
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
    public Page<RoomResponse> getWaitingRooms(GameType gameType, Boolean ranked, Pageable pageable) {
        return loadRoomPort.findRooms(RoomStatus.WAITING, gameType, ranked, pageable)
                .map(room -> buildRoomResponse(room));
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
                    roomEventPublisherPort.publishStatus(roomCode, buildRoomResponse(room, null));
                }
            } else if (room.isInProgress()) {
                // 게임 중: 30초 유예, 최대 2번
                String nickname = gp.getUser().getNickname();
                roomEventPublisherPort.publishNotice(
                        roomCode, nickname + "님의 연결이 끊겼습니다. 30초 내 재접속하지 않으면 패배 처리됩니다.");
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
        if (participants.size() >= 2) {
            ensureDifferentStoneSkins(participants.get(0), participants.get(1));
        }

        // 색상 교체
        participants.forEach(gp -> {
            gp.swapColor();
            gp.resetTimeForRematch(room.getTimeLimit().getSeconds());
            saveGamePlayerPort.save(gp);
        });

        // 새 판 시작 — 지난 판에서 누적된 연결 끊김 유예 상태(타이머·횟수)를 초기화한다.
        disconnectGraceManager.clearRoom(room.getRoomCode());

        room.startGame();
        saveRoomPort.save(room);

        GamePlayer blackPlayer = participants.stream()
                .filter(gp -> gp.getColor() == StoneColor.BLACK).findFirst().orElseThrow();
        GamePlayer whitePlayer = participants.stream()
                .filter(gp -> gp.getColor() == StoneColor.WHITE).findFirst().orElseThrow();

        Game newGame = Game.start(room, blackPlayer.getUser(), whitePlayer.getUser());
        saveGamePort.save(newGame);

        RoomResponse response = buildRoomResponse(room, newGame);
        roomEventPublisherPort.publishStatus(room.getRoomCode(), response);
        return response;
    }

    /**
     * 계정당 활성 방 1개 제약 — 방을 새로 만들기 전에 호출한다.
     * <p>
     * 이미 방장으로 열어둔 방이 있으면 거부하되, 그 방이 "대기 중이고 나 혼자뿐"이라면
     * 방치된 방으로 보고 조용히 닫은 뒤 진행한다. 이 자동 회수가 없으면
     * 브라우저를 강제 종료하는 등으로 남은 유령 방 때문에 정상 사용자가 새 방을
     * 영영 못 만드는 상황이 생긴다(정리 스케줄러가 돌 때까지 최대 수십 분).
     * <p>
     * 반대로 남이 들어와 있거나 대국 중인 방은 절대 닫지 않는다 —
     * 상대가 기다리던 방이나 진행 중인 대국을 실수로 날리는 것이 훨씬 큰 피해다.
     */
    private void ensureCanHostNewRoom(Long userId) {
        loadRoomPort.findActiveHostedRoom(userId).ifPresent(existing -> {
            // 봇(AI 연습 상대)은 사람이 아니므로 "나 혼자"인지 셀 때 제외한다.
            long humanMembers = loadGamePlayerPort.findByRoomId(existing.getId()).stream()
                    .filter(gp -> !gp.getUser().isBot())
                    .count();

            if (!existing.isWaiting() || humanMembers > 1) {
                throw new OmokException(ErrorCode.ROOM_ALREADY_HOSTING);
            }

            log.info("방치된 방 자동 회수: room={} host={}", existing.getRoomCode(), userId);
            discardEmptyRoom(existing, "새 방을 만들어 이전 방이 닫혔습니다.");
        });
    }

    @Override
    @Transactional
    public int cleanupStaleRooms(int staleMinutes) {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(staleMinutes);
        int closed = 0;

        for (Room room : loadRoomPort.findStaleWaitingRooms(cutoff)) {
            // 아직 누군가 접속해 있는 방은 건너뛴다.
            // (리매치를 기다리며 오래 머무는 정상 방을 닫아버리지 않기 위한 안전장치)
            if (sessionRegistry.hasActiveSession(room.getRoomCode())) continue;

            discardEmptyRoom(room, "오랫동안 사용되지 않아 방이 닫혔습니다.");
            closed++;
        }
        return closed;
    }

    /**
     * 아무도 없는(또는 방장 혼자인) 대기방을 닫는다.
     * <p>
     * {@link #closeRoomByHost} 와 달리 승패·레이팅을 건드리지 않는다 — 대기 중인 방에는
     * 정산할 대국이 없기 때문이다. 다만 데이터 정합성을 위해, 어떤 이유로든 남아 있는
     * 진행 중 게임은 무효(abandon) 처리한다.
     */
    private void discardEmptyRoom(Room room, String noticeMessage) {
        String roomCode = room.getRoomCode();

        room.close();
        saveRoomPort.save(room);

        loadGamePort.findActiveGameByRoomCode(roomCode).ifPresent(game -> {
            game.abandon();
            saveGamePort.save(game);
        });

        deleteGamePlayerPort.deleteByRoomId(room.getId());
        rematchRequests.remove(roomCode);
        disconnectGraceManager.clearRoom(roomCode);

        // 피지컬 방이었다면 메모리 세션도 정리한다(클래식이면 null 이라 무해).
        // 대기 중이라 저장할 리플레이는 없으므로 반환값은 버린다.
        physicalGameLifecycle.stopSession(roomCode);

        roomEventPublisherPort.publishClosed(roomCode, noticeMessage);
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
                // 회원 대 회원 대국만 레이팅·전적 반영(봇·게스트 제외)
                if (game.isRated()) {
                    winner.recordWin();
                    loser.recordLoss();
                    EloRating.applyResult(winner, loser, game.getGameType());
                }
            } else {
                game.abandon();
            }
            saveGamePort.save(game);
        });

        deleteGamePlayerPort.deleteByRoomId(room.getId());
        rematchRequests.remove(roomCode);
        disconnectGraceManager.clearRoom(roomCode);
        // 피지컬 메모리 세션 정리(클래식이면 null) + 그때까지의 리플레이 저장
        PhysicalReplayData replay = physicalGameLifecycle.stopSession(roomCode);
        if (replay != null) savePhysicalReplayPort.save(replay);

        roomEventPublisherPort.publishClosed(roomCode, "방장이 방을 나갔습니다.");
    }

    private void handlePlayerDisconnectDuringGame(Room room, Long userId, String roomCode) {
        loadGamePort.findActiveGameByRoomCode(roomCode).ifPresent(game -> {
            User loser = game.getBlackPlayer().getId().equals(userId)
                    ? game.getBlackPlayer() : game.getWhitePlayer();
            User winner = game.getOpponent(loser.getId());

            game.finish(winner);
            // 회원 대 회원 대국만 레이팅·전적 반영(봇·게스트 제외)
            if (game.isRated()) {
                winner.recordWin();
                loser.recordLoss();
                EloRating.applyResult(winner, loser, game.getGameType());
            }
            saveGamePort.save(game);

            // 패배한 플레이어를 방에서 제거
            loadGamePlayerPort.findByRoomIdAndUserId(room.getId(), userId)
                    .ifPresent(deleteGamePlayerPort::delete);

            room.waitForNextGame();
            saveRoomPort.save(room);

            RoomResponse response = buildRoomResponse(room, game);
            roomEventPublisherPort.publishStatus(roomCode, response);
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

    private void ensureDifferentStoneSkins(GamePlayer first, GamePlayer second) {
        Optional<UserActiveItem> firstSkin = loadUserActiveItemPort
                .findByUserIdAndItemType(first.getUser().getId(), ItemType.STONE_SKIN);
        Optional<UserActiveItem> secondSkin = loadUserActiveItemPort
                .findByUserIdAndItemType(second.getUser().getId(), ItemType.STONE_SKIN);

        if (firstSkin.isPresent() && secondSkin.isPresent()
                && firstSkin.get().getItem().getId().equals(secondSkin.get().getItem().getId())) {
            throw new OmokException(ErrorCode.DUPLICATE_STONE_SKIN);
        }
    }

    private Room findRoom(String roomCode) {
        return loadRoomPort.findByRoomCode(roomCode)
                .orElseThrow(() -> new OmokException(ErrorCode.ROOM_NOT_FOUND));
    }

    private User findUserById(Long userId) {
        return loadUserPort.findById(userId)
                .orElseThrow(() -> new OmokException(ErrorCode.USER_NOT_FOUND));
    }

    /** 한쪽이 봇이면 'AI 연습' 대국 → 레이팅·전적을 집계하지 않는다(결과 표시·리플레이는 정상). */
    private String generateUniqueRoomCode() {
        String code;
        do {
            code = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        } while (loadRoomPort.existsByRoomCode(code));
        return code;
    }
}
