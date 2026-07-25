#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────
# 점검 공지 방식(무중단 아님) 운영 배포 스크립트.
#   1) 점검 모드 ON  →  사용자에게 점검 페이지(503) 노출
#   2) 최신 이미지 pull
#   3) DB 백업
#   4) 컨테이너 재기동 (백엔드 부팅 시 Flyway 마이그레이션 자동 실행)
#   5) 백엔드 헬스체크(UP) 확인
#   6) 점검 모드 OFF →  서비스 정상화
# 헬스체크 실패 시 점검 모드를 유지한 채 중단한다(깨진 버전을 노출하지 않음).
#
# 서버에서 실행:  cd /opt/dopamin-omok && bash deploy/scripts/deploy.sh
# ─────────────────────────────────────────────────────────────────────────────
set -euo pipefail

# 항상 레포 루트에서 동작 (이 스크립트는 deploy/scripts/ 하위)
cd "$(dirname "$0")/../.."

COMPOSE="docker compose -f docker-compose.prod.yml --env-file .env.prod"
FLAG="deploy/maintenance/maintenance.on"

c_info="\033[1;36m"; c_ok="\033[1;32m"; c_err="\033[1;31m"; c_off="\033[0m"
log()  { echo -e "${c_info}▶ $*${c_off}"; }

on_error() {
  echo -e "${c_err}✗ 배포 실패 — 점검 모드를 유지합니다(이전 버전 노출 안 됨).${c_off}"
  echo -e "${c_err}  로그 확인:  ${COMPOSE} logs --tail=120 backend${c_off}"
  echo -e "${c_err}  복구 후 정상화:  rm -f ${FLAG}${c_off}"
}
trap on_error ERR

[ -f .env.prod ] || { echo -e "${c_err}.env.prod 가 없습니다. .env.prod.example 를 복사해 채우세요.${c_off}"; exit 1; }

# 어떤 이미지가 뜨는지 배포 로그에 남긴다.
# CI 배포는 IMAGE_BACKEND/IMAGE_FRONTEND 를 커밋 SHA 태그로 export 해서 넘긴다
# (셸 환경변수가 --env-file 값보다 우선). 수동 실행 시에는 .env.prod 값이 쓰인다.
# :latest 로 뜨면 어떤 커밋인지 추적할 수 없으므로 경고한다
# (dev-log 20260716 'latest 태그와 조용한 장애' 참고).
resolved_images=$($COMPOSE config --images 2>/dev/null | grep -E "/(backend|frontend):" || true)
log "0/6  배포 대상 이미지"
echo "${resolved_images:-  (확인 실패 — compose config 를 읽지 못했습니다)}"
if echo "$resolved_images" | grep -q ':latest$'; then
  echo -e "${c_err}⚠ :latest 태그로 배포합니다 — 어떤 커밋이 떠 있는지 추적할 수 없고 롤백도 어렵습니다.${c_off}"
  echo -e "${c_err}  .env.prod 의 IMAGE_BACKEND/IMAGE_FRONTEND 를 커밋 SHA 태그로 바꾸세요.${c_off}"
fi

log "1/6  점검 모드 ON"
mkdir -p "$(dirname "$FLAG")"
touch "$FLAG"
sleep 2   # 진행 중이던 요청이 응답을 끝낼 여유

log "2/6  최신 이미지 받기 (GHCR)"
$COMPOSE pull

log "3/6  DB 백업"
bash deploy/scripts/backup.sh

log "4/6  컨테이너 재기동 (Flyway 마이그레이션 자동 실행)"
$COMPOSE up -d --remove-orphans

# Caddyfile 은 bind-mount 설정 파일이라, 위 up -d 만으로는 실행 중인 Caddy 에 반영되지 않는다.
# (Caddy 서비스 정의가 그대로면 compose 가 컨테이너를 재생성하지 않고,
#  Caddy 는 --watch 없이 떠 있어 시작할 때 읽은 설정을 그대로 계속 쓴다)
# 그래서 Caddyfile 변경(접근 로그 등)을 확실히 적용하려면 caddy 만 강제 재생성한다.
#   - --no-deps: caddy 만 다시 만들고 backend/db/redis 는 건드리지 않는다(불필요한 DB 재시작 방지).
#   - 지금은 점검 모드(1단계에서 ON)라 재생성 중 수 초 블립이 사용자에게 노출되지 않는다.
#   - TLS 인증서는 caddy_data 볼륨에 남아 재발급되지 않는다.
$COMPOSE up -d --no-deps --force-recreate caddy

log "5/6  백엔드 헬스체크 대기 (최대 120초)"
healthy=0
for _ in $(seq 1 40); do
  if $COMPOSE exec -T backend wget -qO- http://localhost:8080/api/actuator/health 2>/dev/null \
       | grep -q '"status":"UP"'; then
    healthy=1; break
  fi
  sleep 3
done
[ "$healthy" = "1" ] || { echo -e "${c_err}헬스체크 실패(UP 아님).${c_off}"; exit 1; }
log "백엔드 정상 (UP)"

log "6/6  점검 모드 OFF — 서비스 정상화"
rm -f "$FLAG"
docker image prune -f >/dev/null 2>&1 || true

trap - ERR
echo -e "${c_ok}✅ 배포 완료${c_off}"
