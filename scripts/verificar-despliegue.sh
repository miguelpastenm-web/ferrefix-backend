#!/usr/bin/env bash
# Verifica, DESDE LA MÁQUINA DEL BACKEND, que Oracle (otra máquina) es alcanzable y que los 4 servicios están sanos.
# Solo lee: no crea ni modifica datos (el único POST es un login con credenciales inexistentes, que debe dar 401).
#
#  1. Oracle: DB_HOST y DB_PORT salen del .env (nunca se imprimen claves) y se prueba la conexión TCP al 1521. Si falla,
#     las causas habituales son: Oracle sin arrancar o sin terminar de iniciar, grupo de seguridad que no permite el
#     1521 desde esta máquina, o una IP privada equivocada.
#  2. Contenedores: ferrefix_gateway, ferrefix_auth, ferrefix_tienda y ferrefix_arriendos en ejecución y "healthy".
#     El health de cada servicio incluye la base: "healthy" significa que su esquema de Oracle respondió.
#     Varios reinicios seguidos suelen indicar que el servicio no logra conectarse a Oracle.
#  3. A través del gateway: su health, los 3 documentos OpenAPI (el gateway llega a cada servicio), las lecturas públicas
#     de productos y máquinas (cada servicio consulta su esquema), la imagen de una máquina inicial y un login con
#     credenciales inexistentes que debe responder 401 (si respondiera 5xx, ms-auth no estaría llegando a su base).
#
# Uso:  ./scripts/verificar-despliegue.sh                 comprobación completa
#       ./scripts/verificar-despliegue.sh --previo        solo Oracle: úsalo ANTES del primer "up" del backend
#       ./scripts/verificar-despliegue.sh --esperar 300   reintenta hasta 300 s mientras los servicios terminan de arrancar
#       ./scripts/verificar-despliegue.sh --url http://IP:8080   gateway a consultar (por defecto http://localhost:8080)
#       ENV_FILE=/ruta/.env ./scripts/verificar-despliegue.sh     otro archivo de variables (por defecto .env)
# Requiere: bash, curl y docker. Código de salida: 0 todo bien, 1 alguna comprobación falló, 2 no pudo empezar.

set -u
cd "$(dirname "$0")/.."

ENV_FILE="${ENV_FILE:-.env}"
DOCKER="${DOCKER:-docker}"
URL="http://localhost:8080"
ESPERAR=0
PREVIO=0

uso() { sed -n '2,/^# Requiere/p' "$0" | sed 's/^# \{0,1\}//'; }
while [ $# -gt 0 ]; do
  case "$1" in
    --previo) PREVIO=1 ;;
    --esperar) shift; ESPERAR="${1:-}" ;;
    --url) shift; URL="${1:-}" ;;
    -h|--help) uso; exit 0 ;;
    *) echo "Opción desconocida: $1 (use --help)"; exit 2 ;;
  esac
  shift
done
case "$ESPERAR" in ''|*[!0-9]*) echo "--esperar requiere un número de segundos"; exit 2 ;; esac
case "$URL" in http://*|https://*) ;; *) echo "--url debe empezar con http:// o https://"; exit 2 ;; esac
URL="${URL%/}"

command -v curl >/dev/null || { echo "Se requiere curl"; exit 2; }
[ "$PREVIO" = "1" ] || command -v "$DOCKER" >/dev/null || { echo "Se requiere docker (o use --previo)"; exit 2; }
[ -f "$ENV_FILE" ] || { echo "No se encontró $ENV_FILE en $(pwd) (copie .env.production.backend.example a .env y complete)"; exit 2; }

env_get() { # lee UNA clave del archivo de variables sin ejecutarlo ni imprimirlo
  grep -E "^$1=" "$ENV_FILE" | head -1 | cut -d= -f2- | tr -d '\r' | sed -e 's/^"//; s/"$//' -e "s/^'//; s/'\$//"
}
DB_HOST="$(env_get DB_HOST)"
DB_PORT="$(env_get DB_PORT)"; DB_PORT="${DB_PORT:-1521}"

curl_() { curl -s --connect-timeout 5 --max-time 20 "$@"; }
codigo() { curl_ -o /dev/null -w '%{http_code}' "$@" 2>/dev/null || true; }

# ---- resultados de cada ronda (se imprimen al final: con --esperar solo se muestra la última ronda)
LINEAS=(); FALLAS=0; AVISOS=0
ok()    { LINEAS+=("  OK     $1"); }
falla() { LINEAS+=("  FALLA  $1"); FALLAS=$((FALLAS+1)); }
aviso() { LINEAS+=("  AVISO  $1"); AVISOS=$((AVISOS+1)); }
titulo() { LINEAS+=("" "== $1 =="); }

comprobar_oracle() {
  titulo "Oracle (otra máquina)"
  if [ -z "$DB_HOST" ]; then
    falla "DB_HOST no está definido en $ENV_FILE (debe ser la IP privada de la máquina Oracle)"; return
  fi
  case "$DB_HOST" in
    oracle|localhost|127.*) aviso "DB_HOST=$DB_HOST apunta a esta misma máquina o a un contenedor local: en producción debe ser la IP privada de la máquina Oracle" ;;
  esac
  if ! [[ "$DB_HOST" =~ ^[A-Za-z0-9._-]+$ ]] || ! [[ "$DB_PORT" =~ ^[0-9]{1,5}$ ]]; then
    falla "DB_HOST o DB_PORT tienen un formato inválido en $ENV_FILE"; return
  fi
  if timeout 5 bash -c "exec 3<>/dev/tcp/$DB_HOST/$DB_PORT" 2>/dev/null; then
    ok "conexión TCP a $DB_HOST:$DB_PORT"
  else
    falla "no hay conexión TCP a $DB_HOST:$DB_PORT. Revise: ¿Oracle terminó de iniciar? (docker compose -f docker-compose.oracle.yml ps, en la máquina Oracle) · el grupo de seguridad de Oracle debe permitir el 1521 desde esta máquina · la IP privada es la correcta"
  fi
}

comprobar_contenedor() { # comprobar_contenedor <nombre>
  local nombre="$1" info estado salud reinicios
  if ! info="$("$DOCKER" inspect -f '{{.State.Status}} {{if .State.Health}}{{.State.Health.Status}}{{else}}sin-healthcheck{{end}} {{.RestartCount}}' "$nombre" 2>/dev/null)"; then
    falla "$nombre: el contenedor no existe (¿docker compose -f docker-compose.backend.yml up -d --build?)"; return
  fi
  read -r estado salud reinicios <<< "$info"
  if [ "$estado" = "running" ] && [ "$salud" = "healthy" ]; then
    ok "$nombre: en ejecución y healthy"
  elif [ "$estado" = "running" ] && [ "$salud" = "starting" ]; then
    falla "$nombre: arrancando (aún no está healthy; los servicios tardan hasta ~90 s)"
  else
    falla "$nombre: estado=$estado salud=$salud (revise: $DOCKER logs --tail 50 $nombre)"
  fi
  if [ "${reinicios:-0}" -ge 3 ]; then
    aviso "$nombre: se reinició $reinicios veces; si fue al arrancar, suele ser que no lograba conectarse a Oracle"
  fi
}

comprobar_gateway() {
  titulo "A través del gateway ($URL)"
  local c cuerpo
  cuerpo="$(curl_ "$URL/actuator/health" 2>/dev/null || true)"
  if ! printf '%s' "$cuerpo" | grep -q '"status":"UP"'; then
    falla "GET /actuator/health: el gateway no responde UP en $URL"; return
  fi
  ok "GET /actuator/health: UP"
  for servicio in ms-auth ms-arriendos ms-tienda; do
    c="$(codigo "$URL/api-docs/$servicio")"
    [ "$c" = "200" ] && ok "GET /api-docs/$servicio: 200 (el gateway llega a $servicio)" || falla "GET /api-docs/$servicio: HTTP $c (se esperaba 200)"
  done
  c="$(codigo "$URL/api/v1/productos")"
  [ "$c" = "200" ] && ok "GET /api/v1/productos: 200 (ms-tienda consulta su esquema)" || falla "GET /api/v1/productos: HTTP $c (se esperaba 200)"
  c="$(codigo "$URL/api/v1/maquinas")"
  [ "$c" = "200" ] && ok "GET /api/v1/maquinas: 200 (ms-arriendos consulta su esquema)" || falla "GET /api/v1/maquinas: HTTP $c (se esperaba 200)"
  c="$(codigo "$URL/media/maquinas/maq-bosch-01.webp")"
  [ "$c" = "200" ] && ok "GET /media/maquinas/maq-bosch-01.webp: 200 (imagen inicial servida por ms-tienda)" || falla "GET /media/maquinas/maq-bosch-01.webp: HTTP $c (se esperaba 200)"
  c="$(printf '%s' '{"email":"verificacion-despliegue@ferrefix.cl","password":"credencial-inexistente"}' \
        | curl_ -o /dev/null -w '%{http_code}' -X POST "$URL/api/v1/auth/login" -H 'Content-Type: application/json' --data-binary @- 2>/dev/null || true)"
  [ "$c" = "401" ] && ok "POST /api/v1/auth/login con credenciales inexistentes: 401 (ms-auth consulta su esquema)" || falla "POST /api/v1/auth/login con credenciales inexistentes: HTTP $c (se esperaba 401)"
}

ronda() {
  LINEAS=(); FALLAS=0; AVISOS=0
  comprobar_oracle
  if [ "$PREVIO" = "1" ]; then return; fi
  titulo "Contenedores"
  for c in ferrefix_gateway ferrefix_auth ferrefix_tienda ferrefix_arriendos; do comprobar_contenedor "$c"; done
  comprobar_gateway
}

echo "Verificación del despliegue (backend). Archivo de variables: $ENV_FILE"
case "$(stat -c %a "$ENV_FILE" 2>/dev/null)" in
  600|400) ;;
  *) echo "AVISO: $ENV_FILE tiene permisos amplios; conviene chmod 600 $ENV_FILE" ;;
esac

INICIO="$(date +%s)"
while :; do
  ronda
  [ "$FALLAS" -eq 0 ] && break
  RESTANTE=$(( ESPERAR - ($(date +%s) - INICIO) ))
  [ "$RESTANTE" -gt 0 ] || break
  echo "  ... $FALLAS comprobación(es) pendientes; reintento en 10 s (quedan $RESTANTE s)"
  sleep $(( RESTANTE < 10 ? RESTANTE : 10 ))
done

printf '%s\n' "${LINEAS[@]}"
echo
if [ "$FALLAS" -eq 0 ]; then
  if [ "$PREVIO" = "1" ]; then echo "RESULTADO: Oracle es alcanzable desde esta máquina. Puede levantar el backend."
  else echo "RESULTADO: Oracle y los 4 servicios están bien."; fi
  [ "$AVISOS" -eq 0 ] || echo "($AVISOS aviso(s): revíselos)"
  exit 0
fi
echo "RESULTADO: $FALLAS comprobación(es) fallaron."
exit 1
