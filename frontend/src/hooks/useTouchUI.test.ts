import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { act, renderHook } from '@testing-library/react';
import { useTouchUI } from './useTouchUI';

/**
 * useTouchUI 는 "터치 기기(coarse) 또는 좁은 화면(≤768px)" 이면 true 다.
 * 두 조건을 OR 로 묶은 이유가 핵심 — 인앱 브라우저나 데스크톱 모드에서는 폰인데도
 * coarse 판정이 실패할 수 있어 화면 폭을 안전망으로 함께 본다.
 * jsdom 에는 matchMedia 가 없으므로 직접 심어 두 조건을 따로 흔들어 본다.
 */

const COARSE = '(hover: none) and (pointer: coarse)';
const NARROW = '(max-width: 768px)';

type Listener = () => void;

/** 쿼리별 매칭 결과를 제어하고, 변경 시 구독자에게 알릴 수 있는 matchMedia 스텁. */
const installMatchMedia = (initial: Record<string, boolean>) => {
  const state = { ...initial };
  const listeners = new Map<string, Set<Listener>>();

  window.matchMedia = vi.fn((query: string) => ({
    get matches() {
      return state[query] ?? false;
    },
    media: query,
    addEventListener: (_: string, cb: Listener) => {
      if (!listeners.has(query)) listeners.set(query, new Set());
      listeners.get(query)!.add(cb);
    },
    removeEventListener: (_: string, cb: Listener) => {
      listeners.get(query)?.delete(cb);
    },
    // 아래는 MediaQueryList 인터페이스를 만족시키기 위한 미사용 스텁
    onchange: null,
    addListener: () => {},
    removeListener: () => {},
    dispatchEvent: () => false,
  })) as unknown as typeof window.matchMedia;

  return {
    set(query: string, value: boolean) {
      state[query] = value;
      listeners.get(query)?.forEach((cb) => cb());
    },
    listenerCount(query: string) {
      return listeners.get(query)?.size ?? 0;
    },
  };
};

let media: ReturnType<typeof installMatchMedia>;

afterEach(() => {
  vi.restoreAllMocks();
});

describe('useTouchUI', () => {
  beforeEach(() => {
    media = installMatchMedia({ [COARSE]: false, [NARROW]: false });
  });

  it('마우스가 있는 넓은 화면(데스크톱)이면 false', () => {
    const { result } = renderHook(() => useTouchUI());
    expect(result.current).toBe(false);
  });

  it('터치 기기면 화면이 넓어도 true (태블릿)', () => {
    media = installMatchMedia({ [COARSE]: true, [NARROW]: false });
    const { result } = renderHook(() => useTouchUI());
    expect(result.current).toBe(true);
  });

  it('화면이 좁으면 터치가 아니어도 true (안전망)', () => {
    media = installMatchMedia({ [COARSE]: false, [NARROW]: true });
    const { result } = renderHook(() => useTouchUI());
    expect(result.current).toBe(true);
  });

  it('창 크기가 바뀌면 결과가 따라 바뀐다', () => {
    const { result } = renderHook(() => useTouchUI());
    expect(result.current).toBe(false);

    act(() => media.set(NARROW, true));
    expect(result.current).toBe(true);

    act(() => media.set(NARROW, false));
    expect(result.current).toBe(false);
  });

  it('언마운트하면 미디어 쿼리 구독을 해제한다', () => {
    const { unmount } = renderHook(() => useTouchUI());
    expect(media.listenerCount(COARSE)).toBe(1);
    expect(media.listenerCount(NARROW)).toBe(1);

    unmount();

    expect(media.listenerCount(COARSE)).toBe(0);
    expect(media.listenerCount(NARROW)).toBe(0);
  });
});
