package com.dopamin.omok.game.physical.bot;

import com.dopamin.omok.game.physical.config.PhysicalOmokProperties;
import com.dopamin.omok.game.physical.domain.ItemDrop;
import com.dopamin.omok.game.physical.domain.PhysicalGame;
import com.dopamin.omok.game.physical.domain.PhysicalOmokEngine;
import com.dopamin.omok.game.physical.domain.PhysicalPlayer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 봇 플레이어들을 매 틱 구동한다 — 관측을 만들어 정책({@link PhysicalBotPolicy})에 묻고,
 * 그 결정을 사람 입력과 동일한 엔진 호출로 적용한다(서버 권위·쿨다운 동일 적용).
 * 세션 락 안에서 {@link com.dopamin.omok.game.physical.application.PhysicalGameSessionManager} 가 호출한다.
 *
 * 정책은 인터페이스로 주입되므로, 나중에 딥러닝 구현으로 빈만 교체하면 이 드라이버는 그대로다.
 */
@Component
@RequiredArgsConstructor
public class PhysicalBotDriver {

    private final PhysicalBotPolicy policy;
    private final PhysicalOmokEngine engine;
    private final PhysicalOmokProperties props;

    /** 게임 내 모든 봇을 한 번씩 구동하고, 적용한 결정들을 반환(로깅용). 봇이 없으면 빈 리스트. */
    public List<BotDecision> drive(PhysicalGame game, long now) {
        List<BotDecision> decisions = new ArrayList<>();
        for (PhysicalPlayer player : game.players()) {
            if (!player.isBot()) continue;
            BotObservation obs = observe(game, player, now);
            BotAction action = policy.decide(obs);
            int px = player.getX(), py = player.getY(); // 행동 직전 위치(로그 앵커)
            apply(game, player, action, now);
            decisions.add(new BotDecision(player.getColor(), action, px, py));
        }
        return decisions;
    }

    private BotObservation observe(PhysicalGame game, PhysicalPlayer player, long now) {
        PhysicalPlayer opp = game.opponentOf(player);
        List<BotObservation.ItemView> items = new ArrayList<>();
        for (ItemDrop d : game.drops()) {
            items.add(new BotObservation.ItemView(d.x(), d.y(), d.type()));
        }
        boolean canPlace = now - player.getLastPlaceAt() >= props.placeCooldownMs();
        boolean canDestroy = now - player.getLastDestroyAt() >= props.destroyCooldownMs();
        return new BotObservation(
                game.board().size(),
                game.winCount(),
                game.board().encode(),
                player.getColor(),
                player.getX(), player.getY(),
                opp != null ? opp.getX() : player.getX(),
                opp != null ? opp.getY() : player.getY(),
                canPlace,
                canDestroy,
                player.isSpeedBoosted(now),
                player.getHeldItem(),
                items);
    }

    private void apply(PhysicalGame game, PhysicalPlayer player, BotAction action, long now) {
        switch (action.kind()) {
            case MOVE -> engine.startMove(game, player, action.direction(), now);
            case PLACE -> {
                engine.stopMove(player);
                engine.place(game, player, now);
            }
            case DESTROY -> {
                engine.stopMove(player);
                engine.destroy(game, player, now);
            }
            case USE_ITEM -> engine.useItem(game, player, now);
            case IDLE -> engine.stopMove(player);
        }
    }
}
