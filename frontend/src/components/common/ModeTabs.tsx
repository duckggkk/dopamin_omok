import { StatMode } from '@/types';
import styles from './ModeTabs.module.css';

const MODES: { key: StatMode; label: string }[] = [
  { key: 'TOTAL', label: '통합' },
  { key: 'CLASSIC', label: '일반' },
  { key: 'PHYSICAL', label: '피지컬' },
];

/** 전적 통합/일반/피지컬 전환 탭 (프로필·로비·랭킹 공용). */
const ModeTabs = ({ value, onChange }: { value: StatMode; onChange: (m: StatMode) => void }) => (
  <div className={styles.tabs}>
    {MODES.map((m) => (
      <button
        key={m.key}
        type="button"
        className={value === m.key ? styles.active : styles.tab}
        onClick={() => onChange(m.key)}
      >
        {m.label}
      </button>
    ))}
  </div>
);

export default ModeTabs;
