#!/bin/bash
# Se ejecuta una sola vez, al crear el contenedor Oracle (gvenzl/oracle-free) con el volumen vacío.
# Crea un esquema (= un usuario de Oracle) por servicio en la misma PDB: cada servicio solo ve sus propias tablas y
# no existen claves foráneas entre esquemas. Permisos mínimos, SIN rol DBA.
#   AUTH_DB_USER      / AUTH_DB_PASS       -> ms-auth       (usuarios y roles)
#   TIENDA_DB_USER    / TIENDA_DB_PASS     -> ms-tienda     (productos y órdenes)
#   ARRIENDOS_DB_USER / ARRIENDOS_DB_PASS  -> ms-arriendos  (máquinas, arriendos y cotizaciones)
set -euo pipefail

VARIABLES_USUARIO=(AUTH_DB_USER TIENDA_DB_USER ARRIENDOS_DB_USER)
VARIABLES_CLAVE=(AUTH_DB_PASS TIENDA_DB_PASS ARRIENDOS_DB_PASS)

for variable in "${VARIABLES_USUARIO[@]}" "${VARIABLES_CLAVE[@]}"; do
  if [ -z "${!variable:-}" ]; then
    echo "Falta la variable $variable: no se pueden crear los esquemas." >&2
    exit 1
  fi
done

# El nombre se escribe sin comillas en el SQL: solo letras, números y guion bajo, empezando por letra.
for variable in "${VARIABLES_USUARIO[@]}"; do
  if ! [[ "${!variable}" =~ ^[A-Za-z][A-Za-z0-9_]{0,29}$ ]]; then
    echo "$variable no es un nombre de esquema válido (letras, números y _; máx. 30; empieza con letra)." >&2
    exit 1
  fi
done
# Oracle no distingue mayúsculas en estos nombres: dos servicios no pueden compartir esquema.
if [ "$(printf '%s\n' "${AUTH_DB_USER^^}" "${TIENDA_DB_USER^^}" "${ARRIENDOS_DB_USER^^}" | sort -u | wc -l)" -ne 3 ]; then
  echo "AUTH_DB_USER, TIENDA_DB_USER y ARRIENDOS_DB_USER deben ser distintos: cada servicio tiene su propio esquema." >&2
  exit 1
fi

PERMISOS="CREATE SESSION, CREATE TABLE, CREATE SEQUENCE, CREATE VIEW, CREATE TRIGGER, CREATE INDEXTYPE"

sqlplus -s / as sysdba <<SQL
WHENEVER SQLERROR EXIT SQL.SQLCODE
ALTER SESSION SET CONTAINER = FREEPDB1;

CREATE USER ${AUTH_DB_USER} IDENTIFIED BY "${AUTH_DB_PASS}" QUOTA UNLIMITED ON USERS;
GRANT ${PERMISOS} TO ${AUTH_DB_USER};

CREATE USER ${TIENDA_DB_USER} IDENTIFIED BY "${TIENDA_DB_PASS}" QUOTA UNLIMITED ON USERS;
GRANT ${PERMISOS} TO ${TIENDA_DB_USER};

CREATE USER ${ARRIENDOS_DB_USER} IDENTIFIED BY "${ARRIENDOS_DB_PASS}" QUOTA UNLIMITED ON USERS;
GRANT ${PERMISOS} TO ${ARRIENDOS_DB_USER};
EXIT;
SQL
