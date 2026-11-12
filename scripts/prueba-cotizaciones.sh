#!/usr/bin/env bash
# Verificación de cotizaciones contra la API en ejecución (docker compose up).
#
#  1. Anónimo crea (201) y el acuse NO trae datos personales; anónimo no lee (401); CLIENTE no lee ni modifica (403).
#  2. POST con el token del CLIENTE (201) => al leerla como ADMIN, usuarioId = id del cliente (NO nulo).
#  3. POST con un token falso (201, sin 401) => al leerla como ADMIN, usuarioId nulo.
#  4. Validaciones: RUT inválido y tipo de proyecto inválido (400); despachoObra por defecto true.
#  5. ADMIN: filtra por estado, cambia estado y nota, PATCH vacío = 400, estado inválido = 400, elimina (204) y luego 404.
#
# Credenciales: de .env (ADMIN_EMAIL/ADMIN_PASSWORD y CLIENT_EMAIL/CLIENT_PASSWORD); NUNCA se imprimen ni viajan por
# argumentos de curl. Datos de prueba: nombre "TESTCOT <epoch> ...". Al terminar (aunque falle) se eliminan las creadas.
#
# Uso:  ./scripts/prueba-cotizaciones.sh      (BASE_URL=http://localhost:8080 por defecto; solo localhost salvo ALLOW_REMOTE=1)

set -u
cd "$(dirname "$0")/.."

BASE_URL="${BASE_URL:-http://localhost:8080}"
case "$BASE_URL" in
  http://localhost:*|http://127.0.0.1:*) ;;
  *) [ "${ALLOW_REMOTE:-0}" = "1" ] || { echo "Rechazado: BASE_URL no es local (use ALLOW_REMOTE=1 si es a propósito)."; exit 2; } ;;
esac
[ -f .env ] || { echo "No se encontró .env en $(pwd)"; exit 2; }
command -v curl >/dev/null && command -v python3 >/dev/null || { echo "Se requieren curl y python3"; exit 2; }

env_get() { grep -E "^$1=" .env | head -1 | cut -d= -f2- | tr -d '\r' | sed -e 's/^"//; s/"$//' -e "s/^'//; s/'\$//"; }
ADMIN_EMAIL="$(env_get ADMIN_EMAIL)"; ADMIN_EMAIL="${ADMIN_EMAIL:-admin@ferrefix.cl}"
CLIENT_EMAIL="$(env_get CLIENT_EMAIL)"; CLIENT_EMAIL="${CLIENT_EMAIL:-cliente@ferrefix.cl}"
ADMIN_PASSWORD="$(env_get ADMIN_PASSWORD)"; CLIENT_PASSWORD="$(env_get CLIENT_PASSWORD)"
[ -n "$ADMIN_PASSWORD" ] && [ -n "$CLIENT_PASSWORD" ] || { echo "Faltan ADMIN_PASSWORD o CLIENT_PASSWORD en .env"; exit 2; }

TMP="$(mktemp -d)"; chmod 700 "$TMP"
HDR_ADMIN="$TMP/admin.hdr"; HDR_CLIENTE="$TMP/cliente.hdr"; HDR_FALSO="$TMP/falso.hdr"; HDR_NINGUNO="$TMP/ninguno.hdr"
IDS="$TMP/ids.txt"; : > "$IDS"; : > "$HDR_NINGUNO"
FALLAS=0
MARCA="TESTCOT $(date +%s)"

json() { python3 -c 'import sys,json; d=json.load(open(sys.argv[1])); print(d.get(sys.argv[2]))' "$1" "$2" 2>/dev/null; }   # un null se imprime como "None"
tiene_clave() { python3 -c 'import sys,json; d=json.load(open(sys.argv[1])); print("si" if sys.argv[2] in d else "no")' "$1" "$2" 2>/dev/null; }
lista_contiene() { python3 -c 'import sys,json; d=json.load(open(sys.argv[1])); print("si" if any(str(x.get("id"))==sys.argv[2] for x in d) else "no")' "$1" "$2" 2>/dev/null; }

login() {
  local token
  token="$(EMAIL="$1" PASS="$2" python3 -c 'import json,os; print(json.dumps({"email":os.environ["EMAIL"],"password":os.environ["PASS"]}))' \
    | curl -s -X POST "$BASE_URL/api/v1/auth/login" -H 'Content-Type: application/json' --data-binary @- \
    | python3 -c 'import sys,json; print(json.load(sys.stdin).get("accessToken",""))' 2>/dev/null)"
  [ -n "$token" ] || return 1
  printf 'Authorization: Bearer %s\n' "$token" > "$3"; chmod 600 "$3"
}

http() { # http <cabecera> <MÉTODO> <ruta> [json] -> imprime el código HTTP; cuerpo en $TMP/last.json
  local hdr="$1" metodo="$2" ruta="$3" cuerpo="${4:-}"
  if [ -n "$cuerpo" ]; then
    printf '%s' "$cuerpo" | curl -s -o "$TMP/last.json" -w '%{http_code}' -X "$metodo" "$BASE_URL$ruta" -H @"$hdr" -H 'Content-Type: application/json' --data-binary @-
  else
    curl -s -o "$TMP/last.json" -w '%{http_code}' -X "$metodo" "$BASE_URL$ruta" -H @"$hdr"
  fi
}

limpiar() {
  echo; echo "== Limpieza =="
  local n=0 id
  while read -r id; do
    [ -z "$id" ] && continue
    [ "$(http "$HDR_ADMIN" DELETE "/api/v1/cotizaciones/$id")" = "204" ] && n=$((n+1))
  done < "$IDS"
  echo "Cotizaciones de prueba eliminadas: $n"
  rm -rf "$TMP"
}
trap limpiar EXIT

verificar() { if [ "$2" = "$3" ]; then echo "  OK   $1: $3"; else echo "  FALLA $1: esperado '$2', obtenido '$3'"; FALLAS=$((FALLAS+1)); fi; }

cuerpo() { # cuerpo <sufijo-nombre> [rut] [tipo] [con-despacho: omitir|true|false]
  local rut="${2:-11.111.111-1}" tipo="${3:-Materiales de Obra Gruesa}" desp="${4:-omitir}" extra=""
  [ "$desp" != "omitir" ] && extra=",\"despachoObra\":$desp"
  printf '{"nombre":"%s %s","rut":"%s","email":"prueba@ferrefix.cl","telefono":"+56 9 8765 4321","tipoProyecto":"%s","region":"Región Metropolitana","detalle":"Solicitud de prueba automatizada para verificar cotizaciones"%s}' "$MARCA" "$1" "$rut" "$tipo" "$extra"
}

crear() { # crear <cabecera> <json> -> imprime el código; si fue 201 registra el id para limpiar
  local code; code="$(http "$1" POST /api/v1/cotizaciones "$2")"
  [ "$code" = "201" ] && json "$TMP/last.json" id >> "$IDS"
  echo "$code"
}

echo "API: $BASE_URL"
login "$ADMIN_EMAIL" "$ADMIN_PASSWORD" "$HDR_ADMIN"     || { echo "No se pudo iniciar sesión como ADMIN"; exit 2; }
login "$CLIENT_EMAIL" "$CLIENT_PASSWORD" "$HDR_CLIENTE" || { echo "No se pudo iniciar sesión como CLIENTE"; exit 2; }
printf 'Authorization: Bearer %s\n' "eyJhbGciOiJIUzI1NiJ9.e30.firma-falsa-que-no-vale" > "$HDR_FALSO"; chmod 600 "$HDR_FALSO"
http "$HDR_CLIENTE" GET /api/v1/auth/me >/dev/null; ID_CLIENTE="$(json "$TMP/last.json" id)"
[[ "$ID_CLIENTE" =~ ^[0-9]+$ ]] || { echo "No se pudo obtener el id del cliente desde /auth/me (valor: $ID_CLIENTE)"; exit 2; }
echo "Sesiones iniciadas (credenciales no mostradas). id del cliente de prueba: $ID_CLIENTE"

echo; echo "== 1. Anónimo crea pero no lee; CLIENTE no lee ni modifica =="
verificar "anónimo crea (HTTP)" 201 "$(crear "$HDR_NINGUNO" "$(cuerpo anonima)")"
ID_ANON="$(json "$TMP/last.json" id)"
verificar "acuse: trae código" "si" "$(tiene_clave "$TMP/last.json" codigo)"
verificar "acuse: NO trae rut" "no" "$(tiene_clave "$TMP/last.json" rut)"
verificar "acuse: NO trae email" "no" "$(tiene_clave "$TMP/last.json" email)"
verificar "acuse: NO trae detalle" "no" "$(tiene_clave "$TMP/last.json" detalle)"
verificar "anónimo lista (HTTP)" 401 "$(http "$HDR_NINGUNO" GET /api/v1/cotizaciones)"
verificar "anónimo ve una (HTTP)" 401 "$(http "$HDR_NINGUNO" GET "/api/v1/cotizaciones/$ID_ANON")"
verificar "CLIENTE lista (HTTP)" 403 "$(http "$HDR_CLIENTE" GET /api/v1/cotizaciones)"
verificar "CLIENTE ve una (HTTP)" 403 "$(http "$HDR_CLIENTE" GET "/api/v1/cotizaciones/$ID_ANON")"
verificar "CLIENTE cambia estado (HTTP)" 403 "$(http "$HDR_CLIENTE" PATCH "/api/v1/cotizaciones/$ID_ANON" '{"estado":"CERRADA"}')"
verificar "CLIENTE elimina (HTTP)" 403 "$(http "$HDR_CLIENTE" DELETE "/api/v1/cotizaciones/$ID_ANON")"
verificar "CLIENTE 'mis cotizaciones' (HTTP)" 403 "$(http "$HDR_CLIENTE" GET /api/v1/cotizaciones/mis-cotizaciones)"

echo; echo "== 2. Con el token del CLIENTE: usuarioId = id del cliente =="
verificar "crear con token del cliente (HTTP)" 201 "$(crear "$HDR_CLIENTE" "$(cuerpo con-token-cliente)")"
ID_TOKEN="$(json "$TMP/last.json" id)"
verificar "ADMIN la lee (HTTP)" 200 "$(http "$HDR_ADMIN" GET "/api/v1/cotizaciones/$ID_TOKEN")"
verificar "usuarioId asociado (no nulo) = id del cliente" "$ID_CLIENTE" "$(json "$TMP/last.json" usuarioId)"

echo; echo "== 3. Con un token falso: 201 y usuarioId nulo =="
verificar "crear con token falso (HTTP, sin 401)" 201 "$(crear "$HDR_FALSO" "$(cuerpo con-token-falso)")"
ID_FALSO="$(json "$TMP/last.json" id)"
verificar "ADMIN la lee (HTTP)" 200 "$(http "$HDR_ADMIN" GET "/api/v1/cotizaciones/$ID_FALSO")"
verificar "usuarioId de la cotización con token falso" "None" "$(json "$TMP/last.json" usuarioId)"
verificar "la anónima también tiene usuarioId nulo" "None" "$(http "$HDR_ADMIN" GET "/api/v1/cotizaciones/$ID_ANON" >/dev/null; json "$TMP/last.json" usuarioId)"

echo; echo "== 4. Validaciones y valores por defecto =="
verificar "RUT con dígito verificador malo (HTTP)" 400 "$(crear "$HDR_NINGUNO" "$(cuerpo rut-malo 11.111.111-2)")"
verificar "tipo de proyecto inexistente (HTTP)" 400 "$(crear "$HDR_NINGUNO" "$(cuerpo tipo-malo 11.111.111-1 'Todas')")"
verificar "cuerpo vacío (HTTP)" 400 "$(http "$HDR_NINGUNO" POST /api/v1/cotizaciones '{}')"
verificar "despachoObra omitido => true" "True" "$(http "$HDR_ADMIN" GET "/api/v1/cotizaciones/$ID_ANON" >/dev/null; json "$TMP/last.json" despachoObra)"
verificar "despachoObra false se respeta (HTTP)" 201 "$(crear "$HDR_NINGUNO" "$(cuerpo sin-despacho 11.111.111-1 'Materiales de Obra Gruesa' false)")"
ID_SIN="$(json "$TMP/last.json" id)"
verificar "despachoObra guardado" "False" "$(http "$HDR_ADMIN" GET "/api/v1/cotizaciones/$ID_SIN" >/dev/null; json "$TMP/last.json" despachoObra)"
verificar "RUT guardado normalizado" "11111111-1" "$(json "$TMP/last.json" rut)"
verificar "estado inicial" "PENDIENTE" "$(json "$TMP/last.json" estado)"

echo; echo "== 5. ADMIN: filtrar, cambiar estado y nota, eliminar =="
verificar "PATCH estado + nota (HTTP)" 200 "$(http "$HDR_ADMIN" PATCH "/api/v1/cotizaciones/$ID_TOKEN" '{"estado":"EN_REVISION","notaAdmin":"Llamar al cliente"}')"
verificar "estado actualizado" "EN_REVISION" "$(json "$TMP/last.json" estado)"
verificar "nota guardada" "Llamar al cliente" "$(json "$TMP/last.json" notaAdmin)"
verificar "PATCH solo nota conserva el estado (HTTP)" 200 "$(http "$HDR_ADMIN" PATCH "/api/v1/cotizaciones/$ID_TOKEN" '{"notaAdmin":"Presupuesto enviado"}')"
verificar "estado intacto" "EN_REVISION" "$(json "$TMP/last.json" estado)"
verificar "PATCH nota vacía la borra (HTTP)" 200 "$(http "$HDR_ADMIN" PATCH "/api/v1/cotizaciones/$ID_TOKEN" '{"notaAdmin":""}')"
verificar "nota borrada" "None" "$(json "$TMP/last.json" notaAdmin)"
verificar "PATCH sin campos (HTTP)" 400 "$(http "$HDR_ADMIN" PATCH "/api/v1/cotizaciones/$ID_TOKEN" '{}')"
verificar "PATCH con estado inexistente (HTTP)" 400 "$(http "$HDR_ADMIN" PATCH "/api/v1/cotizaciones/$ID_TOKEN" '{"estado":"RECHAZADA"}')"
verificar "listar con filtro EN_REVISION (HTTP)" 200 "$(http "$HDR_ADMIN" GET '/api/v1/cotizaciones?estado=EN_REVISION')"
verificar "la filtrada aparece" "si" "$(lista_contiene "$TMP/last.json" "$ID_TOKEN")"
verificar "listar con estado inexistente (HTTP)" 400 "$(http "$HDR_ADMIN" GET '/api/v1/cotizaciones?estado=BASURA')"
verificar "listar sin filtro (HTTP)" 200 "$(http "$HDR_ADMIN" GET /api/v1/cotizaciones)"
verificar "la anónima aparece en el listado" "si" "$(lista_contiene "$TMP/last.json" "$ID_ANON")"
verificar "eliminar (HTTP)" 204 "$(http "$HDR_ADMIN" DELETE "/api/v1/cotizaciones/$ID_SIN")"
verificar "ya no existe (HTTP)" 404 "$(http "$HDR_ADMIN" GET "/api/v1/cotizaciones/$ID_SIN")"
verificar "eliminar otra vez (HTTP)" 404 "$(http "$HDR_ADMIN" DELETE "/api/v1/cotizaciones/$ID_SIN")"

echo
if [ "$FALLAS" -eq 0 ]; then echo "RESULTADO: TODO OK"; STATUS=0; else echo "RESULTADO: $FALLAS verificación(es) fallaron"; STATUS=1; fi
exit "$STATUS"
