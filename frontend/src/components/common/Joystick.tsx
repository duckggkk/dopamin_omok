import { useRef } from 'react';
import { Direction, PhysicalDirection } from '@/types';
import styles from './Joystick.module.css';

interface Props<D extends PhysicalDirection> {
  /** 스틱이 어느 방향으로 기울어진 순간 (이동 시작 intent) */
  onStart: (dir: D) => void;
  /** 스틱이 중앙(데드존)으로 돌아온 순간 (이동 정지 intent) */
  onStop: () => void;
  /** 대각까지 8방향으로 인식한다. 끄면(기본) 가장 가까운 축으로 스냅해 상하좌우만 낸다. */
  diagonal?: boolean;
  /** 모양만 보여주고 입력은 받지 않는다 (게임 시작 전 등) */
  disabled?: boolean;
  /** 배치는 부모가 정한다 (오버레이/인라인) */
  className?: string;
}

const MAX_R = 46; // 노브가 중심에서 벗어날 수 있는 최대 거리(px)
const DEAD_ZONE = 14; // 이 반경 안에서는 정지 — 손가락 미세 떨림으로 캐릭터가 새지 않게
const SPRING_BACK = 'transform 0.12s ease-out';

// atan2 결과(=오른쪽 0, 시계방향으로 증가)를 45°씩 8등분한 순서.
const OCTANTS: PhysicalDirection[] = [
  'RIGHT', 'DOWN_RIGHT', 'DOWN', 'DOWN_LEFT', 'LEFT', 'UP_LEFT', 'UP', 'UP_RIGHT',
];

/** 스틱이 기운 각도를 가장 가까운 방향으로 스냅한다(4방향이면 축, 8방향이면 45° 간격). */
const toDirection = (dx: number, dy: number, diagonal: boolean): PhysicalDirection => {
  if (!diagonal) {
    return Math.abs(dx) > Math.abs(dy) ? (dx > 0 ? 'RIGHT' : 'LEFT') : dy > 0 ? 'DOWN' : 'UP';
  }
  // -4..4 로 나온 옥탄트 번호를 0..7 로 접는다(-4 & 7 === 4).
  return OCTANTS[Math.round(Math.atan2(dy, dx) / (Math.PI / 4)) & 7];
};

/**
 * 모바일 게임식 아날로그 패드. 방향 전환은 손가락을 떼지 않고 이어지며,
 * 방향이 실제로 바뀔 때만 onStart 를 부른다(같은 방향 재전송 없음).
 *
 * 제네릭 D 는 호출부가 받는 방향 집합이다 — diagonal 을 켠 곳만 대각이 섞여 들어오므로
 * 4방향만 처리하는 화면(광장)이 실수로 대각을 받는 일이 없다.
 */
function Joystick<D extends PhysicalDirection = Direction>({
  onStart,
  onStop,
  diagonal = false,
  disabled,
  className,
}: Props<D>) {
  const baseRef = useRef<HTMLDivElement>(null);
  const knobRef = useRef<HTMLDivElement>(null);
  const pointerIdRef = useRef<number | null>(null);
  const dirRef = useRef<D | null>(null);
  const originRef = useRef({ x: 0, y: 0 });

  // 노브는 매 프레임 움직이므로 리렌더 없이 DOM 을 직접 만진다.
  const moveKnob = (dx: number, dy: number) => {
    if (knobRef.current) knobRef.current.style.transform = `translate(${dx}px, ${dy}px)`;
  };

  const setDir = (next: D | null) => {
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
    // diagonal 플래그가 D 의 범위를 결정하므로 여기서만 좁힌다.
    setDir(dist < DEAD_ZONE ? null : (toDirection(dx, dy, diagonal) as D));
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
      {diagonal && (
        <>
          <span className={`${styles.hint} ${styles.hintDiag} ${styles.hintUpLeft}`}>◤</span>
          <span className={`${styles.hint} ${styles.hintDiag} ${styles.hintUpRight}`}>◥</span>
          <span className={`${styles.hint} ${styles.hintDiag} ${styles.hintDownLeft}`}>◣</span>
          <span className={`${styles.hint} ${styles.hintDiag} ${styles.hintDownRight}`}>◢</span>
        </>
      )}
      <div ref={knobRef} className={styles.knob} />
    </div>
  );
}

export default Joystick;
