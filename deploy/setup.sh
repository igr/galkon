#!/usr/bin/env bash
# One-time setup for Hetzner instance.
# Run as root: ssh root@galcon.top 'bash -s' < deploy/setup.sh
set -euo pipefail

# Install Java 21
apt-get update -qq
apt-get install -y -qq openjdk-21-jre-headless unzip

# Create app directory
mkdir -p /opt/galkon

# Create systemd service
cat > /etc/systemd/system/galkon.service << 'EOF'
[Unit]
Description=Galkon game server
After=network.target

[Service]
Type=simple
WorkingDirectory=/opt/galkon/server
ExecStart=/opt/galkon/server/bin/server
Restart=on-failure
RestartSec=5

[Install]
WantedBy=multi-user.target
EOF

systemctl daemon-reload
systemctl enable galkon

echo "Setup complete. Configure GitHub secrets:"
echo "  DEPLOY_HOST = <server IP>"
echo "  DEPLOY_USER = root (or a deploy user)"
echo "  DEPLOY_SSH_KEY = <private key content>"
