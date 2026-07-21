package com.dopamin.omok.game.physical.application.dto;

import com.dopamin.omok.game.application.dto.CharacterSkinResponse;
import com.dopamin.omok.game.application.dto.StoneSkinResponse;
import com.dopamin.omok.game.domain.StoneColor;
import com.dopamin.omok.game.physical.domain.PhysicalItemType;

import java.util.List;

/**
 * 피지컬 오목 리플레이 데이터(영속/전송 공용).
 * 보드 칸 변화 이벤트만 시간순으로 담아, 클라가 임의 시점의 보드를 재구성한다.
 *
 * trainingLog 는 ML 학습용(서버 전용) 부가 데이터로, 같은 JSON 컬럼에 함께 저장하되
 * 클라이언트로 내보낼 땐 {@link #forClient()} 로 떼어낸다(응답 비대화·데이터 노출 방지).
 * (학습 데이터가 커지면 전용 컬럼/테이블로 분리하는 게 다음 단계.)
 *
 * @param gameId 게임 식별자
 * @param boardSize 오목판 한 변의 칸 수
 * @param winCount 승리에 필요한 연속 돌 개수 (추후 룰 수정 대비)
 * @param durationMs 게임 시작부터 종료까지의 진행 시간(ms)
 * @param winnerColor 승자의 돌 색상. 무효 또는 중단된 게임이면 null
 * @param players 리플레이에 등장하는 플레이어 정보
 * @param events 칸 변화 스트림. v: 0=빈칸, 1=흑, 2=백, 3=분화구 (PhysicalBoard.encode 와 동일)
 * @param motionFrames 캐릭터·아이템 위치 시계열(영상 리플레이용). 일정 간격으로 샘플링한 위치 스냅샷.
 *                     보드 상태는 events 로 재구성하고, 이 트랙으로 캐릭터의 '움직임'을 부드럽게 재생한다.
 *                     구버전 리플레이엔 없어 null — 클라가 단계별 뷰어로 폴백.
 * @param trainingLog (선택) 행동 스트림 학습 로그. 비활성/구버전이면 null
 */
public record PhysicalReplayData(
        Long gameId,
        int boardSize,
        int winCount,
        long durationMs,
        StoneColor winnerColor,    // BLACK | WHITE | null(무효/중단). JSON 으론 "BLACK"/"WHITE" 로 직렬화
        List<PlayerInfo> players,
        List<CellChange> events,
        List<MotionFrame> motionFrames,
        PhysicalTrainingLog trainingLog
) {
    /**
     * 리플레이 등장 플레이어. 스킨/캐릭터는 한 판 동안 불변이라 매 프레임이 아닌 여기 1회만 담는다
     * (영상 리플레이가 라이브와 동일한 외형 — 바둑알 스킨·캐릭터 얼굴 — 으로 보이게 함). 구버전/미장착은 null.
     *
     * @param color 플레이어의 돌 색상
     * @param nickname 플레이어 닉네임
     * @param skin 장착한 바둑알 스킨. 구버전 리플레이이거나 미장착이면 null
     * @param character 장착한 캐릭터 스킨. 구버전 리플레이이거나 미장착이면 null
     */
    public record PlayerInfo(StoneColor color, String nickname, StoneSkinResponse skin, CharacterSkinResponse character) {}

    /**
     * 한 칸의 상태 변화 이벤트.
     *
     * @param t 게임 시작 기준 경과 시간(ms)
     * @param x 변경된 칸의 x좌표
     * @param y 변경된 칸의 y좌표
     * @param v 새 칸 값(0=빈칸, 1=흑, 2=백, 3=분화구)
     */
    public record CellChange(long t, int x, int y, int v) {}

    /**
     * 한 시점의 위치 스냅샷 — 캐릭터들과 필드 아이템의 좌표.
     *
     * @param t 게임 시작 기준 경과 시간(ms)
     * @param players 해당 시점의 플레이어 위치와 상태
     * @param items 해당 시점에 필드에 놓인 아이템 정보
     */
    public record MotionFrame(long t, List<PlayerMotion> players, List<ItemMotion> items) {}

    /**
     * 한 캐릭터의 위치/상태(영상 보간용). heldItem/speedBoosted 는 HUD·부스트 링 표시에 쓸 수 있다.
     *
     * @param color 플레이어의 돌 색상
     * @param x 캐릭터의 x좌표
     * @param y 캐릭터의 y좌표
     * @param heldItem 캐릭터가 보유한 아이템 유형. 미보유이거나 지금은 사라진 구버전 아이템이면 null
     * @param speedBoosted 속도 증가 효과 적용 여부
     */
    public record PlayerMotion(StoneColor color, int x, int y, PhysicalItemType heldItem, boolean speedBoosted) {}

    /**
     * 필드에 떨어진 아이템 위치.
     *
     * @param x 아이템의 x좌표
     * @param y 아이템의 y좌표
     * @param type 아이템 유형. 지금은 사라진 구버전 아이템이면 null
     */
    public record ItemMotion(int x, int y, PhysicalItemType type) {}

    /**
     * 클라이언트 전송용 사본 — 서버 전용 학습 로그만 제거한다(영상 트랙은 유지).
     *
     * @return 학습 로그가 제거된 리플레이 데이터. 학습 로그가 없으면 현재 객체
     */
    public PhysicalReplayData forClient() {
        return trainingLog == null
                ? this
                : new PhysicalReplayData(gameId, boardSize, winCount, durationMs, winnerColor,
                        players, events, motionFrames, null);
    }
}
