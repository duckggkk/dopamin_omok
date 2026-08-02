# 운영 로그 보는 법 (Observability Runbook)

도파민 오목 운영 서버에서 문제가 생겼을 때 **로그로 원인을 추적하는 방법**을 정리한다.
초보자도 그대로 따라 할 수 있게 명령어 위주로 적었다.

---

## 0. 한눈에 보기 (로그가 흐르는 길)

```
앱 코드(log.info ...)
   │  ← logback-spring.xml 이 형식 결정: [traceId] 붙이고, 이메일/토큰 마스킹
   ▼
컨테이너 stdout
   │  ← docker json-file 드라이버가 파일로 저장(10MB×3개 로테이션)
   ▼
Promtail (수집 에이전트)
   │  ← 컨테이너마다 로그를 따라가며 service/container 라벨을 붙임
   ▼
Loki (로그 저장소, 14일 보관)
   │
   ▼
Grafana (https://<도메인>/grafana → Explore 에서 검색)
```

핵심 도구는 두 가지다.
- **빠르게 한 컨테이너만** 볼 땐 → `docker logs` (서버에 SSH 접속)
- **여러 컨테이너를 가로질러 검색·기간 조회**할 땐 → **Grafana + Loki**

---

## 1. 가장 빠른 방법 — `docker logs` (SSH)

서버에 접속한 뒤:

```bash
# 백엔드 실시간 로그 따라가기 (가장 자주 씀)
docker logs -f omok_backend

# 최근 200줄만
docker logs --tail 200 omok_backend

# 특정 시간 이후만
docker logs --since 30m omok_backend          # 최근 30분
docker logs --since 2026-06-20T09:00:00 omok_backend

# 키워드로 거르기 (ERROR 만)
docker logs omok_backend 2>&1 | grep ERROR

# 특정 추적 ID(traceId)로 한 요청 전체 모아보기
docker logs omok_backend 2>&1 | grep '3fa85f64-5717-4562-b3fc-2c963f66afa6'
```

컨테이너 이름: `omok_backend`, `omok_frontend`, `omok_db`, `omok_caddy`,
`omok_prometheus`, `omok_grafana`, `omok_loki`, `omok_promtail`.

> 주의: `docker logs` 는 그 컨테이너가 **살아있는 동안의 최근 로그(10MB×3)** 만 본다.
> 재배포로 컨테이너가 교체되면 과거 로그는 사라진다. 그래서 Loki 가 필요하다(아래).

---

## 2. 제대로 된 방법 — Grafana + Loki

브라우저에서 **`https://<도메인>/grafana`** 접속 → 로그인(`GRAFANA_ADMIN_USER` / `GRAFANA_ADMIN_PASSWORD`).

왼쪽 메뉴 **Explore** → 데이터소스 **Loki** 선택 → 아래 LogQL 로 검색한다.

### 자주 쓰는 검색 (LogQL)

```logql
# 백엔드 전체 로그
{service="backend"}

# 백엔드에서 ERROR 만
{service="backend"} |= "ERROR"

# 특정 traceId 로 한 요청 전체 추적 (요청별 디버깅의 핵심)
{service="backend"} |= "3fa85f64-5717-4562-b3fc-2c963f66afa6"

# 이메일 발송 관련만
{service="backend"} |= "이메일"

# 여러 서비스 한 번에
{service=~"backend|frontend"} |= "ERROR"

# 5xx 응답만 (접근 로그 패턴 활용: "-> 500")
{service="backend"} |= "-> 5"
```

### 접근 로그(웹 요청) — nginx / Caddy

Promtail 은 **모든 컨테이너**의 stdout 을 수집하므로 백엔드뿐 아니라 웹 서버 로그도 이미 Loki 에 있다.
드롭다운/쿼리의 `service` 만 바꾸면 된다.

```logql
# 프론트(nginx) 접근·에러 로그 — SPA·/api·/ws 프록시 요청
{service="frontend"}

# Caddy(외부 진입점) 접근 로그 — 가장 바깥에서 본 모든 요청(JSON)
{service="caddy"}

# Caddy 접근 로그에서 5xx 만 (JSON 필드 파싱)
{service="caddy"} | json | status >= 500

# 느린 요청 Top (응답 1초 초과)
{service="caddy"} | json | duration > 1
```

> Caddy 로그는 JSON 이라 `| json` 으로 `status`·`duration`·`uri`·`remote_ip` 등을
> 필드로 뽑아 필터·집계할 수 있다. 민감정보는 자동으로 가려진다 — `Authorization` 헤더·쿠키는
> Caddy 기본값으로, OAuth 콜백의 `code`·`state` 쿼리 파라미터는 `Caddyfile` 의 log 필터로
> `REDACTED` 처리된다(이메일 인증코드·액세스토큰은 각각 POST 본문·URL 프래그먼트라 URL 에 안 남음).
>
> `uuid` 필드가 backend 의 traceId 와 같은 값이다(§3) — `{service="caddy"} | json | uuid="<값>"`
> 로 검색하면 "Caddy 가 이 요청을 어떤 상태코드·응답시간으로 봤는지"를 backend 로그와 나란히 볼 수 있다.

- 오른쪽 위 **시간 범위**로 기간을 좁힌다(예: Last 1 hour, 또는 사건 발생 시각 전후).
- `|=` 포함 / `!=` 제외 / `|~` 정규식 / `!~` 정규식 제외.

### Live 모드
Explore 오른쪽 위 **Live** 버튼 → 실시간 스트리밍(= `docker logs -f` 의 웹 버전, 여러 서비스 동시).

---

## 3. traceId 로 요청 추적하기 (제일 중요)

요청마다 추적 ID 가 붙는다. **운영에서는 가장 바깥의 Caddy 가 이 ID 를 채번**하고
(`deploy/Caddyfile` 의 `header_up X-Request-Id {http.request.uuid}`), nginx 는 커스텀
헤더라 별도 설정 없이 그대로 통과시키며, backend 의 `TraceIdFilter` 는 새로 만들지 않고
이어받아 MDC 에 심는다. 그래서 이 ID 하나로 **Caddy 접근 로그 → nginx 접근 로그 →
backend 로그**를 전부 엮을 수 있다. 형식은 UUID(예: `3fa85f64-5717-4562-b3fc-2c963f66afa6`) —
Caddy 를 거치지 않고 backend 를 직접 호출한 경우(로컬 개발 등)에만 backend 가 예외적으로
8자리 짧은 ID 를 새로 만든다.

1. 사용자가 오류를 겪으면, 응답 헤더 **`X-Request-Id`** 값을 알아낸다.
   (브라우저 개발자도구 Network 탭 → 해당 요청 → Response Headers)
2. 그 값으로 검색하면 그 한 번의 요청이 거친 **컨트롤러·서비스·비동기 메일 발송 로그까지 전부** 한 줄기로 모인다.
   ```logql
   {service="backend"} |= "3fa85f64-5717-4562-b3fc-2c963f66afa6"
   ```
3. 같은 값으로 Caddy 쪽도 검색하면(§2) "엣지에서 본 상태코드·응답시간"까지 같이 확인된다.
   ```logql
   {service="caddy"} | json | uuid="3fa85f64-5717-4562-b3fc-2c963f66afa6"
   ```

로그 한 줄 예시(운영 형식):
```
2026-06-20 09:12:33.444 INFO  [3fa85f64-5717-4562-b3fc-2c963f66afa6] [http-nio-8080-exec-3] c.d.o.g.l.TraceIdFilter - POST /api/auth/login -> 200 (45ms)
```
`[3fa85f64-...]` 가 traceId, `-> 200 (45ms)` 가 응답 상태와 소요시간이다.

---

## 4. 개인정보 마스킹 — 무엇이 가려지나

로그로 새어 나갈 수 있는 민감정보는 **출력 직전에 자동으로 가려진다**
(`PiiMasker` + `MaskingMessageConverter`, 모든 로그에 적용).

| 원본 | 로그에 찍히는 모습 |
|------|----------------------|
| `hong@gmail.com` | `h***@gmail.com` (도메인만 남김) |
| JWT `eyJhbGci...` | `***JWT***` |
| `Bearer eyJ...` | `Bearer ***` |

- 개발자가 호출부마다 마스킹을 신경 쓸 필요 없다(전역 안전망).
- 새 민감정보 유형(예: 전화번호)이 생기면 `PiiMasker` 에 정규식 한 줄만 추가하면 된다.
- 한계: 예외 **스택트레이스 본문**과 구조화 로그로 바꿀 경우엔 별도 처리가 필요하다(현재 코드 경로엔 PII 미포함).

---

## 5. 로그 레벨 조정

평소 운영(prod)은 **INFO**, 로컬은 **DEBUG** 다. (`application-prod.yml` / `application.yml` 의 `logging.level`)

문제 추적을 위해 잠깐 더 자세히 보고 싶을 때:
- `application-prod.yml` 의 `logging.level.com.dopamin.omok: INFO` 를 `DEBUG` 로 바꾸고 백엔드만 재시작.
- ⚠️ Hibernate SQL/바인드 파라미터 DEBUG 는 민감정보를 노출할 수 있어 **켜지 말 것**
  (불가피하면 짧게 켰다가 즉시 되돌리기). 자세한 주의는 `application.yml` 주석 참고.

---

## 6. 디스크/보관 정책

- 컨테이너별 로컬 로그: **10MB × 3개**(json-file 로테이션) — `docker-compose.prod.yml` 의 `x-logging`.
- Loki 보관: **14일** 후 자동 삭제 — `deploy/loki/loki-config.yml` 의 `retention_period`.
- Prometheus(지표)는 15일 — 참고용.

용량이 부담되면 위 두 값을 줄이면 된다.

---

## 7. 자주 겪는 문제

- **Grafana 에 Loki 가 안 보임** → `omok_loki` / `omok_promtail` 컨테이너가 떠 있는지 확인
  (`docker ps`), Promtail 로그 확인(`docker logs omok_promtail`).
- **로그가 Loki 에 안 쌓임** → Promtail 이 `docker.sock` 을 못 읽는 경우. compose 의
  `/var/run/docker.sock:/var/run/docker.sock:ro` 마운트 확인.
- **traceId 가 빈칸 `[]`** → 요청 컨텍스트 밖(서버 시작 로그, 틱 루프 등)이라 정상.
  HTTP 요청 처리 로그에는 항상 채워진다.

---

## 8. 대시보드 — 무엇이 있고 어디서 오나

`deploy/grafana/provisioning/dashboards/json/` 의 JSON 파일이 **정본**이다.
Grafana 가 부팅할 때 자동으로 읽어 "도파민오목" 폴더에 만들어 준다(UI에서 손으로 만들 필요 없음).

| 대시보드 | 데이터 출처 | 언제 보나 |
|---|---|---|
| **도파민오목 · 백엔드 개요** (`backend-overview.json`) | Prometheus (Micrometer 메트릭) | 평소 상태 점검, 느려졌을 때 원인 찾기 |
| **도파민오목 · 로그** (`logs.json`) | Loki (컨테이너 로그) | 알림이 왔을 때 "무슨 일이 있었나" 확인 |

패널을 고치는 방법은 두 가지다.
- **UI 에서 고치고 저장** — `allowUiUpdates: true` 라 가능하다. 단 파일과 달라지므로 실험용으로만.
- **JSON 파일을 고친다(권장)** — 30초 안에 반영된다(`updateIntervalSeconds`). 파일이 정본이라
  서버를 새로 만들어도 그대로 복원된다.

> 메트릭이 전부 "No data" 라면 → 백엔드의 `/api/actuator/prometheus` 가 열려 있는지 확인.
> 프로필별로 `management.endpoints.web.exposure.include: health,prometheus` 가 있어야 한다
> (`application-local.yml` / `application-docker.yml` / `application-prod.yml` 모두 설정되어 있음).
> 이 설정 없이 백엔드를 띄우면 이 엔드포인트가 없어 Prometheus 수집이 실패한다.

---

## 9. 로컬(내 PC)에서 대시보드 보기

운영에 올리기 전에 대시보드를 고쳐보려면 로컬에서 같은 스택을 띄우면 된다.
설정 파일은 운영과 공유하므로, 여기서 확인한 게 운영에서도 그대로 동작한다.

```bash
# 관측 스택만 띄운다(백엔드/DB 는 평소 하던 대로 따로 실행)
docker compose -f docker-compose.dev.yml up -d prometheus loki promtail grafana
```

접속: **http://localhost:3001** (`admin` / `admin`)
Prometheus 쿼리를 직접 실험하고 싶으면: http://localhost:9090

### 백엔드를 어떻게 띄우든 메트릭은 잡힌다
개발용 수집 설정(`deploy/prometheus/prometheus.dev.yml`)은 백엔드를 **`host.docker.internal:8080`**,
즉 "내 PC 의 8080" 에서 찾는다. 그래서 백엔드를 IDE/`gradlew bootRun` 으로 직접 띄우든,
`docker compose -f docker-compose.dev.yml up -d backend` 로 띄우든(8080 을 호스트에 공개) 양쪽 다 수집된다.
(운영은 `prometheus.yml` 이 `backend:8080` 을 쓴다 — 리눅스엔 `host.docker.internal` 이 없기 때문.)

### 로컬에서 다른 점 두 가지 (알고 있어야 함)

1. **백엔드를 네이티브로 띄우면 '로그' 대시보드는 빈다.**
   Promtail 은 *도커 컨테이너*의 로그만 수집한다. IDE 로 띄운 백엔드 로그는 도커 밖이라 Loki 에 안 들어간다.
   로컬에서 로그 대시보드까지 보고 싶으면 백엔드도 도커로 띄워야 한다.
   ⚠️ 단, 네이티브 백엔드와 컨테이너 백엔드를 **동시에** 띄우면 안 된다 —
   8080 포트가 겹치고, `backend/.gradle` 락을 두 gradle 이 함께 건드려 컨테이너가 I/O 에러로 죽는다.
   (증상: `Could not create service of type FileHasher ... java.io.IOException: I/O error` 후 재시작 반복)

2. **알림은 로컬에서 동작하지 않는다.**
   dev compose 의 Grafana 는 `datasources/` 와 `dashboards/` 만 프로비저닝하고 `alerting/` 은 제외한다.
   알림 설정이 `DISCORD_WEBHOOK_URL`·SMTP 값을 요구해서, 값이 빈 로컬에선 프로비저닝이 실패하기 때문이다.
   알림 규칙은 운영에서만 뜬다.

---

## 10. 알림 규칙 — 무엇이 언제 울리나

`deploy/grafana/provisioning/alerting/rules.yaml` 이 정본이며, 디스코드 + 이메일로 함께 나간다.

| 알림 | 조건 | 심각도 |
|---|---|---|
| 백엔드 ERROR 로그 발생 | 최근 5분 ERROR 로그 ≥ 1건 | critical |
| 백엔드 응답 없음 | 메트릭 수집 실패가 2분 지속 | critical |
| 5xx 서버 에러 발생 | 5xx 응답이 2분 지속 | critical |
| JVM 힙 메모리 90% 초과 | 힙 사용률 > 90% 가 5분 지속 | warning |
| 응답시간 지연 | 평균 응답시간 > 1초 가 5분 지속 | warning |
| DB 커넥션 풀 대기 발생 | 커넥션 대기(pending) > 0 이 5분 지속 | warning |

`for` (지속 시간)를 둔 이유는 **재배포 중 잠깐 끊기는 것으로 알림이 오지 않게** 하기 위함이다.
알림이 너무 잦으면 `rules.yaml` 의 임계값(`params`)이나 `for` 를 올리면 된다.
