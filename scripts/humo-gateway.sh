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
#  5. Imagen: el ADMIN sube un PNG (multipart) y se descarga sin token desde /media/**.   (SIN_IMAGEN=1 lo omite)
#  6. CORS: el preflight y la respuesta real traen Access-Control-Allow-Origin exactamente UNA vez.
#  7. Errores del gateway en JSON: una ruta inexistente devuelve 404 con el formato de la API.
#  8. Swagger UI en el gateway, con el documento OpenAPI de cada servicio.
#
# Datos de prueba: el producto lleva SKU TESTHUMO-* y se elimina al terminar (aunque falle), junto con la orden.
# La imagen subida NO se puede borrar por la API (no existe DELETE de imágenes): queda un PNG de 1x1 en uploads.
# La cuenta registrada (humo-<fecha>@ferrefix.cl) tampoco se puede borrar por la API: queda en ms-auth. Es un CLIENTE
# sin órdenes ni arriendos; usa SIN_REGISTRO=1 si no quieres crearla.
# Credenciales: se leen de .env (ADMIN_EMAIL/ADMIN_PASSWORD y CLIENT_EMAIL/CLIENT_PASSWORD) y NUNCA se imprimen:
# no viajan por argumentos de curl (se usan stdin y un archivo de cabeceras con permisos 600).
#
# Uso:  ./scripts/humo-gateway.sh                 (gateway en http://localhost:8080)
#       SIN_IMAGEN=1 ./scripts/humo-gateway.sh    (no sube la imagen)
#       SIN_REGISTRO=1 ./scripts/humo-gateway.sh  (no registra una cuenta nueva)
#       ORIGEN=http://localhost:5173 ./scripts/humo-gateway.sh   (por defecto: el primer origen de CORS_ALLOWED_ORIGINS)
# Solo corre contra localhost salvo ALLOW_REMOTE=1. Requiere: bash, curl, python3.
# Código de salida: 0 si todo pasó, 1 si hubo fallas, 2 si no se pudo empezar.

set -u
cd "$(dirname "$0")/.."

BASE_URL="${BASE_URL:-http://localhost:8080}"
case "$BASE_URL" in
  http://localhost:*|http://127.0.0.1:*) ;;
  *) [ "${ALLOW_REMOTE:-0}" = "1" ] || { echo "Rechazado: BASE_URL no es local (use ALLOW_REMOTE=1 si es a propósito)."; exit 2; } ;;
esac
[ -f .env ] || { echo "No se encontró .env en $(pwd)"; exit 2; }
command -v curl >/dev/null && command -v python3 >/dev/null || { echo "Se requieren curl y python3"; exit 2; }

env_get() { # lee UNA clave de .env sin ejecutarlo ni imprimirlo
  grep -E "^$1=" .env | head -1 | cut -d= -f2- | tr -d '\r' | sed -e 's/^"//; s/"$//' -e "s/^'//; s/'\$//"
}
ADMIN_EMAIL="$(env_get ADMIN_EMAIL)"; ADMIN_EMAIL="${ADMIN_EMAIL:-admin@ferrefix.cl}"
CLIENT_EMAIL="$(env_get CLIENT_EMAIL)"; CLIENT_EMAIL="${CLIENT_EMAIL:-cliente@ferrefix.cl}"
ADMIN_PASSWORD="$(env_get ADMIN_PASSWORD)"; CLIENT_PASSWORD="$(env_get CLIENT_PASSWORD)"
[ -n "$ADMIN_PASSWORD" ] && [ -n "$CLIENT_PASSWORD" ] || { echo "Faltan ADMIN_PASSWORD o CLIENT_PASSWORD en .env"; exit 2; }
# Origen para la prueba de CORS: el primero de CORS_ALLOWED_ORIGINS (el mismo que usa el gateway).
ORIGEN_ENV="$(env_get CORS_ALLOWED_ORIGINS | cut -d, -f1 | tr -d ' ')"
ORIGEN="${ORIGEN:-${ORIGEN_ENV:-http://localhost:5173}}"

TMP="$(mktemp -d)"; chmod 700 "$TMP"
HDR_ADMIN="$TMP/admin.hdr"; HDR_CLIENTE="$TMP/cliente.hdr"; HDR_NINGUNO="$TMP/ninguno.hdr"
: > "$HDR_ADMIN"; : > "$HDR_CLIENTE"; : > "$HDR_NINGUNO"
ORDEN_ID=""; PRODUCTO_ID=""
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
verificar "el selector lista ms-auth y ms-tienda" "ms-auth,ms-tienda" "$(python3 -c '
import json, sys
print(",".join(sorted(u["name"] for u in json.load(open(sys.argv[1])).get("urls", []))))' "$TMP/last.json" 2>/dev/null)"
verificar "GET /api-docs/ms-auth (HTTP)" 200 "$(http "$HDR_NINGUNO" GET /api-docs/ms-auth)"
verificar "ms-auth documenta login, register, refresh, auth/me y users/me" 5 "$(python3 -c '
import json, sys
print(len(json.load(open(sys.argv[1])).get("paths", {})))' "$TMP/last.json" 2>/dev/null)"
verificar "GET /api-docs/ms-tienda (HTTP)" 200 "$(http "$HDR_NINGUNO" GET /api-docs/ms-tienda)"
verificar "ms-tienda documenta /api/v1/productos" si "$(python3 -c '
import json, sys
print("si" if "/api/v1/productos" in json.load(open(sys.argv[1])).get("paths", {}) else "no")' "$TMP/last.json" 2>/dev/null)"

echo
if [ "$FALLAS" -eq 0 ]; then echo "RESULTADO: todo OK a través del gateway."; else echo "RESULTADO: $FALLAS verificación(es) fallaron."; fi
[ "$FALLAS" -eq 0 ]
