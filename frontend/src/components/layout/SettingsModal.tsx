import { createPortal } from 'react-dom';
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
    stoneFallbackSound,
    setMasterVolume,
    setBgmVolume,
    setStoneVolume,
    setStoneFallbackSound,
  } = useSettingsStore();

  const rows: { label: string; value: number; set: (v: number) => void }[] = [
    { label: '전체 음량', value: masterVolume, set: setMasterVolume },
    { label: '배경음악(BGM)', value: bgmVolume, set: setBgmVolume },
    { label: '바둑알 착수음', value: stoneVolume, set: setStoneVolume },
  ];

  // 네비바에 걸린 backdrop-filter가 position:fixed의 기준 박스가 되어 모달이 위로 딸려
  // 올라가는 문제가 있어, 포털로 body 직하위에 렌더해 뷰포트 기준 중앙 정렬을 보장한다.
  return createPortal(
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

        <label className={styles.toggle}>
          <span className={styles.toggleLabel}>기본 착수음 (착수음 스킨 미장착 시)</span>
          <input
            type="checkbox"
            className={styles.check}
            checked={stoneFallbackSound}
            onChange={(e) => setStoneFallbackSound(e.target.checked)}
          />
        </label>

        <p className={styles.hint}>
          실제 볼륨은 전체 음량 × 각 항목으로 적용됩니다.
          기본 착수음을 켜면 스킨이 없어도 합성음이 나며 일반전·AI전이 동일하게 들립니다.
        </p>
      </div>
    </div>,
    document.body,
  );
};

export default SettingsModal;
