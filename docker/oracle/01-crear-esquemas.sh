#!/bin/bash
# Se ejecuta una sola vez, al crear el contenedor Oracle (gvenzl/oracle-free) con el volumen vacío.
# Crea un esquema (= un usuario de Oracle) por servicio en la misma PDB: cada servicio solo ve sus propias tablas y
# no existen claves foráneas entre esquemas. Permisos mínimos, SIN rol DBA.
#   AUTH_DB_USER   / AUTH_DB_PASS    -> ms-auth    (usuarios y roles)
#   TIENDA_DB_USER / TIENDA_DB_PASS  -> ms-tienda  (productos, órdenes, máquinas, arriendos y cotizaciones)
set -euo pipefail

for variable in AUTH_DB_USER AUTH_DB_PASS TIENDA_DB_USER TIENDA_DB_PASS; do
  if [ -z "${!variable:-}" ]; then
    echo "Falta la variable $variable: no se pueden crear los esquemas." >&2
    exit 1
  fi
done

# El nombre se escribe sin comillas en el SQL: solo letras, números y guion bajo, empezando por letra.
for variable in AUTH_DB_USER TIENDA_DB_USER; do
  if ! [[ "${!variable}" =~ ^[A-Za-z][A-Za-z0-9_]{0,29}$ ]]; then
    echo "$variable no es un nombre de esquema válido (letras, números y _; máx. 30; empieza con letra)." >&2
    exit 1
  fi
done
if [ "${AUTH_DB_USER^^}" = "${TIENDA_DB_USER^^}" ]; then
  echo "AUTH_DB_USER y TIENDA_DB_USER deben ser distintos: cada servicio tiene su propio esquema." >&2
  exit 1
fi

sqlplus -s / as sysdba <<SQL
WHENEVER SQLERROR EXIT SQL.SQLCODE
ALTER SESSION SET CONTAINER = FREEPDB1;

CREATE USER ${AUTH_DB_USER} IDENTIFIED BY "${AUTH_DB_PASS}" QUOTA UNLIMITED ON USERS;
GRANT CREATE SESSION, CREATE TABLE, CREATE SEQUENCE, CREATE VIEW, CREATE TRIGGER, CREATE INDEXTYPE TO ${AUTH_DB_USER};

CREATE USER ${TIENDA_DB_USER} IDENTIFIED BY "${TIENDA_DB_PASS}" QUOTA UNLIMITED ON USERS;
GRANT CREATE SESSION, CREATE TABLE, CREATE SEQUENCE, CREATE VIEW, CREATE TRIGGER, CREATE INDEXTYPE TO ${TIENDA_DB_USER};
EXIT;
SQL
