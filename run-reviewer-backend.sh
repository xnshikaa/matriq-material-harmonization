#!/bin/sh
set -eu
cd "$(dirname "$0")"
reviewer_id=$(python3 -c 'import json; print(json.load(open(".matriq-state/accounts.json"))["accounts"]["hetanshi.vora.btech2028@atlasskilltech.university"]["id"])')
export MATRIQ_REVIEWER_IDS="$reviewer_id"
export MATRIQ_SECURE_COOKIE=false
exec mvn spring-boot:run
