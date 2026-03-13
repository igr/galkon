#!/usr/bin/env bash
# One-time setup for Hetzner instance.
# Run as root: ssh root@galcon.top 'bash -s' < deploy/setup.sh
set -euo pipefail

# Install Java 21
apt-get update -qq
apt-get install -y -qq openjdk-21-jre-headless unzip

# Install Caddy
apt-get install -y -qq debian-keyring debian-archive-keyring apt-transport-https curl
curl -1sLf 'https://dl.cloudsmith.io/public/caddy/stable/gpg.key' | gpg --yes --dearmor -o /usr/share/keyrings/caddy-stable-archive-keyring.gpg
curl -1sLf 'https://dl.cloudsmith.io/public/caddy/stable/debian.deb.txt' | tee /etc/apt/sources.list.d/caddy-stable.list
apt-get update -qq
apt-get install -y -qq caddy

# Configure Caddy as reverse proxy
cat > /etc/caddy/Caddyfile << 'EOF'
galkon.top {
    reverse_proxy localhost:8080
}
EOF

systemctl enable --now caddy

# Create app directory
mkdir -p /opt/galkon

# Create systemd service (Ktor on port 8080, Caddy handles 80/443)
cat > /etc/systemd/system/galkon.service << 'EOF'
[Unit]
Description=Galkon game server
After=network.target

[Service]
Type=simple
WorkingDirectory=/opt/galkon/server
Environment=PORT=8080
Environment=GALKON_ENV=production
EnvironmentFile=-/opt/galkon/env
ExecStart=/opt/galkon/server/bin/server
Restart=on-failure
RestartSec=5

[Install]
WantedBy=multi-user.target
EOF

systemctl daemon-reload
systemctl enable galkon

systemctl stop galkon && systemctl restart caddy && systemctl start galkon

echo
echo "Setup complete. Configure GitHub secrets:"
echo "  DEPLOY_HOST = <server IP>"
echo "  DEPLOY_USER = root (or a deploy user)"
echo "  DEPLOY_SSH_KEY = <private key content>"
echo "  DASHBOARD_PASS = <dashboard password>"
