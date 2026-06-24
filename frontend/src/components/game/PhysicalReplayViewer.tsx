import { useEffect, useMemo, useState } from 'react';
import { GameInfo, PhysicalReplay } from '@/types';
import styles from './PhysicalReplayViewer.module.css';

interface Props {
  game: GameInfo;
  replay: PhysicalReplay;
  onClose: () => void;
}

const fmtTime = (ms: number) => {
  const s = Math.floor(ms / 1000);
  return `${String(Math.floor(s / 60)).padStart(2, '0')}:${String(s % 60).padStart(2, '0')}`;
};

const PLAY_MS = 130; // 한 이벤트당 재생 간격

const PhysicalReplayViewer = ({ game, replay, onClose }: Props) => {
  const { boardSize, events } = replay;
  const total = events.length;
  const [step, setStep] = useState(total); // 처음엔 최종 국면
  const [playing, setPlaying] = useState(false);

  // 현재 step까지 이벤트를 순서대로 적용해 보드 재구성 (마지막 값이 그 칸의 상태)
  const cells = useMemo(() => {
    const grid: number[][] = Array.from({ length: boardSize }, () => Array(boardSize).fill(0));
    for (let i = 0; i < step; i++) {
      const e = events[i];
      if (e.y >= 0 && e.y < boardSize && e.x >= 0 && e.x < boardSize) grid[e.y][e.x] = e.v;
    }
    return grid;
  }, [step, events, boardSize]);

  const last = step > 0 ? events[step - 1] : null;
  const curMs = step > 0 ? events[step - 1].t : 0;

  // 자동 재생
  useEffect(() => {
    if (!playing) return;
    if (step >= total) { setPlaying(false); return; }
    const id = window.setInterval(() => {
      setStep((s) => {
        if (s >= total) { return s; }
        return s + 1;
      });
    }, PLAY_MS);
    return () => window.clearInterval(id);
  }, [playing, step, total]);

  // ←/→ 한 칸, Esc 닫기
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'ArrowLeft') { e.preventDefault(); setPlaying(false); setStep((s) => Math.max(0, s - 1)); }
      else if (e.key === 'ArrowRight') { e.preventDefault(); setPlaying(false); setStep((s) => Math.min(total, s + 1)); }
      else if (e.key === 'Escape') onClose();
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [total, onClose]);

  const togglePlay = () => {
    if (step >= total) setStep(0); // 끝이면 처음부터
    setPlaying((p) => !p);
  };

  const C = Math.max(20, Math.min(34, Math.floor(460 / boardSize)));
  const PX = boardSize * C;
  const r = C / 2 - 3;
  const center = (i: number) => i * C + C / 2;

  const black = replay.players.find((p) => p.color === 'BLACK')?.nickname ?? game.blackPlayer?.nickname ?? '흑';
  const white = replay.players.find((p) => p.color === 'WHITE')?.nickname ?? game.whitePlayer?.nickname ?? '백';
  const result =
    replay.winnerColor === 'BLACK' ? `${black} 승`
      : replay.winnerColor === 'WHITE' ? `${white} 승` : '무효·중단';

  return (
    <div className={styles.backdrop} onClick={onClose}>
      <div className={styles.modal} onClick={(e) => e.stopPropagation()}>
        <div className={styles.header}>
          <div>
            <h2 className={styles.title}>
              ⚔️ 피지컬 리플레이 <span className={styles.typeBadge}>피지컬 오목</span>
            </h2>
            <p className={styles.subtitle}>
              <span className={styles.stoneB} />{black}
              <span className={styles.vs}>vs</span>
              <span className={styles.stoneW} />{white}
              <span className={styles.resultChip}>{result}</span>
              <span className={styles.meta}>길이 {fmtTime(replay.durationMs)}</span>
            </p>
          </div>
          <button className={styles.close} onClick={onClose} aria-label="닫기">✕</button>
        </div>

        <div className={styles.boardWrap}>
          <svg width={PX} height={PX} viewBox={`0 0 ${PX} ${PX}`} className={styles.boardSvg}>
            <rect width={PX} height={PX} fill="#caa863" rx={6} />
            {Array.from({ length: boardSize + 1 }, (_, i) => (
              <g key={i} stroke="rgba(90,60,20,0.45)" strokeWidth={1}>
                <line x1={i * C} y1={0} x2={i * C} y2={PX} />
                <line x1={0} y1={i * C} x2={PX} y2={i * C} />
              </g>
            ))}
            {cells.flatMap((row, y) =>
              row.map((v, x) => {
                if (v === 0) return null;
                const cx = center(x), cy = center(y);
                const isLast = !!last && last.x === x && last.y === y;
                if (v === 3) {
                  // 분화구 — 착수 불가 구멍
                  return (
                    <g key={`c-${x}-${y}`}>
                      <circle cx={cx} cy={cy} r={r} fill="#3a2a18" />
                      <circle cx={cx} cy={cy} r={r * 0.6} fill="#1c130a" />
                    </g>
                  );
                }
                const isBlack = v === 1;
                return (
                  <g key={`s-${x}-${y}`}>
                    <circle cx={cx} cy={cy} r={r}
                      fill={isBlack ? '#1a1a1a' : '#f5f5f0'} stroke={isBlack ? '#000' : '#bbb'} strokeWidth={1}
                      style={{ filter: 'drop-shadow(0.5px 1.5px 2px rgba(0,0,0,0.4))' }} />
                    <circle cx={cx - r * 0.3} cy={cy - r * 0.32} r={r * 0.4}
                      fill={isBlack ? '#777' : '#fff'} opacity={0.5} />
                    {isLast && <circle cx={cx} cy={cy} r={3.5} fill={isBlack ? '#ff5252' : '#3a73ff'} />}
                  </g>
                );
              }),
            )}
          </svg>
        </div>

        {total === 0 ? (
          <p className={styles.state}>기록된 착수가 없습니다.</p>
        ) : (
          <>
            <div className={styles.controls}>
              <button className={styles.ctrlBtn} onClick={() => { setPlaying(false); setStep(0); }} disabled={step === 0} title="처음">⏮</button>
              <button className={styles.ctrlBtn} onClick={() => { setPlaying(false); setStep((s) => Math.max(0, s - 1)); }} disabled={step === 0} title="이전">◀</button>
              <button className={`${styles.ctrlBtn} ${styles.playBtn}`} onClick={togglePlay} title="재생/일시정지">
                {playing ? '⏸' : '▶'}
              </button>
              <button className={styles.ctrlBtn} onClick={() => { setPlaying(false); setStep((s) => Math.min(total, s + 1)); }} disabled={step === total} title="다음">▶</button>
              <button className={styles.ctrlBtn} onClick={() => { setPlaying(false); setStep(total); }} disabled={step === total} title="마지막">⏭</button>
              <input
                className={styles.slider}
                type="range" min={0} max={total} value={step}
                onChange={(e) => { setPlaying(false); setStep(Number(e.target.value)); }}
              />
            </div>
            <div className={styles.counter}>
              {fmtTime(curMs)} · {step} / {total} 수
              <span className={styles.counterHint}> · ←→ 키로 이동</span>
            </div>
          </>
        )}
      </div>
    </div>
  );
};

export default PhysicalReplayViewer;
