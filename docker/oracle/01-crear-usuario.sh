#!/bin/bash
# Se ejecuta una sola vez al crear el contenedor Oracle (gvenzl/oracle-free).
# Crea el usuario de la app en la PDB con permisos mínimos (SIN rol DBA).
set -e
sqlplus -s / as sysdba <<SQL
ALTER SESSION SET CONTAINER = FREEPDB1;
CREATE USER ${DB_USER} IDENTIFIED BY "${DB_PASS}" QUOTA UNLIMITED ON USERS;
GRANT CREATE SESSION, CREATE TABLE, CREATE SEQUENCE, CREATE VIEW, CREATE TRIGGER, CREATE INDEXTYPE TO ${DB_USER};
EXIT;
SQL
