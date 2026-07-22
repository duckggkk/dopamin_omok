# 배경음악 출처

## 1. 클래식 오목 배경음

- 제목: Country Background
- 아티스트: Tunetank
- 출처: https://pixabay.com/music/traditional-country-country-background-349052/
- 라이선스: Pixabay Content License

## 2. 피지컬 오목 관련 음원

### A Reason To Smile

- 아티스트: JonasBlakewood
- 출처: https://pixabay.com/music/upbeat-a-reason-to-smile-350631/
- 라이선스: Pixabay Content License
- 현재 피지컬 오목 배경음 파일의 출처로 기록

### The Spring of the Universe

- 아티스트: JonasBlakewood
- 출처: https://pixabay.com/music/rock-the-spring-of-the-universe-449695/
- 라이선스: Pixabay Content License
- 피지컬 오목 음원 후보·출처 이력으로 기록

## 3. 착수음(효과음)

아래 착수음은 **AI 생성 음원**으로, 기존 저작물을 사용하지 않았습니다.

| 파일 | 용도 |
|---|---|
| `backend/src/main/resources/assets/stone_sound/default.m4a` | 기본 착수음 |
| `backend/src/main/resources/assets/stone_sound/iron.wav` | 상점 아이템 '쇠' 착수음 |

## 사용 위치

- 클래식 오목: `frontend/public/audio/classic-omok-bgm.mp3`
- 피지컬 오목: `frontend/public/audio/physical-omok-bgm.mp3`
- 프론트엔드가 정적 파일을 직접 재생하므로 Spring API/WebSocket 서버를 통과하지 않습니다.
- `useBackgroundMusic` 훅은 사용자 입력 이후 재생하고 `preload="none"`으로 초기 로딩 부하를 줄입니다.

## 라이선스 안내

각 음원의 저작권은 원저작자에게 있으며, 도파민 오목은 Pixabay Content License에 따라 게임의 배경음악으로 사용합니다.
Pixabay Content License는 콘텐츠의 무료 사용·수정 등을 허용하지만, 콘텐츠를 원본과 실질적으로 같은 형태로 단독 판매하거나 배포하는 행위는 금지합니다.

- 라이선스 요약: https://pixabay.com/service/license-summary/
- 전체 약관: https://pixabay.com/service/terms/
