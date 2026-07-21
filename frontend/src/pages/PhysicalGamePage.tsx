import { useState, useEffect, useCallback, useRef } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { gameApi } from '@/api/game';
import { useAuthStore } from '@/store/authStore';
import { useWebSocket } from '@/hooks/useWebSocket';
import { useChat } from '@/hooks/useChat';
import { useLeaveGuard } from '@/hooks/useLeaveGuard';
import { useStoneSoundPlayer } from '@/hooks/useStoneSoundPlayer';
import { useBackgroundMusic } from '@/hooks/useBackgroundMusic';
import { useTouchUI } from '@/hooks/useTouchUI';
import { areSameStoneSkin, stonePreviewStyle } from '@/utils/stoneSkin';
import { playSfx, SfxName } from '@/utils/sfx';
import ChatPanel from '@/components/game/ChatPanel';
import Joystick from '@/components/common/Joystick';
import GameResultOverlay, { GameResult } from '@/components/game/GameResultOverlay';
import {
  Room,
  ApiResponse,
  NoticePayload,
  PhysicalSnapshot,
  PhysicalItemType,
  StoneColor,
  Direction,
  PhysicalDirection,
} from '@/types';
import {
  ITEM_META,
  ITEM_FX_DUR,
  drawBoardBase,
  drawStone,
  drawCrater,
  drawCharacter,
  drawItemDrop,
  drawItemFx,
} from '@/utils/physicalCanvas';
import styles from './PhysicalGamePage.module.css';

const CANVAS_PX = 600;
const PHYSICAL_BGM_SRC = '/audio/physical-omok-bgm.mp3';

// 아이템 사용 시 효과음(고유)
const USE_SFX: Record<PhysicalItemType, SfxName> = {
  SPEED_BOOST: 'use_speed',
  CRATER: 'use_crater',
  BOMB: 'use_bomb',
};

const KEY_DIR: Record<string, Direction> = {
  ArrowUp: 'UP',
  ArrowDown: 'DOWN',
  ArrowLeft: 'LEFT',
  ArrowRight: 'RIGHT',
};

/**
 * 누르고 있는 방향키들을 실제로 보낼 한 방향으로 합친다.
 * 세로/가로를 하나씩 잡고 있으면 대각(UP_RIGHT 등), 한 축만이면 그 축, 같은 축을 둘 다면 나중에 누른 쪽.
 * 조이스틱의 8방향과 키보드 조작을 맞추기 위한 규칙이다.
 */
const combineKeys = (pressed: Direction[]): PhysicalDirection | null => {
  let vertical: Direction | null = null;
  let horizontal: Direction | null = null;
  for (const dir of pressed) {
    if (dir === 'UP' || dir === 'DOWN') vertical = dir; // 나중에 누른 것이 덮어쓴다
    else horizontal = dir;
  }
  if (vertical && horizontal) return `${vertical}_${horizontal}` as PhysicalDirection;
  return vertical ?? horizontal;
};

// 직전 스냅샷 대비 '새로 놓인 돌'의 색을 찾는다(착수음 트리거용). 없으면 null.
const detectPlacedColor = (prev: PhysicalSnapshot, snap: PhysicalSnapshot): StoneColor | null => {
  for (let y = 0; y < snap.cells.length; y++) {
    const prevRow = prev.cells[y];
    const row = snap.cells[y];
    if (!prevRow || !row) continue;
    for (let x = 0; x < row.length; x++) {
      const v = row[x];
      if ((v === 1 || v === 2) && prevRow[x] !== 1 && prevRow[x] !== 2) {
        return v === 1 ? 'BLACK' : 'WHITE';
      }
    }
  }
  return null;
};

// ===== 캔버스 그리기 헬퍼(순수) — 돌/분화구/캐릭터/아이템은 @/utils/physicalCanvas 공용 =====

// 충전 중인 완성 오목 '게이지' — 줄의 '모든 돌'이 아래에서 위로 색이 차오르며(액체 채움) + 전체 링/연결 글로우.
// progress(0..1)만큼 돌 전부가 함께 채워지고, 다 차면(서버가 파괴) drawClearFx 로 넘어간다.
// 내 라인은 초록(곧 득점), 상대 라인은 빨강(끊어야 함)으로 색을 구분한다.
const drawGauge = (
  ctx: CanvasRenderingContext2D,
  pts: { x: number; y: number }[],
  stoneR: number,
  progress: number,
  mine: boolean,
) => {
  if (pts.length < 2) return;
  const p = Math.max(0, Math.min(1, progress));
  const rgb = mine ? '110,224,160' : '255,122,146';
  const a = pts[0];
  const b = pts[pts.length - 1];
  const near = p > 0.82; // 임박 — 깜빡여 긴장감
  const pulse = near ? 0.65 + 0.35 * Math.abs(Math.sin(performance.now() / 90)) : 1;
  ctx.save();
  ctx.lineCap = 'round';
  // 연결 글로우 — 어떤 돌들이 한 줄인지 한눈에
  ctx.shadowColor = `rgba(${rgb},0.85)`;
  ctx.shadowBlur = (near ? 14 : 9) * pulse;
  ctx.strokeStyle = `rgba(${rgb},${0.4 * pulse})`;
  ctx.lineWidth = stoneR * 0.55;
  ctx.beginPath();
  ctx.moveTo(a.x, a.y);
  ctx.lineTo(b.x, b.y);
  ctx.stroke();
  ctx.shadowBlur = 0;

  // 돌마다: 아래→위로 차오르는 색 채움(클립) + 전체 외곽 링(항상 보여 완성 라인 강조)
  for (const q of pts) {
    const r = stoneR + 1;
    // 채움(원으로 클립한 뒤 아래에서 progress 높이만큼 사각형)
    ctx.save();
    ctx.beginPath();
    ctx.arc(q.x, q.y, r, 0, Math.PI * 2);
    ctx.clip();
    const fillTop = q.y + r - 2 * r * p;
    ctx.fillStyle = `rgba(${rgb},${0.45 + 0.4 * p})`; // 찰수록 진하게
    ctx.fillRect(q.x - r, fillTop, 2 * r, q.y + r - fillTop);
    ctx.restore();
    // 외곽 링
    ctx.strokeStyle = `rgba(${rgb},${0.9 * pulse})`;
    ctx.lineWidth = near ? 3.5 : 2.5;
    ctx.beginPath();
    ctx.arc(q.x, q.y, r + 1.5, 0, Math.PI * 2);
    ctx.stroke();
  }
  ctx.restore();
};

// 게이지가 다 차서 라인이 파괴되는 순간의 특수효과 — 작은 플래시 + 스파클(글자 없음, 보드를 가리지 않게 절제).
// t(0..1) 진행도에 따라 그려지며, 호출 측이 만료된 효과를 제거한다(상태 없음, 매 프레임 t로만 결정).
const CLEAR_FX_DUR = 600;
const drawClearFx = (
  ctx: CanvasRenderingContext2D,
  pts: { x: number; y: number }[],
  stoneR: number,
  t: number,
  mine: boolean,
) => {
  if (pts.length === 0) return;
  const ease = 1 - Math.pow(1 - t, 3); // ease-out
  const alpha = 1 - t;
  const tint = mine ? '110,224,160' : '255,176,80'; // 내 득점은 초록빛, 상대 득점은 주황빛
  ctx.save();
  for (let i = 0; i < pts.length; i++) {
    const p = pts[i];
    // 칸별 작은 팽창 링(돌 한 칸 범위 — 이웃을 덮지 않게 제한)
    ctx.strokeStyle = `rgba(${tint},${0.8 * alpha})`;
    ctx.lineWidth = 2.5;
    ctx.shadowColor = `rgba(${tint},${alpha})`;
    ctx.shadowBlur = 12 * alpha;
    ctx.beginPath();
    ctx.arc(p.x, p.y, stoneR * (0.5 + ease * 0.9), 0, Math.PI * 2);
    ctx.stroke();
    // 중심 플래시(초반만 — 돌이 '터지는' 느낌)
    if (t < 0.5) {
      ctx.shadowBlur = 0;
      ctx.fillStyle = `rgba(255,255,255,${(0.5 - t) * 1.3})`;
      ctx.beginPath();
      ctx.arc(p.x, p.y, stoneR * 0.7 * (1 - t), 0, Math.PI * 2);
      ctx.fill();
    }
    // 스파클 파티클(6방향으로 짧게 흩어짐)
    ctx.shadowBlur = 0;
    for (let k = 0; k < 6; k++) {
      const ang = (k / 6) * Math.PI * 2 + i * 0.7;
      const dist = stoneR * (0.4 + ease * 1.6);
      ctx.fillStyle = `rgba(${tint},${alpha})`;
      ctx.beginPath();
      ctx.arc(p.x + Math.cos(ang) * dist, p.y + Math.sin(ang) * dist, Math.max(1, stoneR * 0.16 * (1 - t)), 0, Math.PI * 2);
      ctx.fill();
    }
  }
  ctx.restore();
};

const PhysicalGamePage = () => {
  const { gameId: roomCode } = useParams<{ gameId: string }>();
  const navigate = useNavigate();
  const { user } = useAuthStore();

  const [room, setRoom] = useState<Room | null>(null);
  const [snapshot, setSnapshot] = useState<PhysicalSnapshot | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [closedMessage, setClosedMessage] = useState('');
  const [graceNotice, setGraceNotice] = useState<string | null>(null);
  const [gameResult, setGameResult] = useState<GameResult | null>(null);
  const [gameResultDisplayText, setGameResultDisplayText] = useState('');
  const [gameResultEffect, setGameResultEffect] = useState<string | null>(null);

  const canvasRef = useRef<HTMLCanvasElement>(null);
  const snapshotRef = useRef<PhysicalSnapshot | null>(null);
  const prevSnapRef = useRef<PhysicalSnapshot | null>(null);
  const myColorRef = useRef<StoneColor | null>(null);
  const lastBoardSizeRef = useRef(14); // 스냅샷이 없을 때(로딩/종료 후) 빈 판을 그릴 크기
  const roomStatusRef = useRef<string | undefined>(undefined); // 렌더 게이트: IN_PROGRESS 가 아니면 빈 판
  const renderPosRef = useRef<Record<string, { x: number; y: number }>>({});
  // 진행 중인 라인 제거(득점) 특수효과들 — scoreEventId 증가 시 추가되고, 렌더 루프가 만료된 것을 제거한다.
  const clearFxRef = useRef<{ cells: number[][]; color: StoneColor | null; start: number }[]>([]);
  // 아이템 획득/사용 순간 이펙트 — heldItem 변화 감지 시 추가, 렌더 루프가 만료된 것을 제거한다.
  const itemFxRef = useRef<{ kind: 'pickup' | 'use'; item: PhysicalItemType; x: number; y: number; start: number }[]>([]);
  const pressedRef = useRef<Direction[]>([]); // 누르고 있는 방향키(축 단위) — 대각은 combineKeys 가 만든다
  const activeDirRef = useRef<PhysicalDirection | null>(null);
  const prevGameIdRef = useRef<number | undefined>(undefined);
  const prevRoomStatusRef = useRef<string | undefined>(undefined);
  const resultTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const closedTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  const chat = useChat();
  const playStoneSound = useStoneSoundPlayer();

  // 파생
  const myPlayer = room?.players.find((p) => p.userId === user?.id) ?? null;
  const blackPlayer = room?.players.find((p) => p.color === 'BLACK' && p.role !== 'SPECTATOR') ?? null;
  const whitePlayer = room?.players.find((p) => p.color === 'WHITE' && p.role !== 'SPECTATOR') ?? null;
  const playerRolePlayer = room?.players.find((p) => p.role === 'PLAYER') ?? null;
  const opponentRoomPlayer = room?.players.find((p) => p.role !== 'SPECTATOR' && p.userId !== user?.id) ?? null;
  const spectators = room?.players.filter((p) => p.role === 'SPECTATOR') ?? [];
  const currentGame = room?.currentGame ?? null;
  const isHost = myPlayer?.role === 'HOST';
  const isPlayerRole = myPlayer?.role === 'PLAYER';
  const myColor: StoneColor | null = myPlayer?.color ?? null;
  const sameStoneSkin = areSameStoneSkin(blackPlayer?.stoneSkin, whitePlayer?.stoneSkin);
  const isInProgress = room?.status === 'IN_PROGRESS' && currentGame?.status === 'IN_PROGRESS';
  const controllable = !!(room?.status === 'IN_PROGRESS' && myColor);
  // 가상 컨트롤/안내문구: 터치 기기 '또는' 좁은 화면이면 노출.
  // 순수 터치 판정((hover:none) and (pointer:coarse))만 쓰면 인앱 브라우저·데스크톱 모드·
  // 키보드/펜 연결 기기에서 false 가 되어 폰인데도 컨트롤이 안 뜨는 사고가 나므로 화면 폭을 안전망으로 둔다.
  // 키보드가 있는 기기라면 컨트롤이 떠 있어도 키보드 입력이 그대로 동작한다.
  const touchDevice = useTouchUI();
  // 컨트롤은 대기 중에도 띄운다 — 시작 전에 조작법을 눈으로 익히고, 시작 순간 레이아웃이 밀리지 않게.
  // 실제 입력은 controllable 일 때만 나간다.
  const showTouchControls = !!(touchDevice && myPlayer && myPlayer.role !== 'SPECTATOR');

  // BGM은 게임이 진행 중일 때만 재생(대기 중엔 무음)
  useBackgroundMusic(PHYSICAL_BGM_SRC, { enabled: !!isInProgress });

  const { leaveRoom } = useLeaveGuard({
    blockActive: !!(myPlayer && room && room.status !== 'CLOSED' && !closedMessage),
    warnActive: !!(myPlayer && room && !closedMessage),
    isHost,
    roomCode: roomCode!,
  });

  // ===== WebSocket 핸들러 =====
  const handleRoomStatus = useCallback((res: ApiResponse<Room>) => {
    if (res.success && res.data) {
      setRoom(res.data);
      setGraceNotice(null);
    }
  }, []);

  const handleNotice = useCallback((res: NoticePayload) => {
    if (res.message) setGraceNotice(res.message);
  }, []);

  const handleRoomClosed = useCallback(
    (res: NoticePayload) => {
      setClosedMessage(res.message ?? '방장이 방을 나갔습니다.');
      if (closedTimerRef.current) clearTimeout(closedTimerRef.current);
      closedTimerRef.current = setTimeout(() => navigate('/lobby'), 3000);
    },
    [navigate],
  );

  // 스냅샷 수신: 직전 프레임과 비교해 착수음/아이템 효과음을 트리거한다(서버 권위 상태 기반).
  const handleSnapshot = useCallback((snap: PhysicalSnapshot) => {
    const prev = prevSnapRef.current;
    if (prev && prev.status === 'IN_PROGRESS' && snap.status === 'IN_PROGRESS') {
      // (2) 착수음: 새로 놓인 돌의 색 → 그 플레이어의 장착 착수음(없으면 합성 클릭)
      const placed = detectPlacedColor(prev, snap);
      if (placed) {
        const owner = snap.players.find((p) => p.color === placed);
        if (owner?.soundAssetKey) playStoneSound(owner.soundAssetKey);
        else playSfx('place');
      }
      // (3) 아이템 효과음 + 화면 이펙트: 보유 아이템 변화 감지(획득/사용을 양쪽 다 시각화)
      const fxNow = performance.now();
      for (const p of snap.players) {
        const before = prev.players.find((q) => q.color === p.color)?.heldItem ?? null;
        const after = p.heldItem ?? null;
        if (before === null && after !== null) {
          itemFxRef.current.push({ kind: 'pickup', item: after, x: p.x, y: p.y, start: fxNow });
          if (p.color === myColorRef.current) playSfx('pickup'); // 사운드는 내 획득만(소음 최소화)
        } else if (before !== null && after === null) {
          itemFxRef.current.push({ kind: 'use', item: before, x: p.x, y: p.y, start: fxNow });
          playSfx(USE_SFX[before]); // 사용은 양쪽 다 들림(상대 폭탄 등)
        }
      }
    }
    // 득점(게이지 완충 → 라인 파괴): scoreEventId 증가를 감지해 파괴된 줄마다 특수효과 재생(승리 점수 포함).
    if (prev && snap.scoreEventId > prev.scoreEventId && snap.lastClearedLines?.length) {
      const startedAt = performance.now();
      for (const line of snap.lastClearedLines) {
        clearFxRef.current.push({ cells: line.cells, color: line.color, start: startedAt });
      }
      playSfx('score');
    }
    lastBoardSizeRef.current = snap.boardSize;
    prevSnapRef.current = snap;
    snapshotRef.current = snap;
    setSnapshot(snap);
  }, [playStoneSound]);

  const noop = useCallback(() => {}, []);

  const { sendChat, sendReady, sendStart, sendPhysicalInput, sendPhysicalSurrender } = useWebSocket({
    roomCode: roomCode!,
    onMove: noop,
    onRoomStatus: handleRoomStatus,
    onRoomClosed: handleRoomClosed,
    onChat: chat.onChatMessage,
    onNotice: handleNotice,
    onPhysicalSnapshot: handleSnapshot,
  });

  // 초기 방 로드
  useEffect(() => {
    gameApi
      .getRoom(roomCode!)
      .then((res) => {
        if (res.data.data) setRoom(res.data.data);
        else navigate('/lobby');
      })
      .catch(() => navigate('/lobby'))
      .finally(() => setIsLoading(false));
  }, [roomCode, navigate]);

  // 내 색을 ref로 동기화(스냅샷 핸들러를 재생성하지 않고 참조 — WS 재구독 방지)
  useEffect(() => {
    myColorRef.current = myColor;
  }, [myColor]);

  // 방 상태를 ref로 동기화 — 렌더 루프가 'IN_PROGRESS 일 때만 보드 렌더'하는 게이트로 쓴다.
  // (스냅샷/상태가 서로 다른 토픽이라 도착 순서가 엇갈려도, 대기 상태면 항상 빈 판을 보여 보드가 초기화된다.)
  useEffect(() => {
    roomStatusRef.current = room?.status;
  }, [room?.status]);

  // 새 게임 시작 시 캐릭터 보간 위치 초기화 + 결과 리셋
  useEffect(() => {
    if (currentGame?.id !== undefined) {
      if (prevGameIdRef.current !== undefined && currentGame.id !== prevGameIdRef.current) {
        renderPosRef.current = {};
        clearFxRef.current = [];
        itemFxRef.current = [];
        setGameResult(null);
        if (resultTimerRef.current) clearTimeout(resultTimerRef.current);
      }
      prevGameIdRef.current = currentGame.id;
    }
  }, [currentGame?.id]);

  // 게임 종료(IN_PROGRESS → WAITING) 시 결과 표시(클래식과 동일 패턴, /status 기반)
  useEffect(() => {
    if (!room) return;
    const wasInProgress = prevRoomStatusRef.current === 'IN_PROGRESS';
    prevRoomStatusRef.current = room.status;
    if (!wasInProgress || room.status !== 'WAITING') return;

    const game = room.currentGame;
    const isActivePlayer = myPlayer?.role === 'HOST' || myPlayer?.role === 'PLAYER';
    if (isActivePlayer && game) {
      if (game.status === 'FINISHED' || game.status === 'ABANDONED') {
        const isWin = game.winner?.id === user?.id;
        setGameResult(isWin ? 'WIN' : 'LOSS');
        setGameResultDisplayText(isWin ? '승리!' : (game.winnerDefeatMessage ?? '패배'));
        // 승자가 장착한 이펙트(승/패 공용) — 승자는 승리 연출, 패자는 패배 연출. 미장착이면 문구만.
        setGameResultEffect(game.winnerDefeatEffect ?? null);
      }
    }
    // 보드 초기화: 종료된 판의 마지막 스냅샷을 버려 캔버스를 깨끗한 빈 판으로 되돌린다(준비창 복귀).
    // 다음 판이 시작되면 register 의 첫 스냅샷이 다시 채운다.
    snapshotRef.current = null;
    prevSnapRef.current = null;
    renderPosRef.current = {};
    clearFxRef.current = [];
    itemFxRef.current = [];
    setSnapshot(null);
    if (resultTimerRef.current) clearTimeout(resultTimerRef.current);
    resultTimerRef.current = setTimeout(() => {
      setGameResult(null);
      setGameResultDisplayText('');
    }, 3500);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [room?.status]);

  // 입장 직후엔 항상 오목판(맨 위)부터 보이게 한다.
  // 모바일은 세로 배치라 로비에서 스크롤해 둔 위치가 남으면 아래쪽 채팅창이 먼저 보인다.
  useEffect(() => {
    window.scrollTo(0, 0);
  }, []);

  // 키보드 입력(참가자 + 진행 중일 때만)
  useEffect(() => {
    if (!controllable) return;

    const isTyping = (t: EventTarget | null) =>
      t instanceof HTMLElement && (t.tagName === 'INPUT' || t.tagName === 'TEXTAREA');

    const recomputeDir = () => {
      const next = combineKeys(pressedRef.current);
      if (next === activeDirRef.current) return;
      activeDirRef.current = next;
      if (next) sendPhysicalInput('MOVE_START', next);
      else sendPhysicalInput('MOVE_STOP');
    };

    const onKeyDown = (e: KeyboardEvent) => {
      if (isTyping(e.target)) return;
      const dir = KEY_DIR[e.key];
      if (dir) {
        e.preventDefault();
        if (!pressedRef.current.includes(dir)) {
          pressedRef.current.push(dir);
          recomputeDir();
        }
        return;
      }
      if (e.repeat) return; // 단발 액션은 키 반복 무시
      if (e.code === 'Space') {
        e.preventDefault();
        sendPhysicalInput('PLACE');
      } else if (e.code === 'KeyX') {
        // 파괴: X (상대 돌 부수기)
        e.preventDefault();
        sendPhysicalInput('DESTROY');
      } else if (e.code === 'KeyC') {
        // 아이템 사용: C
        e.preventDefault();
        sendPhysicalInput('USE_ITEM');
      }
    };

    const onKeyUp = (e: KeyboardEvent) => {
      const dir = KEY_DIR[e.key];
      if (!dir) return;
      pressedRef.current = pressedRef.current.filter((d) => d !== dir);
      recomputeDir();
    };

    window.addEventListener('keydown', onKeyDown);
    window.addEventListener('keyup', onKeyUp);
    return () => {
      window.removeEventListener('keydown', onKeyDown);
      window.removeEventListener('keyup', onKeyUp);
      pressedRef.current = [];
      if (activeDirRef.current) {
        activeDirRef.current = null;
        sendPhysicalInput('MOVE_STOP');
      }
    };
  }, [controllable, sendPhysicalInput]);

  // 이 페이지에 있는 동안 브라우저 핀치줌 차단.
  // 패드를 밀면서 착수/파괴 버튼을 같이 누르는 게임이라, 두 손가락이 닿는 순간
  // 브라우저가 확대 제스처로 가로채면 컨트롤이 끊긴다. 두 손가락 이상일 때만 막으므로
  // 한 손가락 스크롤은 그대로 된다. 데스크톱은 터치 이벤트 자체가 없어 영향 없음.
  useEffect(() => {
    const blockMultiTouch = (e: TouchEvent) => {
      if (e.touches.length > 1) e.preventDefault();
    };
    const blockGesture = (e: Event) => e.preventDefault(); // iOS 사파리 전용 핀치 이벤트
    document.addEventListener('touchstart', blockMultiTouch, { passive: false });
    document.addEventListener('touchmove', blockMultiTouch, { passive: false });
    document.addEventListener('gesturestart', blockGesture);
    document.addEventListener('gesturechange', blockGesture);
    return () => {
      document.removeEventListener('touchstart', blockMultiTouch);
      document.removeEventListener('touchmove', blockMultiTouch);
      document.removeEventListener('gesturestart', blockGesture);
      document.removeEventListener('gesturechange', blockGesture);
    };
  }, []);

  // 캔버스 렌더 루프
  useEffect(() => {
    let raf = 0;
    const render = () => {
      const canvas = canvasRef.current;
      if (canvas) {
        const ctx = canvas.getContext('2d');
        const snap = snapshotRef.current;
        if (ctx) {
          // 진행 중일 때만 실제 아레나를 그린다. 대기/종료 상태면 스냅샷이 남아 있어도 깨끗한 빈 판(보드 초기화).
          if (snap && roomStatusRef.current === 'IN_PROGRESS') drawArena(ctx, snap, myColor);
          else drawBoardBase(ctx, lastBoardSizeRef.current, CANVAS_PX);
        }
      }
      raf = requestAnimationFrame(render);
    };
    raf = requestAnimationFrame(render);
    return () => cancelAnimationFrame(raf);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [myColor]);

  // 언마운트 타이머 정리
  useEffect(() => {
    return () => {
      if (resultTimerRef.current) clearTimeout(resultTimerRef.current);
      if (closedTimerRef.current) clearTimeout(closedTimerRef.current);
    };
  }, []);

  const drawArena = (ctx: CanvasRenderingContext2D, snap: PhysicalSnapshot, mine: StoneColor | null) => {
    const N = snap.boardSize;
    const { gap, at } = drawBoardBase(ctx, N, CANVAS_PX);
    const stoneR = gap * 0.46;

    // 분화구 + 돌 (교차점 기준)
    for (let y = 0; y < N; y++) {
      for (let x = 0; x < N; x++) {
        const v = snap.cells[y][x];
        const cx = at(x), cy = at(y);
        if (v === 3) {
          drawCrater(ctx, cx, cy, gap);
        } else if (v === 1 || v === 2) {
          const color: StoneColor = v === 1 ? 'BLACK' : 'WHITE';
          const owner = snap.players.find((pl) => pl.color === color);
          drawStone(ctx, cx, cy, stoneR, v === 1, owner?.skin ?? null);
        }
      }
    }

    // 충전 중인 완성 라인들을 게이지로 표시(여러 줄 동시 가능) — settleMs 기준 충전 비율로 채워짐
    for (const line of snap.pendingLines) {
      if (line.cells.length < 2) continue;
      const remaining = line.lockAt - snap.serverTime;
      const progress = snap.settleMs > 0 ? 1 - remaining / snap.settleMs : 1;
      const pts = line.cells.map(([px, py]) => ({ x: at(px), y: at(py) }));
      drawGauge(ctx, pts, stoneR, progress, line.color === mine);
    }

    // 득점(라인 제거) 특수효과 — 만료된 것은 걸러내고 진행 중인 것만 그린다.
    if (clearFxRef.current.length > 0) {
      const fxNow = performance.now();
      clearFxRef.current = clearFxRef.current.filter((fx) => fxNow - fx.start < CLEAR_FX_DUR);
      for (const fx of clearFxRef.current) {
        const t = (fxNow - fx.start) / CLEAR_FX_DUR;
        const pts = fx.cells.map(([px, py]) => ({ x: at(px), y: at(py) }));
        drawClearFx(ctx, pts, stoneR, t, fx.color === mine);
      }
    }

    // 아이템 드롭 — 종류별 모양/색으로 구분(부스트=다이아 / 붕괴=육각 / 폭탄=원)
    for (const it of snap.items) {
      drawItemDrop(ctx, at(it.x), at(it.y), gap, it.type);
    }

    // 캐릭터(보간 이동)
    for (const p of snap.players) {
      const tx = at(p.x), ty = at(p.y);
      const rp = renderPosRef.current[p.color] ?? { x: tx, y: ty };
      rp.x += (tx - rp.x) * 0.28;
      rp.y += (ty - rp.y) * 0.28;
      renderPosRef.current[p.color] = rp;
      drawCharacter(ctx, rp.x, rp.y, gap, p, p.color === mine);
    }

    // 아이템 획득/사용 이펙트 — 만료된 건 거르고 진행 중인 것만 맨 위에 덧그린다.
    if (itemFxRef.current.length > 0) {
      const fxNow = performance.now();
      itemFxRef.current = itemFxRef.current.filter((fx) => fxNow - fx.start < ITEM_FX_DUR);
      for (const fx of itemFxRef.current) {
        const t = (fxNow - fx.start) / ITEM_FX_DUR;
        drawItemFx(ctx, at(fx.x), at(fx.y), gap, fx.kind, fx.item, t);
      }
    }
  };

  if (isLoading) return <div className={styles.loading}>게임을 불러오는 중...</div>;
  if (!room) return null;

  const me = myColor ? snapshot?.players.find((p) => p.color === myColor) ?? null : null;
  const opp = snapshot?.players.find((p) => p.color !== myColor) ?? null;
  const targetScore = snapshot?.targetScore ?? 3;
  // 충전 중인 라인을 내 줄 / 상대 줄로 분류해 배너에 쓴다(여러 줄 동시 가능).
  const myPendingCount = snapshot?.pendingLines.filter((l) => l.color === myColor).length ?? 0;
  const foePendingCount = snapshot?.pendingLines.filter((l) => l.color !== myColor).length ?? 0;
  // 상대의 충전 줄이 '승리 점수'면 매치포인트(끊지 못하면 짐).
  const foeIsMatchPoint = !!(opp && foePendingCount > 0 && opp.score + 1 >= targetScore);
  const myIsMatchPoint = !!(me && myPendingCount > 0 && me.score + 1 >= targetScore);
  const myHudSkin = myPlayer?.stoneSkin ?? me?.skin ?? null;
  const opponentHudSkin = opponentRoomPlayer?.stoneSkin ?? opp?.skin ?? null;
  const destroyRemaining =
    me && snapshot ? Math.max(0, (me.destroyReadyAt - snapshot.serverTime) / 1000) : 0;

  // 시작 카운트다운: 보드/캐릭터가 세팅된 채 serverTime < playStartAt 인 동안 큰 숫자(3·2·1)를 띄운다.
  const inCountdown = !!(
    snapshot && snapshot.status === 'IN_PROGRESS' && snapshot.serverTime < snapshot.playStartAt
  );
  const countdownNum = inCountdown
    ? Math.max(1, Math.ceil((snapshot!.playStartAt - snapshot!.serverTime) / 1000))
    : 0;

  const handleSurrender = () => {
    if (window.confirm('정말 기권하시겠습니까?')) sendPhysicalSurrender();
  };
  const handleSendChat = () => {
    const trimmed = chat.input.trim();
    if (!trimmed) return;
    sendChat(trimmed);
    chat.setInput('');
  };

  // HUD 아이템 슬롯 — 보유 아이템을 종류 색 테두리/글로우로 또렷하게(없으면 빈 슬롯). 내/상대 공용.
  const renderItemSlot = (item: PhysicalItemType | null, label: string) =>
    item ? (
      <span
        className={styles.itemSlot}
        style={{ borderColor: ITEM_META[item].ring, boxShadow: `0 0 9px ${ITEM_META[item].glow}` }}
        title={`${label}: ${ITEM_META[item].label}`}
      >
        <span className={styles.itemSlotIcon}>{ITEM_META[item].emoji}</span>
        <span className={styles.itemSlotName}>{ITEM_META[item].label}</span>
      </span>
    ) : (
      <span className={`${styles.itemSlot} ${styles.itemSlotEmpty}`} title={`${label}: 없음`}>
        <span className={styles.itemSlotIcon}>◌</span>
        <span className={styles.itemSlotName}>비어있음</span>
      </span>
    );

  return (
    <div className={styles.container}>
      {closedMessage && (
        <div className={styles.overlay}>
          <div className={styles.overlayContent}>
            <p>{closedMessage}</p>
            <p className={styles.overlaySubtext}>3초 후 로비로 이동합니다...</p>
          </div>
        </div>
      )}

      <GameResultOverlay result={gameResult} displayText={gameResultDisplayText} effect={gameResultEffect} />

      <div className={styles.arenaArea}>
        <div className={styles.modeBadge}>⚔️ 피지컬 오목</div>

        {/* HUD — 양쪽 플레이어의 보유 아이템을 또렷한 슬롯으로 보여준다 */}
        <div className={styles.hud}>
          <div className={`${styles.hudPlayer} ${myColor ? styles.hudMe : ''}`}>
            <div className={styles.hudTop}>
              <span
                className={`${styles.dot} ${myColor === 'WHITE' ? styles.dotW : styles.dotB}`}
                style={stonePreviewStyle(myHudSkin)}
                title={myHudSkin ? '내 바둑알 스킨' : '내 기본 바둑알'}
              />
              <span className={styles.hudName}>{myPlayer?.nickname ?? '나'}</span>
              {myHudSkin && <span className={styles.skinTag}>스킨</span>}
              {me?.speedBoosted && <span className={styles.boost}>⚡부스트</span>}
            </div>
            {renderItemSlot(me?.heldItem ?? null, '내 아이템')}
          </div>
          <div className={styles.hudCenter}>
            <span className={styles.hudVs}>VS</span>
            <span className={destroyRemaining > 0 ? styles.cdActive : styles.cdReady}>
              파괴: {destroyRemaining > 0 ? `${destroyRemaining.toFixed(1)}초` : '준비됨'}
            </span>
          </div>
          <div className={`${styles.hudPlayer} ${styles.hudPlayerFoe}`}>
            <div className={`${styles.hudTop} ${styles.hudTopFoe}`}>
              <span
                className={`${styles.dot} ${opp?.color === 'WHITE' || opponentRoomPlayer?.color === 'WHITE' ? styles.dotW : styles.dotB}`}
                style={stonePreviewStyle(opponentHudSkin)}
                title={opponentHudSkin ? '상대 바둑알 스킨' : '상대 기본 바둑알'}
              />
              <span className={styles.hudName}>{opp?.nickname ?? opponentRoomPlayer?.nickname ?? '상대'}</span>
              {opponentHudSkin && <span className={styles.skinTag}>스킨</span>}
            </div>
            {renderItemSlot(opp?.heldItem ?? null, '상대 아이템')}
          </div>
        </div>

        {isInProgress && (
          <div className={styles.scoreBoard}>
            <div className={styles.scoreSide}>
              <span className={`${styles.scoreName} ${styles.scoreNameMine}`}>{myPlayer?.nickname ?? '나'}</span>
              {/* key 로 점수가 오를 때마다 리마운트 → pop 애니메이션 재생 */}
              <div className={styles.pips} key={`me-${me?.score ?? 0}`}>
                {Array.from({ length: targetScore }).map((_, i) => (
                  <span key={i} className={`${styles.pip} ${i < (me?.score ?? 0) ? styles.pipMine : ''}`} />
                ))}
              </div>
            </div>
            <span className={styles.scoreVs}>VS</span>
            <div className={`${styles.scoreSide} ${styles.scoreSideRight}`}>
              <div className={styles.pips} key={`op-${opp?.score ?? 0}`}>
                {Array.from({ length: targetScore }).map((_, i) => (
                  <span key={i} className={`${styles.pip} ${i < (opp?.score ?? 0) ? styles.pipFoe : ''}`} />
                ))}
              </div>
              <span className={styles.scoreName}>{opp?.nickname ?? opponentRoomPlayer?.nickname ?? '상대'}</span>
            </div>
          </div>
        )}

        <div className={styles.canvasWrap}>
          <canvas ref={canvasRef} width={CANVAS_PX} height={CANVAS_PX} className={styles.canvas} />
          {graceNotice && <p className={styles.graceNotice}>{graceNotice}</p>}

          {/* 충전 중 라인 안내(슬림 배너) — 상대 줄(끊어야 함)을 우선 표시, 없으면 내 줄 */}
          {foePendingCount > 0 ? (
            <div className={styles.pendingFoe}>
              {foeIsMatchPoint
                ? `⚠ 상대 매치포인트! 끊어라 (${touchDevice ? '파괴/아이템' : 'X/C'})!`
                : `⚠ 상대 오목 완성!${foePendingCount > 1 ? ` ×${foePendingCount}` : ''}! 빨리 끊어요! (${touchDevice ? '파괴/아이템' : 'X/C'})!`}
            </div>
          ) : myPendingCount > 0 ? (
            <div className={styles.pendingMine}>
              {myIsMatchPoint
                ? '⚡ 오목 완성! 버티면 승리!'
                : `⚡ 오목 완성${myPendingCount > 1 ? ` ×${myPendingCount}` : ''}! 버티면 1점!`}
            </div>
          ) : null}

          {inCountdown && (
            <div className={styles.countdownOverlay}>
              {/* key 로 매 숫자마다 리마운트 → pop 애니메이션 재생 */}
              <div key={countdownNum} className={styles.countdownNum}>{countdownNum}</div>
              <div className={styles.countdownHint}>곧 시작합니다!</div>
            </div>
          )}

          {room.status === 'WAITING' && (
            <div className={styles.waitOverlay}>
              <div className={styles.waitCard}>
                <h2 className={styles.waitTitle}>⚔️ 피지컬 오목</h2>
                <p className={styles.waitDesc}>
                  {touchDevice ? (
                    <>
                      패드로 캐릭터를 움직여 <b>⚫ 착수</b>! <b>💥 파괴</b>로 상대 돌을 부수고,
                      필드의 아이템을 주워 <b>🎁 아이템</b>으로 사용하세요.
                    </>
                  ) : (
                    <>
                      방향키로 캐릭터를 움직여 <b>Space</b>로 착수! <b>X</b>로 상대 돌을 부수고,
                      필드의 아이템을 주워 <b>C</b>로 사용하세요.
                    </>
                  )}
                  {' '}<b>오목</b>을 완성할 때마다 1점
                  (완성한 줄만 사라져요)! 먼저 <b>{snapshot?.targetScore ?? 3}점</b>이면 승리!
                </p>
                {!playerRolePlayer && isHost && (
                  <>
                    <p className={styles.inviteText}>방 코드를 공유해 상대를 초대하세요</p>
                    <button className={styles.copyBtn} onClick={() => navigator.clipboard.writeText(room.roomCode)}>
                      코드 복사 ({room.roomCode})
                    </button>
                  </>
                )}
                {myPlayer?.role === 'SPECTATOR' && (
                  <p className={styles.inviteText}>
                    {playerRolePlayer ? '게임 시작을 기다리는 중...' : '플레이어 입장을 기다리는 중...'}
                  </p>
                )}
                {isPlayerRole && (
                  <button
                    className={myPlayer?.ready ? styles.readyOn : styles.readyOff}
                    onClick={() => sendReady()}
                  >
                    {myPlayer?.ready ? '준비 취소' : '준비'}
                  </button>
                )}
                {isHost && playerRolePlayer && (
                  <>
                    {sameStoneSkin && (
                      <p className={styles.skinConflict}>
                        같은 바둑알 스킨은 혼동을 막기 위해 시작할 수 없습니다.
                      </p>
                    )}
                    <button
                      className={styles.startBtn}
                      disabled={!playerRolePlayer.ready || sameStoneSkin}
                      onClick={() => sendStart()}
                      title={sameStoneSkin ? '상대와 다른 바둑알 스킨을 장착해야 시작할 수 있습니다.' : undefined}
                    >
                      게임 시작
                    </button>
                  </>
                )}
              </div>
            </div>
          )}
        </div>

        {/* 터치 화면 전용: 바둑판 바로 밑 가상 컨트롤 (왼쪽 패드 + 오른쪽 액션 버튼) */}
        {showTouchControls && (
          <div
            className={`${styles.touchControls} ${controllable ? '' : styles.touchControlsIdle}`}
            onContextMenu={(e) => e.preventDefault()}
          >
            <Joystick<PhysicalDirection>
              diagonal
              disabled={!controllable}
              onStart={(d) => sendPhysicalInput('MOVE_START', d)}
              onStop={() => sendPhysicalInput('MOVE_STOP')}
            />
            <div className={styles.touchActions}>
              <button
                type="button"
                className={`${styles.actionBtn} ${styles.actionDestroy}`}
                disabled={!controllable}
                onPointerDown={(e) => { e.preventDefault(); sendPhysicalInput('DESTROY'); }}
              >
                💥<span>파괴</span>
              </button>
              <button
                type="button"
                className={`${styles.actionBtn} ${styles.actionItem}`}
                disabled={!controllable}
                onPointerDown={(e) => { e.preventDefault(); sendPhysicalInput('USE_ITEM'); }}
              >
                🎁<span>아이템</span>
              </button>
              <button
                type="button"
                className={`${styles.actionBtn} ${styles.actionPlace}`}
                disabled={!controllable}
                onPointerDown={(e) => { e.preventDefault(); sendPhysicalInput('PLACE'); }}
              >
                ⚫<span>착수</span>
              </button>
            </div>
          </div>
        )}

        <div className={styles.controls}>
          {touchDevice ? (
            <>
              <span>🕹 이동</span>
              <span>⚫ 착수</span>
              <span>💥 파괴</span>
              <span>🎁 아이템</span>
            </>
          ) : (
            <>
              <span><kbd>←↑↓→</kbd> 이동</span>
              <span><kbd>Space</kbd> 착수</span>
              <span><kbd>X</kbd> 파괴</span>
              <span><kbd>C</kbd> 아이템</span>
            </>
          )}
        </div>
      </div>

      <div className={styles.sidebar}>
        <div className={styles.roomInfo}>
          방 코드: <strong>{room.roomCode}</strong>
        </div>

        {isInProgress && myColor && (
          <button onClick={handleSurrender} className={styles.surrenderBtn}>기권</button>
        )}
        <button onClick={() => leaveRoom(!!myPlayer)} className={styles.leaveBtn}>방 나가기</button>

        {spectators.length > 0 && (
          <div className={styles.spectatorPanel}>
            <h3 className={styles.panelTitle}>관전자 ({spectators.length})</h3>
            {spectators.map((s) => (
              <div key={s.userId} className={styles.spectatorItem}>
                👁 {s.nickname}
                {s.userId === user?.id && <span className={styles.badgeMe}>나</span>}
              </div>
            ))}
          </div>
        )}

        <ChatPanel
          messages={chat.messages}
          input={chat.input}
          setInput={chat.setInput}
          onSend={handleSendChat}
          onKeyDown={(e) => {
            if (e.key === 'Enter' && !e.shiftKey) {
              e.preventDefault();
              handleSendChat();
            }
          }}
          myNickname={user?.nickname}
          bottomRef={chat.bottomRef}
        />
      </div>
    </div>
  );
};

export default PhysicalGamePage;
