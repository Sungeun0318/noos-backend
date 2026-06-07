#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://127.0.0.1:8080}"
POLL_INTERVAL_SEC="${POLL_INTERVAL_SEC:-3}"
TIMEOUT_SEC="${TIMEOUT_SEC:-900}"
OUT_DIR="${OUT_DIR:-$(pwd)/build/bench}"
RUN_ID="${RUN_ID:-$(date -u +%Y%m%dT%H%M%SZ)}"
DEVICE_ID="${DEVICE_ID:-smoke-$(uuidgen | tr '[:upper:]' '[:lower:]')}"
PLANET="${PLANET:-Neptune}"
CSV_PATH="$OUT_DIR/adaptive_e2e_smoke_${RUN_ID}.csv"

mkdir -p "$OUT_DIR"

echo "step,status,elapsedSec,sessionId,segmentId,audioId,detail" > "$CSV_PATH"

log() {
  printf '[adaptive-smoke] %s\n' "$*" >&2
}

json_escape() {
  python3 -c 'import json,sys; print(json.dumps(sys.stdin.read())[1:-1])'
}

record() {
  local step="$1"
  local status="$2"
  local elapsed="$3"
  local session_id="${4:-}"
  local segment_id="${5:-}"
  local audio_id="${6:-}"
  local detail="${7:-}"
  local escaped_detail
  escaped_detail="$(printf '%s' "$detail" | json_escape)"
  echo "$step,$status,$elapsed,$session_id,$segment_id,$audio_id,\"$escaped_detail\"" >> "$CSV_PATH"
}

request() {
  local method="$1"
  local path="$2"
  local body="${3:-}"
  if [[ -n "$body" ]]; then
    curl -sS -X "$method" "$BASE_URL$path" \
      -H 'Content-Type: application/json' \
      -H "x-device-id: $DEVICE_ID" \
      -d "$body"
  else
    curl -sS -X "$method" "$BASE_URL$path" \
      -H "x-device-id: $DEVICE_ID"
  fi
}

health_check() {
  curl -sS -m 8 "$BASE_URL/api/mobile/health"
}

poll_segment_ready() {
  local session_id="$1"
  local segment_id="$2"
  local label="$3"
  local started_at="$4"
  local response status audio_id elapsed now

  while true; do
    sleep "$POLL_INTERVAL_SEC"
    response="$(request GET "/api/mobile/adaptive/sessions/$session_id")"
    status="$(echo "$response" | jq -r --argjson segmentId "$segment_id" '
      .segments[]? | select(.segmentId == $segmentId) | .status
    ' | tail -1)"
    audio_id="$(echo "$response" | jq -r --argjson segmentId "$segment_id" '
      .segments[]? | select(.segmentId == $segmentId) | .audioId // empty
    ' | tail -1)"
    now="$(date +%s)"
    elapsed="$((now - started_at))"
    log "$label segmentId=$segment_id status=${status:-missing} elapsed=${elapsed}s audioId=${audio_id:-}"

    if [[ "$status" == "ready" && -n "$audio_id" ]]; then
      printf '%s\n' "$response"
      return 0
    fi
    if [[ "$status" == "failed" ]]; then
      printf '%s\n' "$response"
      return 1
    fi
    if (( elapsed >= TIMEOUT_SEC )); then
      printf '%s\n' "$response"
      return 2
    fi
  done
}

audio_status() {
  local audio_id="$1"
  curl -sS -o /dev/null -w '%{http_code} %{content_type}' \
    "$BASE_URL/api/mobile/audio/$audio_id"
}

main() {
  local t0 now elapsed health session_id seed_segment_id seed_ready_response seed_audio_id
  local window_response action_type next_segment_id next_ready_response next_audio_id status_response audio_probe

  log "BASE_URL=$BASE_URL DEVICE_ID=$DEVICE_ID PLANET=$PLANET RUN_ID=$RUN_ID"
  health="$(health_check)"
  log "health=$health"
  if [[ "$(echo "$health" | jq -r '.backend')" != "ok" || "$(echo "$health" | jq -r '.aceStep')" != "ok" ]]; then
    record "health" "failed" 0 "" "" "" "$health"
    echo "Health check failed: $health" >&2
    exit 1
  fi
  record "health" "pass" 0 "" "" "" "$health"

  t0="$(date +%s)"
  start_body="{\"seedSource\":\"none\",\"planetHint\":\"$PLANET\"}"
  start_response="$(request POST "/api/mobile/adaptive/sessions/start" "$start_body")"
  session_id="$(echo "$start_response" | jq -r '.sessionId // empty')"
  seed_segment_id="$(echo "$start_response" | jq -r '.seedSegment.segmentId // empty')"
  if [[ -z "$session_id" || -z "$seed_segment_id" ]]; then
    elapsed="$(($(date +%s) - t0))"
    record "start" "failed" "$elapsed" "" "" "" "$start_response"
    echo "Start failed: $start_response" >&2
    exit 1
  fi
  elapsed="$(($(date +%s) - t0))"
  record "start" "pass" "$elapsed" "$session_id" "$seed_segment_id" "" "$start_response"
  log "started sessionId=$session_id seedSegmentId=$seed_segment_id"

  if ! seed_ready_response="$(poll_segment_ready "$session_id" "$seed_segment_id" "seed" "$t0")"; then
    elapsed="$(($(date +%s) - t0))"
    record "seed_ready" "failed" "$elapsed" "$session_id" "$seed_segment_id" "" "$seed_ready_response"
    echo "Seed segment did not become ready: $seed_ready_response" >&2
    exit 1
  fi
  seed_audio_id="$(echo "$seed_ready_response" | jq -r --argjson segmentId "$seed_segment_id" '
    .segments[]? | select(.segmentId == $segmentId) | .audioId // empty
  ' | tail -1)"
  elapsed="$(($(date +%s) - t0))"
  record "seed_ready" "pass" "$elapsed" "$session_id" "$seed_segment_id" "$seed_audio_id" "$seed_ready_response"

  now="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  window_body="$(cat <<JSON
{
  "windowIndex": 0,
  "windowStartAt": "$now",
  "windowDurationSec": 300,
  "sampleCount": 76800,
  "sampleRateHz": 256,
  "bands": {
    "delta": 0.08,
    "theta": 0.06,
    "alpha": 0.05,
    "beta": 0.74,
    "gamma": 0.66
  },
  "dominantBand": "beta",
  "qualityScore": 0.92,
  "signalOk": true
}
JSON
)"
  window_response="$(request POST "/api/mobile/adaptive/sessions/$session_id/windows" "$window_body")"
  action_type="$(echo "$window_response" | jq -r '.adaptiveAction.type // empty')"
  next_segment_id="$(echo "$window_response" | jq -r '.nextSegment.id // empty')"
  if [[ "$action_type" != "crossfade" || -z "$next_segment_id" ]]; then
    elapsed="$(($(date +%s) - t0))"
    record "submit_window" "failed" "$elapsed" "$session_id" "" "" "$window_response"
    echo "Window did not produce crossfade next segment: $window_response" >&2
    exit 1
  fi
  elapsed="$(($(date +%s) - t0))"
  record "submit_window" "pass" "$elapsed" "$session_id" "$next_segment_id" "" "$window_response"
  log "window action=$action_type nextSegmentId=$next_segment_id"

  if ! next_ready_response="$(poll_segment_ready "$session_id" "$next_segment_id" "next" "$(date +%s)")"; then
    elapsed="$(($(date +%s) - t0))"
    record "next_ready" "failed" "$elapsed" "$session_id" "$next_segment_id" "" "$next_ready_response"
    echo "Next segment did not become ready: $next_ready_response" >&2
    exit 1
  fi
  next_audio_id="$(echo "$next_ready_response" | jq -r --argjson segmentId "$next_segment_id" '
    .segments[]? | select(.segmentId == $segmentId) | .audioId // empty
  ' | tail -1)"
  elapsed="$(($(date +%s) - t0))"
  record "next_ready" "pass" "$elapsed" "$session_id" "$next_segment_id" "$next_audio_id" "$next_ready_response"

  status_response="$(request POST "/api/mobile/adaptive/sessions/$session_id/pause" '{"reason":"wear_off"}')"
  if [[ "$(echo "$status_response" | jq -r '.status // empty')" != "paused" ]]; then
    elapsed="$(($(date +%s) - t0))"
    record "pause" "failed" "$elapsed" "$session_id" "" "" "$status_response"
    echo "Pause failed: $status_response" >&2
    exit 1
  fi
  elapsed="$(($(date +%s) - t0))"
  record "pause" "pass" "$elapsed" "$session_id" "" "" "$status_response"

  status_response="$(request POST "/api/mobile/adaptive/sessions/$session_id/resume")"
  if [[ "$(echo "$status_response" | jq -r '.status // empty')" != "active" ]]; then
    elapsed="$(($(date +%s) - t0))"
    record "resume" "failed" "$elapsed" "$session_id" "" "" "$status_response"
    echo "Resume failed: $status_response" >&2
    exit 1
  fi
  elapsed="$(($(date +%s) - t0))"
  record "resume" "pass" "$elapsed" "$session_id" "" "" "$status_response"

  status_response="$(request POST "/api/mobile/adaptive/sessions/$session_id/end")"
  if [[ "$(echo "$status_response" | jq -r '.status // empty')" != "ended" ]]; then
    elapsed="$(($(date +%s) - t0))"
    record "end" "failed" "$elapsed" "$session_id" "" "" "$status_response"
    echo "End failed: $status_response" >&2
    exit 1
  fi
  elapsed="$(($(date +%s) - t0))"
  record "end" "pass" "$elapsed" "$session_id" "" "" "$status_response"

  audio_probe="$(audio_status "$seed_audio_id")"
  if [[ "$audio_probe" != 2* ]]; then
    elapsed="$(($(date +%s) - t0))"
    record "audio_seed" "failed" "$elapsed" "$session_id" "$seed_segment_id" "$seed_audio_id" "$audio_probe"
    echo "Seed audio probe failed: $audio_probe" >&2
    exit 1
  fi
  elapsed="$(($(date +%s) - t0))"
  record "audio_seed" "pass" "$elapsed" "$session_id" "$seed_segment_id" "$seed_audio_id" "$audio_probe"

  audio_probe="$(audio_status "$next_audio_id")"
  if [[ "$audio_probe" != 2* ]]; then
    elapsed="$(($(date +%s) - t0))"
    record "audio_next" "failed" "$elapsed" "$session_id" "$next_segment_id" "$next_audio_id" "$audio_probe"
    echo "Next audio probe failed: $audio_probe" >&2
    exit 1
  fi
  elapsed="$(($(date +%s) - t0))"
  record "audio_next" "pass" "$elapsed" "$session_id" "$next_segment_id" "$next_audio_id" "$audio_probe"

  echo "CSV: $CSV_PATH"
  cat "$CSV_PATH"
}

main "$@"
