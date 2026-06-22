import { useState, useEffect, useRef, useCallback, type MouseEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuthStore } from '@/store/authStore';
import { useChat } from '@/hooks/useChat';
import { usePlazaSocket } from '@/hooks/usePlazaSocket';
import { plazaApi } from '@/api/plaza';
import { ApiResponse, ChatMessage, Direction, PlazaAppearance, PlazaJoinResponse, PlazaPlayerView, PlazaSnapshot } from '@/types';
import styles from './PlazaPage.module.css';

// 캔버스 초기(폴백) 해상도. 실제 크기는 컨테이너에 맞춰 ResizeObserver 가 동적으로 맞춘다.
const INIT_W = 960;
const INIT_H = 720;

// 플레이스홀더 외형 카탈로그(Phase 1). 키 → 색 매핑(실제 스프라이트는 추후 교체).
const HAIR_OPTIONS = ['hair_01', 'hair_02', 'hair_03', 'hair_04'];
const OUTFIT_OPTIONS = ['outfit_01', 'outfit_02', 'outfit_03', 'outfit_04'];
const ACCESSORY_OPTIONS: (string | null)[] = [null, 'acc_glasses', 'acc_band'];

const HAIR_COLOR: Record<string, string> = {
  hair_01: '#3a2a1c', hair_02: '#111114', hair_03: '#b5651d', hair_04: '#d9b25b',
};
const OUTFIT_COLOR: Record<string, string> = {
  outfit_01: '#c0392b', outfit_02: '#2e86c1', outfit_03: '#27ae60', outfit_04: '#8e44ad',
};
const ACCESSORY_LABEL: Record<string, string> = { acc_glasses: '안경', acc_band: '머리띠' };

const KEY_DIR: Record<string, Direction> = {
  ArrowUp: 'UP', ArrowDown: 'DOWN', ArrowLeft: 'LEFT', ArrowRight: 'RIGHT',
  w: 'UP', s: 'DOWN', a: 'LEFT', d: 'RIGHT',
};

const clamp = (v: number, min: number, max: number) => (v < min ? min : v > max ? max : v);

// 말풍선이 머리 위에 떠 있는 시간(ms)
const BUBBLE_MS = 4500;

const roundRect = (ctx: CanvasRenderingContext2D, x: number, y: number, w: number, h: number, r: number) => {
  ctx.beginPath();
  ctx.moveTo(x + r, y);
  ctx.arcTo(x + w, y, x + w, y + h, r);
  ctx.arcTo(x + w, y + h, x, y + h, r);
  ctx.arcTo(x, y + h, x, y, r);
  ctx.arcTo(x, y, x + w, y, r);
  ctx.closePath();
};

/** #RRGGBB 를 amt 만큼 밝게/어둡게 — 아바타 음영용. */
const shade = (hex: string, amt: number): string => {
  const n = parseInt(hex.replace('#', ''), 16);
  const r = clamp((n >> 16) + amt, 0, 255);
  const g = clamp(((n >> 8) & 255) + amt, 0, 255);
  const b = clamp((n & 255) + amt, 0, 255);
  return `rgb(${r},${g},${b})`;
};

/** 채팅 말풍선 — 아바타 머리 위(tailTipY 가 꼬리 끝). 문자 단위 줄바꿈(한글 대응)으로 최대 2줄, 넘치면 …. */
const drawSpeechBubble = (ctx: CanvasRenderingContext2D, cx: number, tailTipY: number, raw: string) => {
  ctx.font = '12px sans-serif';
  const maxW = 150, padX = 8, padY = 5, lineH = 15, maxLines = 2, tail = 6;
  const text = raw.length > 80 ? raw.slice(0, 80) : raw;
  const lines: string[] = [];
  let cur = '';
  let truncated = raw.length > 80;
  for (let i = 0; i < text.length; i++) {
    const ch = text[i];
    if (ctx.measureText(cur + ch).width <= maxW) cur += ch;
    else if (lines.length < maxLines - 1) { lines.push(cur); cur = ch; }
    else { truncated = true; break; }
  }
  lines.push(cur);
  if (truncated) {
    let last = lines[lines.length - 1];
    while (last && ctx.measureText(last + '…').width > maxW) last = last.slice(0, -1);
    lines[lines.length - 1] = last + '…';
  }
  const textW = Math.max(1, ...lines.map((l) => ctx.measureText(l).width));
  const boxW = Math.min(maxW, textW) + padX * 2;
  const boxH = lines.length * lineH + padY * 2;
  const x = cx - boxW / 2;
  const yBottom = tailTipY - tail;
  const y = yBottom - boxH;

  ctx.save();
  ctx.shadowColor = 'rgba(0,0,0,0.28)';
  ctx.shadowBlur = 6;
  ctx.shadowOffsetY = 2;
  ctx.fillStyle = 'rgba(255,255,255,0.97)';
  roundRect(ctx, x, y, boxW, boxH, 8);
  ctx.fill();
  ctx.restore();

  ctx.fillStyle = 'rgba(255,255,255,0.97)';
  ctx.beginPath();
  ctx.moveTo(cx - 5, yBottom - 0.5);
  ctx.lineTo(cx + 5, yBottom - 0.5);
  ctx.lineTo(cx, tailTipY);
  ctx.closePath();
  ctx.fill();

  ctx.fillStyle = '#1f2937';
  ctx.textAlign = 'center';
  ctx.textBaseline = 'top';
  lines.forEach((ln, i) => ctx.fillText(ln, cx, y + padY + i * lineH));
};

const PlazaPage = () => {
  const navigate = useNavigate();
  const { user } = useAuthStore();
  const myId = user?.id ?? '';

  const [joinInfo, setJoinInfo] = useState<PlazaJoinResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [onlineCount, setOnlineCount] = useState(0);
  const [appearance, setAppearance] = useState<PlazaAppearance>({
    body: 'base', hair: 'hair_01', outfit: 'outfit_01', accessory: null,
  });

  const canvasRef = useRef<HTMLCanvasElement>(null);
  const wrapRef = useRef<HTMLDivElement>(null);
  const dimsRef = useRef({ w: INIT_W, h: INIT_H });
  const snapshotRef = useRef<PlazaSnapshot | null>(null);
  const renderPosRef = useRef<Record<string, { x: number; y: number }>>({});
  // 채팅 말풍선: 닉네임 → { 내용, 만료시각(performance.now 기준) }. 들어온 채팅을 아바타 위에 잠깐 띄운다.
  const bubblesRef = useRef<Record<string, { text: string; expiresAt: number }>>({});
  const appearanceRef = useRef<PlazaAppearance>(appearance);
  const pressedRef = useRef<Direction[]>([]);
  const activeDirRef = useRef<Direction | null>(null);

  const chat = useChat();
  const [chatActive, setChatActive] = useState(false);
  const chatInputRef = useRef<HTMLInputElement>(null);
  // 아바타 꾸미기 팝업. 열려 있는 동안엔 월드 입력(이동/채팅 열기)을 막는다.
  const [dressOpen, setDressOpen] = useState(false);
  const dressOpenRef = useRef(false);

  useEffect(() => { appearanceRef.current = appearance; }, [appearance]);
  useEffect(() => { dressOpenRef.current = dressOpen; }, [dressOpen]);

  // 꾸미기 팝업: Esc 로 닫기
  useEffect(() => {
    if (!dressOpen) return;
    const onEsc = (e: KeyboardEvent) => { if (e.key === 'Escape') setDressOpen(false); };
    window.addEventListener('keydown', onEsc);
    return () => window.removeEventListener('keydown', onEsc);
  }, [dressOpen]);

  // 캔버스 백킹 해상도를 컨테이너 크기에 맞춰 동적으로 조정(광장이 가용 공간을 가득 채움)
  useEffect(() => {
    const wrap = wrapRef.current;
    const canvas = canvasRef.current;
    if (!wrap || !canvas) return;
    const apply = () => {
      const w = Math.max(320, Math.round(wrap.clientWidth));
      const h = Math.max(320, Math.round(wrap.clientHeight));
      dimsRef.current = { w, h };
      canvas.width = w;
      canvas.height = h;
    };
    apply();
    const ro = new ResizeObserver(apply);
    ro.observe(wrap);
    return () => ro.disconnect();
  }, []);

  // 채팅 입력이 열리면 자동 포커스 (게임 채팅처럼 Enter 로 바로 타이핑)
  useEffect(() => { if (chatActive) chatInputRef.current?.focus(); }, [chatActive]);

  // Enter 로 채팅창 열기 (입력 중이 아닐 때만 — 입력 중 Enter 는 전송이 처리)
  useEffect(() => {
    if (!joinInfo) return;
    const onKey = (e: KeyboardEvent) => {
      if (dressOpenRef.current) return; // 꾸미기 팝업 중엔 무시
      const typing = e.target instanceof HTMLElement && (e.target.tagName === 'INPUT' || e.target.tagName === 'TEXTAREA');
      if (e.key === 'Enter' && !typing) {
        e.preventDefault();
        setChatActive(true);
      }
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [joinInfo]);

  const handleSnapshot = useCallback((snap: PlazaSnapshot) => {
    snapshotRef.current = snap;
    setOnlineCount((c) => (c !== snap.players.length ? snap.players.length : c));
  }, []);

  // 채팅 수신: 말풍선(닉네임 매칭)으로 띄우고 + 기존 채팅 로그에도 전달
  const handlePlazaChat = useCallback((res: ApiResponse<ChatMessage>) => {
    if (res.success && res.data) {
      bubblesRef.current[res.data.senderNickname] = {
        text: res.data.content,
        expiresAt: performance.now() + BUBBLE_MS,
      };
    }
    chat.onChatMessage(res);
  }, [chat.onChatMessage]);

  const { sendInput, sendChat, sendAppearance } = usePlazaSocket({
    channelId: joinInfo?.channelId ?? null,
    appearanceRef,
    onSnapshot: handleSnapshot,
    onChat: handlePlazaChat,
  });

  // 입장: 채널 배정
  useEffect(() => {
    let cancelled = false;
    plazaApi
      .join()
      .then((res) => {
        if (cancelled) return;
        if (res.data.data) setJoinInfo(res.data.data);
        else setError('광장 입장에 실패했습니다.');
      })
      .catch(() => { if (!cancelled) setError('광장 입장에 실패했습니다.'); });
    return () => { cancelled = true; };
  }, []);

  // 키보드 이동(intent — keydown=MOVE_START, keyup=MOVE_STOP). 채팅 입력 중엔 무시.
  useEffect(() => {
    if (!joinInfo) return;
    const isTyping = (t: EventTarget | null) =>
      t instanceof HTMLElement && (t.tagName === 'INPUT' || t.tagName === 'TEXTAREA');

    const recompute = () => {
      const next = pressedRef.current[pressedRef.current.length - 1] ?? null;
      if (next === activeDirRef.current) return;
      activeDirRef.current = next;
      if (next) sendInput('MOVE_START', next);
      else sendInput('MOVE_STOP');
    };
    const onKeyDown = (e: KeyboardEvent) => {
      if (dressOpenRef.current || isTyping(e.target)) return;
      const dir = KEY_DIR[e.key];
      if (!dir) return;
      e.preventDefault();
      if (!pressedRef.current.includes(dir)) {
        pressedRef.current.push(dir);
        recompute();
      }
    };
    const onKeyUp = (e: KeyboardEvent) => {
      const dir = KEY_DIR[e.key];
      if (!dir) return;
      pressedRef.current = pressedRef.current.filter((d) => d !== dir);
      recompute();
    };
    window.addEventListener('keydown', onKeyDown);
    window.addEventListener('keyup', onKeyUp);
    return () => {
      window.removeEventListener('keydown', onKeyDown);
      window.removeEventListener('keyup', onKeyUp);
      pressedRef.current = [];
      if (activeDirRef.current) {
        activeDirRef.current = null;
        sendInput('MOVE_STOP');
      }
    };
  }, [joinInfo, sendInput]);

  // ===== 캔버스 렌더 =====
  const drawAvatar = (ctx: CanvasRenderingContext2D, sx: number, sy: number, p: PlazaPlayerView, isMe: boolean, t: number) => {
    const walk = p.moving ? Math.sin(t / 90 + sx * 0.04) : 0; // -1..1 걸음 위상
    const bob = p.moving ? Math.abs(walk) * 4 : 0;
    const cx = sx;
    const cy = sy - bob;
    const skin = '#f4cda4';
    const hairC = HAIR_COLOR[p.appearance.hair] ?? '#3a2a1c';
    const outfitC = OUTFIT_COLOR[p.appearance.outfit] ?? '#9aa0a6';
    const facing = p.facing;
    const headCx = cx;
    const headCy = cy - 17;
    const headR = 12.5;

    // 바닥 그림자
    ctx.fillStyle = 'rgba(0,0,0,0.22)';
    ctx.beginPath();
    ctx.ellipse(sx, sy + 19, 15, 5.5, 0, 0, Math.PI * 2);
    ctx.fill();

    // 다리(걸음에 따라 앞뒤로)
    const legSwing = walk * 3;
    ctx.fillStyle = '#46352a';
    roundRect(ctx, cx - 7 + legSwing, cy + 13, 5, 10, 2.5); ctx.fill();
    roundRect(ctx, cx + 2 - legSwing, cy + 13, 5, 10, 2.5); ctx.fill();
    // 신발
    ctx.fillStyle = '#2b2118';
    roundRect(ctx, cx - 8 + legSwing, cy + 21, 6, 3.5, 1.8); ctx.fill();
    roundRect(ctx, cx + 2 - legSwing, cy + 21, 6, 3.5, 1.8); ctx.fill();

    // 팔(몸통 뒤, 반대 위상으로 스윙)
    ctx.fillStyle = shade(outfitC, -22);
    roundRect(ctx, cx - 12.5, cy - 3 - legSwing, 5, 14, 2.5); ctx.fill();
    roundRect(ctx, cx + 7.5, cy - 3 + legSwing, 5, 14, 2.5); ctx.fill();

    // 몸통(의상) — 세로 그라디언트 + 외곽선
    const bodyGrad = ctx.createLinearGradient(0, cy - 5, 0, cy + 16);
    bodyGrad.addColorStop(0, shade(outfitC, 26));
    bodyGrad.addColorStop(1, outfitC);
    roundRect(ctx, cx - 11, cy - 5, 22, 21, 7);
    ctx.fillStyle = bodyGrad;
    ctx.fill();
    ctx.lineWidth = 2;
    ctx.strokeStyle = isMe ? '#ffd86b' : shade(outfitC, -34);
    ctx.stroke();
    // 옷깃 하이라이트
    ctx.strokeStyle = 'rgba(255,255,255,0.18)';
    ctx.lineWidth = 1.4;
    ctx.beginPath();
    ctx.arc(cx, cy - 4, 6, Math.PI * 0.15, Math.PI * 0.85);
    ctx.stroke();

    // 목
    ctx.fillStyle = shade(skin, -18);
    ctx.fillRect(cx - 3, headCy + headR - 4, 6, 6);

    // 머리 — 라디얼 음영 + 외곽선
    const headGrad = ctx.createRadialGradient(headCx - 4, headCy - 4, 2, headCx, headCy, headR + 2);
    headGrad.addColorStop(0, '#ffe6cd');
    headGrad.addColorStop(1, skin);
    ctx.beginPath();
    ctx.arc(headCx, headCy, headR, 0, Math.PI * 2);
    ctx.fillStyle = headGrad;
    ctx.fill();
    ctx.lineWidth = 1.4;
    ctx.strokeStyle = 'rgba(120,80,50,0.35)';
    ctx.stroke();

    // 헤어 — 스타일별 + 방향 반영
    ctx.fillStyle = hairC;
    ctx.strokeStyle = shade(hairC, -26);
    ctx.lineWidth = 1;
    const drawCap = (extend: number) => {
      ctx.beginPath();
      ctx.arc(headCx, headCy - 1, headR + 0.6, Math.PI, Math.PI * 2);
      ctx.lineTo(headCx + headR + 0.6, headCy + extend);
      ctx.lineTo(headCx - headR - 0.6, headCy + extend);
      ctx.closePath();
      ctx.fill();
    };
    if (facing === 'UP') {
      ctx.beginPath();
      ctx.arc(headCx, headCy, headR + 1, 0, Math.PI * 2);
      ctx.fill();
    } else if (p.appearance.hair === 'hair_02') {
      // 스파이크
      drawCap(-3);
      for (let i = -2; i <= 2; i++) {
        ctx.beginPath();
        ctx.moveTo(headCx + i * 5 - 3, headCy - headR + 1);
        ctx.lineTo(headCx + i * 5, headCy - headR - 6);
        ctx.lineTo(headCx + i * 5 + 3, headCy - headR + 1);
        ctx.closePath();
        ctx.fill();
      }
    } else if (p.appearance.hair === 'hair_04') {
      // 긴 머리(옆으로 내려옴)
      drawCap(8);
      ctx.beginPath();
      ctx.ellipse(headCx - headR + 1, headCy + 3, 3.5, 8, 0, 0, Math.PI * 2);
      ctx.ellipse(headCx + headR - 1, headCy + 3, 3.5, 8, 0, 0, Math.PI * 2);
      ctx.fill();
    } else if (p.appearance.hair === 'hair_03') {
      // 옆가르마 + 앞머리
      drawCap(-1);
      ctx.beginPath();
      ctx.moveTo(headCx - headR, headCy - 3);
      ctx.quadraticCurveTo(headCx - 2, headCy - 1, headCx + 5, headCy - 5);
      ctx.lineTo(headCx + headR, headCy - headR);
      ctx.lineTo(headCx - headR, headCy - headR);
      ctx.closePath();
      ctx.fill();
    } else {
      // hair_01 기본 단발 캡
      drawCap(1);
    }

    // 얼굴(정면/측면) — 눈 흰자+눈동자, 입, 볼터치
    if (facing !== 'UP') {
      const off = facing === 'LEFT' ? -2.2 : facing === 'RIGHT' ? 2.2 : 0;
      const eyeY = headCy + 1;
      for (const ex of [-4, 4]) {
        ctx.fillStyle = '#fff';
        ctx.beginPath();
        ctx.ellipse(headCx + ex + off * 0.5, eyeY, 2.4, 2.9, 0, 0, Math.PI * 2);
        ctx.fill();
        ctx.fillStyle = '#2a2320';
        ctx.beginPath();
        ctx.arc(headCx + ex + off, eyeY + 0.4, 1.4, 0, Math.PI * 2);
        ctx.fill();
        ctx.fillStyle = 'rgba(255,255,255,0.9)';
        ctx.beginPath();
        ctx.arc(headCx + ex + off - 0.5, eyeY - 0.4, 0.5, 0, Math.PI * 2);
        ctx.fill();
      }
      // 볼터치
      ctx.fillStyle = 'rgba(240,140,120,0.35)';
      ctx.beginPath();
      ctx.arc(headCx - 6, eyeY + 4, 2, 0, Math.PI * 2);
      ctx.arc(headCx + 6, eyeY + 4, 2, 0, Math.PI * 2);
      ctx.fill();
      // 입
      ctx.strokeStyle = '#9c5b45';
      ctx.lineWidth = 1.2;
      ctx.beginPath();
      ctx.arc(headCx + off, eyeY + 5, 2.2, Math.PI * 0.15, Math.PI * 0.85);
      ctx.stroke();
    }

    // 액세서리
    if (p.appearance.accessory === 'acc_glasses' && facing !== 'UP') {
      ctx.strokeStyle = '#23252b';
      ctx.lineWidth = 1.5;
      ctx.beginPath();
      ctx.arc(headCx - 4, headCy + 1, 3, 0, Math.PI * 2);
      ctx.arc(headCx + 4, headCy + 1, 3, 0, Math.PI * 2);
      ctx.moveTo(headCx - 1, headCy + 1);
      ctx.lineTo(headCx + 1, headCy + 1);
      ctx.stroke();
    } else if (p.appearance.accessory === 'acc_band') {
      ctx.fillStyle = '#e74c3c';
      roundRect(ctx, headCx - headR - 0.5, headCy - 7, 2 * headR + 1, 4, 2);
      ctx.fill();
      ctx.fillStyle = '#fff';
      ctx.beginPath();
      ctx.arc(headCx, headCy - 5, 1.6, 0, Math.PI * 2);
      ctx.fill();
    }

    // 닉네임
    ctx.font = 'bold 12px sans-serif';
    ctx.textAlign = 'center';
    ctx.textBaseline = 'bottom';
    ctx.lineWidth = 3;
    ctx.strokeStyle = 'rgba(0,0,0,0.65)';
    ctx.strokeText(p.nickname, cx, headCy - headR - 5);
    ctx.fillStyle = isMe ? '#ffd86b' : '#ffffff';
    ctx.fillText(p.nickname, cx, headCy - headR - 5);
  };

  const drawGround = (ctx: CanvasRenderingContext2D, snap: PlazaSnapshot, camX: number, camY: number, cw: number, ch: number) => {
    ctx.strokeStyle = 'rgba(255,255,255,0.10)';
    ctx.lineWidth = 1;
    const grid = 80;
    for (let x = -(camX % grid); x <= cw; x += grid) {
      ctx.beginPath();
      ctx.moveTo(x, 0);
      ctx.lineTo(x, ch);
      ctx.stroke();
    }
    for (let y = -(camY % grid); y <= ch; y += grid) {
      ctx.beginPath();
      ctx.moveTo(0, y);
      ctx.lineTo(cw, y);
      ctx.stroke();
    }
    // 중앙 광장(장식)
    ctx.fillStyle = 'rgba(255,255,255,0.07)';
    ctx.beginPath();
    ctx.arc(snap.worldWidth / 2 - camX, snap.worldHeight / 2 - camY, 120, 0, Math.PI * 2);
    ctx.fill();
    // 월드 경계
    ctx.strokeStyle = 'rgba(60,40,15,0.55)';
    ctx.lineWidth = 6;
    ctx.strokeRect(-camX, -camY, snap.worldWidth, snap.worldHeight);
  };

  const drawScene = (ctx: CanvasRenderingContext2D) => {
    const { w: CW, h: CH } = dimsRef.current;
    ctx.fillStyle = '#7fb069';
    ctx.fillRect(0, 0, CW, CH);
    const snap = snapshotRef.current;
    if (!snap) {
      ctx.fillStyle = 'rgba(255,255,255,0.85)';
      ctx.font = 'bold 18px sans-serif';
      ctx.textAlign = 'center';
      ctx.textBaseline = 'middle';
      ctx.fillText('광장에 입장 중...', CW / 2, CH / 2);
      return;
    }

    // 사라진 플레이어 보간 위치 정리
    const ids = new Set(snap.players.map((p) => p.playerId));
    for (const k of Object.keys(renderPosRef.current)) if (!ids.has(k)) delete renderPosRef.current[k];

    const interp = (p: PlazaPlayerView) => {
      const rp = renderPosRef.current[p.playerId] ?? { x: p.x, y: p.y };
      rp.x += (p.x - rp.x) * 0.25;
      rp.y += (p.y - rp.y) * 0.25;
      renderPosRef.current[p.playerId] = rp;
      return rp;
    };

    const me = snap.players.find((p) => p.playerId === myId);
    const meRp = me ? interp(me) : { x: snap.worldWidth / 2, y: snap.worldHeight / 2 };
    const camX = clamp(meRp.x - CW / 2, 0, Math.max(0, snap.worldWidth - CW));
    const camY = clamp(meRp.y - CH / 2, 0, Math.max(0, snap.worldHeight - CH));

    drawGround(ctx, snap, camX, camY, CW, CH);

    const t = performance.now();
    const sorted = [...snap.players].sort((a, b) => a.y - b.y); // y 깊이 정렬
    for (const p of sorted) {
      const rp = p.playerId === me?.playerId ? meRp : interp(p);
      drawAvatar(ctx, rp.x - camX, rp.y - camY, p, p.playerId === myId, t);
    }

    // 말풍선 — 모든 아바타 위에 그리도록 별도 패스(앞쪽 아바타에 가려지지 않게)
    const bubbles = bubblesRef.current;
    for (const p of sorted) {
      const b = bubbles[p.nickname];
      if (!b) continue;
      if (b.expiresAt <= t) { delete bubbles[p.nickname]; continue; }
      const rp = renderPosRef.current[p.playerId] ?? { x: p.x, y: p.y };
      drawSpeechBubble(ctx, rp.x - camX, rp.y - camY - 42, b.text);
    }
  };

  useEffect(() => {
    let raf = 0;
    const render = () => {
      const ctx = canvasRef.current?.getContext('2d');
      if (ctx) drawScene(ctx);
      raf = requestAnimationFrame(render);
    };
    raf = requestAnimationFrame(render);
    return () => cancelAnimationFrame(raf);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [myId]);

  // ===== 핸들러 =====
  const changeAppearance = (patch: Partial<PlazaAppearance>) => {
    setAppearance((prev) => {
      const next = { ...prev, ...patch };
      appearanceRef.current = next;
      sendAppearance(next);
      return next;
    });
  };

  const handleSendChat = () => {
    const trimmed = chat.input.trim();
    if (!trimmed) return;
    sendChat(trimmed);
    chat.setInput('');
  };

  const handleCanvasClick = (event: MouseEvent<HTMLCanvasElement>) => {
    if (dressOpenRef.current) return;
    const snap = snapshotRef.current;
    if (!snap) return;

    const canvas = event.currentTarget;
    const rect = canvas.getBoundingClientRect();
    const { w: canvasWidth, h: canvasHeight } = dimsRef.current;
    const x = (event.clientX - rect.left) * (canvasWidth / rect.width);
    const y = (event.clientY - rect.top) * (canvasHeight / rect.height);

    const me = snap.players.find((p) => p.playerId === myId);
    const meRp = me ? (renderPosRef.current[me.playerId] ?? { x: me.x, y: me.y }) : {
      x: snap.worldWidth / 2,
      y: snap.worldHeight / 2,
    };
    const camX = clamp(meRp.x - canvasWidth / 2, 0, Math.max(0, snap.worldWidth - canvasWidth));
    const camY = clamp(meRp.y - canvasHeight / 2, 0, Math.max(0, snap.worldHeight - canvasHeight));

    const hit = [...snap.players]
      .sort((a, b) => b.y - a.y)
      .find((p) => {
        const rp = renderPosRef.current[p.playerId] ?? { x: p.x, y: p.y };
        const dx = x - (rp.x - camX);
        const dy = y - (rp.y - camY - 8);
        return Math.hypot(dx, dy) <= 34;
      });

    if (hit) navigate(hit.playerId === myId ? '/profile' : `/profile/${hit.playerId}`);
  };

  if (error) {
    return (
      <div className={styles.errorWrap}>
        <p>{error}</p>
        <button className={styles.backBtn} onClick={() => navigate('/')}>홈으로</button>
      </div>
    );
  }

  return (
    <div className={styles.container}>
      <div className={styles.arena}>
        <div className={styles.topBar}>
          <span className={styles.modeBadge}>🏛 만남의 광장</span>
          <span className={styles.channelBadge}>{joinInfo?.channelId ?? '...'}</span>
          <span className={styles.onlineBadge}>👥 {onlineCount}/{joinInfo?.capacity ?? 30}</span>
        </div>

        <div className={styles.canvasWrap} ref={wrapRef}>
          <canvas
            ref={canvasRef}
            width={INIT_W}
            height={INIT_H}
            className={styles.canvas}
            onClick={handleCanvasClick}
          />

          {/* 아바타 꾸미기 — 광장 화면 우상단 불투명 버튼. 클릭 시 팝업으로 연다. */}
          <button className={styles.dressBtn} onClick={() => setDressOpen(true)}>
            🎨 아바타 꾸미기
          </button>

          {/* 인터페이스 내장 채팅 — 캔버스 좌하단 오버레이. Enter 로 입력칸 활성화. */}
          <div className={styles.chatOverlay}>
            <div className={styles.chatLog}>
              {chat.messages.slice(-3).map((m, i) => (
                <div key={i} className={styles.chatLine}>
                  <span
                    className={styles.chatName}
                    style={{ color: m.senderNickname === user?.nickname ? '#ffd86b' : '#9fe0ff' }}
                  >
                    {m.senderNickname}
                  </span>
                  <span className={styles.chatText}>{m.content}</span>
                </div>
              ))}
              <div ref={chat.bottomRef} />
            </div>
            {chatActive ? (
              <input
                ref={chatInputRef}
                className={styles.chatInput}
                value={chat.input}
                onChange={(e) => chat.setInput(e.target.value)}
                onBlur={() => setChatActive(false)}
                onKeyDown={(e) => {
                  if (e.key === 'Enter') {
                    e.preventDefault();
                    // 내용이 있으면 전송(입력칸 유지), 빈 채로 Enter 한번 더면 입력칸 닫기
                    if (chat.input.trim()) handleSendChat();
                    else setChatActive(false);
                  } else if (e.key === 'Escape') { e.preventDefault(); chat.setInput(''); setChatActive(false); }
                }}
                placeholder="메시지 입력 후 Enter · 빈 채로 Enter 또는 Esc 로 닫기"
                maxLength={200}
              />
            ) : (
              <button className={styles.chatPrompt} onClick={() => setChatActive(true)}>
                💬 <kbd>Enter</kbd> 로 채팅
              </button>
            )}
          </div>

          {/* 아바타 꾸미기 팝업 — 광장 화면 내부 오버레이 */}
          {dressOpen && (
            <div className={styles.dressBackdrop} onClick={() => setDressOpen(false)}>
              <div className={styles.dressModal} onClick={(e) => e.stopPropagation()}>
                <div className={styles.dressModalHead}>
                  <h3 className={styles.panelTitle}>아바타 꾸미기</h3>
                  <button className={styles.dressClose} onClick={() => setDressOpen(false)} aria-label="닫기">✕</button>
                </div>

                <div className={styles.dressGroup}>
                  <span className={styles.dressLabel}>헤어</span>
                  <div className={styles.swatchRow}>
                    {HAIR_OPTIONS.map((h) => (
                      <button
                        key={h}
                        className={`${styles.swatch} ${appearance.hair === h ? styles.swatchOn : ''}`}
                        style={{ background: HAIR_COLOR[h] }}
                        onClick={() => changeAppearance({ hair: h })}
                        aria-label={h}
                      />
                    ))}
                  </div>
                </div>

                <div className={styles.dressGroup}>
                  <span className={styles.dressLabel}>의상</span>
                  <div className={styles.swatchRow}>
                    {OUTFIT_OPTIONS.map((o) => (
                      <button
                        key={o}
                        className={`${styles.swatch} ${appearance.outfit === o ? styles.swatchOn : ''}`}
                        style={{ background: OUTFIT_COLOR[o] }}
                        onClick={() => changeAppearance({ outfit: o })}
                        aria-label={o}
                      />
                    ))}
                  </div>
                </div>

                <div className={styles.dressGroup}>
                  <span className={styles.dressLabel}>액세서리</span>
                  <div className={styles.swatchRow}>
                    {ACCESSORY_OPTIONS.map((a) => (
                      <button
                        key={a ?? 'none'}
                        className={`${styles.accBtn} ${appearance.accessory === a ? styles.accOn : ''}`}
                        onClick={() => changeAppearance({ accessory: a })}
                      >
                        {a ? ACCESSORY_LABEL[a] : '없음'}
                      </button>
                    ))}
                  </div>
                </div>
                <p className={styles.dressHint}>지금은 플레이스홀더예요. 결제형 아바타는 다음 단계에서 붙습니다.</p>
              </div>
            </div>
          )}
        </div>

        <div className={styles.controls}>
          <span><kbd>←↑↓→</kbd> / <kbd>WASD</kbd> 이동</span>
          <span><kbd>Enter</kbd> 채팅</span>
          <span>아바타 클릭: 프로필</span>
          <span className={styles.muted}>춤·상호작용은 다음 단계에서 추가됩니다</span>
        </div>
      </div>
    </div>
  );
};

export default PlazaPage;
