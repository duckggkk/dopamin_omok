import { useSettingsStore } from '@/store/settingsStore';
import styles from './SettingsModal.module.css';

interface SettingsModalProps {
  onClose: () => void;
}

const toPct = (v: number) => Math.round(v * 100);

const SettingsModal = ({ onClose }: SettingsModalProps) => {
  const {
    masterVolume,
    bgmVolume,
    stoneVolume,
    setMasterVolume,
    setBgmVolume,
    setStoneVolume,
  } = useSettingsStore();

  const rows: { label: string; value: number; set: (v: number) => void }[] = [
    { label: '전체 음량', value: masterVolume, set: setMasterVolume },
    { label: '배경음악(BGM)', value: bgmVolume, set: setBgmVolume },
    { label: '바둑알 착수음', value: stoneVolume, set: setStoneVolume },
  ];

  return (
    <div className={styles.backdrop} onClick={onClose}>
      <div className={styles.modal} onClick={(e) => e.stopPropagation()}>
        <button className={styles.close} onClick={onClose} aria-label="닫기">
          ✕
        </button>
        <h2 className={styles.title}>⚙️ 설정</h2>

        <p className={styles.sectionLabel}>🔊 소리</p>
        <div className={styles.rows}>
          {rows.map((r) => (
            <label key={r.label} className={styles.row}>
              <span className={styles.rowLabel}>{r.label}</span>
              <input
                type="range"
                min={0}
                max={100}
                value={toPct(r.value)}
                onChange={(e) => r.set(Number(e.target.value) / 100)}
                className={styles.slider}
              />
              <span className={styles.rowValue}>{toPct(r.value)}</span>
            </label>
          ))}
        </div>
        <p className={styles.hint}>실제 볼륨은 전체 음량 × 각 항목으로 적용됩니다.</p>
      </div>
    </div>
  );
};

export default SettingsModal;
