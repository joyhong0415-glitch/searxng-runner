#!/data/data/com.termux/files/usr/bin/bash
# ============================================================================
# SearXNG Runner - 시작/중지/재시작 스크립트
#
# 앱은 이 파일 내용 뒤에 "searxng_start", "searxng_stop", "searxng_restart"
# 중 하나를 붙여서 Termux 로 보냅니다. 직접 실행할 때는 마지막 줄에 함수 이름을
# 적으면 됩니다.
# ============================================================================

if ! type rdlog >/dev/null 2>&1; then
    rdlog() { echo "$1"; }
fi
plog() { while IFS= read -r _line; do rdlog "$_line"; done; }

HOST_LOG="$HOME/searxng.log"
PID_FILE="$HOME/searxng.pid"

# [s]earx 형태로 적는 이유:
# 이 스크립트는 통째로 bash -c 인자로 전달되므로 프로세스 명령줄에 본문이 들어갑니다.
# 'searx.webapp' 라고 그대로 적으면 pgrep/pkill 이 이 스크립트 자신을 잡아버립니다.
MATCH='[s]earx.webapp'

# 실행 중인 프로세스가 있는지 확인 (pgrep 이 없으면 ps 로 대체)
_is_running() {
    if command -v pgrep >/dev/null 2>&1; then
        pgrep -f "$MATCH" >/dev/null 2>&1
    else
        ps -ef 2>/dev/null | grep -v grep | grep -q "$MATCH"
    fi
}

# 설치가 끝났는지 확인
_is_installed() {
    proot-distro login debian -- test -x /usr/local/searxng/run-webapp.sh >/dev/null 2>&1
}

searxng_start() {
    if ! _is_installed; then
        rdlog "아직 설치되지 않았습니다. 앱에서 [자동 설치]를 먼저 누르세요."
        return 1
    fi

    if _is_running; then
        rdlog "이미 실행 중입니다."
        return 0
    fi

    rdlog "SearXNG 를 시작합니다…"
    termux-wake-lock 2>/dev/null || true

    nohup proot-distro login debian -- /usr/local/searxng/run-webapp.sh >> "$HOST_LOG" 2>&1 &
    echo $! > "$PID_FILE"

    # 최대 30초까지 기다리며 실제 응답이 오는지 확인
    for i in $(seq 1 15); do
        sleep 2
        if _is_running; then
            rdlog "시작되었습니다. (${i}번째 확인)"
            return 0
        fi
    done

    rdlog "시작에 실패했습니다. 마지막 로그입니다:"
    [ -f "$HOST_LOG" ] && tail -n 20 "$HOST_LOG" | plog
    return 1
}

searxng_stop() {
    if ! _is_running; then
        rdlog "이미 중지되어 있습니다."
        return 0
    fi

    rdlog "SearXNG 를 중지합니다…"
    if command -v pkill >/dev/null 2>&1; then
        pkill -f "$MATCH" 2>/dev/null || true
    else
        ps -ef 2>/dev/null | grep -v grep | grep "$MATCH" | awk '{print $2}' | xargs -r kill 2>/dev/null || true
    fi

    sleep 2
    if _is_running; then
        rdlog "일부 프로세스가 남아 강제 종료합니다."
        if command -v pkill >/dev/null 2>&1; then
            pkill -9 -f "$MATCH" 2>/dev/null || true
        fi
        sleep 1
    fi

    rm -f "$PID_FILE" 2>/dev/null || true
    termux-wake-unlock 2>/dev/null || true

    if _is_running; then
        rdlog "중지하지 못했습니다. Termux 앱에서 직접 종료해 주세요."
        return 1
    fi

    rdlog "중지되었습니다."
    return 0
}

searxng_restart() {
    searxng_stop
    sleep 1
    searxng_start
}

searxng_status() {
    if _is_running; then
        rdlog "상태: 실행 중"
    else
        rdlog "상태: 중지됨"
    fi
}
