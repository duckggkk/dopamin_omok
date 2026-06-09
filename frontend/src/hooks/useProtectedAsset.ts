import { useState, useEffect, useRef } from 'react';
import client from '@/api/client';
import { ItemType } from '@/types';

// 모듈 레벨 캐시 — 같은 에셋 중복 요청 방지 (key: "itemType/assetKey")
const urlCache = new Map<string, string>();
const inFlight = new Map<string, Promise<string>>();

const cacheKey = (itemType: ItemType, assetKey: string) => `${itemType}/${assetKey}`;

/**
 * 백엔드 /api/assets/{itemType}/{assetKey} 에서 보호 에셋(이미지/오디오)을 인증 후 가져온다.
 *
 * 백엔드 응답 타입에 따라 자동 분기:
 *   - image/* · audio/* (classpath 바이너리) → Blob → objectURL 반환
 *   - application/json ({ url })             → S3 서명 URL 그대로 반환
 *
 * assetKey가 없으면(필터 스킨, 기본 스킨, 미장착 등) 요청하지 않고 null 반환.
 */
export async function resolveAssetUrl(itemType: ItemType, assetKey: string): Promise<string> {
  const key = cacheKey(itemType, assetKey);
  if (urlCache.has(key)) return urlCache.get(key)!;
  if (inFlight.has(key)) return inFlight.get(key)!;

  const path = `/assets/${itemType.toLowerCase()}/${assetKey}`;
  const promise = client
    .get(path, { responseType: 'blob' })
    .then((res) => {
      const contentType = String(res.headers['content-type'] ?? '');
      let urlPromise: Promise<string>;

      if (contentType.includes('application/json')) {
        // S3 서명 URL 방식 — JSON 파싱 후 URL 그대로 사용
        urlPromise = (res.data as Blob).text().then((text) => {
          const { url } = JSON.parse(text) as { url: string };
          return url;
        });
      } else {
        // classpath 바이너리 방식 — Blob → objectURL
        urlPromise = Promise.resolve(URL.createObjectURL(res.data as Blob));
      }

      return urlPromise.then((url) => {
        urlCache.set(key, url);
        inFlight.delete(key);
        return url;
      });
    })
    .catch((err) => {
      inFlight.delete(key);
      throw err;
    });

  inFlight.set(key, promise);
  return promise;
}

export function useProtectedAsset(
  itemType: ItemType,
  assetKey: string | null | undefined,
): string | null {
  const [url, setUrl] = useState<string | null>(() =>
    assetKey ? urlCache.get(cacheKey(itemType, assetKey)) ?? null : null,
  );
  const activeKey = useRef<string | null>(null);

  useEffect(() => {
    // assetKey 없으면 fetch 불필요
    if (!assetKey) {
      setUrl(null);
      return;
    }
    const key = cacheKey(itemType, assetKey);
    if (activeKey.current === key && url !== null) return;
    activeKey.current = key;

    if (urlCache.has(key)) {
      setUrl(urlCache.get(key)!);
      return;
    }

    let cancelled = false;
    resolveAssetUrl(itemType, assetKey)
      .then((resolved) => { if (!cancelled) setUrl(resolved); })
      .catch(() => { if (!cancelled) setUrl(null); });

    return () => { cancelled = true; };
  }, [itemType, assetKey]);

  return url;
}
