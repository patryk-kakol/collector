#!/bin/bash
set -e

sudo mkdir -p /data
sudo chown -R docker /data

# Wait briefly for the monitor to start
sleep 10

NODE_NAME=$1

pg_autoctl create postgres \
  --name "$NODE_NAME" \
  --auth trust \
  --no-ssl \
  --pg-hba-lan \
  --monitor "$PG_AUTOCTL_MONITOR"

echo "host postgres collector 0.0.0.0/0 md5" >> "$PGDATA/pg_hba.conf"

# Start the permanent daemon
exec pg_autoctl run