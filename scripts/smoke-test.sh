#!/usr/bin/env bash
set -euo pipefail
BASE="${BASE:-http://localhost:8080}"

echo '1. Health'
curl -fsS "$BASE/health"
echo

echo '2. Login'
TOKEN=$(curl -fsS -X POST "$BASE/api/v1/auth/login" -H 'Content-Type: application/json' -d '{"login":"mechanic01","password":"demo12345"}' | python3 -c 'import sys,json; print(json.load(sys.stdin)["accessToken"])')

echo '3. Work orders'
curl -fsS "$BASE/api/v1/work-orders" -H "Authorization: Bearer $TOKEN" | python3 -m json.tool
