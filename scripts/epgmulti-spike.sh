#!/usr/bin/env bash
# Operator Phase 0 spike: measure Dreambox /web/epgmulti window sizes.
# Cloud agents have no box — run this from a machine that can reach the WebIf.
#
# Usage:
#   BASE=http://dreambox BREF='1:7:1:0:0:0:0:0:0:0:FROM BOUQUET "favourites" ORDER BY bouquet' \
#     bash scripts/epgmulti-spike.sh
# Optional: SREF='1:0:1:...' USER=root PASS=...
set -euo pipefail

BASE="${BASE:?set BASE=http://host}"
BREF="${BREF:?set BREF=bouquet reference}"
SREF="${SREF:-}"
AUTH=()
if [[ -n "${USER:-}" ]]; then
  AUTH=(-u "${USER}:${PASS:-}")
fi

T0="$(date +%s)"
T2H=$((T0 + 7200))
T24H=$((T0 + 86400))

encode() {
  python3 -c 'import urllib.parse,sys; print(urllib.parse.quote(sys.argv[1], safe=""))' "$1"
}

BREF_Q="$(encode "$BREF")"

probe() {
  local label="$1"
  local path="$2"
  local url="${BASE%/}${path}"
  local tmp
  tmp="$(mktemp)"
  local start_ns end_ns ms bytes events status
  start_ns="$(date +%s%N)"
  status="$(curl -sS "${AUTH[@]}" -o "$tmp" -w '%{http_code}' --max-time 120 "$url" || true)"
  end_ns="$(date +%s%N)"
  ms=$(( (end_ns - start_ns) / 1000000 ))
  bytes="$(wc -c <"$tmp" | tr -d ' ')"
  events="$(grep -c '<e2event>' "$tmp" || true)"
  printf '%s\tstatus=%s\tms=%s\tbytes=%s\tevents=%s\n' "$label" "$status" "$ms" "$bytes" "$events"
  rm -f "$tmp"
}

echo "T0=$T0 (unix seconds — pass as time/endTime; Dreambox webif int(float(...)))"
probe "unbounded" "/web/epgmulti?bRef=${BREF_Q}"
probe "2h" "/web/epgmulti?bRef=${BREF_Q}&time=${T0}&endTime=${T2H}"
probe "24h" "/web/epgmulti?bRef=${BREF_Q}&time=${T0}&endTime=${T24H}"
probe "time_only" "/web/epgmulti?bRef=${BREF_Q}&time=${T0}"
if [[ -n "$SREF" ]]; then
  SREF_Q="$(encode "$SREF")"
  probe "epgservice_24h" "/web/epgservice?sRef=${SREF_Q}&time=${T0}&endTime=${T24H}"
fi
echo "Paste the table into docs/multiepg.md Phase 0 notes when done."
