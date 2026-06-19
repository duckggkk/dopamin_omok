import { useCallback, useRef } from 'react';
import { resolveAssetUrl } from './useProtectedAsset';
import { selectStoneVolume, useSettingsStore } from '@/store/settingsStore';
import { playSfx } from '@/utils/sfx';

/**
 * assetKey 별로 착수음을 재생하는 함수를 반환한다.
 * 착수 메시지에 실려온 "둔 사람의 착수음 assetKey"를 그대로 재생하므로,
 * 내 수든 상대 수든 그 수를 둔 사람의 소리가 양쪽 클라이언트에서 동일하게 들린다.
 *
 * assetKey 가 없으면(미장착/AI 등): 설정(stoneFallbackSound)이 켜져 있고 착수음이 음소거가 아니면
 * 기본 합성음(Web Audio)으로 폴백한다 — 이 덕에 일반전·AI전 착수음이 통일된다(스킨 있으면 그 소리, 없으면 기본음).
 */
export function useStoneSoundPlayer(): (assetKey: string | null | undefined) => void {
  const audioCache = useRef<Map<string, HTMLAudioElement>>(new Map());

  const playCached = (audio: HTMLAudioElement) => {
    // 재생 직전 최신 설정 볼륨(전체 × 착수음)을 반영
    audio.volume = selectStoneVolume(useSettingsStore.getState());
    audio.currentTime = 0;
    // 자동재생 정책 등으로 실패 시 조용히 무시
    audio.play().catch(() => {});
  };

  return useCallback((assetKey: string | null | undefined) => {
    if (!assetKey) {
      const s = useSettingsStore.getState();
      if (s.stoneFallbackSound && selectStoneVolume(s) > 0) playSfx('place');
      return;
    }

    const cached = audioCache.current.get(assetKey);
    if (cached) {
      playCached(cached);
      return;
    }

    resolveAssetUrl('STONE_SOUND', assetKey)
      .then((url) => {
        let audio = audioCache.current.get(assetKey);
        if (!audio) {
          audio = new Audio(url);
          audio.preload = 'auto';
          audioCache.current.set(assetKey, audio);
        }
        playCached(audio);
      })
      .catch(() => {});
  }, []);
}
