import { create } from 'zustand';
import { persist } from 'zustand/middleware';

/**
 * 사운드 설정(0~1). 실제 재생 볼륨 = 전체(master) × 채널(bgm/stone).
 * - master: 전체 음량
 * - bgm: 배경음악
 * - stone: 바둑알 착수음
 * 기본값은 기존 체감 볼륨(BGM ~0.2, 착수음 1.0)에 맞춰 둔다.
 */
interface SettingsState {
  masterVolume: number;
  bgmVolume: number;
  stoneVolume: number;
  setMasterVolume: (v: number) => void;
  setBgmVolume: (v: number) => void;
  setStoneVolume: (v: number) => void;
}

const clamp01 = (v: number) => Math.min(1, Math.max(0, v));

export const useSettingsStore = create<SettingsState>()(
  persist(
    (set) => ({
      masterVolume: 1,
      bgmVolume: 0.2,
      stoneVolume: 1,
      setMasterVolume: (v) => set({ masterVolume: clamp01(v) }),
      setBgmVolume: (v) => set({ bgmVolume: clamp01(v) }),
      setStoneVolume: (v) => set({ stoneVolume: clamp01(v) }),
    }),
    { name: 'omok-settings' },
  ),
);

/** 배경음악 실제 볼륨(전체 × BGM) */
export const selectBgmVolume = (s: SettingsState) => clamp01(s.masterVolume * s.bgmVolume);
/** 착수음 실제 볼륨(전체 × 착수음) */
export const selectStoneVolume = (s: SettingsState) => clamp01(s.masterVolume * s.stoneVolume);
