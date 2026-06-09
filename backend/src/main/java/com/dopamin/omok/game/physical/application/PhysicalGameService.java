package com.dopamin.omok.game.physical.application;

import com.dopamin.omok.game.application.dto.CharacterSkinResponse;
import com.dopamin.omok.game.application.dto.StoneSkinResponse;
import com.dopamin.omok.game.application.port.out.LoadStoneSoundPort;
import com.dopamin.omok.game.domain.Game;
import com.dopamin.omok.game.domain.GamePlayer;
import com.dopamin.omok.game.domain.Room;
import com.dopamin.omok.game.domain.StoneColor;
import com.dopamin.omok.game.physical.config.PhysicalOmokProperties;
import com.dopamin.omok.game.physical.domain.PhysicalGame;
import com.dopamin.omok.game.physical.domain.PhysicalItemType;
import com.dopamin.omok.game.physical.domain.PhysicalPlayer;
import com.dopamin.omok.shop.application.port.out.LoadUserActiveItemPort;
import com.dopamin.omok.shop.domain.Item;
import com.dopamin.omok.shop.domain.ItemConfig;
import com.dopamin.omok.shop.domain.ItemType;
import com.dopamin.omok.shop.domain.UserActiveItem;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * RoomService 와 피지컬 런타임(SessionManager) 사이의 얇은 브리지.
 * 시작 시 참가자 색·스킨을 서버 권위로 해석해 세션을 구성하고, 강제 종료 시 세션을 정리한다.
 * 영속/트랜잭션은 다루지 않는다(승패 기록은 PhysicalGameEndedEvent → RoomService 가 처리).
 * 항상 RoomService 의 트랜잭션 안에서 호출되므로 스킨 조회(지연 로딩)가 동작한다.
 */
@Service
@RequiredArgsConstructor
public class PhysicalGameService implements PhysicalGameLifecycle {

    private final PhysicalGameSessionManager manager;
    private final PhysicalOmokProperties props;
    private final LoadUserActiveItemPort loadUserActiveItemPort;
    private final LoadStoneSoundPort loadStoneSoundPort;

    @Override
    public void start(Room room, Game game, List<GamePlayer> participants) {
        int size = props.boardSize();
        PhysicalGame pg = new PhysicalGame(
                room.getRoomCode(), game.getId(), size, props.winCount(), System.currentTimeMillis());

        for (GamePlayer gp : participants) {
            StoneColor color = gp.getColor();
            if (color == null) continue; // 관전자 제외
            Long userId = gp.getUser().getId();
            int[] pos = startPosition(color, size);
            PhysicalPlayer player = new PhysicalPlayer(
                    userId, gp.getUser().getNickname(), color,
                    resolveSkin(userId), resolveCharacter(userId),
                    loadStoneSoundPort.findEquippedStoneSoundKey(userId).orElse(null),
                    pos[0], pos[1]);
            player.hold(PhysicalItemType.REMOVE_STONE); // 시작 시 상대 돌 1개 제거 아이템 보유
            pg.addPlayer(player);
        }
        manager.register(pg);
    }

    @Override
    public void stopSession(String roomCode) {
        manager.stopSession(roomCode);
    }

    /**
     * 중앙 근처에서 서로 가깝게 시작(대각 모서리 아님) — 줄을 까는 곳이 항상 상대 코앞이라
     * 오프닝 직선 러시를 즉시 견제할 수 있다. 같은 중앙 행에서 약 5칸 떨어뜨려 대칭 배치.
     */
    private int[] startPosition(StoneColor color, int size) {
        int mid = size / 2;
        int row = clampCell(mid, size);
        return color == StoneColor.BLACK
                ? new int[]{clampCell(mid - 3, size), row}
                : new int[]{clampCell(mid + 2, size), row};
    }

    private int clampCell(int v, int size) {
        return Math.max(1, Math.min(size - 2, v));
    }

    /** 장착한 STONE_SKIN 색을 서버에서 읽어 내려준다(미보유자는 사용 불가 — RoomService.buildCosmetics 와 동일 권위). */
    private StoneSkinResponse resolveSkin(Long userId) {
        return loadUserActiveItemPort.findByUserIdAndItemType(userId, ItemType.STONE_SKIN)
                .map(UserActiveItem::getItem)
                .map(Item::getItemConfig)
                .map(ItemConfig::stone)
                .map(StoneSkinResponse::from)
                .orElse(null);
    }

    /** 장착한 CHARACTER_SKIN(피지컬 캐릭터 외형)을 서버에서 읽어 내려준다(유료재화 보호, 미장착 null). */
    private CharacterSkinResponse resolveCharacter(Long userId) {
        return loadUserActiveItemPort.findByUserIdAndItemType(userId, ItemType.CHARACTER_SKIN)
                .map(UserActiveItem::getItem)
                .map(Item::getItemConfig)
                .map(ItemConfig::character)
                .map(CharacterSkinResponse::from)
                .orElse(null);
    }
}
