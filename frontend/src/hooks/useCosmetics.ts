import { useEffect, useState } from 'react';
import { shopApi } from '@/api/shop';
import { ItemConfig, StoneStyle } from '@/types';

/** 내가 장착한 코스메틱(서버 권위 인벤토리에서 해석). 미장착 항목은 null. */
export interface EquippedCosmetics {
  boardSkinConfig: ItemConfig | null; // 바둑판 스킨 설정(색/필터/이미지)
  stoneSkin: StoneStyle | null;       // 바둑알 스킨 색
  stoneEffect: string | null;         // 착수 효과 키(예: 'bounce') — STONE_EFFECT 또는 고급 스킨 번들
  stoneSoundKey: string | null;       // 착수음 assetKey
  defeatEffect: string | null;        // 승/패 이펙트 키(예: 'flame')
}

const EMPTY: EquippedCosmetics = {
  boardSkinConfig: null,
  stoneSkin: null,
  stoneEffect: null,
  stoneSoundKey: null,
  defeatEffect: null,
};

/**
 * 장착된 코스메틱 일체를 인벤토리 1회 조회로 로드한다(미장착 시 각 항목 null → 기본값 폴백).
 * 착수음은 "둔 사람의 소리"를 착수 메시지로 받아 재생하는 게 일반전 규칙이지만(useStoneSoundPlayer),
 * AI전처럼 서버를 안 거치는 경우엔 여기서 읽은 내 stoneSoundKey 를 직접 넘겨 같은 소리를 낸다.
 */
export function useCosmetics(): EquippedCosmetics {
  const [cosmetics, setCosmetics] = useState<EquippedCosmetics>(EMPTY);

  useEffect(() => {
    shopApi
      .getInventory()
      .then((res) => {
        const active = res.data.data?.activeItems ?? {};
        const cfg = (type: string): ItemConfig | null => active[type]?.itemConfig ?? null;
        setCosmetics({
          boardSkinConfig: cfg('BOARD_SKIN'),
          stoneSkin: cfg('STONE_SKIN')?.stone ?? null,
          // 착수 효과는 전용 아이템(STONE_EFFECT) 또는 고급 바둑알 스킨에 번들될 수 있다.
          stoneEffect: cfg('STONE_EFFECT')?.effect ?? cfg('STONE_SKIN')?.effect ?? null,
          stoneSoundKey: cfg('STONE_SOUND')?.assetKey ?? null,
          defeatEffect: cfg('DEFEAT_EFFECT')?.effect ?? null,
        });
      })
      .catch(() => {});
  }, []);

  return cosmetics;
}
