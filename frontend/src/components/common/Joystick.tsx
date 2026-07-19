import { useRef } from 'react';
import { Direction } from '@/types';
import styles from './Joystick.module.css';

interface Props {
  /** 스틱이 어느 방향으로 기울어진 순간 (이동 시작 intent) */
  onStart: (dir: Direction) => void;
  /** 스틱이 중앙(데드존)으로 돌아온 순간 (이동 정지 intent) */
  onStop: () => void;
  /** 모양만 보여주고 입력은 받지 않는다 (게임 시작 전 등) */
  disabled?: boolean;
  /** 배치는 부모가 정한다 (오버레이/인라인) */
  className?: string;
}

const MAX_R = 46; // 노브가 중심에서 벗어날 수 있는 최대 거리(px)
const DEAD_ZONE = 14; // 이 반경 안에서는 정지 — 손가락 미세 떨림으로 캐릭터가 새지 않게
const SPRING_BACK = 'transform 0.12s ease-out';

// 서버는 4방향만 받으므로 스틱이 기운 각도를 가장 가까운 축으로 스냅한다.
const toDirection = (dx: number, dy: number): Direction =>
  Math.abs(dx) > Math.abs(dy) ? (dx > 0 ? 'RIGHT' : 'LEFT') : dy > 0 ? 'DOWN' : 'UP';

/**
 * 모바일 게임식 아날로그 패드. 방향 전환은 손가락을 떼지 않고 이어지며,
 * 방향이 실제로 바뀔 때만 onStart 를 부른다(같은 방향 재전송 없음).
 */
const Joystick = ({ onStart, onStop, disabled, className }: Props) => {
  const baseRef = useRef<HTMLDivElement>(null);
  const knobRef = useRef<HTMLDivElement>(null);
  const pointerIdRef = useRef<number | null>(null);
  const dirRef = useRef<Direction | null>(null);
  const originRef = useRef({ x: 0, y: 0 });

  // 노브는 매 프레임 움직이므로 리렌더 없이 DOM 을 직접 만진다.
  const moveKnob = (dx: number, dy: number) => {
    if (knobRef.current) knobRef.current.style.transform = `translate(${dx}px, ${dy}px)`;
  };

  const setDir = (next: Direction | null) => {
    if (next === dirRef.current) return;
    dirRef.current = next;
    baseRef.current?.classList.toggle(styles.active, next !== null);
    if (next) onStart(next);
    else onStop();
  };

  const track = (clientX: number, clientY: number) => {
    let dx = clientX - originRef.current.x;
    let dy = clientY - originRef.current.y;
    const dist = Math.hypot(dx, dy);
    if (dist > MAX_R) {
      dx = (dx / dist) * MAX_R;
      dy = (dy / dist) * MAX_R;
    }
    moveKnob(dx, dy);
    setDir(dist < DEAD_ZONE ? null : toDirection(dx, dy));
  };

  const handleDown = (e: React.PointerEvent<HTMLDivElement>) => {
    if (disabled || !baseRef.current) return;
    e.preventDefault();
    const rect = baseRef.current.getBoundingClientRect();
    originRef.current = { x: rect.left + rect.width / 2, y: rect.top + rect.height / 2 };
    pointerIdRef.current = e.pointerId;
    // 손가락이 패드 밖으로 나가도 이 요소가 계속 이벤트를 받도록 캡처
    e.currentTarget.setPointerCapture(e.pointerId);
    if (knobRef.current) knobRef.current.style.transition = 'none'; // 끄는 중엔 노브가 손가락에 붙어야 한다
    track(e.clientX, e.clientY);
  };

  const handleMove = (e: React.PointerEvent<HTMLDivElement>) => {
    if (pointerIdRef.current !== e.pointerId) return;
    e.preventDefault();
    track(e.clientX, e.clientY);
  };

  const handleUp = (e: React.PointerEvent<HTMLDivElement>) => {
    if (pointerIdRef.current !== e.pointerId) return;
    pointerIdRef.current = null;
    if (knobRef.current) knobRef.current.style.transition = SPRING_BACK;
    moveKnob(0, 0);
    setDir(null);
  };

  return (
    <div
      ref={baseRef}
      className={`${styles.base} ${disabled ? styles.disabled : ''} ${className ?? ''}`}
      onPointerDown={handleDown}
      onPointerMove={handleMove}
      onPointerUp={handleUp}
      onPointerCancel={handleUp}
      onContextMenu={(e) => e.preventDefault()}
      role="application"
      aria-label="이동 패드"
    >
      <span className={`${styles.hint} ${styles.hintUp}`}>▲</span>
      <span className={`${styles.hint} ${styles.hintLeft}`}>◀</span>
      <span className={`${styles.hint} ${styles.hintRight}`}>▶</span>
      <span className={`${styles.hint} ${styles.hintDown}`}>▼</span>
      <div ref={knobRef} className={styles.knob} />
    </div>
  );
};

export default Joystick;
