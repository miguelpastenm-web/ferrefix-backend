# Ferrefix Backend

API REST (Spring Boot 4 · Java 21 · Oracle · JWT) de la tienda Ferrefix: venta de productos,
arriendo de máquinas, cotizaciones y panel administrativo.

Frontend: https://github.com/miguelpastenm-web/ferrefix-ui

## Arquitectura

Proyecto Maven multimódulo. Todo el tráfico entra por el gateway; los servicios no se publican en el host.

```
navegador / frontend
        │  http://<host>:8080
        ▼
┌──────────────────┐   /api/v1/auth/**  /api/v1/users/**    ┌──────────────────┐
│                  │ ─────────────────────────────────────▶ │     ms-auth      │ ──┐
│    ms-gateway    │                                        │  :8081 (interno) │   │   ┌─────────────────────┐
│  :8080 (público) │   resto de /api/v1/**  /media/**       ├──────────────────┤   ├──▶│        Oracle       │
│                  │ ─────────────────────────────────────▶ │    ms-tienda     │   │   │ FERREFIX_AUTH       │
└──────────────────┘                                        │  :8082 (interno) │ ──┘   │ FERREFIX_TIENDA     │
                                                            └──────────────────┘       └─────────────────────┘
```

| Módulo | Qué hace | Puerto |
|---|---|---|
| `ms-gateway` | Spring Cloud Gateway (WebFlux). Único punto de entrada: enruta, resuelve CORS, sirve Swagger UI y responde en JSON los errores de enrutado (404, 502, 504). No conoce secretos: no valida JWT ni usa la base. | 8080, público |
| `ms-auth` | Registro, login, renovación de token (`/auth/refresh`), `/auth/me` y perfil (`/users/me`). Dueño de los usuarios y **único emisor de tokens JWT**. | 8081, solo red interna |
| `ms-tienda` | Productos, imágenes, órdenes, máquinas, arriendos y cotizaciones. **Solo verifica** los tokens y toma la identidad del propio token. | 8082, solo red interna |
| `ferrefix-common` | Librería (no es un servicio): verificación del JWT, formato de error, manejo de excepciones y utilidades que comparten los servicios. | – |
| `oracle` | Una instancia con **un esquema por servicio** (`FERREFIX_AUTH` y `FERREFIX_TIENDA`), cada uno con su usuario y permisos mínimos. Flyway migra cada esquema al arrancar su servicio. | 1521, solo `127.0.0.1` |

- **El contrato público no cambió**: mismas rutas (`/api/v1/**`, `/media/**`), mismos cuerpos y mismos códigos de estado. El frontend sigue apuntando a `http://<host>:8080`.
- **Rutas del gateway**: `/api/v1/auth/**` y `/api/v1/users/**` van a `ms-auth`; el resto de `/api/v1/**` y `/media/**`, a `ms-tienda`.
- **Sin claves foráneas entre servicios**: `ordenes`, `arriendos` y `cotizaciones` guardan `USUARIO_ID` como una columna simple. El RUT, el nombre y el correo del cliente se **copian del token** al comprar o arrendar, así el historial no cambia si luego edita su perfil.
- **CORS vive solo en el gateway.** Los servicios no añaden cabeceras `Access-Control-*`; si lo hicieran, el navegador recibiría `Access-Control-Allow-Origin` duplicada y rechazaría la respuesta.
- **Errores del gateway** con el mismo formato que los de la API (`timestamp`, `status`, `error`, `message`, `path`): 404 si la ruta no es de ningún servicio, 502 si el servicio está caído y 504 si no responde en 60 s (margen para las subidas de imágenes de hasta 6 MB). Los errores que responde un servicio pasan tal cual.

## Autenticación con JWT y sus límites

`ms-auth` firma los tokens con HS512. El **access token** (15 min por defecto) lleva la identidad completa: `sub` (correo), `uid`, `rut`, `nombre` y `roles`. El **refresh token** (7 días) solo identifica al usuario y sirve únicamente en `POST /api/v1/auth/refresh`.

Cada servicio verifica la firma, la vigencia y el tipo del token con el mismo `JWT_SECRET`, y arma la identidad y los roles con lo que trae el token. **No consulta a `ms-auth` ni a la base de usuarios en cada petición**: por eso `ms-tienda` sigue funcionando aunque `ms-auth` esté caído, mientras los tokens estén vigentes.

Esa decisión tiene límites que conviene conocer:

- **Desfase de hasta 15 minutos en nombre y roles.** Si un usuario cambia su nombre, o un administrador le agrega o le quita un rol, los access tokens ya emitidos siguen con los datos antiguos hasta que vencen. Al renovar con `/auth/refresh`, `ms-auth` vuelve a leer nombre, RUT y **roles de la base** y emite un token con los datos vigentes.
- **Una cuenta desactivada conserva el acceso hasta que venza su access token** (como máximo 15 minutos) en `ms-tienda`: ese servicio no sabe que la cuenta se desactivó. Desde ese momento no puede iniciar sesión ni renovar el token, y `ms-auth` rechaza sus peticiones a `/auth/me` y `/users/me`.
- **Los tokens no se pueden revocar** antes de su vencimiento. Para cortar el acceso de inmediato hay que cambiar `JWT_SECRET`, lo que invalida los tokens de todos los usuarios.
- **El secreto es compartido**: quien lo conozca puede fabricar tokens válidos con cualquier rol. Solo lo reciben `ms-auth` y `ms-tienda` (el gateway no lo conoce); debe tener al menos 64 bytes y no puede subirse al repositorio.
- **Tokens sin identidad completa se rechazan (401).** Un access token que no traiga `uid`, `rut` y `nombre` (por ejemplo, uno emitido antes de este diseño) no sirve para operar: el cliente debe renovarlo con `/auth/refresh`.

Para acortar el desfase se puede bajar `JWT_ACCESS_EXPIRATION_MS` (a costa de más renovaciones).

## Puesta en marcha

```bash
cp .env.example .env        # completar ORACLE_PASSWORD, AUTH_DB_PASS, TIENDA_DB_PASS, JWT_SECRET y las claves de ADMIN y CLIENTE
docker compose up -d --build
```

Si vienes de la versión de un solo servicio, usa `docker compose up -d --build --remove-orphans` para retirar el contenedor antiguo `ferrefix_app`. El volumen `uploads_data` se conserva (no uses `down -v`).

- Swagger UI (un solo lugar, con selector entre `ms-tienda` y `ms-auth`): http://localhost:8080/swagger-ui.html
- Health del gateway: http://localhost:8080/actuator/health (solo `{"status":"UP"}`)

El orden de arranque es Oracle → `ms-auth` y `ms-tienda` → `ms-gateway`: el gateway espera a que ambos servicios estén sanos, así que el 8080 puede tardar un par de minutos en responder la primera vez.

`JWT_SECRET` (mín. 64 **bytes** UTF-8; los tokens se firman con HS512): `openssl rand -hex 64`, que produce 128 caracteres hexadecimales (128 bytes). Si tu `.env` tiene un secreto más corto, los servicios no arrancarán hasta que lo regeneres.
Sin `.env` completo la app no arranca (no hay credenciales por defecto).

### Esquemas de Oracle

El script `docker/oracle/01-crear-esquemas.sh` crea los dos esquemas (`AUTH_DB_USER` y `TIENDA_DB_USER`) **solo al crear el volumen de Oracle por primera vez**. Si ya tenías un volumen de una versión anterior, hay que eliminarlo para que se ejecute (se pierden los datos de la base):

```bash
docker compose down            # sin -v: no toca los volúmenes
docker volume rm ferrefix-backend_oracle_data
docker compose up -d --build   # recrea Oracle, los esquemas y los datos iniciales
```

Los productos y las máquinas iniciales los siembra Flyway al arrancar `ms-tienda`; las cuentas de ADMIN y CLIENTE, `ms-auth`.

### Oracle en otra máquina

`DB_HOST`, `DB_PORT` y `DB_SERVICE` indican dónde está la base. Por defecto (sin definirlas) los servicios usan el contenedor `oracle` de este compose; fuera de Docker usan `localhost:1521`. Para una base en otra máquina, defínelas en `.env` y levanta solo los servicios:

```bash
# .env
DB_HOST=10.0.2.15
DB_PORT=1521
docker compose up -d --build --no-deps ms-auth ms-tienda ms-gateway   # sin --no-deps también levantaría el contenedor oracle
```

Los esquemas y sus usuarios deben existir en esa base (el script de `docker/oracle` muestra los permisos que necesitan).

## Prueba de humo

Con la API arriba, comprueba el recorrido completo **a través del gateway**: login de ADMIN y CLIENTE; registro de una cuenta nueva, login, `/auth/me`, renovación con `/auth/refresh` y el token aceptado por `ms-tienda`; productos; crear y cancelar una orden; subida de imagen; CORS sin cabeceras duplicadas; errores en JSON y Swagger UI.

```bash
./scripts/humo-gateway.sh
```

Crea un producto y una orden de prueba y los elimina al terminar. Quedan dos restos, porque la API no permite borrarlos: un PNG de 1x1 en `uploads_data` y una cuenta `humo-<fecha>@ferrefix.cl` (CLIENTE sin órdenes) en `ms-auth`. `SIN_IMAGEN=1` omite la imagen y `SIN_REGISTRO=1`, la cuenta.

## Tests

```bash
./mvnw test                         # todos los módulos
./mvnw -pl ms-tienda -am test       # un servicio, con lo que necesita (ferrefix-common)
./mvnw -pl ms-gateway test          # solo el gateway
```

Los tests no necesitan Oracle ni Docker. Los de cada servicio arrancan la aplicación completa con los repositorios simulados y firman los tokens con el secreto compartido; los del gateway levantan el gateway contra servicios HTTP de prueba.

## Ejecución local sin Docker

Con Oracle disponible en `localhost:1521`, los esquemas creados y el `.env` en la raíz del repo:

```bash
./mvnw -pl ms-auth spring-boot:run       # autenticación en :8081
./mvnw -pl ms-tienda spring-boot:run     # tienda en :8082
./mvnw -pl ms-gateway spring-boot:run    # gateway en :8080 (AUTH_URL y TIENDA_URL apuntan a localhost)
```

## Variables de entorno

Ver `.env.example` (desarrollo) y `.env.production.example` (servidor).

| Variable | La lee | Para qué |
|---|---|---|
| `ORACLE_PASSWORD` | Oracle | Clave de SYS/SYSTEM, solo para inicializar el contenedor |
| `AUTH_DB_USER`, `AUTH_DB_PASS` | Oracle (crea el esquema) y `ms-auth` | Esquema de usuarios |
| `TIENDA_DB_USER`, `TIENDA_DB_PASS` | Oracle (crea el esquema) y `ms-tienda` | Esquema de la tienda |
| `DB_HOST`, `DB_PORT`, `DB_SERVICE` | `ms-auth` y `ms-tienda` | Dónde está la base (opcionales; ver arriba) |
| `JWT_SECRET` | `ms-auth` y `ms-tienda` | Firma y verificación de los tokens (≥ 64 bytes) |
| `JWT_ACCESS_EXPIRATION_MS`, `JWT_REFRESH_EXPIRATION_MS` | `ms-auth` | Vigencia de cada token |
| `ADMIN_EMAIL`, `ADMIN_PASSWORD`, `CLIENT_EMAIL`, `CLIENT_PASSWORD` | `ms-auth` | Cuentas iniciales |
| `CORS_ALLOWED_ORIGINS` | `ms-gateway` | Orígenes del frontend |
| `AUTH_URL`, `TIENDA_URL` | `ms-gateway` | Direcciones internas de los servicios (el compose las define) |
