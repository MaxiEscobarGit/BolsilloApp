# Restore de la base

Los backups son `bolsillo-<fecha>.sql.gz.age` en `s3://<R2_BUCKET>/daily/` (pg_dump con `--clean --if-exists`, gzip, cifrado con age).
Necesitás la clave privada de age (guardada offline, nunca en el VPS) y `age` + `awscli` instalados donde restaures.

## 1. Bajar el backup

```bash
export AWS_ACCESS_KEY_ID=... AWS_SECRET_ACCESS_KEY=... AWS_DEFAULT_REGION=auto
aws s3 --endpoint-url "$R2_ENDPOINT" ls s3://$R2_BUCKET/daily/
aws s3 --endpoint-url "$R2_ENDPOINT" cp s3://$R2_BUCKET/daily/bolsillo-XXXX.sql.gz.age .
```

## 2. (Recomendado) probar primero en una base descartable

```bash
cd ~/bolsillo/deploy
C="docker compose -f docker-compose.prod.yml exec -T postgres"
$C psql -U "$POSTGRES_USER" -d postgres -c "create database restore_test"
age -d -i key.txt bolsillo-XXXX.sql.gz.age | gunzip \
  | $C psql -q -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d restore_test
$C psql -U "$POSTGRES_USER" -d restore_test -c "select count(*) from flyway_schema_history"
$C psql -U "$POSTGRES_USER" -d postgres -c "drop database restore_test"
```

## 3. Restore real (pisa los datos actuales)

```bash
cd ~/bolsillo/deploy && set -a && . ./.env && set +a
docker compose -f docker-compose.prod.yml stop api
age -d -i key.txt bolsillo-XXXX.sql.gz.age | gunzip \
  | docker compose -f docker-compose.prod.yml exec -T postgres \
    psql -q -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"
docker compose -f docker-compose.prod.yml start api
curl -fsS https://$DOMAIN/actuator/health
```

Si la base está totalmente vacía/nueva, es el mismo comando (el dump trae el esquema y `flyway_schema_history`, así que la API arranca sin re-migrar).
Borrá `key.txt` de la máquina apenas termines.

## Backup diario (systemd timer)

```bash
sudo apt install -y age awscli
age-keygen -o key.txt      # en tu PC: guardá key.txt offline; el valor "age1..." va en AGE_RECIPIENT del .env
sudo cp ~/bolsillo/deploy/bolsillo-backup.{service,timer} /etc/systemd/system/
```

`bolsillo-backup.{service,timer}` se copian al VPS junto con el resto: si no están, `scp deploy/bolsillo-backup.* deploy@host:~/bolsillo/deploy/`.

```bash
sudo systemctl daemon-reload && sudo systemctl enable --now bolsillo-backup.timer
sudo systemctl start bolsillo-backup.service && journalctl -u bolsillo-backup -n 20   # prueba manual
```
