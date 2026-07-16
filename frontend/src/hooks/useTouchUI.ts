import { useEffect, useState } from 'react';

const useMediaQuery = (query: string) => {
  const [matches, setMatches] = useState(() => window.matchMedia(query).matches);
  useEffect(() => {
    const mq = window.matchMedia(query);
    setMatches(mq.matches); // 구독 전에 값이 바뀌었을 수 있다
    const update = () => setMatches(mq.matches);
    mq.addEventListener('change', update);
    return () => mq.removeEventListener('change', update);
  }, [query]);
  return matches;
};

/** 마우스가 없는 진짜 터치 기기(폰·태블릿). 화면 폭은 보지 않는다. */
const COARSE = '(hover: none) and (pointer: coarse)';
const NARROW = '(max-width: 768px)';

/**
 * 터치 기기 '또는' 좁은 화면.
 * 마우스로도 쓸 수 있어서 창을 줄인 데스크톱에서도 켜져야 하는 UI에 쓴다
 * (오목판 탭 미리보기/줌, 광장 방향패드).
 */
export const useTouchUI = () => {
  const coarse = useMediaQuery(COARSE);
  const narrow = useMediaQuery(NARROW);
  return coarse || narrow;
};

/**
 * 진짜 터치 기기인지만 본다(창 폭 무관).
 * 데스크톱에서 창을 줄였다고 켜지면 안 되는 모바일 전용 UI에 쓴다 — 피지컬 가상 컨트롤은
 * 키보드로 하는 게 더 정확하므로, 키보드가 있는 기기에는 띄우지 않는다.
 */
export const useCoarsePointer = () => useMediaQuery(COARSE);
