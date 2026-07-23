import { useRef, useState } from 'react';
import { Direction, PhysicalDirection } from '@/types';
import styles from './DirectionPad.module.css';

interface Props<D extends PhysicalDirection> {
  /** 버튼을 누른(또는 밀어 들어간) 순간 — 이동 시작 intent */
  onStart: (dir: D) => void;
  /** 버튼에서 손을 떼거나 가운데(허브)로 벗어난 순간 — 이동 정지 intent */
  onStop: () => void;
  /** 대각 버튼까지 8방향으로 조작한다. 끄면(기본) 상하좌우 4방향만. */
  diagonal?: boolean;
  /** 모양만 보여주고 입력은 받지 않는다 (게임 시작 전 등) */
  disabled?: boolean;
  /** 배치는 부모가 정한다 (오버레이/인라인) */
  className?: string;
}

// 3×3 격자 배치 — 가운데(2,2)는 허브라 비운다. area = "row / col".
const CELLS: { dir: PhysicalDirection; label: string; area: string }[] = [
  { dir: 'UP_LEFT',    label: '◤', area: '1 / 1' },
  { dir: 'UP',         label: '▲', area: '1 / 2' },
  { dir: 'UP_RIGHT',   label: '◥', area: '1 / 3' },
  { dir: 'LEFT',       label: '◀', area: '2 / 1' },
  { dir: 'RIGHT',      label: '▶', area: '2 / 3' },
  { dir: 'DOWN_LEFT',  label: '◣', area: '3 / 1' },
  { dir: 'DOWN',       label: '▼', area: '3 / 2' },
  { dir: 'DOWN_RIGHT', label: '◢', area: '3 / 3' },
];

/**
 * 방향 버튼(D-Pad). 아날로그 조이스틱과 달리 버튼을 누르는 즉시 그 방향으로 움직여
 * 키보드 방향키와 동일한 즉각 반응을 준다(데드존을 넘도록 '밀어야' 시작하는 지연이 없다).
 * 손가락을 뗴지 않고 다른 버튼으로 밀면 방향이 바로 바뀌고(pointer capture + hit-test),
 * 가운데 허브나 패드 밖으로 벗어나면 정지한다. 방향이 실제로 바뀔 때만 onStart 를 부른다.
 *
 * 제네릭 D 는 호출부가 받는 방향 집합이다 — diagonal 을 켠 곳만 대각이 섞여 들어온다.
 */
function DirectionPad<D extends PhysicalDirection = Direction>({
  onStart,
  onStop,
  diagonal = false,
  disabled,
  className,
}: Props<D>) {
  const dirRef = useRef<D | null>(null);
  const activeRef = useRef(false);
  const [active, setActive] = useState<D | null>(null); // 눌린 버튼 하이라이트(방향 바뀔 때만 갱신)

  const cells = diagonal ? CELLS : CELLS.filter((c) => !c.dir.includes('_'));

  const setDir = (next: D | null) => {
    if (next === dirRef.current) return; // 같은 방향 재전송 없음
    dirRef.current = next;
    setActive(next);
    if (next) onStart(next);
    else onStop();
  };

  // 손가락 아래의 방향 버튼을 찾는다(없으면 허브/바깥 → 정지). 슬라이드 방향 전환 지원.
  const dirAtPoint = (x: number, y: number): D | null => {
    const el = document.elementFromPoint(x, y);
    const btn = el?.closest<HTMLElement>('[data-dir]');
    return (btn?.dataset.dir as D | undefined) ?? null;
  };

  const handleDown = (e: React.PointerEvent<HTMLDivElement>) => {
    if (disabled) return;
    e.preventDefault();
    activeRef.current = true;
    // 손가락이 패드 밖으로 나가도 move/up 을 계속 받도록 캡처
    e.currentTarget.setPointerCapture(e.pointerId);
    setDir(dirAtPoint(e.clientX, e.clientY));
  };

  const handleMove = (e: React.PointerEvent<HTMLDivElement>) => {
    if (!activeRef.current) return;
    e.preventDefault();
    setDir(dirAtPoint(e.clientX, e.clientY));
  };

  const handleUp = () => {
    if (!activeRef.current) return;
    activeRef.current = false;
    setDir(null);
  };

  return (
    <div
      className={`${styles.pad} ${disabled ? styles.disabled : ''} ${className ?? ''}`}
      onPointerDown={handleDown}
      onPointerMove={handleMove}
      onPointerUp={handleUp}
      onPointerCancel={handleUp}
      onContextMenu={(e) => e.preventDefault()}
      role="application"
      aria-label="이동 패드"
    >
      {cells.map((c) => (
        <button
          key={c.dir}
          type="button"
          data-dir={c.dir}
          tabIndex={-1}
          aria-label={c.dir}
          className={`${styles.btn} ${active === c.dir ? styles.btnActive : ''}`}
          style={{ gridArea: c.area }}
        >
          {c.label}
        </button>
      ))}
      <span className={styles.hub} aria-hidden />
    </div>
  );
}

export default DirectionPad;
