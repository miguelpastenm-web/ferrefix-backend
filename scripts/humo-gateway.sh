#!/usr/bin/env bash
# Prueba de humo A TRAVÉS DEL GATEWAY contra la API en ejecución (docker compose up).
# Todo entra por el puerto público (8080): si algo de esto falla, el front tampoco funcionará.
#
#  1. Gateway arriba: /actuator/health público y solo con el estado.
#  2. Login de ADMIN y de CLIENTE.
#  2b. Autenticación completa (ms-auth): registro de una cuenta nueva, login, /auth/me, renovación (refresh) y el
#      token resultante aceptado por ms-tienda (que lo verifica con el secreto compartido, sin llamar a ms-auth).
#      (SIN_REGISTRO=1 lo omite)
#  3. Productos: el ADMIN crea un producto de prueba (stock 2); el listado público lo incluye.
#  4. Orden: el CLIENTE compra 1 unidad (201, stock 1); el ADMIN la cancela (200, el stock vuelve a 2) y la elimina (204).
#  4b. Máquinas y arriendos (ms-arriendos): las 4 máquinas iniciales son públicas y sus imágenes se sirven por el gateway
#      (desde ms-tienda); el ADMIN crea una máquina con una unidad, el CLIENTE la arrienda, el ADMIN ve los datos del
#      cliente guardados al arrendar, la devuelve y se elimina todo. Las rutas de ADMIN rechazan al CLIENTE (403).
#  4c. Cotizaciones (ms-arriendos): cualquiera pide una (con o sin token: con token queda asociada al usuario); solo el
#      ADMIN las lista, las actualiza y las elimina.
#  5. Imagen: el ADMIN sube un PNG (multipart) y se descarga sin token desde /media/**.   (SIN_IMAGEN=1 lo omite)
#  6. CORS: el preflight y la respuesta real traen Access-Control-Allow-Origin exactamente UNA vez.
#  7. Errores del gateway en JSON: una ruta inexistente devuelve 404 con el formato de la API.
#  8. Swagger UI en el gateway, con el documento OpenAPI de cada uno de los 3 servicios.
#
# Datos de prueba: el producto y la máquina llevan SKU o código TESTHUMO-* y se eliminan al terminar (aunque falle),
# junto con la orden, el arriendo y las cotizaciones.
# La imagen subida NO se puede borrar por la API (no existe DELETE de imágenes): queda un PNG de 1x1 en uploads.
# La cuenta registrada (humo-<fecha>@ferrefix.cl) tampoco se puede borrar por la API: queda en ms-auth. Es un CLIENTE
# sin órdenes ni arriendos; usa SIN_REGISTRO=1 si no quieres crearla.
# Credenciales: se leen de .env o del archivo indicado en ENV_FILE (ADMIN_EMAIL/ADMIN_PASSWORD y CLIENT_EMAIL/
# CLIENT_PASSWORD) y NUNCA se imprimen: no viajan por argumentos de curl (se usan stdin y un archivo de cabeceras con
# permisos 600).
#
# Uso:  ./scripts/humo-gateway.sh                 (gateway en http://localhost:8080)
#       SIN_IMAGEN=1 ./scripts/humo-gateway.sh    (no sube la imagen)
#       SIN_REGISTRO=1 ./scripts/humo-gateway.sh  (no registra una cuenta nueva)
#       ORIGEN=http://localhost:5173 ./scripts/humo-gateway.sh   (por defecto: el primer origen de CORS_ALLOWED_ORIGINS)
#       ENV_FILE=./humo.env ./scripts/humo-gateway.sh   (credenciales en otro archivo; ver "Destino remoto")
#
# Destino remoto (por ejemplo, el gateway en AWS, desde tu equipo):
#   ALLOW_REMOTE=1 BASE_URL=http://IP-ELASTICA-DEL-BACKEND:8080 ORIGEN=http://IP-PUBLICA-DEL-FRONT \
#     ENV_FILE=./humo.env ./scripts/humo-gateway.sh
# humo.env solo necesita ADMIN_EMAIL, ADMIN_PASSWORD, CLIENT_EMAIL y CLIENT_PASSWORD (las del .env del backend); manténgalo
# con permisos 600 y fuera del repositorio. ORIGEN debe ser el origen del front tal como lo envía el navegador. Contra un
# destino remoto el script CREA datos de prueba en esa base (los elimina al terminar, salvo el PNG y la cuenta de humo).
# Cada llamada HTTP tiene un tiempo máximo (CURL_MAX_TIME, 60 s por defecto): un grupo de seguridad que bloquea no lo cuelga.
# Solo corre contra localhost salvo ALLOW_REMOTE=1. Requiere: bash, curl, python3.
# Código de salida: 0 si todo pasó, 1 si hubo fallas, 2 si no se pudo empezar.

set -u
# ENV_FILE indicado por el usuario: se resuelve contra el directorio desde donde se invoca el script, antes del cd.
if [ -n "${ENV_FILE:-}" ]; then
  case "$ENV_FILE" in /*) ;; *) ENV_FILE="$PWD/$ENV_FILE" ;; esac
fi
cd "$(dirname "$0")/.."
ENV_FILE="${ENV_FILE:-$PWD/.env}"

BASE_URL="${BASE_URL:-http://localhost:8080}"
REMOTO=0
case "$BASE_URL" in
  http://localhost:*|http://127.0.0.1:*) ;;
  *) [ "${ALLOW_REMOTE:-0}" = "1" ] || { echo "Rechazado: BASE_URL no es local (use ALLOW_REMOTE=1 si es a propósito)."; exit 2; }
     REMOTO=1 ;;
esac
[ -f "$ENV_FILE" ] || { echo "No se encontró el archivo de variables $ENV_FILE"; exit 2; }
command -v curl >/dev/null && command -v python3 >/dev/null || { echo "Se requieren curl y python3"; exit 2; }

# Todas las llamadas HTTP con tiempo máximo: contra un destino remoto que no responde (grupo de seguridad cerrado) el
# script no debe quedarse esperando para siempre.
curl() { command curl --connect-timeout 5 --max-time "${CURL_MAX_TIME:-60}" "$@"; }

env_get() { # lee UNA clave del archivo de variables sin ejecutarlo ni imprimirlo
  grep -E "^$1=" "$ENV_FILE" | head -1 | cut -d= -f2- | tr -d '\r' | sed -e 's/^"//; s/"$//' -e "s/^'//; s/'\$//"
}
ADMIN_EMAIL="$(env_get ADMIN_EMAIL)"; ADMIN_EMAIL="${ADMIN_EMAIL:-admin@ferrefix.cl}"
CLIENT_EMAIL="$(env_get CLIENT_EMAIL)"; CLIENT_EMAIL="${CLIENT_EMAIL:-cliente@ferrefix.cl}"
ADMIN_PASSWORD="$(env_get ADMIN_PASSWORD)"; CLIENT_PASSWORD="$(env_get CLIENT_PASSWORD)"
[ -n "$ADMIN_PASSWORD" ] && [ -n "$CLIENT_PASSWORD" ] || { echo "Faltan ADMIN_PASSWORD o CLIENT_PASSWORD en $ENV_FILE"; exit 2; }
# Origen para la prueba de CORS: el primero de CORS_ALLOWED_ORIGINS (el mismo que usa el gateway).
ORIGEN_ENV="$(env_get CORS_ALLOWED_ORIGINS | cut -d, -f1 | tr -d ' ')"
ORIGEN_INDICADO="${ORIGEN:-}"
ORIGEN="${ORIGEN:-${ORIGEN_ENV:-http://localhost:5173}}"

if [ "$REMOTO" = "1" ]; then
  echo "ATENCIÓN: destino REMOTO ($BASE_URL). Esta prueba crea datos de prueba en esa base (producto, orden, máquina, arriendo,"
  echo "          cotizaciones y una cuenta de humo) y los elimina al terminar, salvo un PNG y la cuenta registrada."
  if [ -z "$ORIGEN_INDICADO" ] && [ -z "$ORIGEN_ENV" ]; then
    echo "AVISO: ORIGEN no está definido: la prueba de CORS usará $ORIGEN, que probablemente no es el origen del front."
    echo "       Defina ORIGEN=http://IP-PUBLICA-DEL-FRONT para que coincida con CORS_ALLOWED_ORIGINS del backend."
  fi
  echo
fi

TMP="$(mktemp -d)"; chmod 700 "$TMP"
HDR_ADMIN="$TMP/admin.hdr"; HDR_CLIENTE="$TMP/cliente.hdr"; HDR_NINGUNO="$TMP/ninguno.hdr"
: > "$HDR_ADMIN"; : > "$HDR_CLIENTE"; : > "$HDR_NINGUNO"
ORDEN_ID=""; PRODUCTO_ID=""; MAQUINA_ID=""; ARRIENDO_ID=""; COTIZACION_IDS=""
FALLAS=0

json() { python3 -c 'import sys,json; d=json.load(open(sys.argv[1])); print(d.get(sys.argv[2],""))' "$1" "$2" 2>/dev/null; }

login() { # login <email> <password> <archivo-de-cabecera>
  local token
  token="$(EMAIL="$1" PASS="$2" python3 -c 'import json,os; print(json.dumps({"email":os.environ["EMAIL"],"password":os.environ["PASS"]}))' \
    | curl -s -X POST "$BASE_URL/api/v1/auth/login" -H 'Content-Type: application/json' --data-binary @- \
    | python3 -c 'import sys,json; print(json.load(sys.stdin).get("accessToken",""))' 2>/dev/null)"
  [ -n "$token" ] || return 1
  printf 'Authorization: Bearer %s\n' "$token" > "$3"; chmod 600 "$3"
}

http() { # http <cabecera> <MÉTODO> <ruta> [json] -> imprime el código HTTP; el cuerpo queda en $TMP/last.json
  local hdr="$1" metodo="$2" ruta="$3" cuerpo="${4:-}"
  if [ -n "$cuerpo" ]; then
    printf '%s' "$cuerpo" | curl -s -o "$TMP/last.json" -w '%{http_code}' -X "$metodo" "$BASE_URL$ruta" -H @"$hdr" -H 'Content-Type: application/json' --data-binary @-
  else
    curl -s -o "$TMP/last.json" -w '%{http_code}' -X "$metodo" "$BASE_URL$ruta" -H @"$hdr"
  fi
}

verificar() { # verificar <descripción> <esperado> <obtenido>
  if [ "$2" = "$3" ]; then echo "  OK   $1: $3"; else echo "  FALLA $1: esperado '$2', obtenido '$3'"; FALLAS=$((FALLAS+1)); fi
}

stock_producto() { http "$HDR_ADMIN" GET "/api/v1/productos/$1" >/dev/null; json "$TMP/last.json" stock; }

# Lee un claim del payload de un JWT (sin verificar la firma: solo para inspeccionar lo que emitió ms-auth).
# Las listas (roles) se imprimen separadas por coma.
claim_jwt() { # claim_jwt <token> <claim>
  TOKEN="$1" python3 -c '
import base64, json, os, sys
payload = os.environ["TOKEN"].split(".")[1]
datos = json.loads(base64.urlsafe_b64decode(payload + "=" * (-len(payload) % 4)))
valor = datos.get(sys.argv[1], "")
print(",".join(str(x) for x in valor) if isinstance(valor, list) else valor)' "$2" 2>/dev/null
}

# Cabecera de autorización con un token ya conocido (archivo con permisos 600, como los demás).
cabecera_con() { # cabecera_con <token> <archivo>
  printf 'Authorization: Bearer %s\n' "$1" > "$2"; chmod 600 "$2"
}

# http con un cuerpo JSON que viaja por stdin (no aparece en los argumentos de curl).
http_sin_sesion() { # http_sin_sesion <MÉTODO> <ruta> <json>
  printf '%s' "$3" | curl -s -o "$TMP/last.json" -w '%{http_code}' -X "$1" "$BASE_URL$2" -H 'Content-Type: application/json' --data-binary @-
}

# ¿El cuerpo guardado (una lista JSON) trae un elemento cuyo campo vale lo indicado? Imprime "si" o "no".
lista_tiene() { # lista_tiene <campo> <valor>
  python3 -c '
import json, sys
d = json.load(open(sys.argv[1])); d = d if isinstance(d, list) else d.get("content", [])
print("si" if any(str(x.get(sys.argv[2])) == sys.argv[3] for x in d) else "no")' "$TMP/last.json" "$1" "$2" 2>/dev/null
}

# Valor de un campo dentro de un objeto del cuerpo guardado: anidado cliente rut -> last.json["cliente"]["rut"].
anidado() { # anidado <clave> <subclave>
  python3 -c '
import json, sys
d = json.load(open(sys.argv[1])).get(sys.argv[2]) or {}
print(d.get(sys.argv[3], ""))' "$TMP/last.json" "$1" "$2" 2>/dev/null
}

# Cuenta cuántas veces viene Access-Control-Allow-Origin en las cabeceras guardadas (debe ser exactamente 1).
contar_acao() { grep -ci '^access-control-allow-origin:' "$1"; }
valor_acao() { grep -i '^access-control-allow-origin:' "$1" | head -1 | cut -d: -f2- | tr -d ' \r'; }

limpiar() {
  echo; echo "== Limpieza =="
  if [ -n "$ORDEN_ID" ]; then
    http "$HDR_ADMIN" PATCH "/api/v1/ordenes/$ORDEN_ID/estado" '{"estado":"CANCELADA"}' >/dev/null   # devuelve el stock
    if [ "$(http "$HDR_ADMIN" DELETE "/api/v1/ordenes/$ORDEN_ID")" = "204" ]; then echo "  orden de prueba eliminada"
    else echo "  ADVERTENCIA: no se pudo eliminar la orden $ORDEN_ID. Revisarla a mano."; fi
  fi
  if [ -n "$PRODUCTO_ID" ]; then
    if [ "$(http "$HDR_ADMIN" DELETE "/api/v1/productos/$PRODUCTO_ID")" = "204" ]; then echo "  producto de prueba eliminado"
    else echo "  ADVERTENCIA: no se pudo eliminar el producto $PRODUCTO_ID (SKU TESTHUMO-*). Revisarlo a mano."; fi
  fi
  # El arriendo va antes que la máquina: una máquina con arriendos registrados no se puede eliminar.
  if [ -n "$ARRIENDO_ID" ]; then
    if [ "$(http "$HDR_ADMIN" DELETE "/api/v1/arriendos/$ARRIENDO_ID")" = "204" ]; then echo "  arriendo de prueba eliminado"
    else echo "  ADVERTENCIA: no se pudo eliminar el arriendo $ARRIENDO_ID. Revisarlo a mano."; fi
  fi
  if [ -n "$MAQUINA_ID" ]; then
    if [ "$(http "$HDR_ADMIN" DELETE "/api/v1/maquinas/$MAQUINA_ID")" = "204" ]; then echo "  máquina de prueba eliminada"
    else echo "  ADVERTENCIA: no se pudo eliminar la máquina $MAQUINA_ID (código TESTHUMO-*). Revisarla a mano."; fi
  fi
  for id in $COTIZACION_IDS; do
    if [ "$(http "$HDR_ADMIN" DELETE "/api/v1/cotizaciones/$id")" = "204" ]; then echo "  cotización $id eliminada"
    else echo "  ADVERTENCIA: no se pudo eliminar la cotización $id. Revisarla a mano."; fi
  done
  rm -rf "$TMP"
}
trap limpiar EXIT

echo "Gateway: $BASE_URL"

echo; echo "== 1. Gateway arriba =="
verificar "GET /actuator/health (HTTP)" 200 "$(http "$HDR_NINGUNO" GET /actuator/health)"
verificar "estado" UP "$(json "$TMP/last.json" status)"
verificar "solo devuelve el estado (cantidad de campos)" 1 "$(python3 -c 'import sys,json; print(len(json.load(open(sys.argv[1]))))' "$TMP/last.json" 2>/dev/null)"

echo; echo "== 2. Login por el gateway =="
login "$ADMIN_EMAIL" "$ADMIN_PASSWORD" "$HDR_ADMIN"     || { echo "No se pudo iniciar sesión como ADMIN (revise .env y que la API esté arriba)"; exit 2; }
login "$CLIENT_EMAIL" "$CLIENT_PASSWORD" "$HDR_CLIENTE" || { echo "No se pudo iniciar sesión como CLIENTE (revise .env)"; exit 2; }
echo "  OK   sesiones de ADMIN y CLIENTE iniciadas (credenciales no mostradas)"
verificar "GET /api/v1/auth/me sin token (HTTP)" 401 "$(http "$HDR_NINGUNO" GET /api/v1/auth/me)"
verificar "GET /api/v1/auth/me con token de CLIENTE (HTTP)" 200 "$(http "$HDR_CLIENTE" GET /api/v1/auth/me)"

if [ "${SIN_REGISTRO:-0}" = "1" ]; then
  echo; echo "== 2b. Autenticación completa (omitida: SIN_REGISTRO=1) =="
else
  echo; echo "== 2b. Autenticación completa por el gateway (ms-auth) =="
  # Datos únicos por ejecución: correo con la fecha, RUT con dígito verificador válido y una clave aleatoria
  # (se pasa por variables de entorno del proceso hijo y nunca se imprime).
  eval "$(python3 -c '
import json, secrets, time
cuerpo = str(10_000_000 + secrets.randbelow(10_000_000))
suma = sum(int(d) * (2 + i % 6) for i, d in enumerate(reversed(cuerpo)))
dv = 11 - suma % 11
dv = "0" if dv == 11 else "K" if dv == 10 else str(dv)
print("HUMO_RUT=%s-%s" % (cuerpo, dv))
print("HUMO_EMAIL=humo-%d@ferrefix.cl" % int(time.time()))
print("HUMO_PASS=%s" % secrets.token_hex(12))')"
  REGISTRO="$(HUMO_RUT="$HUMO_RUT" HUMO_EMAIL="$HUMO_EMAIL" HUMO_PASS="$HUMO_PASS" python3 -c '
import json, os
print(json.dumps({"rut": os.environ["HUMO_RUT"], "nombre": "Cuenta de humo", "email": os.environ["HUMO_EMAIL"],
                  "password": os.environ["HUMO_PASS"], "telefono": "+56 9 8765 4321"}))')"
  verificar "register: cuenta nueva (HTTP)" 201 "$(http_sin_sesion POST /api/v1/auth/register "$REGISTRO")"
  verificar "register: devuelve accessToken y refreshToken" si "$([ -n "$(json "$TMP/last.json" accessToken)" ] && [ -n "$(json "$TMP/last.json" refreshToken)" ] && echo si || echo no)"
  verificar "register: repetir el mismo correo (HTTP)" 409 "$(http_sin_sesion POST /api/v1/auth/register "$REGISTRO")"

  LOGIN="$(HUMO_EMAIL="$HUMO_EMAIL" HUMO_PASS="$HUMO_PASS" python3 -c '
import json, os
print(json.dumps({"email": os.environ["HUMO_EMAIL"], "password": os.environ["HUMO_PASS"]}))')"
  verificar "login con la cuenta nueva (HTTP)" 200 "$(http_sin_sesion POST /api/v1/auth/login "$LOGIN")"
  ACCESS="$(json "$TMP/last.json" accessToken)"; REFRESH="$(json "$TMP/last.json" refreshToken)"
  verificar "login con clave incorrecta (HTTP)" 401 "$(http_sin_sesion POST /api/v1/auth/login "$(HUMO_EMAIL="$HUMO_EMAIL" python3 -c 'import json,os; print(json.dumps({"email": os.environ["HUMO_EMAIL"], "password": "clave-incorrecta"}))')")"

  echo "  -- el access token trae la identidad completa"
  verificar "claim email (sub)" "$HUMO_EMAIL" "$(claim_jwt "$ACCESS" sub)"
  verificar "claim rut" "$HUMO_RUT" "$(claim_jwt "$ACCESS" rut)"
  verificar "claim nombre" "Cuenta de humo" "$(claim_jwt "$ACCESS" nombre)"
  verificar "claim roles" CLIENTE "$(claim_jwt "$ACCESS" roles)"
  verificar "claim type" access "$(claim_jwt "$ACCESS" type)"
  verificar "el refresh token no lleva roles" "" "$(claim_jwt "$REFRESH" roles)"

  HDR_HUMO="$TMP/humo.hdr"; cabecera_con "$ACCESS" "$HDR_HUMO"
  verificar "GET /api/v1/auth/me (HTTP)" 200 "$(http "$HDR_HUMO" GET /api/v1/auth/me)"
  verificar "me: email" "$HUMO_EMAIL" "$(json "$TMP/last.json" email)"
  verificar "me: no expone la contraseña" "" "$(json "$TMP/last.json" passwordHash)"

  echo "  -- ms-tienda acepta el token emitido por ms-auth (verifica la firma con el secreto compartido)"
  verificar "GET /api/v1/ordenes/mis-ordenes como CLIENTE (HTTP)" 200 "$(http "$HDR_HUMO" GET /api/v1/ordenes/mis-ordenes)"
  verificar "GET /api/v1/ordenes (solo ADMIN) como CLIENTE (HTTP)" 403 "$(http "$HDR_HUMO" GET /api/v1/ordenes)"

  echo "  -- renovación: los datos se leen de la base en ese momento"
  verificar "PUT /api/v1/users/me cambia el nombre (HTTP)" 200 "$(http "$HDR_HUMO" PUT /api/v1/users/me '{"nombre":"Cuenta de humo renovada"}')"
  verificar "el access token anterior sigue con el nombre viejo (desfase de hasta 15 min)" "Cuenta de humo" "$(claim_jwt "$ACCESS" nombre)"
  verificar "POST /api/v1/auth/refresh (HTTP)" 200 "$(http_sin_sesion POST /api/v1/auth/refresh "{\"refreshToken\":\"$REFRESH\"}")"
  NUEVO_ACCESS="$(json "$TMP/last.json" accessToken)"
  verificar "el access token nuevo trae el nombre actualizado" "Cuenta de humo renovada" "$(claim_jwt "$NUEVO_ACCESS" nombre)"
  verificar "el access token nuevo conserva el rol" CLIENTE "$(claim_jwt "$NUEVO_ACCESS" roles)"
  cabecera_con "$NUEVO_ACCESS" "$HDR_HUMO"
  verificar "GET /api/v1/auth/me con el token renovado (HTTP)" 200 "$(http "$HDR_HUMO" GET /api/v1/auth/me)"
  verificar "refresh con un access token (HTTP)" 401 "$(http_sin_sesion POST /api/v1/auth/refresh "{\"refreshToken\":\"$ACCESS\"}")"
  verificar "refresh con basura (HTTP)" 401 "$(http_sin_sesion POST /api/v1/auth/refresh '{"refreshToken":"no.es.un.jwt"}')"
  cabecera_con "$REFRESH" "$HDR_HUMO"
  verificar "un refresh token no sirve como access token (HTTP)" 401 "$(http "$HDR_HUMO" GET /api/v1/auth/me)"
fi

echo; echo "== 3. Productos =="
SKU="TESTHUMO-$(date +%s)"
CUERPO_PRODUCTO="$(printf '{"sku":"%s","nombre":"Producto de prueba de humo","marca":"Prueba","categoria":"Herramientas","precioVenta":1000,"stock":2,"descripcion":"Creado por scripts/humo-gateway.sh"}' "$SKU")"
verificar "ADMIN crea el producto de prueba (HTTP)" 201 "$(http "$HDR_ADMIN" POST /api/v1/productos "$CUERPO_PRODUCTO")"
PRODUCTO_ID="$(json "$TMP/last.json" id)"
[ -n "$PRODUCTO_ID" ] || { echo "No se pudo crear el producto de prueba: no se puede continuar"; exit 1; }
verificar "GET /api/v1/productos sin token (HTTP)" 200 "$(http "$HDR_NINGUNO" GET /api/v1/productos)"
verificar "el listado público incluye el producto de prueba" si "$(python3 -c '
import sys, json
d = json.load(open(sys.argv[1]))
lista = d if isinstance(d, list) else d.get("content", [])
print("si" if any(str(p.get("id")) == sys.argv[2] for p in lista) else "no")' "$TMP/last.json" "$PRODUCTO_ID" 2>/dev/null)"
verificar "stock inicial" 2 "$(stock_producto "$PRODUCTO_ID")"

echo; echo "== 4. Orden: crear, cancelar y eliminar =="
CUERPO_ORDEN="$(printf '{"items":[{"productoId":%s,"cantidad":1}],"metodoPago":"WEBPAY_DEBITO","telefono":"+56 9 8765 4321","direccionEnvio":"Av. Prueba 123","region":"Región Metropolitana","comuna":"Providencia","indicaciones":"prueba de humo del gateway"}' "$PRODUCTO_ID")"
verificar "CLIENTE crea la orden (HTTP)" 201 "$(http "$HDR_CLIENTE" POST /api/v1/ordenes "$CUERPO_ORDEN")"
ORDEN_ID="$(json "$TMP/last.json" id)"
verificar "estado de la orden" PAGADA "$(json "$TMP/last.json" estado)"
verificar "stock tras la compra" 1 "$(stock_producto "$PRODUCTO_ID")"
if [ -n "$ORDEN_ID" ]; then
  verificar "ADMIN cancela la orden (HTTP)" 200 "$(http "$HDR_ADMIN" PATCH "/api/v1/ordenes/$ORDEN_ID/estado" '{"estado":"CANCELADA"}')"
  verificar "estado tras cancelar" CANCELADA "$(json "$TMP/last.json" estado)"
  verificar "el stock vuelve al cancelar" 2 "$(stock_producto "$PRODUCTO_ID")"
  verificar "ADMIN elimina la orden cancelada (HTTP)" 204 "$(http "$HDR_ADMIN" DELETE "/api/v1/ordenes/$ORDEN_ID")"
  ORDEN_ID=""
else
  echo "  FALLA la orden no devolvió id: se omite cancelar y eliminar"; FALLAS=$((FALLAS+1))
fi

echo; echo "== 4b. Máquinas y arriendos (ms-arriendos) =="
verificar "GET /api/v1/maquinas sin token (HTTP)" 200 "$(http "$HDR_NINGUNO" GET /api/v1/maquinas)"
for codigo in MAQ-BOSCH-01 MAQ-BET-02 MAQ-GEN-03 MAQ-PLAC-04; do
  verificar "el catálogo trae la máquina inicial $codigo" si "$(lista_tiene codigo "$codigo")"
done
verificar "imagen de una máquina inicial por el gateway (HTTP)" 200 "$(curl -s -o /dev/null -w '%{http_code}' "$BASE_URL/media/maquinas/maq-bosch-01.webp")"

# Identidad del cliente tal como la ve ms-auth: el arriendo debe guardar esa misma copia.
verificar "GET /api/v1/auth/me del CLIENTE (HTTP)" 200 "$(http "$HDR_CLIENTE" GET /api/v1/auth/me)"
CLI_ID="$(json "$TMP/last.json" id)"; CLI_RUT="$(json "$TMP/last.json" rut)"; CLI_NOMBRE="$(json "$TMP/last.json" nombre)"; CLI_EMAIL="$(json "$TMP/last.json" email)"

CODIGO_MAQUINA="TESTHUMO-$(date +%s)"
CUERPO_MAQUINA="$(printf '{"codigo":"%s","nombre":"Máquina de prueba de humo","marca":"Prueba","categoria":"Pruebas","tarifaDia":10000,"incluye":"Creada por scripts/humo-gateway.sh"}' "$CODIGO_MAQUINA")"
verificar "CLIENTE no puede crear máquinas (HTTP)" 403 "$(http "$HDR_CLIENTE" POST /api/v1/maquinas "$CUERPO_MAQUINA")"
verificar "ADMIN crea la máquina de prueba (HTTP)" 201 "$(http "$HDR_ADMIN" POST /api/v1/maquinas "$CUERPO_MAQUINA")"
MAQUINA_ID="$(json "$TMP/last.json" id)"
[ -n "$MAQUINA_ID" ] || { echo "No se pudo crear la máquina de prueba: no se puede continuar"; exit 1; }
verificar "sin unidades la máquina nace NO_DISPONIBLE" NO_DISPONIBLE "$(json "$TMP/last.json" estado)"
verificar "ADMIN agrega 1 unidad (HTTP)" 201 "$(http "$HDR_ADMIN" POST "/api/v1/maquinas/$MAQUINA_ID/unidades" '{"cantidad":1}')"
verificar "GET /api/v1/maquinas/{id} sin token (HTTP)" 200 "$(http "$HDR_NINGUNO" GET "/api/v1/maquinas/$MAQUINA_ID")"
verificar "con una unidad libre queda DISPONIBLE" DISPONIBLE "$(json "$TMP/last.json" estado)"
verificar "el detalle de unidades es solo ADMIN: CLIENTE (HTTP)" 403 "$(http "$HDR_CLIENTE" GET "/api/v1/maquinas/$MAQUINA_ID/unidades")"
verificar "el detalle de unidades es solo ADMIN: ADMIN (HTTP)" 200 "$(http "$HDR_ADMIN" GET "/api/v1/maquinas/$MAQUINA_ID/unidades")"

FECHA_INICIO="$(TZ=America/Santiago date +%F)"   # el servicio valida contra la fecha de Chile
CUERPO_ARRIENDO="$(printf '{"maquinaId":%s,"diasArriendo":2,"fechaInicio":"%s","telefono":"+56 9 8765 4321","direccionFaena":"Av. Prueba 123","region":"Región Metropolitana","comuna":"Providencia"}' "$MAQUINA_ID" "$FECHA_INICIO")"
verificar "ADMIN no puede arrendar (HTTP)" 403 "$(http "$HDR_ADMIN" POST /api/v1/arriendos "$CUERPO_ARRIENDO")"
verificar "sin token no se arrienda (HTTP)" 401 "$(http "$HDR_NINGUNO" POST /api/v1/arriendos "$CUERPO_ARRIENDO")"
verificar "CLIENTE arrienda la máquina (HTTP)" 201 "$(http "$HDR_CLIENTE" POST /api/v1/arriendos "$CUERPO_ARRIENDO")"
ARRIENDO_ID="$(json "$TMP/last.json" id)"
verificar "estado del arriendo" VIGENTE "$(json "$TMP/last.json" estado)"
verificar "total = tarifa x días" 20000 "$(json "$TMP/last.json" total)"
verificar "el CLIENTE no ve los datos del cliente en su propio arriendo" "" "$(anidado cliente rut)"
verificar "sin unidades libres, la máquina queda sin disponibles" 0 "$(http "$HDR_NINGUNO" GET "/api/v1/maquinas/$MAQUINA_ID" >/dev/null; json "$TMP/last.json" unidadesDisponibles)"
verificar "arrendar la misma máquina otra vez (HTTP)" 409 "$(http "$HDR_CLIENTE" POST /api/v1/arriendos "$CUERPO_ARRIENDO")"
if [ -n "$ARRIENDO_ID" ]; then
  verificar "GET /api/v1/arriendos/mis-arriendos del CLIENTE (HTTP)" 200 "$(http "$HDR_CLIENTE" GET /api/v1/arriendos/mis-arriendos)"
  verificar "mis-arriendos incluye el arriendo" si "$(lista_tiene id "$ARRIENDO_ID")"
  verificar "GET /api/v1/arriendos es solo ADMIN: CLIENTE (HTTP)" 403 "$(http "$HDR_CLIENTE" GET /api/v1/arriendos)"
  verificar "GET /api/v1/arriendos/{id} como ADMIN (HTTP)" 200 "$(http "$HDR_ADMIN" GET "/api/v1/arriendos/$ARRIENDO_ID")"
  echo "  -- el ADMIN ve la copia del cliente que se guardó al arrendar (tomada del token, sin consultar a ms-auth)"
  verificar "cliente.id" "$CLI_ID" "$(anidado cliente id)"
  verificar "cliente.rut" "$CLI_RUT" "$(anidado cliente rut)"
  verificar "cliente.nombre" "$CLI_NOMBRE" "$(anidado cliente nombre)"
  verificar "cliente.email" "$CLI_EMAIL" "$(anidado cliente email)"
  verificar "ADMIN devuelve la máquina (HTTP)" 200 "$(http "$HDR_ADMIN" PATCH "/api/v1/arriendos/$ARRIENDO_ID/devolver")"
  verificar "estado tras devolver" FINALIZADO "$(json "$TMP/last.json" estado)"
  verificar "la unidad vuelve a estar libre" 1 "$(http "$HDR_NINGUNO" GET "/api/v1/maquinas/$MAQUINA_ID" >/dev/null; json "$TMP/last.json" unidadesDisponibles)"
  verificar "ADMIN elimina el arriendo (HTTP)" 204 "$(http "$HDR_ADMIN" DELETE "/api/v1/arriendos/$ARRIENDO_ID")"
  ARRIENDO_ID=""
else
  echo "  FALLA el arriendo no devolvió id: se omiten las comprobaciones siguientes"; FALLAS=$((FALLAS+1))
fi
verificar "ADMIN elimina la máquina de prueba (HTTP)" 204 "$(http "$HDR_ADMIN" DELETE "/api/v1/maquinas/$MAQUINA_ID")"
[ "$(http "$HDR_NINGUNO" GET "/api/v1/maquinas/$MAQUINA_ID")" = "404" ] && MAQUINA_ID=""

echo; echo "== 4c. Cotizaciones (ms-arriendos) =="
CUERPO_COTIZACION='{"nombre":"Cotización de humo","rut":"11.111.111-1","email":"humo@ferrefix.cl","telefono":"+56 9 8765 4321","tipoProyecto":"Arriendo de Maquinaria y Demoledores","region":"Región Metropolitana","detalle":"Prueba de humo del gateway, se elimina sola","despachoObra":true}'
verificar "cotización pública sin token (HTTP)" 201 "$(http "$HDR_NINGUNO" POST /api/v1/cotizaciones "$CUERPO_COTIZACION")"
COT_ANONIMA="$(json "$TMP/last.json" id)"; COTIZACION_IDS="$COTIZACION_IDS $COT_ANONIMA"
verificar "la respuesta pública es solo un acuse (sin RUT)" "" "$(json "$TMP/last.json" rut)"
verificar "cotización con token del CLIENTE (HTTP)" 201 "$(http "$HDR_CLIENTE" POST /api/v1/cotizaciones "$CUERPO_COTIZACION")"
COT_CLIENTE="$(json "$TMP/last.json" id)"; COTIZACION_IDS="$COTIZACION_IDS $COT_CLIENTE"
verificar "cotización con RUT inválido (HTTP)" 400 "$(http "$HDR_NINGUNO" POST /api/v1/cotizaciones "${CUERPO_COTIZACION/11.111.111-1/11.111.111-2}")"
verificar "listar cotizaciones es solo ADMIN: sin token (HTTP)" 401 "$(http "$HDR_NINGUNO" GET /api/v1/cotizaciones)"
verificar "listar cotizaciones es solo ADMIN: CLIENTE (HTTP)" 403 "$(http "$HDR_CLIENTE" GET /api/v1/cotizaciones)"
verificar "listar cotizaciones como ADMIN (HTTP)" 200 "$(http "$HDR_ADMIN" GET /api/v1/cotizaciones)"
verificar "el listado incluye la cotización anónima" si "$(lista_tiene id "$COT_ANONIMA")"
verificar "el listado incluye la del CLIENTE" si "$(lista_tiene id "$COT_CLIENTE")"
if [ -n "$COT_CLIENTE" ]; then
  verificar "GET /api/v1/cotizaciones/{id} como ADMIN (HTTP)" 200 "$(http "$HDR_ADMIN" GET "/api/v1/cotizaciones/$COT_CLIENTE")"
  verificar "con token queda asociada al usuario (usuarioId del token)" "$CLI_ID" "$(json "$TMP/last.json" usuarioId)"
  verificar "ADMIN actualiza estado y nota (HTTP)" 200 "$(http "$HDR_ADMIN" PATCH "/api/v1/cotizaciones/$COT_CLIENTE" '{"estado":"EN_REVISION","notaAdmin":"Revisada por la prueba de humo"}')"
  verificar "estado tras actualizar" EN_REVISION "$(json "$TMP/last.json" estado)"
fi
if [ -n "$COT_ANONIMA" ]; then
  verificar "GET de la cotización anónima como ADMIN (HTTP)" 200 "$(http "$HDR_ADMIN" GET "/api/v1/cotizaciones/$COT_ANONIMA")"
  verificar "sin token queda sin usuario" "" "$(json "$TMP/last.json" usuarioId | sed 's/^None$//')"
fi

echo; echo "== 5. Subida de imagen (multipart) =="
if [ "${SIN_IMAGEN:-0}" = "1" ]; then
  echo "  omitida (SIN_IMAGEN=1)"
else
  # PNG válido de 1x1 píxel, generado aquí para no depender de ningún archivo.
  python3 -c '
import struct, sys, zlib
def bloque(tipo, datos):
    return struct.pack(">I", len(datos)) + tipo + datos + struct.pack(">I", zlib.crc32(tipo + datos) & 0xffffffff)
png = (b"\x89PNG\r\n\x1a\n" + bloque(b"IHDR", struct.pack(">IIBBBBB", 1, 1, 8, 2, 0, 0, 0))
       + bloque(b"IDAT", zlib.compress(b"\x00\xff\x7a\x00")) + bloque(b"IEND", b""))
open(sys.argv[1], "wb").write(png)' "$TMP/humo.png"
  verificar "ADMIN sube un PNG (HTTP)" 201 "$(curl -s -o "$TMP/last.json" -w '%{http_code}' -X POST "$BASE_URL/api/v1/imagenes" \
      -H @"$HDR_ADMIN" -F "archivo=@$TMP/humo.png;type=image/png" -F "destino=productos")"
  URL_IMAGEN="$(json "$TMP/last.json" url)"
  case "$URL_IMAGEN" in
    /media/productos/*.png)
      echo "  OK   URL devuelta: $URL_IMAGEN"
      verificar "la imagen se descarga sin token (HTTP)" 200 "$(curl -s -o "$TMP/descargada.png" -w '%{http_code}' "$BASE_URL$URL_IMAGEN")"
      verificar "la imagen descargada es idéntica a la subida" si "$(cmp -s "$TMP/humo.png" "$TMP/descargada.png" && echo si || echo no)"
      ;;
    *) echo "  FALLA URL de imagen inesperada: '$URL_IMAGEN'"; FALLAS=$((FALLAS+1)) ;;
  esac
  verificar "subir sin token (HTTP)" 401 "$(curl -s -o /dev/null -w '%{http_code}' -X POST "$BASE_URL/api/v1/imagenes" \
      -F "archivo=@$TMP/humo.png;type=image/png" -F "destino=productos")"
fi

echo; echo "== 6. CORS (origen: $ORIGEN) =="
verificar "preflight OPTIONS de PATCH (HTTP)" 200 "$(curl -s -o /dev/null -D "$TMP/pre.hdr" -w '%{http_code}' -X OPTIONS "$BASE_URL/api/v1/ordenes/1/estado" \
    -H "Origin: $ORIGEN" -H 'Access-Control-Request-Method: PATCH' -H 'Access-Control-Request-Headers: Authorization, Content-Type')"
verificar "preflight: Access-Control-Allow-Origin viene una sola vez" 1 "$(contar_acao "$TMP/pre.hdr")"
verificar "preflight: valor de Access-Control-Allow-Origin" "$ORIGEN" "$(valor_acao "$TMP/pre.hdr")"
verificar "GET real con Origin (HTTP)" 200 "$(curl -s -o /dev/null -D "$TMP/real.hdr" -w '%{http_code}' "$BASE_URL/api/v1/productos" -H "Origin: $ORIGEN")"
verificar "respuesta real: Access-Control-Allow-Origin viene una sola vez" 1 "$(contar_acao "$TMP/real.hdr")"
verificar "respuesta real: valor de Access-Control-Allow-Origin" "$ORIGEN" "$(valor_acao "$TMP/real.hdr")"
verificar "preflight desde un origen no autorizado (HTTP)" 403 "$(curl -s -o /dev/null -w '%{http_code}' -X OPTIONS "$BASE_URL/api/v1/productos" \
    -H 'Origin: http://sitio-malicioso.example' -H 'Access-Control-Request-Method: GET')"

echo; echo "== 7. Errores del gateway en JSON =="
verificar "ruta inexistente (HTTP)" 404 "$(http "$HDR_NINGUNO" GET /ruta-que-no-existe)"
verificar "cuerpo JSON: status" 404 "$(json "$TMP/last.json" status)"
verificar "cuerpo JSON: path" /ruta-que-no-existe "$(json "$TMP/last.json" path)"
verificar "/actuator/env no está expuesto (HTTP)" 404 "$(http "$HDR_NINGUNO" GET /actuator/env)"

echo; echo "== 8. Swagger UI en el gateway, con un documento por servicio =="
verificar "GET /swagger-ui.html siguiendo la redirección (HTTP)" 200 "$(curl -s -L -o /dev/null -w '%{http_code}' "$BASE_URL/swagger-ui.html")"
verificar "GET /v3/api-docs/swagger-config (HTTP)" 200 "$(http "$HDR_NINGUNO" GET /v3/api-docs/swagger-config)"
verificar "el selector lista ms-arriendos, ms-auth y ms-tienda" "ms-arriendos,ms-auth,ms-tienda" "$(python3 -c '
import json, sys
print(",".join(sorted(u["name"] for u in json.load(open(sys.argv[1])).get("urls", []))))' "$TMP/last.json" 2>/dev/null)"
verificar "GET /api-docs/ms-auth (HTTP)" 200 "$(http "$HDR_NINGUNO" GET /api-docs/ms-auth)"
verificar "ms-auth documenta login, register, refresh, auth/me y users/me" 5 "$(python3 -c '
import json, sys
print(len(json.load(open(sys.argv[1])).get("paths", {})))' "$TMP/last.json" 2>/dev/null)"
verificar "GET /api-docs/ms-arriendos (HTTP)" 200 "$(http "$HDR_NINGUNO" GET /api-docs/ms-arriendos)"
verificar "ms-arriendos documenta máquinas, arriendos y cotizaciones" si "$(python3 -c '
import json, sys
p = json.load(open(sys.argv[1])).get("paths", {})
print("si" if all(any(r.startswith(pref) for r in p) for pref in ("/api/v1/maquinas", "/api/v1/arriendos", "/api/v1/cotizaciones")) else "no")' "$TMP/last.json" 2>/dev/null)"
verificar "ms-arriendos no documenta productos ni órdenes" no "$(python3 -c '
import json, sys
p = json.load(open(sys.argv[1])).get("paths", {})
print("si" if any(r.startswith(("/api/v1/productos", "/api/v1/ordenes")) for r in p) else "no")' "$TMP/last.json" 2>/dev/null)"
verificar "GET /api-docs/ms-tienda (HTTP)" 200 "$(http "$HDR_NINGUNO" GET /api-docs/ms-tienda)"
verificar "ms-tienda documenta /api/v1/productos" si "$(python3 -c '
import json, sys
print("si" if "/api/v1/productos" in json.load(open(sys.argv[1])).get("paths", {}) else "no")' "$TMP/last.json" 2>/dev/null)"
verificar "ms-tienda ya no documenta máquinas ni arriendos" no "$(python3 -c '
import json, sys
p = json.load(open(sys.argv[1])).get("paths", {})
print("si" if any(r.startswith(("/api/v1/maquinas", "/api/v1/arriendos", "/api/v1/cotizaciones")) for r in p) else "no")' "$TMP/last.json" 2>/dev/null)"

echo
if [ "$FALLAS" -eq 0 ]; then echo "RESULTADO: todo OK a través del gateway."; else echo "RESULTADO: $FALLAS verificación(es) fallaron."; fi
[ "$FALLAS" -eq 0 ]
