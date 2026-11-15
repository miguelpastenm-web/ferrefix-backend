# Despliegue en 3 máquinas (AWS)

Producción con tres máquinas separadas dentro de una misma VPC. El desarrollo local no cambia: sigue usando
`docker-compose.yml`, que levanta todo junto.

| Máquina | Tipo | Qué corre | Archivo |
|---|---|---|---|
| **Front** | t3.small | nginx con el build del front, puerto 80 | `docker-compose.yml` del repo `ferrefix-ui` |
| **Backend** | t3.large | `ms-gateway`, `ms-auth`, `ms-tienda` y `ms-arriendos` | `docker-compose.backend.yml` |
| **Oracle** | t3.medium | solo la base de datos, con los 3 esquemas | `docker-compose.oracle.yml` |

Requisitos de las tres: Ubuntu 24.04 con Docker Engine y el plugin de Compose, y el repositorio correspondiente clonado.
El backend lleva una **Elastic IP** (el navegador llama al gateway directamente y el front la incrusta al construirse).
Oracle necesita salida a internet la primera vez, para bajar su imagen.

## 1. Grupos de seguridad

| Grupo | Entrada | Desde | Por qué |
|---|---|---|---|
| **front** | 80 (TCP) | donde se acceda (público, o solo las IP del profesor) | el sitio |
| | 22 (TCP) | solo tu IP | administración |
| **backend** | 8080 (TCP) | `0.0.0.0/0` | el navegador llama al gateway directamente |
| | 22 (TCP) | solo tu IP | administración |
| **oracle** | 1521 (TCP) | **solo el grupo de seguridad del backend** (su id `sg-...`) | la consume el backend |
| | 22 (TCP) | solo tu IP | administración |

- **El 1521 nunca debe estar abierto a `0.0.0.0/0`.** El compose lo publica hacia la red porque lo consume otra máquina; la
  protección es esta regla. Como defensa extra, define `ORACLE_BIND_IP` con la IP privada de la máquina Oracle y el puerto
  queda atado a esa interfaz.
- Los puertos 8081, 8082 y 8083 de los servicios no se publican en el backend: no abras nada más que el 8080.
- La salida (outbound) queda en el valor por defecto de AWS (todo permitido).

## 2. Variables de entorno: qué va en cada máquina

Cada máquina tiene **su propio** `.env`, con permisos 600 y sin subirlo nunca al repositorio. Así cada una recibe solo los
secretos que necesita.

| Variable | Backend | Oracle | Front |
|---|:-:|:-:|:-:|
| `DB_HOST` (IP privada de la máquina Oracle), `DB_PORT`, `DB_SERVICE` | ✔ | | |
| `AUTH_DB_USER/PASS`, `TIENDA_DB_USER/PASS`, `ARRIENDOS_DB_USER/PASS` | ✔ | ✔ (**las mismas**) | |
| `JWT_SECRET`, `JWT_ACCESS_EXPIRATION_MS`, `JWT_REFRESH_EXPIRATION_MS` | ✔ | | |
| `ADMIN_*`, `CLIENT_*` | ✔ | | |
| `CORS_ALLOWED_ORIGINS` | ✔ | | |
| `JAVA_TOOL_OPTIONS` (opcional) | ✔ | | |
| `ORACLE_PASSWORD`, `ORACLE_BIND_IP` (opcional) | | ✔ | |
| `VITE_API_URL` | | | ✔ |

Plantillas: `.env.production.backend.example` (backend) y `.env.production.oracle.example` (Oracle). El front usa la del
repo `ferrefix-ui`.

Las **claves de los esquemas deben ser idénticas** en Oracle (allí se crean) y en el backend (allí se usan). Genéralas en una
máquina y copia a la otra solo esas líneas, por `scp` y nunca por chat ni correo:

```bash
# en la máquina Oracle
grep -E '^(AUTH|TIENDA|ARRIENDOS)_DB_(USER|PASS)=' .env > esquemas.env
scp esquemas.env ubuntu@IP-DEL-BACKEND:~/        # y luego: cat ~/esquemas.env >> .env ; rm ~/esquemas.env
```

## 3. Orden de arranque

Compose no puede esperar a Oracle entre máquinas (no hay `depends_on` hacia otra máquina): si el backend arranca antes, cada
servicio falla al conectar y se reinicia solo hasta que Oracle responde. Con este orden no ocurre:

1. **Oracle.** En su máquina:
   ```bash
   cp .env.production.oracle.example .env && chmod 600 .env     # completar
   docker compose -f docker-compose.oracle.yml up -d
   docker compose -f docker-compose.oracle.yml ps               # esperar a "healthy" (3 a 5 min la primera vez)
   ```
2. **Conectividad desde el backend.** En su máquina, con el `.env` ya completo:
   ```bash
   cp .env.production.backend.example .env && chmod 600 .env     # completar (DB_HOST = IP privada de Oracle)
   ./scripts/verificar-despliegue.sh --previo
   ```
   Si falla: revisa el grupo de seguridad de Oracle (paso 1) y que Oracle esté `healthy`.
3. **Backend.**
   ```bash
   docker compose -f docker-compose.backend.yml up -d --build
   ./scripts/verificar-despliegue.sh --esperar 300               # reintenta mientras los servicios terminan de arrancar
   ```
4. **Front.** En su máquina, con `VITE_API_URL=http://IP-ELASTICA-DEL-BACKEND:8080` en su `.env` (ver el README del front):
   `docker compose up -d --build`.
5. **Prueba de humo** (sección 6).

## 4. Orígenes (CORS) y URL de la API

- `CORS_ALLOWED_ORIGINS` (backend) debe ser **exactamente** el origen que envía el navegador: esquema + IP pública del front,
  sin `:80`. Ejemplo: `http://IP-PUBLICA-DEL-FRONT`. Varios orígenes, separados por coma.
- `VITE_API_URL` (front) es `http://IP-ELASTICA-DEL-BACKEND:8080`. **Se incrusta al construir el front**: si la IP del backend
  cambia, hay que reconstruirlo (`docker compose up -d --build`).
- Front y backend deben usar el mismo esquema (los dos `http`, o los dos `https`): un front `https` no puede llamar a un
  backend `http`.

## 5. Cómo comprobar cada máquina

**Oracle**
```bash
docker compose -f docker-compose.oracle.yml ps            # ferrefix_oracle "healthy"
docker compose -f docker-compose.oracle.yml logs --tail 30
ss -ltn | grep 1521                                       # escuchando en la interfaz esperada
```
Desde el backend: `./scripts/verificar-despliegue.sh --previo`.

**Backend**
```bash
./scripts/verificar-despliegue.sh                         # Oracle + 4 servicios + rutas a través del gateway
docker compose -f docker-compose.backend.yml ps           # los 4 contenedores "healthy"
docker logs --tail 50 ferrefix_auth                       # (o ferrefix_tienda, ferrefix_arriendos, ferrefix_gateway)
```
- Servicios reiniciándose: casi siempre es Oracle inalcanzable o con claves distintas (`ORA-01017: invalid
  username/password`). Revisa los pasos 1 y 2.
- Swagger UI (con selector de servicio): `http://IP-ELASTICA-DEL-BACKEND:8080/swagger-ui.html`.

**Front**
```bash
curl -I http://IP-PUBLICA-DEL-FRONT/                      # 200
# CORS: la respuesta debe traer Access-Control-Allow-Origin igual al origen enviado
curl -s -D - -o /dev/null -H 'Origin: http://IP-PUBLICA-DEL-FRONT' http://IP-ELASTICA-DEL-BACKEND:8080/api/v1/productos | grep -i '^access-control-allow-origin'
```
Si no aparece la cabecera, `CORS_ALLOWED_ORIGINS` no coincide con el origen. Después abre el front en el navegador y revisa
que cargue el catálogo (consola del navegador sin errores de CORS).

## 6. Prueba de humo contra el gateway remoto

`scripts/humo-gateway.sh` recorre login, registro, productos, órdenes, máquinas y arriendos, cotizaciones, imágenes, CORS y
Swagger a través del gateway.

- **Desde la máquina del backend** funciona tal cual: `./scripts/humo-gateway.sh` (usa el `.env` de esa máquina).
- **Desde tu equipo**: crea un archivo `humo.env` (permisos 600, fuera del repositorio) con solo `ADMIN_EMAIL`,
  `ADMIN_PASSWORD`, `CLIENT_EMAIL` y `CLIENT_PASSWORD`, las mismas del `.env` del backend, y ejecuta:
  ```bash
  ALLOW_REMOTE=1 BASE_URL=http://IP-ELASTICA-DEL-BACKEND:8080 ORIGEN=http://IP-PUBLICA-DEL-FRONT \
    ENV_FILE=./humo.env ./scripts/humo-gateway.sh
  ```
  `ORIGEN` debe ser el origen del front, para que la prueba de CORS use el mismo que `CORS_ALLOWED_ORIGINS`.
- El script **crea datos de prueba en la base real** (producto, orden, máquina, arriendo, cotizaciones y una cuenta de humo)
  y los elimina al terminar, aunque falle. Quedan un PNG de 1x1 y la cuenta `humo-<fecha>@ferrefix.cl`, porque la API no
  permite borrarlos. Usa `SIN_IMAGEN=1` y `SIN_REGISTRO=1` para evitarlos.
- Cada llamada tiene un tiempo máximo (60 s, ajustable con `CURL_MAX_TIME`): si un grupo de seguridad bloquea el 8080, el
  script falla en segundos en vez de colgarse.

## 7. Memoria del backend

Los 4 servicios son JVM que comparten la máquina del backend (t3.large, 8 GB). Sin límite, cada JVM puede tomar hasta un
cuarto de la memoria de la máquina. Si ves lentitud o reinicios por memoria, define en el `.env` del backend:

```
JAVA_TOOL_OPTIONS=-Xms128m -Xmx384m
```

Se aplica a los 4 por igual (en una t3.medium de 4 GB es imprescindible). Después: `docker compose -f
docker-compose.backend.yml up -d`. La variable es opcional y vacía por defecto.

## 8. Operación

- **Actualizar el backend**: `git pull && docker compose -f docker-compose.backend.yml up -d --build`.
- **Nunca uses `down -v`**: borra los volúmenes (`uploads_data` en el backend, `oracle_data` en Oracle).
- **Respaldo**: el volumen de Oracle vive en el disco de su instancia; programa un *snapshot* del disco EBS. Las imágenes
  subidas están en el volumen `ferrefix-backend_uploads_data` del backend.
- **Cambiar las claves de los esquemas** exige recrear el volumen de Oracle (el script de esquemas solo corre sobre un
  volumen vacío): `docker compose -f docker-compose.oracle.yml down`, `docker volume rm ferrefix-oracle_oracle_data` y
  `up -d`. Se pierden los datos; los iniciales se regeneran al arrancar el backend.
- **Alineación con desarrollo**: `scripts/comprobar-compose.sh` verifica, sin levantar nada, que los dos compose de
  producción siguen alineados con `docker-compose.yml`. Ejecútalo después de tocar cualquiera de los tres.
