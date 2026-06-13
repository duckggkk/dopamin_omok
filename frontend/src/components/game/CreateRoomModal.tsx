import { useState } from 'react';
import { CreateRoomOptions } from '@/api/game';
import { GameType, OmokRule, TimeLimit, ByoyomiOption } from '@/types';
import styles from './CreateRoomModal.module.css';

const GAME_TYPE_OPTIONS: { key: GameType; label: string; desc: string }[] = [
  { key: 'CLASSIC', label: '⚫ 일반 오목', desc: '번갈아 한 수씩 두는 표준 오목' },
  { key: 'PHYSICAL', label: '⚔️ 피지컬 오목', desc: '실시간으로 움직이며 두는 액션 모드' },
];

// 클래식 오목의 규칙 변형. 렌주룰은 흑(선)에 금수를 적용해 선 유리를 상쇄한다.
const OMOK_RULE_OPTIONS: { key: OmokRule; label: string }[] = [
  { key: 'FREESTYLE', label: '🆓 일반 (자유룰)' },
  { key: 'RENJU', label: '⚖️ 렌주룰' },
];

const TIME_LIMIT_LABELS: Record<TimeLimit, string> = {
  UNLIMITED: '무제한',
  ONE_MIN: '1분',
  THREE_MIN: '3분',
  FIVE_MIN: '5분',
  TEN_MIN: '10분',
};

const BYOYOMI_LABELS: Record<ByoyomiOption, string> = {
  NONE: '없음',
  TEN_SEC: '10초',
  FIFTEEN_SEC: '15초',
  THIRTY_SEC: '30초',
};

export const DEFAULT_ROOM_OPTIONS: CreateRoomOptions = {
  gameType: 'CLASSIC',
  omokRule: 'FREESTYLE',
  timeLimit: 'UNLIMITED',
  byoyomiOption: 'NONE',
};

interface Props {
  /** 모달이 열릴 때의 초기 게임 타입(예: 홈에서 '피지컬'로 바로 열기). 기본 일반. */
  initialGameType?: GameType;
  busy?: boolean;
  onConfirm: (options: CreateRoomOptions) => void;
  onClose: () => void;
}

const CreateRoomModal = ({ initialGameType = 'CLASSIC', busy = false, onConfirm, onClose }: Props) => {
  const [options, setOptions] = useState<CreateRoomOptions>({
    ...DEFAULT_ROOM_OPTIONS,
    gameType: initialGameType,
  });

  // 피지컬 오목은 실시간 액션이라 제한시간/초읽기 개념이 없다 → 선택 시 강제로 비활성.
  const isPhysical = options.gameType === 'PHYSICAL';

  const selectGameType = (gameType: GameType) =>
    setOptions((o) =>
      gameType === 'PHYSICAL'
        ? { gameType, omokRule: 'FREESTYLE', timeLimit: 'UNLIMITED', byoyomiOption: 'NONE' }
        : { ...o, gameType });

  return (
    <div className={styles.modalBackdrop} onClick={onClose}>
      <div className={styles.modal} onClick={(e) => e.stopPropagation()}>
        <h2 className={styles.modalTitle}>방 만들기</h2>

        <div className={styles.formGroup}>
          <label className={styles.formLabel}>게임 타입</label>
          <div className={styles.typeGroup}>
            {GAME_TYPE_OPTIONS.map((opt) => (
              <button
                key={opt.key}
                type="button"
                className={`${styles.typeCard} ${options.gameType === opt.key ? styles.typeSelected : ''}`}
                onClick={() => selectGameType(opt.key)}
              >
                <span className={styles.typeName}>{opt.label}</span>
                <span className={styles.typeDesc}>{opt.desc}</span>
              </button>
            ))}
          </div>
        </div>

        {!isPhysical && (
          <div className={styles.formGroup}>
            <label className={styles.formLabel}>오목 규칙</label>
            <div className={styles.typeGroup}>
              {OMOK_RULE_OPTIONS.map((opt) => (
                <button
                  key={opt.key}
                  type="button"
                  className={`${styles.typeCard} ${options.omokRule === opt.key ? styles.typeSelected : ''}`}
                  onClick={() => setOptions((o) => ({ ...o, omokRule: opt.key }))}
                >
                  <span className={styles.typeName}>{opt.label}</span>
                </button>
              ))}
            </div>
          </div>
        )}

        <div className={`${styles.formGroup} ${isPhysical ? styles.disabledGroup : ''}`}>
          <label className={styles.formLabel}>제한 시간 (1수당)</label>
          <div className={styles.radioGroup}>
            {(Object.keys(TIME_LIMIT_LABELS) as TimeLimit[]).map((key) => (
              <label key={key} className={`${styles.radioOption} ${options.timeLimit === key ? styles.radioSelected : ''}`}>
                <input type="radio" name="timeLimit" value={key} checked={options.timeLimit === key} disabled={isPhysical}
                  onChange={() => setOptions((o) => ({ ...o, timeLimit: key }))} />
                {TIME_LIMIT_LABELS[key]}
              </label>
            ))}
          </div>
        </div>

        <div className={`${styles.formGroup} ${isPhysical ? styles.disabledGroup : ''}`}>
          <label className={styles.formLabel}>초읽기</label>
          <div className={styles.radioGroup}>
            {(Object.keys(BYOYOMI_LABELS) as ByoyomiOption[]).map((key) => (
              <label key={key} className={`${styles.radioOption} ${options.byoyomiOption === key ? styles.radioSelected : ''}`}>
                <input type="radio" name="byoyomiOption" value={key} checked={options.byoyomiOption === key} disabled={isPhysical}
                  onChange={() => setOptions((o) => ({ ...o, byoyomiOption: key }))} />
                {BYOYOMI_LABELS[key]}
              </label>
            ))}
          </div>
        </div>

        {isPhysical && <p className={styles.hint}>피지컬 오목은 실시간 액션 모드라 제한시간 설정이 없습니다.</p>}

        <div className={styles.modalActions}>
          <button onClick={onClose} className={styles.cancelBtn}>취소</button>
          <button onClick={() => onConfirm(options)} disabled={busy} className={styles.confirmBtn}>
            {busy ? '생성 중...' : '방 만들기'}
          </button>
        </div>
      </div>
    </div>
  );
};

export default CreateRoomModal;
