# Ferrefix Backend

API REST (Spring Boot 4 · Java 21 · Oracle · JWT) de la tienda Ferrefix: venta de productos,
arriendo de máquinas, cotizaciones y panel administrativo. Un solo servicio.

Frontend: https://github.com/miguelpastenm-web/ferrefix-ui

## Puesta en marcha

```bash
cp .env.example .env        # completar ORACLE_PASSWORD, DB_PASS, JWT_SECRET, ADMIN_PASSWORD
docker compose up -d --build
```

- Swagger UI: http://localhost:8080/swagger-ui.html
- Health: http://localhost:8080/actuator/health

`JWT_SECRET` (mín. 64 **bytes** UTF-8; los tokens se firman con HS512): `openssl rand -hex 64`, que produce 128 caracteres hexadecimales (128 bytes). Si tu `.env` tiene un secreto más corto, la app no arrancará hasta que lo regeneres.
Sin `.env` completo la app no arranca (no hay credenciales por defecto).

## Variables de entorno

Ver `.env.example`.
