#!/usr/bin/env bash
# Prueba de concurrencia de órdenes contra la API en ejecución (docker compose up).
#
#  Prueba 1 (sin sobreventa):  producto con stock 1 y 8 órdenes simultáneas
#                              => exactamente 1 x 201 y 7 x 409; stock final 0 y estado AGOTADO.
#  Prueba 2 (sin deadlock):    dos productos y órdenes simultáneas con las MISMAS líneas en orden inverso
#                              ([A,B] y [B,A]) => todas 201, ningún 5xx; stock descontado exacto.
#  Prueba 3 (cancelar y borrar): producto con stock 1: comprar => 0/AGOTADO; cancelar => vuelve a 1/DISPONIBLE;
#                              cancelar otra vez => 409 (sin doble devolución); un ADMIN no puede comprar (403);
#                              DELETE de una orden PAGADA => 409; DELETE de la CANCELADA => 204.
#
# Credenciales: se leen de .env (ADMIN_EMAIL/ADMIN_PASSWORD y CLIENT_EMAIL/CLIENT_PASSWORD) y NUNCA se imprimen:
# no viajan por argumentos de curl (se usan stdin y un archivo de cabeceras con permisos 600).
# Datos de prueba: los productos llevan SKU TESTCONC-* y se crean/eliminan aquí. Al terminar (aunque falle) se
# cancelan y eliminan las órdenes creadas y se eliminan los productos. Las dos "partes" de la prueba 2 usan la misma
# cuenta CLIENTE (el riesgo de deadlock está en las filas de producto, no en el usuario).
#
# Uso:  ./scripts/prueba-concurrencia-ordenes.sh            (API en http://localhost:8080)
#       BASE_URL=http://localhost:8080 ./scripts/prueba-concurrencia-ordenes.sh
# Solo corre contra localhost salvo ALLOW_REMOTE=1. Requiere: bash, curl, python3.

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

TMP="$(mktemp -d)"; chmod 700 "$TMP"
HDR_ADMIN="$TMP/admin.hdr"; HDR_CLIENTE="$TMP/cliente.hdr"
ORDENES="$TMP/ordenes.txt"; PRODUCTOS="$TMP/productos.txt"; : > "$ORDENES"; : > "$PRODUCTOS"
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

limpiar() {
  echo; echo "== Limpieza =="
  local n=0 p=0 id
  while read -r id; do
    [ -z "$id" ] && continue
    http "$HDR_ADMIN" PATCH "/api/v1/ordenes/$id/estado" '{"estado":"CANCELADA"}' >/dev/null   # devuelve el stock
    [ "$(http "$HDR_ADMIN" DELETE "/api/v1/ordenes/$id")" = "204" ] && n=$((n+1))
  done < "$ORDENES"
  while read -r id; do
    [ -z "$id" ] && continue
    [ "$(http "$HDR_ADMIN" DELETE "/api/v1/productos/$id")" = "204" ] && p=$((p+1))
  done < "$PRODUCTOS"
  echo "Órdenes eliminadas: $n · productos de prueba eliminados: $p"
  rm -rf "$TMP"
}
trap limpiar EXIT

crear_producto() { # crear_producto <sufijo> <stock> -> imprime el id
  local sku="TESTCONC-$(date +%s)-$1"
  local body
  body="$(printf '{"sku":"%s","nombre":"Producto de prueba %s","marca":"Prueba","categoria":"Herramientas","precioVenta":1000,"stock":%s,"descripcion":"Creado por la prueba de concurrencia"}' "$sku" "$1" "$2")"
  [ "$(http "$HDR_ADMIN" POST /api/v1/productos "$body")" = "201" ] || return 1
  local id; id="$(json "$TMP/last.json" id)"
  echo "$id" >> "$PRODUCTOS"; echo "$id"
}

orden_json() { # orden_json <id:cant> [<id:cant> ...]
  local items="" par
  for par in "$@"; do items="$items${items:+,}{\"productoId\":${par%%:*},\"cantidad\":${par##*:}}"; done
  printf '{"items":[%s],"metodoPago":"WEBPAY_DEBITO","telefono":"+56 9 8765 4321","direccionEnvio":"Av. Prueba 123","region":"Región Metropolitana","comuna":"Providencia","indicaciones":"prueba de concurrencia"}' "$items"
}

disparar() { # disparar <prefijo> <N> <json-de-orden> ...: lanza N órdenes en paralelo, guarda resultados en $TMP/<prefijo>_i.*
  local prefijo="$1" n="$2" i; shift 2
  local cuerpos=("$@")
  for i in $(seq 1 "$n"); do
    ( cuerpo="${cuerpos[$(( (i - 1) % ${#cuerpos[@]} ))]}"
      printf '%s' "$cuerpo" | curl -s -o "$TMP/${prefijo}_$i.json" -w '%{http_code}' -X POST "$BASE_URL/api/v1/ordenes" \
        -H @"$HDR_CLIENTE" -H 'Content-Type: application/json' --data-binary @- > "$TMP/${prefijo}_$i.code" ) &
  done
  wait
}

contar() { # contar <prefijo> <código> -> cantidad de respuestas con ese código
  local c=0 f; for f in "$TMP"/"$1"_*.code; do [ "$(cat "$f")" = "$2" ] && c=$((c+1)); done; echo "$c"
}
codigos5xx() { local c=0 f; for f in "$TMP"/"$1"_*.code; do case "$(cat "$f")" in 5*|000) c=$((c+1));; esac; done; echo "$c"; }
registrar_ordenes() { local f; for f in "$TMP"/"$1"_*.code; do [ "$(cat "$f")" = "201" ] && json "${f%.code}.json" id >> "$ORDENES"; done; }
estado_producto() { http "$HDR_ADMIN" GET "/api/v1/productos/$1" >/dev/null; echo "$(json "$TMP/last.json" stock) $(json "$TMP/last.json" estado)"; }
verificar() { # verificar <descripción> <esperado> <obtenido>
  if [ "$2" = "$3" ]; then echo "  OK   $1: $3"; else echo "  FALLA $1: esperado '$2', obtenido '$3'"; FALLAS=$((FALLAS+1)); fi
}

echo "API: $BASE_URL"
login "$ADMIN_EMAIL" "$ADMIN_PASSWORD" "$HDR_ADMIN"   || { echo "No se pudo iniciar sesión como ADMIN (revise .env y que la API esté arriba)"; exit 2; }
login "$CLIENT_EMAIL" "$CLIENT_PASSWORD" "$HDR_CLIENTE" || { echo "No se pudo iniciar sesión como CLIENTE (revise .env)"; exit 2; }
echo "Sesiones iniciadas (credenciales no mostradas)."

echo; echo "== Prueba 1: sin sobreventa (stock 1, 8 órdenes simultáneas) =="
P1="$(crear_producto A 1)" || { echo "No se pudo crear el producto de prueba"; exit 2; }
disparar p1 8 "$(orden_json "$P1:1")"
registrar_ordenes p1
verificar "órdenes creadas (201)" 1 "$(contar p1 201)"
verificar "rechazadas por stock (409)" 7 "$(contar p1 409)"
verificar "errores 5xx" 0 "$(codigos5xx p1)"
verificar "stock y estado finales" "0 AGOTADO" "$(estado_producto "$P1")"
MSG="$(for f in "$TMP"/p1_*.code; do [ "$(cat "$f")" = "409" ] && { json "${f%.code}.json" message; break; }; done)"
echo "  mensaje del 409: $MSG"

echo; echo "== Prueba 2: sin deadlock (mismas líneas en orden inverso, 3 rondas de 10) =="
PA="$(crear_producto B 60)" && PB="$(crear_producto C 60)" || { echo "No se pudieron crear los productos de prueba"; exit 2; }
for ronda in 1 2 3; do
  disparar "r$ronda" 10 "$(orden_json "$PA:1" "$PB:1")" "$(orden_json "$PB:1" "$PA:1")"
  registrar_ordenes "r$ronda"
  verificar "ronda $ronda: órdenes 201" 10 "$(contar "r$ronda" 201)"
  verificar "ronda $ronda: errores 5xx" 0 "$(codigos5xx "r$ronda")"
done
verificar "stock final del producto B" "30 DISPONIBLE" "$(estado_producto "$PA")"
verificar "stock final del producto C" "30 DISPONIBLE" "$(estado_producto "$PB")"

echo; echo "== Prueba 3: cancelar devuelve el stock una sola vez, y reglas de borrado y de rol =="
P3="$(crear_producto D 1)" || { echo "No se pudo crear el producto de prueba"; exit 2; }

verificar "CLIENTE compra 1 unidad (HTTP)" 201 "$(http "$HDR_CLIENTE" POST /api/v1/ordenes "$(orden_json "$P3:1")")"
O1="$(json "$TMP/last.json" id)"; echo "$O1" >> "$ORDENES"
verificar "tras comprar: stock y estado" "0 AGOTADO" "$(estado_producto "$P3")"

verificar "ADMIN cancela la orden (HTTP)" 200 "$(http "$HDR_ADMIN" PATCH "/api/v1/ordenes/$O1/estado" '{"estado":"CANCELADA"}')"
verificar "orden cancelada: estado" CANCELADA "$(json "$TMP/last.json" estado)"
verificar "tras cancelar: el stock vuelve" "1 DISPONIBLE" "$(estado_producto "$P3")"

verificar "cancelar de nuevo (HTTP)" 409 "$(http "$HDR_ADMIN" PATCH "/api/v1/ordenes/$O1/estado" '{"estado":"CANCELADA"}')"
verificar "tras la 2ª cancelación: sin doble devolución" "1 DISPONIBLE" "$(estado_producto "$P3")"

verificar "ADMIN intenta comprar (HTTP)" 403 "$(http "$HDR_ADMIN" POST /api/v1/ordenes "$(orden_json "$P3:1")")"
verificar "tras el intento del ADMIN: stock intacto" "1 DISPONIBLE" "$(estado_producto "$P3")"

verificar "CLIENTE vuelve a comprar (HTTP)" 201 "$(http "$HDR_CLIENTE" POST /api/v1/ordenes "$(orden_json "$P3:1")")"
O2="$(json "$TMP/last.json" id)"; echo "$O2" >> "$ORDENES"
verificar "segunda orden: estado" PAGADA "$(json "$TMP/last.json" estado)"
verificar "DELETE de la orden PAGADA (HTTP)" 409 "$(http "$HDR_ADMIN" DELETE "/api/v1/ordenes/$O2")"
verificar "la orden PAGADA sigue existiendo" PAGADA "$(http "$HDR_ADMIN" GET "/api/v1/ordenes/$O2" >/dev/null; json "$TMP/last.json" estado)"
verificar "DELETE no tocó el stock" "0 AGOTADO" "$(estado_producto "$P3")"

verificar "DELETE de la orden CANCELADA (HTTP)" 204 "$(http "$HDR_ADMIN" DELETE "/api/v1/ordenes/$O1")"
verificar "la orden eliminada ya no existe (HTTP)" 404 "$(http "$HDR_ADMIN" GET "/api/v1/ordenes/$O1")"
verificar "eliminar no devolvió stock extra" "0 AGOTADO" "$(estado_producto "$P3")"

echo
if [ "$FALLAS" -eq 0 ]; then echo "RESULTADO: TODO OK"; STATUS=0; else echo "RESULTADO: $FALLAS verificación(es) fallaron"; STATUS=1; fi
exit "$STATUS"
