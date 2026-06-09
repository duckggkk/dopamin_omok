#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────
# MySQL 논리 백업(mysqldump) — gzip 압축 후 ./backups 에 저장, 14일 초과분 자동 정리.
#
# 수동 실행:  cd /opt/dopamin-omok && bash deploy/scripts/backup.sh
# 매일 새벽 4시 자동 백업(crontab -e):
#   0 4 * * *  cd /opt/dopamin-omok && bash deploy/scripts/backup.sh >> backups/backup.log 2>&1
# ─────────────────────────────────────────────────────────────────────────────
set -euo pipefail

cd "$(dirname "$0")/../.."     # 레포 루트

# .env.prod 의 KEY=VALUE 를 환경변수로 로드 (단순 KEY=VALUE 형식 전제)
set -a; . ./.env.prod; set +a

BACKUP_DIR="${BACKUP_DIR:-./backups}"
mkdir -p "$BACKUP_DIR"

TS="$(date +%Y%m%d_%H%M%S)"
FILE="$BACKUP_DIR/dopamin_omok_${TS}.sql.gz"

docker compose -f docker-compose.prod.yml --env-file .env.prod exec -T \
  -e MYSQL_PWD="$MYSQL_ROOT_PASSWORD" db \
  mysqldump -uroot --single-transaction --routines --triggers --databases dopamin_omok \
  | gzip > "$FILE"

# 빈/실패 백업이면 남기지 않음
if [ ! -s "$FILE" ]; then
  echo "백업 실패: 결과 파일이 비어 있습니다 ($FILE)"; rm -f "$FILE"; exit 1
fi

echo "백업 생성: $FILE ($(du -h "$FILE" | cut -f1))"

# 14일 초과 백업 삭제
find "$BACKUP_DIR" -name 'dopamin_omok_*.sql.gz' -mtime +14 -delete 2>/dev/null || true
