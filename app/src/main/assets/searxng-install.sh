#!/data/data/com.termux/files/usr/bin/bash
# ============================================================================
# SearXNG Runner - 자동 설치 스크립트
#
# 실행 위치: Termux (앱이 RUN_COMMAND 인텐트로 이 내용을 bash 에 넘깁니다)
# 하는 일  : Termux 에 proot-distro 설치 → Debian 설치 → Debian 안에 SearXNG 설치
#            → settings.yml 자동 생성 (secret_key 무작위, html+json, 0.0.0.0:8080)
#            → 시작 스크립트와 부팅 스크립트 생성
#
# 안전성  : 여러 번 실행해도 됩니다. 이미 설치된 단계는 건너뜁니다.
# 참고    : 설치 방법은 공식 문서 docs.searxng.org 의 "Step by step installation" 을
#           따르되, proot 안에서는 sudo 가 동작하지 않으므로 useradd 없이 root 로
#           /usr/local/searxng 에 설치합니다.
# ============================================================================

# 앱이 로그 수신 함수를 미리 정의해 주지만, 직접 실행할 때를 위해 대비합니다.
if ! type rdlog >/dev/null 2>&1; then
    rdlog() { echo "$1"; }
fi

# proot 출력 한 줄씩 앱으로 전달
plog() { while IFS= read -r _line; do rdlog "$_line"; done; }

HOST_LOG="$HOME/searxng.log"
LOG_TAG="[설치]"

# [s]earx 형태: 이 스크립트 본문이 프로세스 명령줄에 들어가므로
# 'searx.webapp' 라고 그대로 쓰면 pgrep 이 자기 자신을 잡습니다.
MATCH='[s]earx.webapp'

rdlog "$LOG_TAG 시작합니다. 화면을 닫아도 Termux 알림에서 진행 상황을 볼 수 있습니다."

# ---------------------------------------------------------------------------
# 1. Termux 패키지 준비
# ---------------------------------------------------------------------------
rdlog "[1/6] Termux 패키지를 준비합니다…"
pkg update -y >/dev/null 2>&1 || true
if ! command -v proot-distro >/dev/null 2>&1; then
    yes | pkg install -y proot-distro 2>&1 | plog
fi
# curl: 앱으로 진행 로그를 보내는 데 사용합니다.
if ! command -v curl >/dev/null 2>&1; then
    yes | pkg install -y curl 2>&1 | plog
fi
# pgrep/pkill: 실행 상태 확인과 중지에 사용합니다.
if ! command -v pgrep >/dev/null 2>&1; then
    yes | pkg install -y procps 2>&1 | plog
fi
if ! command -v proot-distro >/dev/null 2>&1; then
    rdlog "오류: proot-distro 를 설치하지 못했습니다. 인터넷 연결을 확인한 뒤 다시 시도하세요."
    exit 1
fi
rdlog "[1/6] 완료"

# ---------------------------------------------------------------------------
# 2. Debian 설치 (이미 있으면 건너뜀)
# ---------------------------------------------------------------------------
rdlog "[2/6] Debian 환경을 확인합니다…"
if proot-distro login debian -- true >/dev/null 2>&1; then
    rdlog "[2/6] Debian 이 이미 설치되어 있습니다. 건너뜁니다."
else
    rdlog "[2/6] Debian 을 설치합니다. 처음에는 몇 분 걸립니다…"
    yes | proot-distro install debian 2>&1 | plog
    if ! proot-distro login debian -- true >/dev/null 2>&1; then
        rdlog "오류: Debian 설치에 실패했습니다. 위 로그를 확인하세요."
        exit 1
    fi
    rdlog "[2/6] 완료"
fi

# ---------------------------------------------------------------------------
# 3. Debian 안에 필요한 패키지 설치
# ---------------------------------------------------------------------------
rdlog "[3/6] Debian 패키지를 설치합니다… (몇 분 걸립니다)"
proot-distro login debian -- bash -c '
set -e
export DEBIAN_FRONTEND=noninteractive
apt-get update
apt-get install -y --no-install-recommends \
  python3 python3-dev python3-venv python3-pip \
  git build-essential ca-certificates \
  libxslt1-dev zlib1g-dev libffi-dev libssl-dev
' 2>&1 | plog
if [ "${PIPESTATUS[0]}" != "0" ]; then
    rdlog "오류: Debian 패키지 설치에 실패했습니다."
    exit 1
fi
rdlog "[3/6] 완료"

# ---------------------------------------------------------------------------
# 4. SearXNG 설치 (이미 설치되어 있으면 건너뜀)
# ---------------------------------------------------------------------------
rdlog "[4/6] SearXNG 설치 상태를 확인합니다…"

ALREADY_INSTALLED=0
if proot-distro login debian -- /usr/local/searxng/searx-pyenv/bin/python -c 'import searx' >/dev/null 2>&1; then
    ALREADY_INSTALLED=1
    VERSION=$(proot-distro login debian -- /usr/local/searxng/searx-pyenv/bin/python -c 'import searx; print(searx.__version__)' 2>/dev/null | tr -d '\r')
    rdlog "[4/6] 이미 설치되어 있습니다 (버전 $VERSION). 건너뜁니다."
fi

if [ "$ALREADY_INSTALLED" = "0" ]; then
    rdlog "[4/6] 소스를 내려받습니다…"
    proot-distro login debian -- bash -c '
    set -e
    mkdir -p /usr/local/searxng
    if [ -d /usr/local/searxng/searxng-src/.git ]; then
        cd /usr/local/searxng/searxng-src
        git pull --ff-only || true
    else
        git clone --depth 1 https://github.com/searxng/searxng /usr/local/searxng/searxng-src
    fi
    ' 2>&1 | plog

    rdlog "[4/6] 파이썬 환경을 만들고 SearXNG 를 설치합니다… (가장 오래 걸립니다)"
    proot-distro login debian -- bash -c '
    set -e
    python3 -m venv /usr/local/searxng/searx-pyenv
    . /usr/local/searxng/searx-pyenv/bin/activate
    pip install -U pip setuptools wheel
    pip install -U pyyaml msgspec typing-extensions pybind11
    cd /usr/local/searxng/searxng-src
    pip install --use-pep517 --no-build-isolation -e .
    ' 2>&1 | plog

    if ! proot-distro login debian -- /usr/local/searxng/searx-pyenv/bin/python -c 'import searx' >/dev/null 2>&1; then
        rdlog "오류: SearXNG 설치에 실패했습니다. 위 로그의 마지막 오류를 확인하세요."
        exit 1
    fi
    rdlog "[4/6] 완료"
fi

# ---------------------------------------------------------------------------
# 5. settings.yml 생성 + 실행 스크립트 생성
# ---------------------------------------------------------------------------
rdlog "[5/6] settings.yml 을 만듭니다…"

# Debian 안에서 직접 settings.yml 과 실행 스크립트를 만듭니다.
# (secret_key 는 python 의 secrets.token_hex(32) 로 64자 무작위 생성.
#  이미 settings.yml 이 있으면 기존 secret_key 를 그대로 유지하므로 재실행해도 안전합니다.)
proot-distro login debian -- bash -s <<'OUTER' 2>&1 | plog
set -e
mkdir -p /etc/searxng

SECRET=""
if [ -f /etc/searxng/settings.yml ]; then
    SECRET=$(sed -n 's/^[[:space:]]*secret_key:[[:space:]]*"\{0,1\}\([^"]*\)"\{0,1\}[[:space:]]*$/\1/p' \
        /etc/searxng/settings.yml | head -n 1)
fi
if [ -z "$SECRET" ] || [ "$SECRET" = "ultrasecretkey" ]; then
    SECRET=$(python3 -c 'import secrets; print(secrets.token_hex(32))')
    echo "새 secret_key 를 생성했습니다."
else
    echo "기존 secret_key 를 유지합니다."
fi

cat > /etc/searxng/settings.yml <<YML
# SearXNG Runner 가 자동 생성한 설정 파일입니다.
# 값을 바꾸려면 이 파일을 수정한 뒤 앱에서 [재시작]을 누르세요.
use_default_settings: true

general:
  instance_name: "SearXNG Runner"

search:
  safe_search: 0
  formats:
    - html
    - json

server:
  secret_key: "$SECRET"
  limiter: false
  public_instance: false
  image_proxy: false
  port: 8080
  bind_address: "0.0.0.0"
YML
chmod 644 /etc/searxng/settings.yml
echo "settings.yml 생성 완료 (secret_key $(echo -n "$SECRET" | wc -c)자)"

cat > /usr/local/searxng/run-webapp.sh <<'RUNEOF'
#!/bin/bash
# SearXNG Runner 가 만든 실행 스크립트입니다.
export SEARXNG_SETTINGS_PATH=/etc/searxng/settings.yml
export SEARXNG_PORT=8080
export SEARXNG_BIND_ADDRESS=0.0.0.0
cd /usr/local/searxng/searxng-src || exit 1
. /usr/local/searxng/searx-pyenv/bin/activate
exec python -m searx.webapp
RUNEOF
chmod +x /usr/local/searxng/run-webapp.sh
echo "실행 스크립트 생성 완료"
OUTER

if ! proot-distro login debian -- test -x /usr/local/searxng/run-webapp.sh >/dev/null 2>&1; then
    rdlog "오류: settings.yml 또는 실행 스크립트를 만들지 못했습니다."
    exit 1
fi
rdlog "[5/6] 완료"

# ---------------------------------------------------------------------------
# 6. Termux 쪽 부팅 자동 시작 스크립트 (Termux:Boot 앱이 있을 때만 동작)
# ---------------------------------------------------------------------------
rdlog "[6/6] 부팅 자동 시작 스크립트를 준비합니다…"
mkdir -p "$HOME/.termux/boot"
cat > "$HOME/.termux/boot/10-searxng.sh" <<'BOOTEOF'
#!/data/data/com.termux/files/usr/bin/bash
# Termux:Boot 가 부팅 시 자동 실행합니다.
# (Termux:Boot 앱이 설치되어 있어야 하며, 앱을 한 번 실행해 두어야 합니다)
termux-wake-lock 2>/dev/null || true
if pgrep -f 'searx.webapp' >/dev/null 2>&1; then
    exit 0
fi
nohup proot-distro login debian -- /usr/local/searxng/run-webapp.sh >> "$HOME/searxng.log" 2>&1 &
BOOTEOF
chmod +x "$HOME/.termux/boot/10-searxng.sh"
rdlog "[6/6] 완료"

# ---------------------------------------------------------------------------
# 마무리: 바로 시작
# ---------------------------------------------------------------------------
rdlog "설치가 끝났습니다. SearXNG 를 시작합니다…"
termux-wake-lock 2>/dev/null || true

if pgrep -f "$MATCH" >/dev/null 2>&1; then
    rdlog "이미 실행 중입니다. 앱에서 [JSON 테스트]로 확인하세요."
else
    nohup proot-distro login debian -- /usr/local/searxng/run-webapp.sh >> "$HOST_LOG" 2>&1 &
    echo $! > "$HOME/searxng.pid"

    STARTED=0
    for _i in $(seq 1 15); do
        sleep 2
        if pgrep -f "$MATCH" >/dev/null 2>&1; then
            STARTED=1
            break
        fi
    done

    if [ "$STARTED" = "1" ]; then
        rdlog "SearXNG 시작 완료. 앱에서 [JSON 테스트]를 눌러 확인하세요."
    else
        rdlog "시작하지 못했습니다. 아래 마지막 로그를 확인하세요:"
        if [ -f "$HOST_LOG" ]; then
            tail -n 20 "$HOST_LOG" | plog
        fi
    fi
fi

rdlog "=== 설치 스크립트 끝 ==="
