# Background music assets

이 폴더의 음원은 모두 **Pixabay Content License** 로 배포되는 무료 음원입니다.
상세 출처와 라이선스 설명은 [docs/music-from.md](../../../docs/music-from.md) 를 참고하세요.

| 파일 | 제목 | 아티스트 | 출처 |
|---|---|---|---|
| `classic-omok-bgm.mp3` | Country Background | Tunetank | https://pixabay.com/music/traditional-country-country-background-349052/ |
| `physical-omok-bgm.mp3` | A Reason To Smile | JonasBlakewood | https://pixabay.com/music/upbeat-a-reason-to-smile-350631/ |

## 재생 방식

프론트엔드 정적 자산으로 서빙되므로 Spring 백엔드나 WebSocket 서버를 거치지 않습니다.
`useBackgroundMusic` 훅이 사용자 입력 이후 재생하고 `preload="none"` 으로 초기 로딩 부하를 줄입니다.
배포 시에는 Nginx/CDN 정적 캐시 대상으로 두는 것이 서버 부하가 가장 적습니다.

## 음원을 교체할 때

1. 재사용·재배포가 허용되는 라이선스인지 확인 (Pixabay, CC0 등)
2. 파일명을 위 표와 동일하게 유지
3. `docs/music-from.md` 와 `frontend/src/pages/LicensesPage.tsx` 의 고지 내용을 함께 갱신
