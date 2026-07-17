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
 * 터치 기기 '또는' 좁은 화면이면 true — 터치 UI(가상 컨트롤·탭 미리보기 등) 노출 기준.
 * 순수 터치 판정(COARSE)만 쓰면 인앱 브라우저·데스크톱 모드·키보드/펜 연결 기기에서
 * 폰인데도 false 가 나올 수 있어, 화면 폭(NARROW)을 안전망으로 함께 본다.
 */
export const useTouchUI = () => {
  const coarse = useMediaQuery(COARSE);
  const narrow = useMediaQuery(NARROW);
  return coarse || narrow;
};
