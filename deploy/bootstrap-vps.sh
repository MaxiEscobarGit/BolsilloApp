#!/usr/bin/env bash
# Prepara un VPS Ubuntu 24.04 limpio. Correr como root; idempotente.
# Uso: DEPLOY_USER=deploy DEPLOY_PUBKEY_FILE=/root/id.pub ./bootstrap-vps.sh
#   DEPLOY_PUBKEY_FILE: archivo con TU clave publica SSH (se copia a authorized_keys; no va en el script).
# ATENCION: antes de cerrar la sesion actual, probar login SSH con el usuario deploy en otra terminal.
set -euo pipefail

DEPLOY_USER="${DEPLOY_USER:-deploy}"
DEPLOY_PUBKEY_FILE="${DEPLOY_PUBKEY_FILE:?Indica DEPLOY_PUBKEY_FILE con la clave publica SSH}"
[ "$(id -u)" -eq 0 ] || { echo "Correr como root"; exit 1; }
[ -s "$DEPLOY_PUBKEY_FILE" ] || { echo "No existe o esta vacio: $DEPLOY_PUBKEY_FILE"; exit 1; }

export DEBIAN_FRONTEND=noninteractive
apt-get update -y
apt-get install -y ca-certificates curl gnupg ufw fail2ban unattended-upgrades

# --- Usuario deploy (no-root, sudo) ---
if ! id "$DEPLOY_USER" >/dev/null 2>&1; then
  adduser --disabled-password --gecos "" "$DEPLOY_USER"
fi
usermod -aG sudo "$DEPLOY_USER"
# sudo sin password porque el usuario no tiene password; el acceso lo protege la clave SSH
echo "$DEPLOY_USER ALL=(ALL) NOPASSWD:ALL" > "/etc/sudoers.d/90-$DEPLOY_USER"
chmod 440 "/etc/sudoers.d/90-$DEPLOY_USER"
install -d -m 700 -o "$DEPLOY_USER" -g "$DEPLOY_USER" "/home/$DEPLOY_USER/.ssh"
AK="/home/$DEPLOY_USER/.ssh/authorized_keys"
touch "$AK"
while IFS= read -r key; do
  [ -n "$key" ] && { grep -qxF "$key" "$AK" || echo "$key" >> "$AK"; }
done < "$DEPLOY_PUBKEY_FILE"
chown "$DEPLOY_USER:$DEPLOY_USER" "$AK"; chmod 600 "$AK"

# --- SSH solo por clave ---
cat > /etc/ssh/sshd_config.d/00-hardening.conf <<'CONF'
PasswordAuthentication no
KbdInteractiveAuthentication no
PermitRootLogin no
PubkeyAuthentication yes
CONF
sshd -t && systemctl reload ssh

# --- Firewall ---
ufw default deny incoming
ufw default allow outgoing
ufw allow 22/tcp
ufw allow 80/tcp
ufw allow 443/tcp
ufw allow 443/udp
ufw --force enable

# --- fail2ban (sshd) ---
cat > /etc/fail2ban/jail.d/sshd.local <<'CONF'
[sshd]
enabled = true
maxretry = 5
findtime = 10m
bantime = 1h
CONF
systemctl enable --now fail2ban
systemctl restart fail2ban

# --- Actualizaciones automaticas con reboot nocturno ---
cat > /etc/apt/apt.conf.d/20auto-upgrades <<'CONF'
APT::Periodic::Update-Package-Lists "1";
APT::Periodic::Unattended-Upgrade "1";
CONF
cat > /etc/apt/apt.conf.d/52bolsillo-unattended <<'CONF'
Unattended-Upgrade::Automatic-Reboot "true";
Unattended-Upgrade::Automatic-Reboot-WithUsers "true";
Unattended-Upgrade::Automatic-Reboot-Time "04:00";
CONF

# --- Docker (repo oficial) ---
if ! command -v docker >/dev/null 2>&1; then
  install -m 0755 -d /etc/apt/keyrings
  curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
  chmod a+r /etc/apt/keyrings/docker.asc
  echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo "$VERSION_CODENAME") stable" \
    > /etc/apt/sources.list.d/docker.list
  apt-get update -y
  apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
fi
systemctl enable --now docker
usermod -aG docker "$DEPLOY_USER"

cat <<MSG

Listo. IMPORTANTE:
 - Docker publica puertos saltandose ufw. Por eso en docker-compose.prod.yml SOLO caddy publica (80/443).
   Nunca agregues "ports:" a postgres ni a la api.
 - Probá en otra terminal: ssh $DEPLOY_USER@<ip> antes de cerrar esta sesion.
MSG
