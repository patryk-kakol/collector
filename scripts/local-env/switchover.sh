#!/bin/bash

echo "Waiting for the PostgreSQL cluster to bootstrap and elect a primary..."

PRIMARY_URI="postgresql://docker@node1:5432,node2:5432/postgres?target_session_attrs=read-write"
until psql "$PRIMARY_URI" -c "SELECT 1;"; do
    echo "Waiting for primary node to be ready..."
    sleep 5
done

echo "Primary node is up! Injecting collector schema and user..."

psql "$PRIMARY_URI" -c "SELECT pg_reload_conf();" || true
psql "$PRIMARY_URI" -c "CREATE USER collector WITH PASSWORD 'collector';" || true
psql "$PRIMARY_URI" -c "CREATE SCHEMA collector AUTHORIZATION collector;" || true
psql "$PRIMARY_URI" -c "GRANT ALL PRIVILEGES ON DATABASE postgres TO collector;" || true
psql "$PRIMARY_URI" -c "GRANT ALL PRIVILEGES ON SCHEMA collector TO collector;" || true

echo "Collector schema initialized successfully."
echo "Starting 5-minute switchover orchestration loop..."

while true; do
  sleep 300
  echo "Initiating scheduled switchover..."
  pg_autoctl perform switchover
  echo "Switchover successfully orchestrated by the monitor."
done