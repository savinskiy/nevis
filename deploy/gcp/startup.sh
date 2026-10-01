#!/bin/bash
# Compute Engine startup script: runs as root on every boot of the VM.
# Installs Docker once, fetches the latest source from the deploy bucket and (re)starts the stack.
set -euo pipefail

metadata() {
  curl -sf -H "Metadata-Flavor: Google" \
    "http://metadata.google.internal/computeMetadata/v1/instance/attributes/$1" || true
}

if ! command -v docker >/dev/null; then
  curl -fsSL https://get.docker.com | sh
fi

BUCKET="$(metadata source-bucket)"
mkdir -p /opt/nevis
gcloud storage cp "gs://${BUCKET}/nevis.tgz" /tmp/nevis.tgz
tar -xzf /tmp/nevis.tgz -C /opt/nevis
printf 'ANTHROPIC_API_KEY=%s\n' "$(metadata anthropic-api-key)" > /opt/nevis/.env
chmod 600 /opt/nevis/.env

cd /opt/nevis
# one-off switch: delete all data (PostgreSQL and Elasticsearch volumes) before starting
if [ "$(metadata reset-data)" = "yes" ]; then
  docker compose down -v || true
fi
docker compose up -d --build
