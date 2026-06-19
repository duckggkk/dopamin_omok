import { create } from 'zustand';
import { persist } from 'zustand/middleware';

/**
 * 사운드 설정(0~1). 실제 재생 볼륨 = 전체(master) × 채널(bgm/stone).
 * - master: 전체 음량
 * - bgm: 배경음악
 * - stone: 바둑알 착수음
 * 기본값은 기존 체감 볼륨(BGM ~0.2, 착수음 1.0)에 맞춰 둔다.
 *
 * stoneFallbackSound: 장착한 착수음 스킨이 없을 때 기본 합성음(Web Audio)으로 폴백할지.
 *   켜면 일반전·AI전이 통일된다(스킨 있으면 그 소리, 없으면 기본음). 끄면 미장착 시 무음.
 */
interface SettingsState {
  masterVolume: number;
  bgmVolume: number;
  stoneVolume: number;
  stoneFallbackSound: boolean;
  setMasterVolume: (v: number) => void;
  setBgmVolume: (v: number) => void;
  setStoneVolume: (v: number) => void;
  setStoneFallbackSound: (on: boolean) => void;
}

const clamp01 = (v: number) => Math.min(1, Math.max(0, v));

export const useSettingsStore = create<SettingsState>()(
  persist(
    (set) => ({
      masterVolume: 1,
      bgmVolume: 0.2,
      stoneVolume: 1,
      stoneFallbackSound: true,
      setMasterVolume: (v) => set({ masterVolume: clamp01(v) }),
      setBgmVolume: (v) => set({ bgmVolume: clamp01(v) }),
      setStoneVolume: (v) => set({ stoneVolume: clamp01(v) }),
      setStoneFallbackSound: (on) => set({ stoneFallbackSound: on }),
    }),
    { name: 'omok-settings' },
  ),
);

/** 배경음악 실제 볼륨(전체 × BGM) */
export const selectBgmVolume = (s: SettingsState) => clamp01(s.masterVolume * s.bgmVolume);
/** 착수음 실제 볼륨(전체 × 착수음) */
export const selectStoneVolume = (s: SettingsState) => clamp01(s.masterVolume * s.stoneVolume);
