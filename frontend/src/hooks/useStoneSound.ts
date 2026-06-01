import { useEffect, useRef, useCallback } from 'react';
import { useProtectedAsset } from './useProtectedAsset';

/**
 * 장착된 착수음(STONE_SOUND) 오디오를 백엔드에서 받아 준비하고,
 * 돌을 둘 때마다 호출할 재생 함수를 반환한다.
 *
 * assetKey가 없으면(미장착) 무음 — 반환 함수 호출 시 아무 일도 일어나지 않음.
 */
export function useStoneSound(assetKey: string | null | undefined): () => void {
  const url = useProtectedAsset('STONE_SOUND', assetKey);
  const audioRef = useRef<HTMLAudioElement | null>(null);

  useEffect(() => {
    if (!url) {
      audioRef.current = null;
      return;
    }
    const audio = new Audio(url);
    audio.preload = 'auto';
    audioRef.current = audio;
    return () => {
      audioRef.current = null;
    };
  }, [url]);

  return useCallback(() => {
    const audio = audioRef.current;
    if (!audio) return;
    // 연속 착수 시 겹치지 않도록 매번 처음부터 재생
    audio.currentTime = 0;
    audio.play().catch(() => {
      // 브라우저 자동재생 정책 등으로 실패 시 조용히 무시
    });
  }, []);
}
