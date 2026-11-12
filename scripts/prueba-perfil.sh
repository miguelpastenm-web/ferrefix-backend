#!/usr/bin/env bash
# Verificación de PUT /api/v1/users/me contra la API en ejecución (docker compose up).
#
#  1. Anónimo no puede (401).
#  2. CLIENTE edita su perfil (200); GET /auth/me lo refleja y el MISMO token sigue valiendo (no hay que volver a iniciar sesión).
#  3. Un cuerpo con email, rut, roles, password o activo NO cambia nada de eso (el login con el email original sigue funcionando).
#  4. Opcionales omitidos o en blanco => null.  5. Validaciones => 400.
#  6. ADMIN edita su propio perfil (200).  7. No existe edición de otros usuarios (PUT /users/{id} => 404).
#
# RESTAURA los valores originales de las dos cuentas al terminar (aunque falle), incluidos los que estaban en null.
# Credenciales: de .env (ADMIN_EMAIL/ADMIN_PASSWORD y CLIENT_EMAIL/CLIENT_PASSWORD); NUNCA se imprimen ni viajan por
# argumentos de curl (stdin y un archivo de cabeceras con permisos 600).
#
# Uso:  ./scripts/prueba-perfil.sh      (BASE_URL=http://localhost:8080 por defecto; solo localhost salvo ALLOW_REMOTE=1)

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
HDR_ADMIN="$TMP/admin.hdr"; HDR_CLIENTE="$TMP/cliente.hdr"; HDR_NINGUNO="$TMP/ninguno.hdr"; : > "$HDR_NINGUNO"
ORIG_ADMIN="$TMP/orig_admin.json"; ORIG_CLIENTE="$TMP/orig_cliente.json"
FALLAS=0
MARCA="TESTPERFIL $(date +%s)"

campo() { python3 -c 'import sys,json; d=json.load(open(sys.argv[1])); print(d.get(sys.argv[2]))' "$1" "$2" 2>/dev/null; }   # un null se imprime como "None"

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

verificar() { if [ "$2" = "$3" ]; then echo "  OK   $1: $3"; else echo "  FALLA $1: esperado '$2', obtenido '$3'"; FALLAS=$((FALLAS+1)); fi; }

restaurar() { # restaurar <cabecera> <archivo-con-el-/me-original> <etiqueta>: PUT con los valores originales (null incluido)
  local hdr="$1" orig="$2" etiqueta="$3" body code
  [ -s "$orig" ] || return 0
  body="$(python3 -c 'import sys,json; d=json.load(open(sys.argv[1])); print(json.dumps({k:d.get(k) for k in ("nombre","telefono","direccion","region","comuna")}))' "$orig")"
  code="$(http "$hdr" PUT /api/v1/users/me "$body")"
  if [ "$code" = "200" ]; then echo "  perfil $etiqueta restaurado"; else echo "  ADVERTENCIA: no se pudo restaurar el perfil $etiqueta (HTTP $code). Revisarlo a mano."; fi
}

limpiar() {
  echo; echo "== Restauración de los perfiles originales =="
  restaurar "$HDR_CLIENTE" "$ORIG_CLIENTE" "CLIENTE"
  restaurar "$HDR_ADMIN" "$ORIG_ADMIN" "ADMIN"
  rm -rf "$TMP"
}
trap limpiar EXIT

echo "API: $BASE_URL"
login "$ADMIN_EMAIL" "$ADMIN_PASSWORD" "$HDR_ADMIN"     || { echo "No se pudo iniciar sesión como ADMIN"; exit 2; }
login "$CLIENT_EMAIL" "$CLIENT_PASSWORD" "$HDR_CLIENTE" || { echo "No se pudo iniciar sesión como CLIENTE"; exit 2; }
# Estado ORIGINAL (se guarda ANTES de tocar nada, para poder restaurarlo).
[ "$(http "$HDR_CLIENTE" GET /api/v1/auth/me)" = "200" ] && cp "$TMP/last.json" "$ORIG_CLIENTE"
[ "$(http "$HDR_ADMIN" GET /api/v1/auth/me)" = "200" ] && cp "$TMP/last.json" "$ORIG_ADMIN"
[ -s "$ORIG_CLIENTE" ] && [ -s "$ORIG_ADMIN" ] || { echo "No se pudo leer el perfil original de las cuentas (GET /auth/me)"; exit 2; }
ID_CLIENTE="$(campo "$ORIG_CLIENTE" id)"; RUT_CLIENTE="$(campo "$ORIG_CLIENTE" rut)"
[[ "$ID_CLIENTE" =~ ^[0-9]+$ ]] || { echo "id del cliente no numérico (valor: $ID_CLIENTE)"; exit 2; }
echo "Sesiones iniciadas (credenciales no mostradas). Perfiles originales guardados para restaurarlos al final."
echo "  CLIENTE original: nombre='$(campo "$ORIG_CLIENTE" nombre)' telefono='$(campo "$ORIG_CLIENTE" telefono)' direccion='$(campo "$ORIG_CLIENTE" direccion)' region='$(campo "$ORIG_CLIENTE" region)' comuna='$(campo "$ORIG_CLIENTE" comuna)'"

NUEVO="{\"nombre\":\"$MARCA Cliente\",\"telefono\":\"+56 9 1234 5678\",\"direccion\":\"Av. Prueba 123\",\"region\":\"Región de Valparaíso\",\"comuna\":\"Viña del Mar\"}"

echo; echo "== 1. Anónimo no puede =="
verificar "PUT sin token (HTTP)" 401 "$(http "$HDR_NINGUNO" PUT /api/v1/users/me "$NUEVO")"

echo; echo "== 2. CLIENTE edita su perfil; el mismo token sigue valiendo =="
verificar "PUT del CLIENTE (HTTP)" 200 "$(http "$HDR_CLIENTE" PUT /api/v1/users/me "$NUEVO")"
verificar "respuesta: nombre" "$MARCA Cliente" "$(campo "$TMP/last.json" nombre)"
verificar "respuesta: sin passwordHash" "None" "$(campo "$TMP/last.json" passwordHash)"
verificar "GET /auth/me con el MISMO token (HTTP)" 200 "$(http "$HDR_CLIENTE" GET /api/v1/auth/me)"
verificar "/me refleja el nombre" "$MARCA Cliente" "$(campo "$TMP/last.json" nombre)"
verificar "/me refleja el teléfono" "+56 9 1234 5678" "$(campo "$TMP/last.json" telefono)"
verificar "/me refleja la dirección" "Av. Prueba 123" "$(campo "$TMP/last.json" direccion)"
verificar "/me refleja la región" "Región de Valparaíso" "$(campo "$TMP/last.json" region)"
verificar "/me refleja la comuna" "Viña del Mar" "$(campo "$TMP/last.json" comuna)"

echo; echo "== 3. Lo inmutable no cambia aunque se mande =="
INTRUSOS="{\"id\":99999,\"email\":\"hack@example.com\",\"rut\":\"1-9\",\"roles\":[\"ADMIN\"],\"password\":\"claveNueva12345\",\"passwordHash\":\"x\",\"activo\":false,\"nombre\":\"$MARCA Intrusos\"}"
verificar "PUT con campos prohibidos (HTTP)" 200 "$(http "$HDR_CLIENTE" PUT /api/v1/users/me "$INTRUSOS")"
verificar "el nombre sí cambió" "$MARCA Intrusos" "$(campo "$TMP/last.json" nombre)"
http "$HDR_CLIENTE" GET /api/v1/auth/me >/dev/null
verificar "id intacto" "$ID_CLIENTE" "$(campo "$TMP/last.json" id)"
verificar "rut intacto" "$RUT_CLIENTE" "$(campo "$TMP/last.json" rut)"
verificar "email intacto" "$CLIENT_EMAIL" "$(campo "$TMP/last.json" email)"
verificar "roles intactos" "['CLIENTE']" "$(campo "$TMP/last.json" roles)"
verificar "sigue activo" "True" "$(campo "$TMP/last.json" activo)"
rm -f "$TMP/cliente2.hdr"
verificar "login con el email y la contraseña originales sigue funcionando" "si" "$(login "$CLIENT_EMAIL" "$CLIENT_PASSWORD" "$TMP/cliente2.hdr" && echo si || echo no)"

echo; echo "== 4. Opcionales omitidos o en blanco => null =="
verificar "PUT solo con nombre (HTTP)" 200 "$(http "$HDR_CLIENTE" PUT /api/v1/users/me "{\"nombre\":\"$MARCA Solo\"}")"
verificar "teléfono pasó a null" "None" "$(campo "$TMP/last.json" telefono)"
verificar "dirección pasó a null" "None" "$(campo "$TMP/last.json" direccion)"
verificar "región pasó a null" "None" "$(campo "$TMP/last.json" region)"
verificar "comuna pasó a null" "None" "$(campo "$TMP/last.json" comuna)"
verificar "PUT con opcionales en blanco (HTTP)" 200 "$(http "$HDR_CLIENTE" PUT /api/v1/users/me "{\"nombre\":\"$MARCA Blancos\",\"telefono\":\"\",\"direccion\":\"   \",\"region\":\"\",\"comuna\":\"\"}")"
verificar "dirección en blanco pasó a null" "None" "$(campo "$TMP/last.json" direccion)"
verificar "teléfono vacío pasó a null" "None" "$(campo "$TMP/last.json" telefono)"

echo; echo "== 5. Validaciones =="
LARGO121="$(python3 -c "print('x'*121)")"; LARGO201="$(python3 -c "print('x'*201)")"
verificar "cuerpo vacío (HTTP)" 400 "$(http "$HDR_CLIENTE" PUT /api/v1/users/me '{}')"
verificar "nombre vacío (HTTP)" 400 "$(http "$HDR_CLIENTE" PUT /api/v1/users/me '{"nombre":""}')"
verificar "nombre en blanco (HTTP)" 400 "$(http "$HDR_CLIENTE" PUT /api/v1/users/me '{"nombre":"   "}')"
verificar "nombre de 121 caracteres (HTTP)" 400 "$(http "$HDR_CLIENTE" PUT /api/v1/users/me "{\"nombre\":\"$LARGO121\"}")"
verificar "teléfono con letras (HTTP)" 400 "$(http "$HDR_CLIENTE" PUT /api/v1/users/me '{"nombre":"Ana","telefono":"abc"}')"
verificar "dirección de 201 caracteres (HTTP)" 400 "$(http "$HDR_CLIENTE" PUT /api/v1/users/me "{\"nombre\":\"Ana\",\"direccion\":\"$LARGO201\"}")"
verificar "el 400 trae detalles" "list" "$(python3 -c 'import json,sys; d=json.load(open(sys.argv[1])); print(type(d.get("details")).__name__)' "$TMP/last.json")"
verificar "tras los 400 el perfil no cambió" "$MARCA Blancos" "$(http "$HDR_CLIENTE" GET /api/v1/auth/me >/dev/null; campo "$TMP/last.json" nombre)"

echo; echo "== 6. ADMIN edita su propio perfil =="
verificar "PUT del ADMIN (HTTP)" 200 "$(http "$HDR_ADMIN" PUT /api/v1/users/me "{\"nombre\":\"$MARCA Admin\",\"telefono\":\"+56 2 2345 6789\"}")"
verificar "/me del ADMIN refleja el cambio" "$MARCA Admin" "$(http "$HDR_ADMIN" GET /api/v1/auth/me >/dev/null; campo "$TMP/last.json" nombre)"
verificar "el ADMIN sigue siendo ADMIN" "['ADMIN']" "$(campo "$TMP/last.json" roles)"

echo; echo "== 7. No existe edición de otros usuarios =="
verificar "ADMIN: PUT /users/$ID_CLIENTE (HTTP)" 404 "$(http "$HDR_ADMIN" PUT "/api/v1/users/$ID_CLIENTE" "$NUEVO")"
verificar "CLIENTE: PUT /users/1 (HTTP)" 404 "$(http "$HDR_CLIENTE" PUT /api/v1/users/1 "$NUEVO")"

echo
if [ "$FALLAS" -eq 0 ]; then echo "RESULTADO: TODO OK"; STATUS=0; else echo "RESULTADO: $FALLAS verificación(es) fallaron"; STATUS=1; fi
exit "$STATUS"
