#!/usr/bin/env bash
# Backup diario: pg_dump -> gzip -> age (clave publica) -> Cloudflare R2. Retiene los ultimos KEEP.
# Requiere en deploy/.env: AGE_RECIPIENT, R2_BUCKET, R2_ENDPOINT, R2_ACCESS_KEY_ID, R2_SECRET_ACCESS_KEY,
# POSTGRES_USER, POSTGRES_DB. Dependencias en el host: age, awscli (apt install age awscli / snap aws-cli).
# La clave PRIVADA de age NO vive en el servidor: guardala offline (gestor de contrasenas).
set -euo pipefail
cd "$(dirname "$0")"
set -a; . ./.env; set +a

KEEP="${KEEP:-14}"
STAMP="$(date -u +%Y%m%dT%H%M%SZ)"
FILE="bolsillo-$STAMP.sql.gz.age"
TMP="$(mktemp -d)"; trap 'rm -rf "$TMP"' EXIT
export AWS_ACCESS_KEY_ID="$R2_ACCESS_KEY_ID" AWS_SECRET_ACCESS_KEY="$R2_SECRET_ACCESS_KEY" AWS_DEFAULT_REGION=auto
S3=(aws s3 --endpoint-url "$R2_ENDPOINT")

docker compose -f docker-compose.prod.yml exec -T postgres \
  pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" --no-owner --clean --if-exists \
  | gzip -9 | age -r "$AGE_RECIPIENT" -o "$TMP/$FILE"
[ -s "$TMP/$FILE" ] || { echo "dump vacio"; exit 1; }

"${S3[@]}" cp "$TMP/$FILE" "s3://$R2_BUCKET/daily/$FILE" --only-show-errors

# Retencion: borrar los mas viejos que los ultimos KEEP
"${S3[@]}" ls "s3://$R2_BUCKET/daily/" | awk '{print $4}' | grep '\.age$' | sort | head -n "-$KEEP" \
  | while read -r old; do "${S3[@]}" rm "s3://$R2_BUCKET/daily/$old" --only-show-errors; done
echo "OK $FILE"
