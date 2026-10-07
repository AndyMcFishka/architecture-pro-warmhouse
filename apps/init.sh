#!/bin/bash
set -e
cd "$(dirname "$0")"
docker compose up --build -d --wait
echo "Monolith: http://localhost:8080"
echo "Temperature API: http://localhost:8081"
