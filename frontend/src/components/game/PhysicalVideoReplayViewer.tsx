import { useEffect, useRef, useState, useCallback, useMemo } from 'react';
import { GameInfo, PhysicalReplay, PhysicalMotionFrame, StoneColor, StoneStyle, CharacterStyle } from '@/types';
import { drawBoardBase, drawStone, drawCrater, drawCharacter, drawItemDrop } from '@/utils/physicalCanvas';
import styles from './PhysicalVideoReplayViewer.module.css';

interface Props {
  game: GameInfo;
  replay: PhysicalReplay;
  onClose: () => void;
}

const CANVAS_PX = 520;
const SPEEDS = [0.5, 1, 2] as const;

const fmtTime = (ms: number) => {
  const s = Math.max(0, Math.floor(ms / 1000));
  return `${String(Math.floor(s / 60)).padStart(2, '0')}:${String(s % 60).padStart(2, '0')}`;
};

/**
 * 피지컬 오목 '영상 리플레이' — 스타크래프트 리플레이처럼 캐릭터가 실제로 움직이는 연속 재생.
 * 보드(돌·분화구)는 칸 변화 이벤트로 시점별 재구성하고, 캐릭터/아이템은 위치 트랙(motionFrames)을
 * 프레임 사이 보간해 부드럽게 그린다. 재생/일시정지/구간이동/배속 지원.
 */
const PhysicalVideoReplayViewer = ({ replay, onClose }: Props) => {
  const N = replay.boardSize;
  const frames: PhysicalMotionFrame[] = replay.motionFrames ?? [];
  const duration = Math.max(replay.durationMs, frames.length ? frames[frames.length - 1].t : 0, 1);

  const [playT, setPlayT] = useState(0);
  const [playing, setPlaying] = useState(true);
  const [speed, setSpeed] = useState(1);

  const canvasRef = useRef<HTMLCanvasElement>(null);
  const playTRef = useRef(0);
  const playingRef = useRef(true);
  const speedRef = useRef(1);
  const lastTsRef = useRef<number | null>(null);

  useEffect(() => { playingRef.current = playing; }, [playing]);
  useEffect(() => { speedRef.current = speed; }, [speed]);

  const nameOf = useCallback(
    (color: StoneColor) =>
      replay.players.find((p) => p.color === color)?.nickname ?? (color === 'BLACK' ? '흑' : '백'),
    [replay.players],
  );

  // 색별 외형(스킨/캐릭터) — 한 판 동안 불변. 라이브와 동일한 돌/캐릭터 외형으로 렌더한다.
  const styleByColor = useMemo(() => {
    const m: Record<string, { skin: StoneStyle | null; character: CharacterStyle | null }> = {};
    for (const p of replay.players) m[p.color] = { skin: p.skin ?? null, character: p.character ?? null };
    return m;
  }, [replay.players]);

  // 시점 playT 의 보드 칸 상태 — 이벤트(시간순)를 t<=playT 까지 누적 적용.
  const cellsAt = useCallback(
    (t: number): number[][] => {
      const grid: number[][] = Array.from({ length: N }, () => Array(N).fill(0));
      for (const e of replay.events) {
        if (e.t > t) break;
        if (e.y >= 0 && e.y < N && e.x >= 0 && e.x < N) grid[e.y][e.x] = e.v;
      }
      return grid;
    },
    [replay.events, N],
  );

  const seek = useCallback((t: number) => {
    const v = Math.max(0, Math.min(duration, t));
    playTRef.current = v;
    setPlayT(v);
  }, [duration]);

  // ←/→ 5초, Space 재생/정지, Esc 닫기
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'ArrowLeft') { e.preventDefault(); seek(playTRef.current - 5000); }
      else if (e.key === 'ArrowRight') { e.preventDefault(); seek(playTRef.current + 5000); }
      else if (e.code === 'Space') { e.preventDefault(); setPlaying((p) => !p); }
      else if (e.key === 'Escape') onClose();
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [seek, onClose]);

  // 렌더/재생 루프 (rAF) — 캔버스는 ref 값으로 그리고, 슬라이더용으로만 state 갱신.
  useEffect(() => {
    let raf = 0;
    const tick = (ts: number) => {
      const last = lastTsRef.current;
      lastTsRef.current = ts;
      if (playingRef.current && last != null) {
        playTRef.current += (ts - last) * speedRef.current;
        if (playTRef.current >= duration) {
          playTRef.current = duration;
          playingRef.current = false;
          setPlaying(false);
        }
        setPlayT(playTRef.current);
      }
      draw(playTRef.current);
      raf = requestAnimationFrame(tick);
    };
    raf = requestAnimationFrame(tick);
    return () => { cancelAnimationFrame(raf); lastTsRef.current = null; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [duration, N]);

  const draw = (t: number) => {
    const canvas = canvasRef.current;
    const ctx = canvas?.getContext('2d');
    if (!ctx) return;

    // 보드/돌/분화구/아이템/캐릭터 — 모두 라이브와 같은 공용 헬퍼로 그린다(외형 100% 일치).
    const { gap, at } = drawBoardBase(ctx, N, CANVAS_PX);
    const stoneR = gap * 0.46;

    const cells = cellsAt(t);
    for (let y = 0; y < N; y++) {
      for (let x = 0; x < N; x++) {
        const v = cells[y][x];
        if (v === 3) {
          drawCrater(ctx, at(x), at(y), gap);
        } else if (v === 1 || v === 2) {
          const color: StoneColor = v === 1 ? 'BLACK' : 'WHITE';
          drawStone(ctx, at(x), at(y), stoneR, v === 1, styleByColor[color]?.skin ?? null);
        }
      }
    }

    // 보간 프레임 — 위치 트랙에서 t 를 감싸는 두 프레임을 lerp 해 캐릭터가 '실제로 움직이게' 한다.
    if (frames.length > 0) {
      let i = 0;
      while (i < frames.length - 1 && frames[i + 1].t <= t) i++;
      const f0 = frames[i];
      const f1 = frames[Math.min(i + 1, frames.length - 1)];
      const span = f1.t - f0.t;
      const a = span > 0 ? Math.min(1, Math.max(0, (t - f0.t) / span)) : 0;

      // 아이템(직전 프레임 기준) — 라이브와 동일한 모양/색
      for (const it of f0.items) {
        drawItemDrop(ctx, at(it.x), at(it.y), gap, it.type);
      }

      // 캐릭터(보간 이동) — 스킨/얼굴/보유 아이템 배지까지 라이브와 동일
      for (const p0 of f0.players) {
        const p1 = f1.players.find((q) => q.color === p0.color) ?? p0;
        const gx = p0.x + (p1.x - p0.x) * a;
        const gy = p0.y + (p1.y - p0.y) * a;
        drawCharacter(ctx, at(gx), at(gy), gap, {
          color: p0.color,
          nickname: nameOf(p0.color),
          character: styleByColor[p0.color]?.character ?? null,
          speedBoosted: p0.speedBoosted,
          heldItem: p0.heldItem,
        }, false);
      }
    }
  };

  const black = nameOf('BLACK');
  const white = nameOf('WHITE');
  const result =
    replay.winnerColor === 'BLACK' ? `${black} 승`
      : replay.winnerColor === 'WHITE' ? `${white} 승` : '무효·중단';
  const ended = playT >= duration;

  return (
    <div className={styles.backdrop} onClick={onClose}>
      <div className={styles.modal} onClick={(e) => e.stopPropagation()}>
        <div className={styles.header}>
          <div>
            <h2 className={styles.title}>
              🎬 피지컬 영상 리플레이 <span className={styles.typeBadge}>피지컬 오목</span>
            </h2>
            <p className={styles.subtitle}>
              <span className={styles.stoneB} />{black}
              <span className={styles.vs}>vs</span>
              <span className={styles.stoneW} />{white}
              <span className={styles.resultChip}>{result}</span>
              <span className={styles.meta}>길이 {fmtTime(duration)}</span>
            </p>
          </div>
          <button className={styles.close} onClick={onClose} aria-label="닫기">✕</button>
        </div>

        <div className={styles.stage}>
          <canvas ref={canvasRef} width={CANVAS_PX} height={CANVAS_PX} className={styles.canvas} />
        </div>

        <div className={styles.controls}>
          <button
            className={styles.playBtn}
            onClick={() => { if (ended) seek(0); setPlaying((p) => !p); }}
          >
            {ended ? '↻ 처음부터' : playing ? '⏸ 일시정지' : '▶ 재생'}
          </button>
          <span className={styles.time}>{fmtTime(playT)} / {fmtTime(duration)}</span>
          <input
            type="range"
            className={styles.seek}
            min={0}
            max={duration}
            value={Math.min(playT, duration)}
            onChange={(e) => seek(Number(e.target.value))}
          />
          <div className={styles.speeds}>
            {SPEEDS.map((s) => (
              <button
                key={s}
                className={`${styles.speedBtn} ${speed === s ? styles.speedOn : ''}`}
                onClick={() => setSpeed(s)}
              >
                {s}×
              </button>
            ))}
          </div>
        </div>
        <p className={styles.hint}>Space 재생/정지 · ←/→ 5초 이동 · Esc 닫기</p>
      </div>
    </div>
  );
};

export default PhysicalVideoReplayViewer;
