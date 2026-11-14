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
┌──────────────────┐        red interna de Docker        ┌──────────────────┐        ┌──────────┐
│    ms-gateway    │ ──────────────────────────────────▶ │    ms-tienda     │ ─────▶ │  Oracle  │
│  :8080 (público) │   /api/v1/**  /media/**  Swagger    │  :8082 (interno) │        │  :1521   │
└──────────────────┘                                     └──────────────────┘        └──────────┘
```

| Módulo | Qué hace | Puerto |
|---|---|---|
| `ms-gateway` | Spring Cloud Gateway (WebFlux). Único punto de entrada: enruta, resuelve CORS y responde en JSON los errores de enrutado (404, 502, 504). No conoce secretos: no valida JWT ni usa la base. | 8080, público |
| `ms-tienda` | La API: autenticación JWT, usuarios, productos, imágenes, órdenes, máquinas, arriendos y cotizaciones. Valida el token y los roles. | 8082, solo red interna |
| `oracle` | Base de datos (Flyway crea el esquema al arrancar `ms-tienda`). | 1521, solo `127.0.0.1` |

- **El contrato público no cambió**: mismas rutas (`/api/v1/**`, `/media/**`), mismos cuerpos y mismos códigos de estado. El frontend sigue apuntando a `http://<host>:8080`.
- **CORS vive solo en el gateway.** `ms-tienda` no añade cabeceras `Access-Control-*`; si lo hiciera, el navegador recibiría `Access-Control-Allow-Origin` duplicada y rechazaría la respuesta.
- **Errores del gateway** con el mismo formato que los de la API (`timestamp`, `status`, `error`, `message`, `path`): 404 si la ruta no es de ningún servicio, 502 si el servicio está caído y 504 si no responde en 60 s (margen para las subidas de imágenes de hasta 6 MB). Los errores que responde el servicio pasan tal cual.

## Puesta en marcha

```bash
cp .env.example .env        # completar ORACLE_PASSWORD, DB_PASS, JWT_SECRET, ADMIN_PASSWORD, CLIENT_PASSWORD
docker compose up -d --build
```

Si vienes de la versión de un solo servicio, usa `docker compose up -d --build --remove-orphans` para retirar el contenedor antiguo `ferrefix_app`. Los volúmenes `oracle_data` y `uploads_data` se conservan (no uses `down -v`).

- Swagger UI: http://localhost:8080/swagger-ui.html
- Health del gateway: http://localhost:8080/actuator/health (solo `{"status":"UP"}`)

El orden de arranque es Oracle → `ms-tienda` → `ms-gateway`: el gateway espera a que `ms-tienda` esté sano, así que el 8080 puede tardar un par de minutos en responder la primera vez.

`JWT_SECRET` (mín. 64 **bytes** UTF-8; los tokens se firman con HS512): `openssl rand -hex 64`, que produce 128 caracteres hexadecimales (128 bytes). Si tu `.env` tiene un secreto más corto, la app no arrancará hasta que lo regeneres.
Sin `.env` completo la app no arranca (no hay credenciales por defecto).

## Prueba de humo

Con la API arriba, comprueba el recorrido completo **a través del gateway** (login, productos, crear y cancelar una orden, subida de imagen, CORS sin cabeceras duplicadas, errores en JSON y Swagger):

```bash
./scripts/humo-gateway.sh
```

Crea un producto y una orden de prueba y los elimina al terminar. La imagen subida (un PNG de 1x1) queda en `uploads_data`, porque la API no tiene borrado de imágenes; `SIN_IMAGEN=1 ./scripts/humo-gateway.sh` omite ese paso.

## Tests

```bash
./mvnw test                   # todos los módulos
./mvnw -pl ms-tienda test     # solo la API
./mvnw -pl ms-gateway test    # solo el gateway
```

Los tests no necesitan Oracle ni Docker. Los del gateway levantan el gateway completo contra un servicio HTTP de prueba.

## Ejecución local sin Docker

Con Oracle disponible en `localhost:1521` y el `.env` en la raíz del repo:

```bash
./mvnw -pl ms-tienda spring-boot:run     # API en :8082
./mvnw -pl ms-gateway spring-boot:run    # gateway en :8080 (reenvía a http://localhost:8082)
```

## Variables de entorno

Ver `.env.example` (desarrollo) y `.env.production.example` (servidor). `CORS_ALLOWED_ORIGINS` la lee el gateway; el resto, `ms-tienda` y Oracle. El gateway acepta además `TIENDA_URL` (dirección interna de `ms-tienda`), que `docker-compose.yml` ya define.
