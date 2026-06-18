import { useEffect, useRef } from 'react';
import { selectBgmVolume, useSettingsStore } from '@/store/settingsStore';

const START_EVENTS = ['pointerdown', 'keydown', 'touchstart'] as const;

type UseBackgroundMusicOptions = {
  enabled?: boolean;
};

/**
 * 배경음악 재생 훅.
 * - enabled 가 true 일 때만 재생한다(게임 시작 시 true 로 넘기면 대기 중엔 무음).
 * - 볼륨은 설정(전체 × BGM)에서 읽어 슬라이더 변경 시 즉시 반영된다.
 * - 브라우저 자동재생 정책상 사용자 첫 상호작용 이후에만 실제로 소리가 난다.
 *   enabled 가 켜진 시점에 아직 상호작용이 없었다면, 첫 상호작용 때 재생을 시작한다.
 */
export const useBackgroundMusic = (
  src: string,
  { enabled = true }: UseBackgroundMusicOptions = {},
) => {
  const audioRef = useRef<HTMLAudioElement | null>(null);
  const interactedRef = useRef(false);
  const volume = useSettingsStore(selectBgmVolume);

  // 오디오 엘리먼트는 src 당 한 번만 생성/정리한다.
  useEffect(() => {
    if (!src) return;
    const audio = new Audio(src);
    audio.loop = true;
    audio.preload = 'none';
    audioRef.current = audio;
    return () => {
      audio.pause();
      audio.removeAttribute('src');
      audio.load();
      audioRef.current = null;
    };
  }, [src]);

  // 설정에서 볼륨이 바뀌면 즉시 반영(오디오 재생성 없이)
  useEffect(() => {
    if (audioRef.current) audioRef.current.volume = volume;
  }, [volume]);

  // enabled 토글에 따라 재생/정지. 자동재생 정책 때문에 첫 상호작용을 기다릴 수 있음.
  useEffect(() => {
    const audio = audioRef.current;
    if (!audio) return;

    if (!enabled) {
      audio.pause();
      return;
    }

    if (interactedRef.current) {
      audio.play().catch(() => {});
      return;
    }

    // 아직 사용자 상호작용 전 — 첫 상호작용 때 재생 시작(enabled=true 인 effect 인스턴스에서만 등록)
    const onInteract = () => {
      interactedRef.current = true;
      START_EVENTS.forEach((eventName) => window.removeEventListener(eventName, onInteract));
      audio.play().catch(() => {});
    };
    START_EVENTS.forEach((eventName) => {
      window.addEventListener(eventName, onInteract, { passive: true });
    });
    return () => {
      START_EVENTS.forEach((eventName) => window.removeEventListener(eventName, onInteract));
    };
  }, [enabled]);
};
