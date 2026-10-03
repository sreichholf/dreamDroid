#!/usr/bin/env bash
# Captures what an OpenWebif receiver answers to the requests dreamDroid's OpenWebif client
# (OpenWebifApi) sends, so its synthetic test fixtures (app/test/resources/owif) can be checked
# against a real box.
#
#   scripts/owif-capture.sh HOST [USER PASS]
#
# HOST is the receiver's address, with :PORT when the web interface is not on 80, or a full
# http:// or https:// URL. USER and PASS are the web interface login, if it has one.
#
# Only reads. Nothing is zapped, switched, recorded, deleted or edited: every request that would
# change something on the box is listed under "Skipped" in index.txt and not sent.
#
# What to send back: the whole owif-capture-<time> folder (zip it), with the receiver model,
# image and OpenWebif version. Look through it first: deviceinfo has the box's MAC and IP
# addresses, grab.jpg is a screenshot of the TV picture, and the lists show your channels,
# timers and recordings. Delete or blank out what you do not want to share.
set -euo pipefail

if [[ $# -ne 1 && $# -ne 3 ]]; then
    echo "usage: $0 HOST [USER PASS]" >&2
    exit 2
fi

case "$1" in
    http://* | https://*) base="${1%/}" ;;
    *) base="http://$1" ;;
esac
auth=()
if [[ $# -eq 3 ]]; then
    auth=(--user "$2:$3")
fi

out="owif-capture-$(date +%Y%m%d-%H%M%S)"
mkdir -p "$out"
index="$out/index.txt"
now="$(date +%s)"
tv_bouquets='1:7:1:0:0:0:0:0:0:0:(type == 1) || (type == 17) || (type == 195) || (type == 25) FROM BOUQUET "bouquets.tv" ORDER BY bouquet'

{
    echo "OpenWebif capture of $base at $(date -u +%Y-%m-%dT%H:%M:%SZ)"
    echo "file | HTTP status | content type | request"
} >"$index"

# fetch FILE PATH [NAME=VALUE ...]: GETs PATH with the URL-encoded parameters into FILE.
fetch() {
    local file="$1" path="$2"
    shift 2
    local params=()
    local param
    for param in "$@"; do
        params+=(--data-urlencode "$param")
    done
    local status
    status="$(curl -sS -G --max-time 120 ${auth[@]+"${auth[@]}"} \
        ${params[@]+"${params[@]}"} -o "$out/$file" \
        -w '%{http_code} | %{content_type}' "$base$path")" || status="curl failed"
    echo "$file | $status | $path $*" >>"$index"
    echo "$file: $status"
}

# first_ref FILE: the first "servicereference" in a getservices answer, JSON escapes undone.
first_ref() {
    sed -nE 's/.*"servicereference": "(([^"\\]|\\.)*)".*/\1/p' "$out/$1" |
        grep -v '^1:64:' | head -n 1 | sed 's/\\"/"/g; s/\\\\/\\/g'
}

# Detection and device info.
fetch web_deviceinfo.xml /web/deviceinfo
fetch statusinfo.json /api/statusinfo
fetch deviceinfo.json /api/deviceinfo

# Services and EPG, on the first TV bouquet and its first service.
fetch getservices_bouquets.json /api/getservices "sRef=$tv_bouquets"
bouquet="$(first_ref getservices_bouquets.json || true)"
if [[ -n "$bouquet" ]]; then
    fetch getservices.json /api/getservices "sRef=$bouquet"
    fetch getservices_hidden.json /api/getservices "sRef=$bouquet" "hidden=1"
    fetch epgnownext.json /api/epgnownext "bRef=$bouquet"
    fetch epgbouquet.json /api/epgbouquet "bRef=$bouquet" "time=$now" "endTime=0"
    fetch epgmulti.json /api/epgmulti "bRef=$bouquet" "time=$now" "endTime=120"
    service="$(first_ref getservices.json || true)"
    if [[ -n "$service" ]]; then
        fetch epgservice.json /api/epgservice "sRef=$service"
        fetch epgservice_window.json /api/epgservice "sRef=$service" "time=$now" "endTime=180"
    fi
else
    echo "no bouquet found; services and EPG skipped" | tee -a "$index"
fi
fetch epgsearch.json /api/epgsearch "search=news"
fetch getcurrent.json /api/getcurrent
fetch signal.json /api/signal

# Timers, recordings, locations, tags.
fetch timerlist.json /api/timerlist
fetch movielist.json /api/movielist
fetch getlocations.json /api/getlocations
fetch gettags.json /api/gettags
fetch file_missing.txt /file "file=/dreamdroid-capture-no-such-file.ts"

# Control state, read without changing it.
fetch vol.json /api/vol
fetch powerstate.json /api/powerstate
fetch sleeptimer.json /api/sleeptimer
fetch grab.jpg /grab "format=jpg"

# Plugins.
fetch autotimer_get.xml /autotimer/get
fetch autotimer.xml /autotimer
fetch getsatellites_tv.json /api/getsatellites "stype=tv"
fetch getsatellites_radio.json /api/getsatellites "stype=radio"

cat >>"$index" <<'EOF'

Skipped, because they change the box (or, for /autotimer/test, search the whole EPG):
/api/zap, /api/remotecontrol, /api/message, /api/mediaplayerplay, /api/moviedelete,
/api/timeradd, /api/timerchange, /api/timeraddbyeventid, /api/timerdelete, /api/timercleanup,
/api/vol?set=, /api/powerstate?newstate=, /api/sleeptimer?cmd=set,
/autotimer/edit, /autotimer/change, /autotimer/remove, /autotimer/parse, /autotimer/test,
/bouqueteditor/api/addbouquet, removebouquet, movebouquet, renameservice, addservicetobouquet,
removeservice, moveservice, addmarkertobouquet, backup, and /file of a real recording.
EOF

echo "Done: $out (see $index)"
