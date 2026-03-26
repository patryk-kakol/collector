#!/bin/bash
set -e

sudo mkdir -p /data
sudo chown -R docker /data

# Initialize the monitor using trust for the internal docker network
pg_autoctl create monitor \
  --auth trust \
  --no-ssl

# Start the permanent daemon
pg_autoctl run