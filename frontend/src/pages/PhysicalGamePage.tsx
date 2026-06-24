import { useState, useEffect, useCallback, useRef } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { gameApi } from '@/api/game';
import { useAuthStore } from '@/store/authStore';
import { useWebSocket } from '@/hooks/useWebSocket';
import { useChat } from '@/hooks/useChat';
import { useLeaveGuard } from '@/hooks/useLeaveGuard';
import { useStoneSoundPlayer } from '@/hooks/useStoneSoundPlayer';
import { useBackgroundMusic } from '@/hooks/useBackgroundMusic';
import { areSameStoneSkin, stonePreviewStyle } from '@/utils/stoneSkin';
import { playSfx, SfxName } from '@/utils/sfx';
import ChatPanel from '@/components/game/ChatPanel';
import GameResultOverlay, { GameResult } from '@/components/game/GameResultOverlay';
import {
  Room,
  ApiResponse,
  NoticePayload,
  PhysicalSnapshot,
  PhysicalPlayerView,
  PhysicalItemType,
  StoneColor,
  Direction,
} from '@/types';
import styles from './PhysicalGamePage.module.css';

const CANVAS_PX = 600;
const PHYSICAL_BGM_SRC = '/audio/physical-omok-bgm.mp3';

const ITEM_META: Record<PhysicalItemType, { emoji: string; label: string }> = {
  SPEED_BOOST: { emoji: '⚡', label: '이동 부스트' },
  CRATER: { emoji: '🕳️', label: '바둑판 붕괴' },
  BOMB: { emoji: '💣', label: '광역 폭탄' },
};

// 아이템 사용 시 효과음(고유)
const USE_SFX: Record<PhysicalItemType, SfxName> = {
  SPEED_BOOST: 'use_speed',
  CRATER: 'use_crater',
  BOMB: 'use_bomb',
};

// 캐릭터 스킨 face 키워드 → 이모지 (백엔드는 안전 키워드만 저장)
const FACE_EMOJI: Record<string, string> = {
  robot: '🤖',
  rabbit: '🐰',
  ghost: '👻',
  cat: '🐱',
  fox: '🦊',
  bear: '🐻',
};

const KEY_DIR: Record<string, Direction> = {
  ArrowUp: 'UP',
  ArrowDown: 'DOWN',
  ArrowLeft: 'LEFT',
  ArrowRight: 'RIGHT',
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

// ===== 캔버스 그리기 헬퍼(순수) =====
const drawStone = (
  ctx: CanvasRenderingContext2D,
  cx: number,
  cy: number,
  r: number,
  black: boolean,
  skin: PhysicalPlayerView['skin'],
) => {
  const fill = skin?.fill ?? (black ? '#1b1b1b' : '#f4f1e8');
  const stroke = skin?.stroke ?? (black ? '#000000' : '#cfc7b4');
  const shine = skin?.shine ?? (black ? '#5a5a5a' : '#ffffff');
  const grad = ctx.createRadialGradient(cx - r * 0.32, cy - r * 0.32, r * 0.1, cx, cy, r);
  grad.addColorStop(0, shine);
  grad.addColorStop(0.55, fill);
  grad.addColorStop(1, fill);
  ctx.fillStyle = grad;
  ctx.beginPath();
  ctx.arc(cx, cy, r, 0, Math.PI * 2);
  ctx.fill();
  ctx.lineWidth = 1.5;
  ctx.strokeStyle = stroke;
  ctx.stroke();
};

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

// 분화구(바둑판 붕괴) — 들쭉날쭉한 갈색 잔해 + 어두운 구덩이 + 균열. 흑돌과 확실히 구분된다.
const drawCrater = (ctx: CanvasRenderingContext2D, cx: number, cy: number, unit: number) => {
  const r = unit * 0.5;
  ctx.beginPath();
  const spikes = 9;
  for (let k = 0; k <= spikes; k++) {
    const ang = (k / spikes) * Math.PI * 2;
    const rr = r * (k % 2 === 0 ? 1 : 0.62);
    const px = cx + Math.cos(ang) * rr;
    const py = cy + Math.sin(ang) * rr;
    if (k === 0) ctx.moveTo(px, py);
    else ctx.lineTo(px, py);
  }
  ctx.closePath();
  ctx.fillStyle = '#5a3a1c'; // 갈색 잔해 림
  ctx.fill();
  ctx.beginPath();
  ctx.arc(cx, cy, r * 0.62, 0, Math.PI * 2);
  ctx.fillStyle = '#1c120a'; // 어두운 구덩이
  ctx.fill();
  ctx.strokeStyle = 'rgba(20,10,4,0.85)';
  ctx.lineWidth = 1.5;
  for (let k = 0; k < 4; k++) {
    const a = k * (Math.PI / 2) + 0.5;
    ctx.beginPath();
    ctx.moveTo(cx, cy);
    ctx.lineTo(cx + Math.cos(a) * r * 0.95, cy + Math.sin(a) * r * 0.95);
    ctx.stroke();
  }
};

const drawCharacter = (
  ctx: CanvasRenderingContext2D,
  cx: number,
  cy: number,
  unit: number,
  p: PhysicalPlayerView,
  isMe: boolean,
) => {
  const r = unit * 0.42;
  const black = p.color === 'BLACK';
  // 캐릭터 스킨(유료) 우선, 미장착이면 흑/백 기본 외형
  const body = p.character?.body ?? (black ? '#3b3b40' : '#ece7da');
  const accent = p.character?.accent ?? (black ? '#111114' : '#b7af9c');
  const face = p.character ? FACE_EMOJI[p.character.face] ?? '🙂' : null;

  if (p.speedBoosted) {
    ctx.strokeStyle = 'rgba(90,200,255,0.9)';
    ctx.lineWidth = 2;
    ctx.beginPath();
    ctx.arc(cx, cy, r + 7, 0, Math.PI * 2);
    ctx.stroke();
  }
  // 몸체
  ctx.fillStyle = body;
  ctx.beginPath();
  ctx.arc(cx, cy, r, 0, Math.PI * 2);
  ctx.fill();
  ctx.lineWidth = 3;
  ctx.strokeStyle = isMe ? '#e0a83f' : accent;
  ctx.stroke();

  if (face) {
    // 캐릭터 스킨: 이모지 얼굴
    ctx.font = `${Math.floor(r * 1.25)}px serif`;
    ctx.textAlign = 'center';
    ctx.textBaseline = 'middle';
    ctx.fillText(face, cx, cy + 1);
  } else {
    // 기본: 눈 2개
    ctx.fillStyle = black ? '#ffffff' : '#33312b';
    ctx.beginPath();
    ctx.arc(cx - r * 0.34, cy - r * 0.08, r * 0.16, 0, Math.PI * 2);
    ctx.fill();
    ctx.beginPath();
    ctx.arc(cx + r * 0.34, cy - r * 0.08, r * 0.16, 0, Math.PI * 2);
    ctx.fill();
  }

  // 닉네임
  ctx.font = `bold ${Math.floor(unit * 0.32)}px sans-serif`;
  ctx.textAlign = 'center';
  ctx.textBaseline = 'bottom';
  ctx.lineWidth = 3;
  ctx.strokeStyle = 'rgba(0,0,0,0.7)';
  ctx.strokeText(p.nickname, cx, cy - r - 4);
  ctx.fillStyle = isMe ? '#ffd86b' : '#ffffff';
  ctx.fillText(p.nickname, cx, cy - r - 4);
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
  const renderPosRef = useRef<Record<string, { x: number; y: number }>>({});
  // 진행 중인 라인 제거(득점) 특수효과들 — scoreEventId 증가 시 추가되고, 렌더 루프가 만료된 것을 제거한다.
  const clearFxRef = useRef<{ cells: number[][]; color: StoneColor | null; start: number }[]>([]);
  const pressedRef = useRef<Direction[]>([]);
  const activeDirRef = useRef<Direction | null>(null);
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
      // (3) 아이템 효과음: 보유 아이템 변화 감지
      for (const p of snap.players) {
        const before = prev.players.find((q) => q.color === p.color)?.heldItem ?? null;
        const after = p.heldItem ?? null;
        if (before === null && after !== null) {
          if (p.color === myColorRef.current) playSfx('pickup'); // 내 획득만(소음 최소화)
        } else if (before !== null && after === null) {
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

  // 새 게임 시작 시 캐릭터 보간 위치 초기화 + 결과 리셋
  useEffect(() => {
    if (currentGame?.id !== undefined) {
      if (prevGameIdRef.current !== undefined && currentGame.id !== prevGameIdRef.current) {
        renderPosRef.current = {};
        clearFxRef.current = [];
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
    if (resultTimerRef.current) clearTimeout(resultTimerRef.current);
    resultTimerRef.current = setTimeout(() => {
      setGameResult(null);
      setGameResultDisplayText('');
    }, 3500);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [room?.status]);

  // 키보드 입력(참가자 + 진행 중일 때만)
  useEffect(() => {
    if (!controllable) return;

    const isTyping = (t: EventTarget | null) =>
      t instanceof HTMLElement && (t.tagName === 'INPUT' || t.tagName === 'TEXTAREA');

    const recomputeDir = () => {
      const next = pressedRef.current[pressedRef.current.length - 1] ?? null;
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
      } else if (e.key === 'Control') {
        // 파괴: 기본키 Ctrl (상대 돌 부수기)
        e.preventDefault();
        sendPhysicalInput('DESTROY');
      } else if (e.key === 'Shift') {
        // 아이템 사용: Shift
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

  // 캔버스 렌더 루프
  useEffect(() => {
    let raf = 0;
    const render = () => {
      const canvas = canvasRef.current;
      if (canvas) {
        const ctx = canvas.getContext('2d');
        const snap = snapshotRef.current;
        if (ctx) {
          if (snap) drawArena(ctx, snap, myColor);
          else {
            ctx.fillStyle = '#dcb95b';
            ctx.fillRect(0, 0, CANVAS_PX, CANVAS_PX);
          }
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
    const pad = CANVAS_PX / (N + 1);
    const gap = (CANVAS_PX - pad * 2) / (N - 1);
    const at = (i: number) => pad + i * gap; // 그리드 인덱스 → 픽셀(교차점)
    const stoneR = gap * 0.46;

    // 보드 배경
    ctx.fillStyle = '#dcb95b';
    ctx.fillRect(0, 0, CANVAS_PX, CANVAS_PX);

    // 격자선 — 바둑판처럼 선을 긋고 돌·캐릭터는 교차점(점)에 놓는다
    ctx.strokeStyle = '#8b6914';
    ctx.lineWidth = 1;
    const lo = at(0), hi = at(N - 1);
    for (let i = 0; i < N; i++) {
      const c = at(i);
      ctx.beginPath();
      ctx.moveTo(c, lo);
      ctx.lineTo(c, hi);
      ctx.stroke();
      ctx.beginPath();
      ctx.moveTo(lo, c);
      ctx.lineTo(hi, c);
      ctx.stroke();
    }
    // 화점
    ctx.fillStyle = '#8b6914';
    const star = [3, N - 4];
    for (const sx of star) {
      for (const sy of star) {
        ctx.beginPath();
        ctx.arc(at(sx), at(sy), 3, 0, Math.PI * 2);
        ctx.fill();
      }
    }

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

    // 아이템 드롭
    for (const it of snap.items) {
      const cx = at(it.x), cy = at(it.y);
      ctx.fillStyle = 'rgba(255,255,255,0.9)';
      ctx.beginPath();
      ctx.arc(cx, cy, gap * 0.42, 0, Math.PI * 2);
      ctx.fill();
      ctx.strokeStyle = 'rgba(224,168,63,0.95)';
      ctx.lineWidth = 2;
      ctx.stroke();
      ctx.font = `${Math.floor(gap * 0.55)}px serif`;
      ctx.textAlign = 'center';
      ctx.textBaseline = 'middle';
      ctx.fillText(ITEM_META[it.type].emoji, cx, cy + 1);
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

        {/* HUD */}
        <div className={styles.hud}>
          <div className={`${styles.hudPlayer} ${myColor ? styles.hudMe : ''}`}>
            <span
              className={`${styles.dot} ${myColor === 'WHITE' ? styles.dotW : styles.dotB}`}
              style={stonePreviewStyle(myHudSkin)}
              title={myHudSkin ? '내 바둑알 스킨' : '내 기본 바둑알'}
            />
            <span className={styles.hudName}>{myPlayer?.nickname ?? '나'}</span>
            {myHudSkin && <span className={styles.skinTag}>스킨</span>}
            {me?.speedBoosted && <span className={styles.boost}>⚡부스트</span>}
          </div>
          <div className={styles.hudCenter}>
            <span className={styles.hudItem}>
              아이템:{' '}
              {me?.heldItem ? (
                <b>{ITEM_META[me.heldItem].emoji} {ITEM_META[me.heldItem].label}</b>
              ) : (
                <span className={styles.muted}>없음</span>
              )}
            </span>
            <span className={destroyRemaining > 0 ? styles.cdActive : styles.cdReady}>
              파괴: {destroyRemaining > 0 ? `${destroyRemaining.toFixed(1)}초` : '준비됨'}
            </span>
          </div>
          <div className={styles.hudPlayer}>
            <span
              className={`${styles.dot} ${opp?.color === 'WHITE' || opponentRoomPlayer?.color === 'WHITE' ? styles.dotW : styles.dotB}`}
              style={stonePreviewStyle(opponentHudSkin)}
              title={opponentHudSkin ? '상대 바둑알 스킨' : '상대 기본 바둑알'}
            />
            <span className={styles.hudName}>{opp?.nickname ?? opponentRoomPlayer?.nickname ?? '상대'}</span>
            {opponentHudSkin && <span className={styles.skinTag}>스킨</span>}
            {opp?.heldItem && <span className={styles.muted}>{ITEM_META[opp.heldItem].emoji}</span>}
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
                ? `⚠ 상대 매치포인트! 끊어라 (Ctrl/Shift)!`
                : `⚠ 상대 오목 충전 중${foePendingCount > 1 ? ` ×${foePendingCount}` : ''}! 끊어라 (Ctrl/Shift)!`}
            </div>
          ) : myPendingCount > 0 ? (
            <div className={styles.pendingMine}>
              {myIsMatchPoint
                ? '⚡ 오목 완성! 버티면 승리!'
                : `⚡ 오목 완성${myPendingCount > 1 ? ` ×${myPendingCount}` : ''}! 게이지 지키면 득점!`}
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
                  방향키로 캐릭터를 움직여 <b>Space</b>로 착수! <b>Ctrl</b>로 상대 돌을 부수고,
                  필드의 아이템을 주워 <b>Shift</b>로 사용하세요. <b>오목</b>을 완성할 때마다 1점
                  (완성한 줄만 사라져요)! 먼저 <b>{snapshot?.targetScore ?? 3}점</b>이면 승리!
                </p>
                {!playerRolePlayer && (
                  <>
                    <p className={styles.inviteText}>방 코드를 공유해 상대를 초대하세요</p>
                    <button className={styles.copyBtn} onClick={() => navigator.clipboard.writeText(room.roomCode)}>
                      코드 복사 ({room.roomCode})
                    </button>
                  </>
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

        <div className={styles.controls}>
          <span><kbd>←↑↓→</kbd> 이동</span>
          <span><kbd>Space</kbd> 착수</span>
          <span><kbd>Ctrl</kbd> 파괴</span>
          <span><kbd>Shift</kbd> 아이템</span>
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
