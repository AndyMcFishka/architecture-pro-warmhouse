#!/bin/bash
set -e
cd "$(dirname "$0")"
docker compose up --build -d --wait
echo "Monolith: http://localhost:8080"
echo "Temperature API: http://localhost:8081"
echo "Proxy: http://localhost:8090"
echo "Telemetry: http://localhost:8090/api/v1/telemetry/"
echo "Heating: http://localhost:8090/api/v1/heating/"
echo "Lighting: http://localhost:8090/api/v1/lighting/"
echo "Gates: http://localhost:8090/api/v1/gates/"
echo "Video: http://localhost:8090/api/v1/video/"
echo "Scenarios: http://localhost:8090/api/v1/scenarios/"
echo "Connectivity: http://connectivity:8080 (Docker network only)"
