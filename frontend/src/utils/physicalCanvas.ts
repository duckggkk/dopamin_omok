import { StoneColor, StoneStyle, CharacterStyle, PhysicalItemType } from '@/types';

/**
 * 피지컬 오목 캔버스 렌더 공용 헬퍼.
 * 라이브 화면(PhysicalGamePage)과 영상 리플레이(PhysicalVideoReplayViewer)가 같은 함수를 써서
 * 보드·돌·분화구·캐릭터·아이템을 '완전히 동일한 외형'으로 그린다(둘이 어긋나지 않게 단일 출처).
 */

// 캐릭터 스킨 face 키워드 → 이모지 (백엔드는 안전 키워드만 저장)
export const FACE_EMOJI: Record<string, string> = {
  robot: '🤖',
  rabbit: '🐰',
  ghost: '👻',
  cat: '🐱',
  fox: '🦊',
  bear: '🐻',
};

// 아이템 표시 메타 — 종류마다 '모양 + 색'을 달리해 한눈에 구분되게 한다(필드 드롭/이펙트/HUD 공용).
export type ItemShape = 'diamond' | 'hexagon' | 'circle';
export const ITEM_META: Record<
  PhysicalItemType,
  { emoji: string; label: string; shape: ItemShape; ring: string; glow: string; fill: [string, string] }
> = {
  SPEED_BOOST: {
    emoji: '⚡', label: '이동 부스트', shape: 'diamond',
    ring: '#6fd6ff', glow: 'rgba(95,200,255,0.85)', fill: ['#2a8fd0', '#0b3a5c'],
  },
  CRATER: {
    emoji: '🕳️', label: '바둑판 붕괴', shape: 'hexagon',
    ring: '#e8a24a', glow: 'rgba(214,140,60,0.85)', fill: ['#9a5a22', '#3a2410'],
  },
  BOMB: {
    emoji: '💣', label: '광역 폭탄', shape: 'circle',
    ring: '#ff7a7a', glow: 'rgba(255,90,90,0.85)', fill: ['#c62d2d', '#5c0b0b'],
  },
};

// 빈 바둑판(배경 + 격자 + 화점)을 그리고 좌표 변환 헬퍼를 돌려준다.
export const drawBoardBase = (ctx: CanvasRenderingContext2D, N: number, px: number) => {
  const pad = px / (N + 1);
  const gap = (px - pad * 2) / (N - 1);
  const at = (i: number) => pad + i * gap; // 그리드 인덱스 → 픽셀(교차점)

  ctx.fillStyle = '#dcb95b';
  ctx.fillRect(0, 0, px, px);

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
  ctx.fillStyle = '#8b6914';
  const star = [3, N - 4];
  for (const sx of star) {
    for (const sy of star) {
      ctx.beginPath();
      ctx.arc(at(sx), at(sy), 3, 0, Math.PI * 2);
      ctx.fill();
    }
  }
  return { pad, gap, at };
};

// 돌 — 장착 바둑알 스킨(색) 우선, 미장착이면 흑/백 기본 외형.
export const drawStone = (
  ctx: CanvasRenderingContext2D,
  cx: number,
  cy: number,
  r: number,
  black: boolean,
  skin: StoneStyle | null,
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

// 분화구(바둑판 붕괴) — 들쭉날쭉한 갈색 잔해 + 어두운 구덩이 + 균열. 흑돌과 확실히 구분된다.
export const drawCrater = (ctx: CanvasRenderingContext2D, cx: number, cy: number, unit: number) => {
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

// 아이템 모양 경로 — 종류별로 다른 실루엣(부스트=다이아 / 붕괴=육각 / 폭탄=원).
export const traceItemShape = (
  ctx: CanvasRenderingContext2D, cx: number, cy: number, r: number, shape: ItemShape,
) => {
  ctx.beginPath();
  if (shape === 'circle') {
    ctx.arc(cx, cy, r, 0, Math.PI * 2);
  } else if (shape === 'diamond') {
    ctx.moveTo(cx, cy - r);
    ctx.lineTo(cx + r, cy);
    ctx.lineTo(cx, cy + r);
    ctx.lineTo(cx - r, cy);
    ctx.closePath();
  } else {
    for (let k = 0; k < 6; k++) {
      const a = (k / 6) * Math.PI * 2 - Math.PI / 2;
      const px = cx + Math.cos(a) * r, py = cy + Math.sin(a) * r;
      if (k === 0) ctx.moveTo(px, py);
      else ctx.lineTo(px, py);
    }
    ctx.closePath();
  }
};

// 필드에 떨어진 아이템 — 종류별 테마색 모양 + 글로우 + 가벼운 맥동으로 눈에 띄게.
export const drawItemDrop = (
  ctx: CanvasRenderingContext2D, cx: number, cy: number, unit: number, type: PhysicalItemType,
) => {
  const m = ITEM_META[type];
  const pulse = 0.9 + 0.1 * Math.sin(performance.now() / 240 + cx * 0.3);
  const r = unit * 0.44 * pulse;
  ctx.save();
  ctx.shadowColor = m.glow;
  ctx.shadowBlur = unit * 0.55 * pulse;
  const grad = ctx.createLinearGradient(cx, cy - r, cx, cy + r);
  grad.addColorStop(0, m.fill[0]);
  grad.addColorStop(1, m.fill[1]);
  ctx.fillStyle = grad;
  traceItemShape(ctx, cx, cy, r, m.shape);
  ctx.fill();
  ctx.shadowBlur = 0;
  ctx.strokeStyle = m.ring;
  ctx.lineWidth = 2.4;
  traceItemShape(ctx, cx, cy, r, m.shape);
  ctx.stroke();
  ctx.font = `${Math.floor(unit * 0.48)}px serif`;
  ctx.textAlign = 'center';
  ctx.textBaseline = 'middle';
  ctx.fillText(m.emoji, cx, cy + 1);
  ctx.restore();
};

// 획득/사용 순간 이펙트 — 종류 색으로 터지고 이모지가 위로 떠오른다(먹은/쓴 걸 확실히 인지).
export const ITEM_FX_DUR = 720;
export const drawItemFx = (
  ctx: CanvasRenderingContext2D,
  cx: number,
  cy: number,
  unit: number,
  kind: 'pickup' | 'use',
  type: PhysicalItemType,
  t: number,
) => {
  const m = ITEM_META[type];
  const ease = 1 - Math.pow(1 - t, 3);
  const alpha = 1 - t;
  ctx.save();
  if (kind === 'pickup') {
    ctx.strokeStyle = `rgba(255,221,120,${alpha})`;
    ctx.lineWidth = 3;
    ctx.shadowColor = `rgba(255,221,120,${alpha})`;
    ctx.shadowBlur = 14 * alpha;
    ctx.beginPath();
    ctx.arc(cx, cy, unit * (0.3 + ease * 0.85), 0, Math.PI * 2);
    ctx.stroke();
    ctx.shadowBlur = 0;
    for (let k = 0; k < 8; k++) {
      const ang = (k / 8) * Math.PI * 2;
      const dist = unit * (0.3 + ease * 1.1);
      ctx.fillStyle = `rgba(255,221,120,${alpha})`;
      ctx.beginPath();
      ctx.arc(cx + Math.cos(ang) * dist, cy + Math.sin(ang) * dist, Math.max(1, unit * 0.1 * (1 - t)), 0, Math.PI * 2);
      ctx.fill();
    }
  } else {
    const scale = type === 'BOMB' ? 2.4 : 1.5;
    ctx.strokeStyle = m.ring;
    ctx.globalAlpha = alpha;
    ctx.lineWidth = 3.5;
    ctx.shadowColor = m.glow;
    ctx.shadowBlur = 18 * alpha;
    ctx.beginPath();
    ctx.arc(cx, cy, unit * (0.4 + ease * scale), 0, Math.PI * 2);
    ctx.stroke();
    if (t < 0.45) {
      ctx.shadowBlur = 0;
      ctx.fillStyle = `rgba(255,255,255,${(0.45 - t) * 1.6})`;
      ctx.beginPath();
      ctx.arc(cx, cy, unit * scale * 0.5 * (1 - t), 0, Math.PI * 2);
      ctx.fill();
    }
    ctx.globalAlpha = 1;
  }
  ctx.shadowBlur = 0;
  ctx.globalAlpha = alpha;
  ctx.font = `${Math.floor(unit * (0.5 + 0.25 * ease))}px serif`;
  ctx.textAlign = 'center';
  ctx.textBaseline = 'middle';
  ctx.fillText(m.emoji, cx, cy - unit * (0.3 + ease * 1.0));
  ctx.restore();
};

// 캐릭터 그리기 입력(라이브 PhysicalPlayerView 도 구조적으로 이 형태를 만족 — 그대로 넘길 수 있다).
export interface CharacterRender {
  color: StoneColor;
  nickname: string;
  character: CharacterStyle | null;
  speedBoosted: boolean;
  heldItem: PhysicalItemType | null;
}

// 캐릭터 — 부스트 링 + 몸체(스킨 우선) + 얼굴(스킨 이모지 / 기본 눈) + 닉네임 + 보유 아이템 배지.
export const drawCharacter = (
  ctx: CanvasRenderingContext2D,
  cx: number,
  cy: number,
  unit: number,
  p: CharacterRender,
  isMe: boolean,
) => {
  const r = unit * 0.42;
  const black = p.color === 'BLACK';
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
  ctx.fillStyle = body;
  ctx.beginPath();
  ctx.arc(cx, cy, r, 0, Math.PI * 2);
  ctx.fill();
  ctx.lineWidth = 3;
  ctx.strokeStyle = isMe ? '#e0a83f' : accent;
  ctx.stroke();

  if (face) {
    ctx.font = `${Math.floor(r * 1.25)}px serif`;
    ctx.textAlign = 'center';
    ctx.textBaseline = 'middle';
    ctx.fillText(face, cx, cy + 1);
  } else {
    ctx.fillStyle = black ? '#ffffff' : '#33312b';
    ctx.beginPath();
    ctx.arc(cx - r * 0.34, cy - r * 0.08, r * 0.16, 0, Math.PI * 2);
    ctx.fill();
    ctx.beginPath();
    ctx.arc(cx + r * 0.34, cy - r * 0.08, r * 0.16, 0, Math.PI * 2);
    ctx.fill();
  }

  ctx.font = `bold ${Math.floor(unit * 0.32)}px sans-serif`;
  ctx.textAlign = 'center';
  ctx.textBaseline = 'bottom';
  ctx.lineWidth = 3;
  ctx.strokeStyle = 'rgba(0,0,0,0.7)';
  ctx.strokeText(p.nickname, cx, cy - r - 4);
  ctx.fillStyle = isMe ? '#ffd86b' : '#ffffff';
  ctx.fillText(p.nickname, cx, cy - r - 4);

  // 보유 아이템 배지 — 캐릭터 우상단에 종류 색 원 + 이모지(누가 무엇을 들었는지 한눈에)
  if (p.heldItem) {
    const m = ITEM_META[p.heldItem];
    const bx = cx + r * 0.82, by = cy - r * 0.82, br = r * 0.5;
    ctx.beginPath();
    ctx.arc(bx, by, br, 0, Math.PI * 2);
    ctx.fillStyle = m.fill[1];
    ctx.fill();
    ctx.lineWidth = 1.6;
    ctx.strokeStyle = m.ring;
    ctx.stroke();
    ctx.font = `${Math.floor(br * 1.25)}px serif`;
    ctx.textAlign = 'center';
    ctx.textBaseline = 'middle';
    ctx.fillText(m.emoji, bx, by + 0.5);
  }
};
