-- V5: máquinas iniciales de Ferrefix (las 4 del catálogo de arriendo de ferrefix-ui), con 2 unidades cada una.
-- GENERADO desde ferrefix-ui/src/data/maquinarias.js: no editar a mano.
-- NO MODIFICAR este archivo una vez aplicado (Flyway valida el checksum): los cambios van en una V6.
--
-- Idempotente: si un CODIGO ya existe NO se toca (ni se pisa ni se borra nada). Las especificaciones solo se cargan
-- en modelos que aún no tienen ninguna, y cada unidad solo se crea si su código no existe y el modelo está DISPONIBLE
-- (así nunca queda un modelo NO_DISPONIBLE con unidades libres). No se siembra ningún arriendo.
-- Las imágenes se sirven desde classpath:/seed-media/ en /media/maquinas/*.

-- MAQ-BOSCH-01 · Martillo Demoledor Bosch GSH 16-28 Professional 1750W
MERGE INTO MAQUINAS m
USING (SELECT 'MAQ-BOSCH-01' AS CODIGO FROM DUAL) s ON (m.CODIGO = s.CODIGO)
WHEN NOT MATCHED THEN INSERT (CODIGO, NOMBRE, MARCA, CATEGORIA, TARIFA_DIA, ESTADO, INCLUYE, IMAGEN_URL)
    VALUES ('MAQ-BOSCH-01', 'Martillo Demoledor Bosch GSH 16-28 Professional 1750W', 'Bosch', 'Demolición y Perforación Pesada', 28990, 'DISPONIBLE', 'Cincel de punta, cincel plano, paño de limpieza y maletín de transporte con ruedas', '/media/maquinas/maq-bosch-01.webp');

-- MAQ-BET-02 · Betonera Trompo Eléctrica 150 Litros 0.75HP
MERGE INTO MAQUINAS m
USING (SELECT 'MAQ-BET-02' AS CODIGO FROM DUAL) s ON (m.CODIGO = s.CODIGO)
WHEN NOT MATCHED THEN INSERT (CODIGO, NOMBRE, MARCA, CATEGORIA, TARIFA_DIA, ESTADO, INCLUYE, IMAGEN_URL)
    VALUES ('MAQ-BET-02', 'Betonera Trompo Eléctrica 150 Litros 0.75HP', 'Ingco Heavy', 'Hormigón y Mezcla', 18990, 'DISPONIBLE', 'Cable con enchufe industrial Legrand y manubrio de volteo reforzado', '/media/maquinas/maq-bet-02.webp');

-- MAQ-GEN-03 · Generador Eléctrico a Gasolina 6.5kVA Monofásico
MERGE INTO MAQUINAS m
USING (SELECT 'MAQ-GEN-03' AS CODIGO FROM DUAL) s ON (m.CODIGO = s.CODIGO)
WHEN NOT MATCHED THEN INSERT (CODIGO, NOMBRE, MARCA, CATEGORIA, TARIFA_DIA, ESTADO, INCLUYE, IMAGEN_URL)
    VALUES ('MAQ-GEN-03', 'Generador Eléctrico a Gasolina 6.5kVA Monofásico', 'Krafter Pro', 'Energía y Grupos Electrógenos', 24990, 'DISPONIBLE', 'Kit de ruedas de transporte y horómetro digital', '/media/maquinas/maq-gen-03.webp');

-- MAQ-PLAC-04 · Placa Compactadora Vibratoria 90kg Motor 6.5HP
MERGE INTO MAQUINAS m
USING (SELECT 'MAQ-PLAC-04' AS CODIGO FROM DUAL) s ON (m.CODIGO = s.CODIGO)
WHEN NOT MATCHED THEN INSERT (CODIGO, NOMBRE, MARCA, CATEGORIA, TARIFA_DIA, ESTADO, INCLUYE, IMAGEN_URL)
    VALUES ('MAQ-PLAC-04', 'Placa Compactadora Vibratoria 90kg Motor 6.5HP', 'Husqvarna Construction', 'Compactación y Pavimento', 22990, 'DISPONIBLE', 'Tanque de agua para asfalto y ruedas rebatibles', '/media/maquinas/maq-plac-04.webp');

-- MAQ-BOSCH-01 · especificaciones (solo si el modelo aún no tiene ninguna)
INSERT INTO MAQUINA_ESPECIFICACIONES (MAQUINA_ID, CLAVE, VALOR, ORDEN)
SELECT m.ID, v.CLAVE, v.VALOR, v.ORDEN
FROM MAQUINAS m
CROSS JOIN (
        SELECT 'Potencia Absorbida' AS CLAVE, '1.750 Watts' AS VALOR, 0 AS ORDEN FROM DUAL UNION ALL
        SELECT 'Energía de Impacto', '41 Joules', 1 FROM DUAL UNION ALL
        SELECT 'Portaherramientas', 'Hexagonal 28 mm', 2 FROM DUAL UNION ALL
        SELECT 'Peso', '17.9 kg', 3 FROM DUAL UNION ALL
        SELECT 'Rendimiento de arranque', '13 toneladas de hormigón al día', 4 FROM DUAL UNION ALL
        SELECT 'Tecnología', 'Sistema Vibration Control para reducción de fatiga en faena', 5 FROM DUAL
    ) v
WHERE m.CODIGO = 'MAQ-BOSCH-01'
  AND NOT EXISTS (SELECT 1 FROM MAQUINA_ESPECIFICACIONES e WHERE e.MAQUINA_ID = m.ID);

-- MAQ-BET-02 · especificaciones (solo si el modelo aún no tiene ninguna)
INSERT INTO MAQUINA_ESPECIFICACIONES (MAQUINA_ID, CLAVE, VALOR, ORDEN)
SELECT m.ID, v.CLAVE, v.VALOR, v.ORDEN
FROM MAQUINAS m
CROSS JOIN (
        SELECT 'Capacidad de Tambor' AS CLAVE, '150 Litros' AS VALOR, 0 AS ORDEN FROM DUAL UNION ALL
        SELECT 'Motor', '0.75 HP Eléctrico 220V', 1 FROM DUAL UNION ALL
        SELECT 'Ruedas', 'Goma maciza 10" para fácil traslado en obra', 2 FROM DUAL UNION ALL
        SELECT 'Espesor Tambor', 'Chapa de acero 1.8 mm', 3 FROM DUAL
    ) v
WHERE m.CODIGO = 'MAQ-BET-02'
  AND NOT EXISTS (SELECT 1 FROM MAQUINA_ESPECIFICACIONES e WHERE e.MAQUINA_ID = m.ID);

-- MAQ-GEN-03 · especificaciones (solo si el modelo aún no tiene ninguna)
INSERT INTO MAQUINA_ESPECIFICACIONES (MAQUINA_ID, CLAVE, VALOR, ORDEN)
SELECT m.ID, v.CLAVE, v.VALOR, v.ORDEN
FROM MAQUINAS m
CROSS JOIN (
        SELECT 'Potencia Nominal' AS CLAVE, '5.500 Watts (Peak 6.500W)' AS VALOR, 0 AS ORDEN FROM DUAL UNION ALL
        SELECT 'Voltaje', '230V / 50Hz', 1 FROM DUAL UNION ALL
        SELECT 'Autonomía', '9.5 horas al 75% de carga', 2 FROM DUAL UNION ALL
        SELECT 'Capacidad Estanque', '25 Litros Gasolina 93', 3 FROM DUAL
    ) v
WHERE m.CODIGO = 'MAQ-GEN-03'
  AND NOT EXISTS (SELECT 1 FROM MAQUINA_ESPECIFICACIONES e WHERE e.MAQUINA_ID = m.ID);

-- MAQ-PLAC-04 · especificaciones (solo si el modelo aún no tiene ninguna)
INSERT INTO MAQUINA_ESPECIFICACIONES (MAQUINA_ID, CLAVE, VALOR, ORDEN)
SELECT m.ID, v.CLAVE, v.VALOR, v.ORDEN
FROM MAQUINAS m
CROSS JOIN (
        SELECT 'Peso Operativo' AS CLAVE, '90 kg' AS VALOR, 0 AS ORDEN FROM DUAL UNION ALL
        SELECT 'Fuerza Centrífuga', '15 kN', 1 FROM DUAL UNION ALL
        SELECT 'Profundidad de Compactación', 'Hasta 30 cm', 2 FROM DUAL UNION ALL
        SELECT 'Motor', '4 Tiempos 196cc OHV', 3 FROM DUAL
    ) v
WHERE m.CODIGO = 'MAQ-PLAC-04'
  AND NOT EXISTS (SELECT 1 FROM MAQUINA_ESPECIFICACIONES e WHERE e.MAQUINA_ID = m.ID);

-- MAQ-BOSCH-01-U01
INSERT INTO MAQUINA_UNIDADES (MAQUINA_ID, CODIGO, ESTADO)
SELECT m.ID, 'MAQ-BOSCH-01-U01', 'DISPONIBLE' FROM MAQUINAS m
WHERE m.CODIGO = 'MAQ-BOSCH-01' AND m.ESTADO = 'DISPONIBLE'
  AND NOT EXISTS (SELECT 1 FROM MAQUINA_UNIDADES u WHERE u.CODIGO = 'MAQ-BOSCH-01-U01');

-- MAQ-BOSCH-01-U02
INSERT INTO MAQUINA_UNIDADES (MAQUINA_ID, CODIGO, ESTADO)
SELECT m.ID, 'MAQ-BOSCH-01-U02', 'DISPONIBLE' FROM MAQUINAS m
WHERE m.CODIGO = 'MAQ-BOSCH-01' AND m.ESTADO = 'DISPONIBLE'
  AND NOT EXISTS (SELECT 1 FROM MAQUINA_UNIDADES u WHERE u.CODIGO = 'MAQ-BOSCH-01-U02');

-- MAQ-BET-02-U01
INSERT INTO MAQUINA_UNIDADES (MAQUINA_ID, CODIGO, ESTADO)
SELECT m.ID, 'MAQ-BET-02-U01', 'DISPONIBLE' FROM MAQUINAS m
WHERE m.CODIGO = 'MAQ-BET-02' AND m.ESTADO = 'DISPONIBLE'
  AND NOT EXISTS (SELECT 1 FROM MAQUINA_UNIDADES u WHERE u.CODIGO = 'MAQ-BET-02-U01');

-- MAQ-BET-02-U02
INSERT INTO MAQUINA_UNIDADES (MAQUINA_ID, CODIGO, ESTADO)
SELECT m.ID, 'MAQ-BET-02-U02', 'DISPONIBLE' FROM MAQUINAS m
WHERE m.CODIGO = 'MAQ-BET-02' AND m.ESTADO = 'DISPONIBLE'
  AND NOT EXISTS (SELECT 1 FROM MAQUINA_UNIDADES u WHERE u.CODIGO = 'MAQ-BET-02-U02');

-- MAQ-GEN-03-U01
INSERT INTO MAQUINA_UNIDADES (MAQUINA_ID, CODIGO, ESTADO)
SELECT m.ID, 'MAQ-GEN-03-U01', 'DISPONIBLE' FROM MAQUINAS m
WHERE m.CODIGO = 'MAQ-GEN-03' AND m.ESTADO = 'DISPONIBLE'
  AND NOT EXISTS (SELECT 1 FROM MAQUINA_UNIDADES u WHERE u.CODIGO = 'MAQ-GEN-03-U01');

-- MAQ-GEN-03-U02
INSERT INTO MAQUINA_UNIDADES (MAQUINA_ID, CODIGO, ESTADO)
SELECT m.ID, 'MAQ-GEN-03-U02', 'DISPONIBLE' FROM MAQUINAS m
WHERE m.CODIGO = 'MAQ-GEN-03' AND m.ESTADO = 'DISPONIBLE'
  AND NOT EXISTS (SELECT 1 FROM MAQUINA_UNIDADES u WHERE u.CODIGO = 'MAQ-GEN-03-U02');

-- MAQ-PLAC-04-U01
INSERT INTO MAQUINA_UNIDADES (MAQUINA_ID, CODIGO, ESTADO)
SELECT m.ID, 'MAQ-PLAC-04-U01', 'DISPONIBLE' FROM MAQUINAS m
WHERE m.CODIGO = 'MAQ-PLAC-04' AND m.ESTADO = 'DISPONIBLE'
  AND NOT EXISTS (SELECT 1 FROM MAQUINA_UNIDADES u WHERE u.CODIGO = 'MAQ-PLAC-04-U01');

-- MAQ-PLAC-04-U02
INSERT INTO MAQUINA_UNIDADES (MAQUINA_ID, CODIGO, ESTADO)
SELECT m.ID, 'MAQ-PLAC-04-U02', 'DISPONIBLE' FROM MAQUINAS m
WHERE m.CODIGO = 'MAQ-PLAC-04' AND m.ESTADO = 'DISPONIBLE'
  AND NOT EXISTS (SELECT 1 FROM MAQUINA_UNIDADES u WHERE u.CODIGO = 'MAQ-PLAC-04-U02');
