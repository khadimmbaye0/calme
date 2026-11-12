#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

# Garde la configuration Docker locale dans le dépôt (ignorée par Git).
export DOCKER_CONFIG="${DOCKER_CONFIG:-$ROOT_DIR/.docker}"
mkdir -p "$DOCKER_CONFIG"

if ! command -v docker >/dev/null 2>&1; then
  echo "Docker est requis." >&2
  exit 1
fi
if ! docker compose version >/dev/null 2>&1; then
  echo "Docker Compose v2 est requis." >&2
  exit 1
fi
if ! command -v python3 >/dev/null 2>&1; then
  echo "Python 3 est requis pour préparer la configuration locale." >&2
  exit 1
fi

if [ ! -f .env ]; then
  cp .env.example .env
  chmod 600 .env
fi

generated_auth="$(
  python3 - <<'PY'
from pathlib import Path
from typing import Optional
import re
import secrets

path = Path(".env")
text = path.read_text()


def get(key: str) -> Optional[str]:
    match = re.search(rf"^{re.escape(key)}=(.*)$", text, re.MULTILINE)
    return match.group(1).strip() if match else None


def set_value(key: str, value: str) -> None:
    global text
    pattern = re.compile(rf"^{re.escape(key)}=.*$", re.MULTILINE)
    replacement = f"{key}={value}"
    if pattern.search(text):
        text = pattern.sub(replacement, text)
    else:
        text = text.rstrip("\n") + f"\n{replacement}\n"


postgres_password = get("POSTGRES_PASSWORD")
if not postgres_password or postgres_password == "replace-locally":
    set_value("POSTGRES_PASSWORD", secrets.token_urlsafe(24))

auth_password = get("CALME_AUTH_PASSWORD")
if not auth_password or auth_password == "replace-locally":
    auth_password = secrets.token_urlsafe(24)
    set_value("CALME_AUTH_PASSWORD", auth_password)
    generated = auth_password
else:
    generated = ""

if get("DEEPSEEK_API_KEY") is None:
    set_value("DEEPSEEK_API_KEY", "")
if not get("SPRING_DATASOURCE_PASSWORD"):
    set_value("SPRING_DATASOURCE_PASSWORD", "${POSTGRES_PASSWORD}")

path.write_text(text.rstrip("\n") + "\n")
path.chmod(0o600)
print(generated)
PY
)"

if [ "${1:-}" = "--prepare-only" ]; then
  echo "Configuration .env prête."
  if [ -n "$generated_auth" ]; then
    echo "Mot de passe généré : ${generated_auth}"
  fi
  exit 0
fi

echo "Construction et démarrage de Calme..."
docker compose up --build -d

frontend_port="$(
  python3 - <<'PY'
from pathlib import Path
import re

text = Path(".env").read_text()
match = re.search(r"^FRONTEND_PORT=(.*)$", text, re.MULTILINE)
value = match.group(1).strip() if match else ""
print(value or "5173")
PY
)"

echo "Attente de la disponibilité de l'application..."
ready=0
for _ in $(seq 1 90); do
  if python3 - "$frontend_port" <<'PY'
import sys
import urllib.request

port = sys.argv[1]
try:
    with urllib.request.urlopen(f"http://127.0.0.1:{port}/api/health", timeout=2) as response:
        raise SystemExit(0 if response.status == 200 else 1)
except Exception:
    raise SystemExit(1)
PY
  then
    ready=1
    break
  fi
  sleep 2
done

if [ "$ready" -ne 1 ]; then
  echo "Calme n'est pas prêt. État des services :" >&2
  docker compose ps >&2
  docker compose logs --tail=80 backend frontend >&2
  exit 1
fi

echo
echo "Calme est disponible : http://localhost:${frontend_port}"
echo "Compte local : local@calme.invalid"
if [ -n "$generated_auth" ]; then
  echo "Mot de passe généré : ${generated_auth}"
  echo "Il est enregistré dans .env (non versionné). Note-le maintenant."
else
  echo "Mot de passe : valeur de CALME_AUTH_PASSWORD dans .env."
fi
