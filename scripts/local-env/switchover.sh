#!/bin/bash

# If the script is called with the "loop" argument, skip setup and just run the loop
if [ "$1" == "loop" ]; then
  # Default to 5 minutes if SWITCHOVER_INTERVAL_MINUTES is not set
  INTERVAL_MINUTES=${SWITCHOVER_INTERVAL_MINUTES:-5}
  INTERVAL_SECONDS=$((INTERVAL_MINUTES * 60))

  echo "Switchover orchestrator started. Scheduled switchover loop running every ${INTERVAL_MINUTES} minute(s)..."

  while true; do
    sleep $INTERVAL_SECONDS
    echo "Initiating scheduled switchover..."
    pg_autoctl perform switchover
    echo "Switchover successfully orchestrated by the monitor."
  done

  exit 0
fi

echo "Waiting for the PostgreSQL cluster to bootstrap and elect a primary..."

PRIMARY_URI="postgresql://docker@node1:5432,node2:5432/postgres?target_session_attrs=read-write"
until psql "$PRIMARY_URI" -c "SELECT 1;"; do
    echo "Waiting for primary node to be ready..."
    sleep 5
done

echo "Primary node is up! Injecting collector schema and user..."

psql "$PRIMARY_URI" -c "SELECT pg_reload_conf();" || true

# If the RESET_DB environment variable is set to true, we drop the schema and user first
if [ "${RESET_DB:-false}" == "true" ]; then
    echo "RESET_DB is set to true. Dropping existing schema and user to start from scratch..."
    psql "$PRIMARY_URI" -c "DROP SCHEMA IF EXISTS collector CASCADE;" || true
    psql "$PRIMARY_URI" -c "DROP OWNED BY collector;" || true
    psql "$PRIMARY_URI" -c "DROP USER IF EXISTS collector;" || true
fi

psql "$PRIMARY_URI" -c "CREATE USER collector WITH PASSWORD 'collector';" || true
psql "$PRIMARY_URI" -c "CREATE SCHEMA IF NOT EXISTS collector AUTHORIZATION collector;" || true
psql "$PRIMARY_URI" -c "GRANT ALL PRIVILEGES ON DATABASE postgres TO collector;" || true
psql "$PRIMARY_URI" -c "GRANT ALL PRIVILEGES ON SCHEMA collector TO collector;" || true

echo "Collector schema initialized successfully."