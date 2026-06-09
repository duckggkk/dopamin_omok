import { useEffect, useState } from 'react';
import { shopApi } from '@/api/shop';
import { ItemConfig } from '@/types';

/**
 * 장착된 바둑판 스킨을 로드한다(미장착 시 null → 기본값 폴백).
 * 착수음은 "둔 사람의 소리"를 착수 메시지(soundAssetKey)로 받아 재생하므로 여기서 다루지 않는다.
 * (useStoneSoundPlayer 참고)
 */
export function useCosmetics() {
  const [boardSkinConfig, setBoardSkinConfig] = useState<ItemConfig | null>(null);

  useEffect(() => {
    shopApi
      .getInventory()
      .then((res) => {
        setBoardSkinConfig(res.data.data?.activeItems?.['BOARD_SKIN']?.itemConfig ?? null);
      })
      .catch(() => {});
  }, []);

  return { boardSkinConfig };
}
