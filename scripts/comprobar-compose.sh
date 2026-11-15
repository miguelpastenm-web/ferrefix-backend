#!/usr/bin/env bash
# Comprueba que los compose de producción (3 máquinas) siguen alineados con el de desarrollo, sin levantar nada.
#
#  - docker-compose.backend.yml: tiene exactamente ms-gateway, ms-auth, ms-tienda y ms-arriendos, SIN Oracle ni
#    depends_on hacia Oracle; DB_HOST es obligatorio y ORACLE_PASSWORD no se pide.
#  - Cada uno de esos 4 servicios es IGUAL al de docker-compose.yml (imagen/build, puertos, volúmenes, healthcheck,
#    variables, logging...), salvo lo que cambia a propósito: depends_on hacia Oracle, DB_HOST y JAVA_TOOL_OPTIONS
#    (opcional). En producción, además, el origen CORS y DB_HOST son obligatorios.
#  - docker-compose.oracle.yml: solo el servicio oracle, igual al de docker-compose.yml salvo el puerto 1521 (publicado
#    hacia la red o atado a ORACLE_BIND_IP), con el volumen y el script de esquemas.
#
# Usa un archivo de variables ficticio propio (--env-file): NO lee tu .env ni imprime secretos.
# Uso:  ./scripts/comprobar-compose.sh        Código de salida: 0 todo alineado, 1 hay diferencias, 2 no se pudo comprobar.

set -u
cd "$(dirname "$0")/.."

DOCKER="${DOCKER:-docker}"
command -v "$DOCKER" >/dev/null && command -v python3 >/dev/null || { echo "Se requieren docker (con compose) y python3"; exit 2; }
for f in docker-compose.yml docker-compose.backend.yml docker-compose.oracle.yml; do
  [ -f "$f" ] || { echo "No se encontró $f en $(pwd)"; exit 2; }
done

TMP="$(mktemp -d)"; chmod 700 "$TMP"; trap 'rm -rf "$TMP"' EXIT
CLAVE_JWT="$(printf 'k%.0s' $(seq 1 70))"
# Valores ficticios: sirven solo para que Compose pueda interpolar los archivos.
cat > "$TMP/completo.env" <<ENV
ORACLE_PASSWORD=ficticia1
AUTH_DB_USER=FERREFIX_AUTH
AUTH_DB_PASS=ficticia2
TIENDA_DB_USER=FERREFIX_TIENDA
TIENDA_DB_PASS=ficticia3
ARRIENDOS_DB_USER=FERREFIX_ARRIENDOS
ARRIENDOS_DB_PASS=ficticia4
JWT_SECRET=$CLAVE_JWT
DB_HOST=10.0.2.15
CORS_ALLOWED_ORIGINS=http://localhost:5173
ENV
grep -v '^ORACLE_PASSWORD=' "$TMP/completo.env" > "$TMP/backend.env"                              # lo que tiene la máquina del backend
grep -v '^DB_HOST=\|^CORS_ALLOWED_ORIGINS=\|^JWT_SECRET=' "$TMP/completo.env" > "$TMP/oracle.env"  # lo que tiene la de Oracle
FALLAS=0
ok()    { echo "  OK    $1"; }
falla() { echo "  FALLA $1"; FALLAS=$((FALLAS+1)); }

config() { # config <env-file> <archivo-compose> <salida.json>
  "$DOCKER" compose --env-file "$1" -f "$2" config --format json > "$3" 2> "$TMP/error.txt"
}

echo "== Interpolación de los tres archivos =="
config "$TMP/completo.env" docker-compose.yml "$TMP/base.json" && ok "docker-compose.yml (desarrollo)" \
  || { falla "docker-compose.yml no es válido: $(head -1 "$TMP/error.txt")"; exit 1; }
config "$TMP/backend.env" docker-compose.backend.yml "$TMP/backend.json" && ok "docker-compose.backend.yml con las variables del backend (sin ORACLE_PASSWORD)" \
  || { falla "docker-compose.backend.yml no es válido con las variables del backend: $(head -1 "$TMP/error.txt")"; exit 1; }
config "$TMP/oracle.env" docker-compose.oracle.yml "$TMP/oracle.json" && ok "docker-compose.oracle.yml con las variables de Oracle (sin JWT_SECRET ni DB_HOST)" \
  || { falla "docker-compose.oracle.yml no es válido con las variables de Oracle: $(head -1 "$TMP/error.txt")"; exit 1; }

echo; echo "== Variables obligatorias =="
for var in DB_HOST CORS_ALLOWED_ORIGINS JWT_SECRET AUTH_DB_USER AUTH_DB_PASS TIENDA_DB_USER TIENDA_DB_PASS ARRIENDOS_DB_USER ARRIENDOS_DB_PASS; do
  grep -v "^$var=" "$TMP/backend.env" > "$TMP/sin.env"
  if config "$TMP/sin.env" docker-compose.backend.yml "$TMP/x.json"; then falla "el backend arranca sin $var"; else ok "el backend exige $var"; fi
done
for var in ORACLE_PASSWORD AUTH_DB_USER AUTH_DB_PASS TIENDA_DB_USER TIENDA_DB_PASS ARRIENDOS_DB_USER ARRIENDOS_DB_PASS; do
  grep -v "^$var=" "$TMP/oracle.env" > "$TMP/sin.env"
  if config "$TMP/sin.env" docker-compose.oracle.yml "$TMP/x.json"; then falla "Oracle arranca sin $var"; else ok "Oracle exige $var"; fi
done

echo; echo "== Contenido =="
python3 - "$TMP" <<'FIN_PY'
import json, sys
T = sys.argv[1]
base, backend, oracle = (json.load(open(f"{T}/{n}.json")) for n in ("base", "backend", "oracle"))
fallas = 0
def ok(m): print("  OK    " + m)
def falla(m):
    global fallas
    fallas += 1
    print("  FALLA " + m)

SERVICIOS = ["ms-arriendos", "ms-auth", "ms-gateway", "ms-tienda"]
sb = backend["services"]
(ok if sorted(sb) == SERVICIOS else falla)("el backend tiene exactamente los 4 servicios: " + ", ".join(sorted(sb)))
(ok if "oracle" not in sb else falla)("el backend no incluye el contenedor oracle")
dep = [k for k, v in sb.items() if "oracle" in v.get("depends_on", {})]
(ok if not dep else falla)("ningún servicio del backend depende de oracle" + (": " + ", ".join(dep) if dep else ""))
# El compose de desarrollo no fija nombre: toma el de la carpeta (normalmente ferrefix-backend). El del backend lo fija,
# para que el volumen de imágenes se llame igual que en desarrollo (ferrefix-backend_uploads_data) en cualquier carpeta.
(ok if backend["name"] == "ferrefix-backend" else falla)(f'el proyecto del backend se llama "{backend["name"]}" (se esperaba "ferrefix-backend", el nombre habitual del de desarrollo: mismo volumen de uploads)')
(ok if sorted(backend.get("volumes", {})) == ["uploads_data"] else falla)("el backend declara solo el volumen uploads_data")

# Los 4 servicios deben ser iguales a los de desarrollo, salvo lo que cambia a propósito.
def normalizar(s):
    s = json.loads(json.dumps(s))
    env = s.get("environment", {})
    env.pop("JAVA_TOOL_OPTIONS", None)                 # solo existe en producción (opcional)
    env.pop("DB_HOST", None)                           # el backend apunta a otra máquina
    s.get("depends_on", {}).pop("oracle", None)        # no hay Oracle en esta máquina
    if not s.get("depends_on"):
        s.pop("depends_on", None)
    return s

for nombre in SERVICIOS:
    a, b = normalizar(base["services"][nombre]), normalizar(sb[nombre])
    if a == b:
        ok(f"{nombre}: idéntico al de desarrollo (salvo DB_HOST, depends_on a Oracle y JAVA_TOOL_OPTIONS)")
    else:
        falla(f"{nombre}: difiere del de desarrollo")
        for k in sorted(set(a) | set(b)):
            if a.get(k) != b.get(k):
                if isinstance(a.get(k), dict) and isinstance(b.get(k), dict):
                    for kk in sorted(set(a[k]) | set(b[k])):
                        if a[k].get(kk) != b[k].get(kk):
                            print(f"        {k}.{kk}: desarrollo={a[k].get(kk)!r}  backend={b[k].get(kk)!r}")
                else:
                    print(f"        {k}: desarrollo={a.get(k)!r}  backend={b.get(k)!r}")

(ok if sb["ms-gateway"]["depends_on"] == base["services"]["ms-gateway"]["depends_on"] else falla)("el gateway espera a los mismos 3 servicios sanos")
(ok if all(v["environment"]["DB_HOST"] == "10.0.2.15" for k, v in sb.items() if k != "ms-gateway") else falla)("DB_HOST llega a los 3 servicios con base de datos")
(ok if "DB_HOST" not in sb["ms-gateway"]["environment"] else falla)("el gateway no recibe datos de la base")
publicados = {k: [(p["published"], p["target"]) for p in v.get("ports", [])] for k, v in sb.items() if v.get("ports")}
(ok if publicados == {"ms-gateway": [("8080", 8080)]} else falla)(f"único puerto publicado del backend: {publicados}")

so = oracle["services"]
(ok if list(so) == ["oracle"] else falla)("el compose de Oracle tiene solo el servicio oracle")
o_base = json.loads(json.dumps(base["services"]["oracle"]))
o_prod = json.loads(json.dumps(so["oracle"]))
pb = o_base.pop("ports")
pp = o_prod.pop("ports")
(ok if o_base == o_prod else falla)("el servicio oracle es igual al de desarrollo (imagen, variables, volúmenes, healthcheck) salvo el puerto")
(ok if len(pb) == 1 and pb[0]["host_ip"] == "127.0.0.1" else falla)("desarrollo publica el 1521 solo en 127.0.0.1")
(ok if len(pp) == 1 and pp[0]["host_ip"] == "0.0.0.0" and pp[0]["published"] == "1521" and pp[0]["target"] == 1521 else falla)(f"producción publica el 1521 en 0.0.0.0 por defecto: {pp}")
montajes = {v["target"]: (v["source"], v.get("read_only")) for v in so["oracle"]["volumes"]}
(ok if montajes.get("/opt/oracle/oradata", ("",))[0] == "oracle_data" else falla)("volumen persistente oracle_data en /opt/oracle/oradata")
script = montajes.get("/container-entrypoint-initdb.d/01-crear-esquemas.sh")
(ok if script and script[1] else falla)("el script de esquemas está montado en solo lectura")
(ok if sorted(oracle.get("volumes", {})) == ["oracle_data"] else falla)("el compose de Oracle declara solo oracle_data")
(ok if oracle["name"] != backend["name"] else falla)(f'los proyectos de Oracle ("{oracle["name"]}") y del backend ("{backend["name"]}") no comparten nombre')
sys.exit(1 if fallas else 0)
FIN_PY
[ $? -eq 0 ] || FALLAS=$((FALLAS+1))

echo; echo "== ORACLE_BIND_IP =="
cp "$TMP/oracle.env" "$TMP/bind.env"
echo "ORACLE_BIND_IP=10.0.2.15" >> "$TMP/bind.env"
if config "$TMP/bind.env" docker-compose.oracle.yml "$TMP/bind.json" \
   && python3 -c 'import json,sys; p=json.load(open(sys.argv[1]))["services"]["oracle"]["ports"][0]; sys.exit(0 if p["host_ip"]=="10.0.2.15" else 1)' "$TMP/bind.json"; then
  ok "con ORACLE_BIND_IP=10.0.2.15 el 1521 queda atado a esa IP"
else
  falla "ORACLE_BIND_IP no se aplica al puerto 1521"
fi

echo
if [ "$FALLAS" -eq 0 ]; then echo "RESULTADO: los compose de producción están alineados con el de desarrollo."; else echo "RESULTADO: $FALLAS comprobación(es) fallaron."; fi
[ "$FALLAS" -eq 0 ]
