import { useEffect, useState } from 'react';
import { shopApi } from '@/api/shop';
import { ShopItem, StoneColor, StoneStyle } from '@/types';
import {
  DEFAULT_BLACK_STONE,
  DEFAULT_WHITE_STONE,
  areSameStoneSkin,
  stonePreviewStyle,
} from '@/utils/stoneSkin';
import styles from '@/pages/GamePage.module.css';

interface StoneSkinPickerProps {
  /** 현재 장착 중인 바둑알 스킨(미장착=기본 스킨이면 null) */
  currentSkin: StoneStyle | null;
  /** 내 돌 색 — 기본 스킨 미리보기를 흑/백에 맞춰 보여주기 위함 */
  myColor: StoneColor | null;
  /** itemId=null 이면 기본 스킨으로 되돌림 */
  onSelect: (itemId: number | null) => void;
  onClose: () => void;
}

/**
 * 대기 중 방에서 자신의 바둑알 스킨을 고르는 모달.
 * 첫 칸은 항상 '기본 스킨'(장착 해제), 그 뒤로 보유한 STONE_SKIN 을 보여준다.
 */
const StoneSkinPicker = ({ currentSkin, myColor, onSelect, onClose }: StoneSkinPickerProps) => {
  const [skins, setSkins] = useState<ShopItem[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let active = true;
    shopApi
      .getInventory()
      .then((res) => {
        if (!active) return;
        const items = res.data.data?.items ?? [];
        setSkins(items.filter((it) => it.itemType === 'STONE_SKIN' && it.itemConfig?.stone));
      })
      .catch(() => {})
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, []);

  const defaultStone = myColor === 'WHITE' ? DEFAULT_WHITE_STONE : DEFAULT_BLACK_STONE;
  const isDefaultCurrent = !currentSkin;

  return (
    <div className={styles.profileModalBackdrop} onClick={onClose}>
      <div className={styles.skinPicker} onClick={(e) => e.stopPropagation()}>
        <button className={styles.profileModalClose} onClick={onClose} aria-label="닫기">
          ✕
        </button>
        <h3 className={styles.skinPickerTitle}>바둑알 스킨 선택</h3>

        {loading && <p className={styles.profileModalMsg}>불러오는 중...</p>}

        {!loading && (
          <div className={styles.skinPickerGrid}>
            {/* 기본 스킨 — 항상 첫 칸 */}
            <button
              type="button"
              className={`${styles.skinPickerItem} ${isDefaultCurrent ? styles.skinPickerItemOn : ''}`}
              onClick={() => onSelect(null)}
              disabled={isDefaultCurrent}
              title={isDefaultCurrent ? '현재 장착 중' : '기본 스킨으로 변경'}
            >
              <span className={styles.skinPickerStone} style={stonePreviewStyle(defaultStone)} />
              <span className={styles.skinPickerName}>기본 스킨</span>
              {isDefaultCurrent && <span className={styles.skinPickerBadge}>장착중</span>}
            </button>

            {skins.map((it) => {
              const isCurrent = areSameStoneSkin(currentSkin, it.itemConfig?.stone);
              return (
                <button
                  key={it.id}
                  type="button"
                  className={`${styles.skinPickerItem} ${isCurrent ? styles.skinPickerItemOn : ''}`}
                  onClick={() => onSelect(it.id)}
                  disabled={isCurrent}
                  title={isCurrent ? '현재 장착 중' : '이 스킨으로 변경'}
                >
                  <span className={styles.skinPickerStone} style={stonePreviewStyle(it.itemConfig?.stone)} />
                  <span className={styles.skinPickerName}>
                    {it.itemConfig?.displayName ?? it.displayName ?? it.name}
                  </span>
                  {isCurrent && <span className={styles.skinPickerBadge}>장착중</span>}
                </button>
              );
            })}
          </div>
        )}
      </div>
    </div>
  );
};

export default StoneSkinPicker;
