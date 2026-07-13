import { useEffect, useState } from 'react';

/**
 * 터치 화면(호버 없는 기기) 또는 좁은 화면 여부.
 * 오목판 탭 미리보기/줌, 광장·피지컬 가상 컨트롤 등 모바일 전용 UI의 노출 조건으로 쓴다.
 */
export const useTouchUI = () => {
  const [touchUI, setTouchUI] = useState(
    () => window.matchMedia('(hover: none) and (pointer: coarse)').matches
      || window.matchMedia('(max-width: 768px)').matches,
  );
  useEffect(() => {
    const coarse = window.matchMedia('(hover: none) and (pointer: coarse)');
    const narrow = window.matchMedia('(max-width: 768px)');
    const update = () => setTouchUI(coarse.matches || narrow.matches);
    coarse.addEventListener('change', update);
    narrow.addEventListener('change', update);
    return () => {
      coarse.removeEventListener('change', update);
      narrow.removeEventListener('change', update);
    };
  }, []);
  return touchUI;
};
