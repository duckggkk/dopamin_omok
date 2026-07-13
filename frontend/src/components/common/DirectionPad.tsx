import { useRef } from 'react';
import { Direction } from '@/types';
import styles from './DirectionPad.module.css';

interface Props {
  /** 방향 버튼을 누르는 순간 (이동 시작 intent) */
  onStart: (dir: Direction) => void;
  /** 모든 방향 버튼에서 손을 뗀 순간 (이동 정지 intent) */
  onStop: () => void;
  /** 배치는 부모가 정한다 (오버레이/인라인) */
  className?: string;
}

const ARROWS: { dir: Direction; label: string; cell: string }[] = [
  { dir: 'UP', label: '▲', cell: styles.up },
  { dir: 'LEFT', label: '◀', cell: styles.left },
  { dir: 'RIGHT', label: '▶', cell: styles.right },
  { dir: 'DOWN', label: '▼', cell: styles.down },
];

/**
 * 모바일 가상 방향패드. 키보드의 keydown/keyup 스택과 같은 규칙:
 * 여러 손가락이 눌려 있으면 마지막으로 누른 방향이 이기고, 다 떼면 정지.
 */
const DirectionPad = ({ onStart, onStop, className }: Props) => {
  // pointerId → 누르고 있는 방향 (Map 은 삽입 순서를 유지한다)
  const activeRef = useRef(new Map<number, Direction>());

  const press = (dir: Direction) => (e: React.PointerEvent<HTMLButtonElement>) => {
    e.preventDefault();
    // 손가락이 버튼 밖으로 미끄러져도 pointerup 을 이 버튼이 받도록 캡처
    e.currentTarget.setPointerCapture(e.pointerId);
    activeRef.current.set(e.pointerId, dir);
    onStart(dir);
  };

  const release = (e: React.PointerEvent<HTMLButtonElement>) => {
    if (!activeRef.current.delete(e.pointerId)) return;
    const remaining = [...activeRef.current.values()];
    if (remaining.length > 0) onStart(remaining[remaining.length - 1]);
    else onStop();
  };

  return (
    <div
      className={`${styles.pad} ${className ?? ''}`}
      onContextMenu={(e) => e.preventDefault()}
    >
      {ARROWS.map((a) => (
        <button
          key={a.dir}
          type="button"
          className={`${styles.btn} ${a.cell}`}
          onPointerDown={press(a.dir)}
          onPointerUp={release}
          onPointerCancel={release}
          aria-label={a.dir}
        >
          {a.label}
        </button>
      ))}
    </div>
  );
};

export default DirectionPad;
