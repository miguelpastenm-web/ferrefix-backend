-- V3: productos iniciales de Ferrefix (los 18 del catálogo de ferrefix-ui).
-- GENERADO desde ferrefix-ui/src/data/productos.js: no editar a mano.
-- NO MODIFICAR este archivo una vez aplicado (Flyway valida el checksum): los cambios van en una V4.
--
-- Idempotente: si un SKU ya existe NO se toca (ni se pisa ni se borra nada); las especificaciones
-- solo se cargan en productos que aún no tienen ninguna. Las imágenes se sirven desde
-- classpath:/seed-media/ en /media/productos/* y /media/ofertas/*.

-- 5422223214 · Taladro Percutor Inalámbrico 20V Max XR
MERGE INTO PRODUCTOS p
USING (SELECT '5422223214' AS SKU FROM DUAL) s ON (p.SKU = s.SKU)
WHEN NOT MATCHED THEN INSERT (SKU, NOMBRE, MARCA, CATEGORIA, PRECIO_VENTA, STOCK, ESTADO, IMAGEN_URL, IMAGEN_OFERTA_URL, DESCRIPCION)
    VALUES ('5422223214', 'Taladro Percutor Inalámbrico 20V Max XR', 'DeWalt', 'Herramientas', 129990, 12, 'DISPONIBLE', '/media/productos/taladro-percutor.webp', NULL, 'Taladro percutor inalámbrico compacto con motor sin carbones (Brushless) de alto rendimiento. Ofrece mayor autonomía y durabilidad para perforaciones exigentes en concreto, madera y metal. Cuenta con luz LED integrada con temporizador para iluminar el área de trabajo en espacios reducidos.');

-- 5422223215 · Sierra Circular 7-1/4" 1800W con Guía Láser
MERGE INTO PRODUCTOS p
USING (SELECT '5422223215' AS SKU FROM DUAL) s ON (p.SKU = s.SKU)
WHEN NOT MATCHED THEN INSERT (SKU, NOMBRE, MARCA, CATEGORIA, PRECIO_VENTA, STOCK, ESTADO, IMAGEN_URL, IMAGEN_OFERTA_URL, DESCRIPCION)
    VALUES ('5422223215', 'Sierra Circular 7-1/4" 1800W con Guía Láser', 'Makita', 'Herramientas', 149990, 8, 'DISPONIBLE', '/media/productos/sierra-circular.webp', NULL, 'Sierra circular potente diseñada para cortes longitudinales y transversales precisos en madera maciza y derivados. Equipada con base de aluminio resistente, soplador de polvo integrado y empuñadura ergonómica antideslizante para mayor control y seguridad.');

-- 5422223216 · Esmeril Angular 4-1/2" 850W Heavy Duty
MERGE INTO PRODUCTOS p
USING (SELECT '5422223216' AS SKU FROM DUAL) s ON (p.SKU = s.SKU)
WHEN NOT MATCHED THEN INSERT (SKU, NOMBRE, MARCA, CATEGORIA, PRECIO_VENTA, STOCK, ESTADO, IMAGEN_URL, IMAGEN_OFERTA_URL, DESCRIPCION)
    VALUES ('5422223216', 'Esmeril Angular 4-1/2" 850W Heavy Duty', 'Bosch', 'Herramientas', 54990, 25, 'DISPONIBLE', '/media/productos/esmeril-angular.webp', NULL, 'Esmeril angular profesional diseñado para corte, desbaste y lijado en metales y mampostería. Su carcasa compacta permite un agarre cómodo con una sola mano, mientras que sus rejillas de ventilación optimizadas extienden la vida útil del motor.');

-- 5422223217 · Cemento Especial Bío Bío Saco 25 kg
MERGE INTO PRODUCTOS p
USING (SELECT '5422223217' AS SKU FROM DUAL) s ON (p.SKU = s.SKU)
WHEN NOT MATCHED THEN INSERT (SKU, NOMBRE, MARCA, CATEGORIA, PRECIO_VENTA, STOCK, ESTADO, IMAGEN_URL, IMAGEN_OFERTA_URL, DESCRIPCION)
    VALUES ('5422223217', 'Cemento Especial Bío Bío Saco 25 kg', 'Bío Bío', 'Construcción', 4890, 140, 'DISPONIBLE', '/media/productos/cemento-biobio.webp', NULL, 'Cemento de uso general de fraguado controlado y alta resistencia mecánica inicial y final. Formulado para la confección de hormigones estructurales, morteros de pega, estucos y radieres residenciales e industriales.');

-- 5422223218 · Set de Brocas y Puntas Mixtas 100 Piezas en Estuche
MERGE INTO PRODUCTOS p
USING (SELECT '5422223218' AS SKU FROM DUAL) s ON (p.SKU = s.SKU)
WHEN NOT MATCHED THEN INSERT (SKU, NOMBRE, MARCA, CATEGORIA, PRECIO_VENTA, STOCK, ESTADO, IMAGEN_URL, IMAGEN_OFERTA_URL, DESCRIPCION)
    VALUES ('5422223218', 'Set de Brocas y Puntas Mixtas 100 Piezas en Estuche', 'Bosch', 'Fijaciones', 29990, 18, 'DISPONIBLE', '/media/productos/set-brocas.webp', NULL, 'Set integral para taladrado y atornillado compatible con todas las marcas de taladros del mercado. Incluye brocas para madera, metal (HSS-TiN) y concreto, además de una amplia variedad de puntas Phillips, Pozidriv, Torx y hexagonales con adaptador magnético.');

-- 5422223219 · Generador Eléctrico a Gasolina 3000W 7HP Monofásico
MERGE INTO PRODUCTOS p
USING (SELECT '5422223219' AS SKU FROM DUAL) s ON (p.SKU = s.SKU)
WHEN NOT MATCHED THEN INSERT (SKU, NOMBRE, MARCA, CATEGORIA, PRECIO_VENTA, STOCK, ESTADO, IMAGEN_URL, IMAGEN_OFERTA_URL, DESCRIPCION)
    VALUES ('5422223219', 'Generador Eléctrico a Gasolina 3000W 7HP Monofásico', 'KRAFTER', 'Electricidad', 289990, 5, 'DISPONIBLE', '/media/productos/generador-electrico.webp', NULL, 'Generador a combustión ideal para respaldo eléctrico en hogares, parcelas o faenas de construcción donde no hay suministro de red. Posee regulador automático de voltaje (AVR) que protege artefactos sensibles contra fluctuaciones eléctricas.');

-- 5422223220 · Set Llave Monomando Lavaplatos Cuello Flexible Negro Mate
MERGE INTO PRODUCTOS p
USING (SELECT '5422223220' AS SKU FROM DUAL) s ON (p.SKU = s.SKU)
WHEN NOT MATCHED THEN INSERT (SKU, NOMBRE, MARCA, CATEGORIA, PRECIO_VENTA, STOCK, ESTADO, IMAGEN_URL, IMAGEN_OFERTA_URL, DESCRIPCION)
    VALUES ('5422223220', 'Set Llave Monomando Lavaplatos Cuello Flexible Negro Mate', 'Stretto', 'Gasfitería', 42990, 15, 'DISPONIBLE', '/media/productos/llave-monomando.webp', NULL, 'Grifería monomando moderna con caño de silicona flexible de 360 grados y terminación negro mate de alta resistencia a la corrosión. Incorpora cartucho cerámico antifuga de 35 mm y aireador que reduce hasta un 40% el consumo de agua.');

-- 5422223221 · Esmalte al Agua Profesional Blanco Semibrillo 1 Galón
MERGE INTO PRODUCTOS p
USING (SELECT '5422223221' AS SKU FROM DUAL) s ON (p.SKU = s.SKU)
WHEN NOT MATCHED THEN INSERT (SKU, NOMBRE, MARCA, CATEGORIA, PRECIO_VENTA, STOCK, ESTADO, IMAGEN_URL, IMAGEN_OFERTA_URL, DESCRIPCION)
    VALUES ('5422223221', 'Esmalte al Agua Profesional Blanco Semibrillo 1 Galón', 'Ceresita', 'Pinturas', 31990, 32, 'DISPONIBLE', '/media/productos/esmalte-ceresita.webp', NULL, 'Pintura esmalte al agua de formulación premium con tecnología antihongos y antibacterias. Posee excelente lavabilidad, secado rápido y bajo olor, ideal para aplicación en muros interiores y exteriores de baños, cocinas, piezas y fachadas.');

-- 5422223222 · Arnés de Seguridad 4 Argollas Anticaídas con Cabo de Vida
MERGE INTO PRODUCTOS p
USING (SELECT '5422223222' AS SKU FROM DUAL) s ON (p.SKU = s.SKU)
WHEN NOT MATCHED THEN INSERT (SKU, NOMBRE, MARCA, CATEGORIA, PRECIO_VENTA, STOCK, ESTADO, IMAGEN_URL, IMAGEN_OFERTA_URL, DESCRIPCION)
    VALUES ('5422223222', 'Arnés de Seguridad 4 Argollas Anticaídas con Cabo de Vida', 'Steelpro', 'Seguridad', 45990, 20, 'DISPONIBLE', '/media/productos/arnes-seguridad.webp', NULL, 'Arnés de cuerpo entero diseñado para detención de caídas y posicionamiento en trabajos en altura. Fabricado en cintas de poliéster de alta tenacidad con costuras reforzadas, hebillas de conexión rápida y almohadilla lumbar transpirable para mayor confort.');

-- 5422223223 · Pack 1000 Tornillos Autoperforantes Drywall 6x1-1/4"
MERGE INTO PRODUCTOS p
USING (SELECT '5422223223' AS SKU FROM DUAL) s ON (p.SKU = s.SKU)
WHEN NOT MATCHED THEN INSERT (SKU, NOMBRE, MARCA, CATEGORIA, PRECIO_VENTA, STOCK, ESTADO, IMAGEN_URL, IMAGEN_OFERTA_URL, DESCRIPCION)
    VALUES ('5422223223', 'Pack 1000 Tornillos Autoperforantes Drywall 6x1-1/4"', 'Mamut', 'Fijaciones', 11990, 55, 'DISPONIBLE', '/media/productos/tornillos-drywall.webp', NULL, 'Tornillos fosfatizados negros con punta broca diseñados para la fijación de planchas de yeso cartón (Volcanita/Gyplac) a perfiles metálicos estructurales (Metalcon) de hasta 0.85 mm de espesor sin necesidad de guía previa.');

-- 5422223224 · Cubierta Melamina Café 15mm 60x120cm
MERGE INTO PRODUCTOS p
USING (SELECT '5422223224' AS SKU FROM DUAL) s ON (p.SKU = s.SKU)
WHEN NOT MATCHED THEN INSERT (SKU, NOMBRE, MARCA, CATEGORIA, PRECIO_VENTA, STOCK, ESTADO, IMAGEN_URL, IMAGEN_OFERTA_URL, DESCRIPCION)
    VALUES ('5422223224', 'Cubierta Melamina Café 15mm 60x120cm', 'Masisa', 'Maderas', 9990, 45, 'DISPONIBLE', '/media/productos/cubierta-melamina.webp', '/media/ofertas/cubierta-melamina.webp', 'Cubierta de melamina tono café de 15 mm y 60x120 cm, diseñada para la construcción de muebles, cubiertas de closet, escritorios y superficies de trabajo. Su laminado melamínico resiste la humedad, los rayados y la limpieza diaria, entregando una terminación uniforme sin necesidad de barniz.');

-- 5422223225 · Tablero OSB Multiplac 8mm 122x244cm
MERGE INTO PRODUCTOS p
USING (SELECT '5422223225' AS SKU FROM DUAL) s ON (p.SKU = s.SKU)
WHEN NOT MATCHED THEN INSERT (SKU, NOMBRE, MARCA, CATEGORIA, PRECIO_VENTA, STOCK, ESTADO, IMAGEN_URL, IMAGEN_OFERTA_URL, DESCRIPCION)
    VALUES ('5422223225', 'Tablero OSB Multiplac 8mm 122x244cm', 'Multiplac', 'Maderas', 10990, 60, 'DISPONIBLE', '/media/productos/osb-multiplac.webp', '/media/ofertas/osb-multiplac.webp', 'Tablero OSB de 8 mm y 122x244 cm, fabricado con virutas de madera encoladas y prensadas en capas cruzadas. Ideal para revestimiento de muros, tabiques, cielos y bases de mobiliario, con buena resistencia mecánica y estabilidad dimensional.');

-- 5422223226 · Terciado Ranurado 9mm 122x244cm
MERGE INTO PRODUCTOS p
USING (SELECT '5422223226' AS SKU FROM DUAL) s ON (p.SKU = s.SKU)
WHEN NOT MATCHED THEN INSERT (SKU, NOMBRE, MARCA, CATEGORIA, PRECIO_VENTA, STOCK, ESTADO, IMAGEN_URL, IMAGEN_OFERTA_URL, DESCRIPCION)
    VALUES ('5422223226', 'Terciado Ranurado 9mm 122x244cm', 'Multiplac', 'Maderas', 10990, 38, 'DISPONIBLE', '/media/productos/terciado-ranurado.webp', '/media/ofertas/terciado-ranurado.webp', 'Terciado ranurado de 9 mm con listones verticales que imitan madera machihembrada. Su cara ranurada entrega un acabado decorativo inmediato, ideal para revestir muros, cielos, tabiques y fondos de muebles en interiores.');

-- 5422223227 · Terciado Estructural 9mm 122x244cm
MERGE INTO PRODUCTOS p
USING (SELECT '5422223227' AS SKU FROM DUAL) s ON (p.SKU = s.SKU)
WHEN NOT MATCHED THEN INSERT (SKU, NOMBRE, MARCA, CATEGORIA, PRECIO_VENTA, STOCK, ESTADO, IMAGEN_URL, IMAGEN_OFERTA_URL, DESCRIPCION)
    VALUES ('5422223227', 'Terciado Estructural 9mm 122x244cm', 'Multiplac', 'Maderas', 15290, 30, 'DISPONIBLE', '/media/productos/terciado-estructural.webp', '/media/ofertas/terciado-estructural.webp', 'Terciado estructural de 9 mm y 122x244 cm, conformado por chapas de madera encoladas en capas cruzadas que le entregan alta resistencia mecánica y estabilidad dimensional. Diseñado para soportar cargas y reforzar estructuras de madera en construcción y habilitación de espacios.');

-- 5422223228 · Guantes de Nitrilo Alta Destreza
MERGE INTO PRODUCTOS p
USING (SELECT '5422223228' AS SKU FROM DUAL) s ON (p.SKU = s.SKU)
WHEN NOT MATCHED THEN INSERT (SKU, NOMBRE, MARCA, CATEGORIA, PRECIO_VENTA, STOCK, ESTADO, IMAGEN_URL, IMAGEN_OFERTA_URL, DESCRIPCION)
    VALUES ('5422223228', 'Guantes de Nitrilo Alta Destreza', 'Steelpro', 'Seguridad', 2090, 80, 'DISPONIBLE', '/media/productos/guantes-nitrilo.webp', '/media/ofertas/guantes-nitrilo.webp', 'Guante de alta destreza para trabajos de precisión. Fabricado en poliéster 13 gauge con palma recubierta de espuma de nitrilo, entrega buen agarre en superficies mojadas y resistencia a hidrocarburos.');

-- 5422223229 · Lentes de Protección Envolventes ANSI Z87.1
MERGE INTO PRODUCTOS p
USING (SELECT '5422223229' AS SKU FROM DUAL) s ON (p.SKU = s.SKU)
WHEN NOT MATCHED THEN INSERT (SKU, NOMBRE, MARCA, CATEGORIA, PRECIO_VENTA, STOCK, ESTADO, IMAGEN_URL, IMAGEN_OFERTA_URL, DESCRIPCION)
    VALUES ('5422223229', 'Lentes de Protección Envolventes ANSI Z87.1', 'Steelpro', 'Seguridad', 2880, 70, 'DISPONIBLE', '/media/productos/lentes-proteccion.webp', '/media/ofertas/lentes-proteccion.webp', 'Lentes envolventes con protección lateral y lente resistente a impactos. Cumplen la norma ANSI Z87.1, ideales para obras y trabajos que exigen certificación de seguridad ocular.');

-- 5422223230 · Chaleco Reflectante Alta Visibilidad
MERGE INTO PRODUCTOS p
USING (SELECT '5422223230' AS SKU FROM DUAL) s ON (p.SKU = s.SKU)
WHEN NOT MATCHED THEN INSERT (SKU, NOMBRE, MARCA, CATEGORIA, PRECIO_VENTA, STOCK, ESTADO, IMAGEN_URL, IMAGEN_OFERTA_URL, DESCRIPCION)
    VALUES ('5422223230', 'Chaleco Reflectante Alta Visibilidad', 'Steelpro', 'Seguridad', 19990, 50, 'DISPONIBLE', '/media/productos/chaleco-reflectante.webp', '/media/ofertas/chaleco-reflectante.webp', 'Chaleco de alta visibilidad con franjas reflectantes, esencial para la seguridad en obras de construcción y trabajos nocturnos. Confeccionado en malla liviana y transpirable.');

-- 5422223231 · Casco de Construcción Estándar EN397
MERGE INTO PRODUCTOS p
USING (SELECT '5422223231' AS SKU FROM DUAL) s ON (p.SKU = s.SKU)
WHEN NOT MATCHED THEN INSERT (SKU, NOMBRE, MARCA, CATEGORIA, PRECIO_VENTA, STOCK, ESTADO, IMAGEN_URL, IMAGEN_OFERTA_URL, DESCRIPCION)
    VALUES ('5422223231', 'Casco de Construcción Estándar EN397', 'Steelpro', 'Seguridad', 4761, 65, 'DISPONIBLE', '/media/productos/casco-construccion.webp', '/media/ofertas/casco-construccion.webp', 'Casco de polietileno de alta densidad con arnés interior ajustable de 6 puntos, banda de sudor y ranuras universales para acoplar orejeras o viseras. Cumple norma EN397 / ANSI Z89.1.');

-- 5422223214 · especificaciones (solo si el producto aún no tiene ninguna)
INSERT INTO PRODUCTO_ESPECIFICACIONES (PRODUCTO_ID, CLAVE, VALOR, ORDEN)
SELECT p.ID, v.CLAVE, v.VALOR, v.ORDEN
FROM PRODUCTOS p
CROSS JOIN (
        SELECT 'Potencia / Voltaje' AS CLAVE, '20V MAX' AS VALOR, 0 AS ORDEN FROM DUAL UNION ALL
        SELECT 'Tipo de Motor', 'Brushless (Sin escobillas)', 1 FROM DUAL UNION ALL
        SELECT 'Velocidad variable', '0 - 550 / 0 - 2,000 RPM', 2 FROM DUAL UNION ALL
        SELECT 'Impactos por minuto', '0 - 34,000 GPM', 3 FROM DUAL UNION ALL
        SELECT 'Mandril', '1/2 pulgada (13 mm) metálico autoajustable', 4 FROM DUAL UNION ALL
        SELECT 'Peso', '1.6 kg', 5 FROM DUAL UNION ALL
        SELECT 'Garantía', '3 años oficial de la marca', 6 FROM DUAL UNION ALL
        SELECT 'Incluye', '2 Baterías 20V 2.0Ah, cargador rápido y maletín rígido', 7 FROM DUAL
    ) v
WHERE p.SKU = '5422223214'
  AND NOT EXISTS (SELECT 1 FROM PRODUCTO_ESPECIFICACIONES e WHERE e.PRODUCTO_ID = p.ID);

-- 5422223215 · especificaciones (solo si el producto aún no tiene ninguna)
INSERT INTO PRODUCTO_ESPECIFICACIONES (PRODUCTO_ID, CLAVE, VALOR, ORDEN)
SELECT p.ID, v.CLAVE, v.VALOR, v.ORDEN
FROM PRODUCTOS p
CROSS JOIN (
        SELECT 'Potencia' AS CLAVE, '1800 Watts' AS VALOR, 0 AS ORDEN FROM DUAL UNION ALL
        SELECT 'Diámetro del disco', '7-1/4" (185 mm)', 1 FROM DUAL UNION ALL
        SELECT 'Velocidad sin carga', '5,800 RPM', 2 FROM DUAL UNION ALL
        SELECT 'Profundidad de corte a 90°', '64 mm', 3 FROM DUAL UNION ALL
        SELECT 'Profundidad de corte a 45°', '43 mm', 4 FROM DUAL UNION ALL
        SELECT 'Peso', '4.2 kg', 5 FROM DUAL UNION ALL
        SELECT 'Garantía', '1 año oficial Makita', 6 FROM DUAL UNION ALL
        SELECT 'Incluye', 'Disco de carburo de 24 dientes, guía paralela y llave', 7 FROM DUAL
    ) v
WHERE p.SKU = '5422223215'
  AND NOT EXISTS (SELECT 1 FROM PRODUCTO_ESPECIFICACIONES e WHERE e.PRODUCTO_ID = p.ID);

-- 5422223216 · especificaciones (solo si el producto aún no tiene ninguna)
INSERT INTO PRODUCTO_ESPECIFICACIONES (PRODUCTO_ID, CLAVE, VALOR, ORDEN)
SELECT p.ID, v.CLAVE, v.VALOR, v.ORDEN
FROM PRODUCTOS p
CROSS JOIN (
        SELECT 'Potencia' AS CLAVE, '850 Watts' AS VALOR, 0 AS ORDEN FROM DUAL UNION ALL
        SELECT 'Diámetro del disco', '4-1/2" (115 mm)', 1 FROM DUAL UNION ALL
        SELECT 'Velocidad', '11,000 RPM', 2 FROM DUAL UNION ALL
        SELECT 'Eje', 'M14 estándar', 3 FROM DUAL UNION ALL
        SELECT 'Peso', '1.9 kg', 4 FROM DUAL UNION ALL
        SELECT 'Garantía', '2 años oficial Bosch', 5 FROM DUAL UNION ALL
        SELECT 'Incluye', 'Empuñadura auxiliar, guarda de protección y llave de brida', 6 FROM DUAL
    ) v
WHERE p.SKU = '5422223216'
  AND NOT EXISTS (SELECT 1 FROM PRODUCTO_ESPECIFICACIONES e WHERE e.PRODUCTO_ID = p.ID);

-- 5422223217 · especificaciones (solo si el producto aún no tiene ninguna)
INSERT INTO PRODUCTO_ESPECIFICACIONES (PRODUCTO_ID, CLAVE, VALOR, ORDEN)
SELECT p.ID, v.CLAVE, v.VALOR, v.ORDEN
FROM PRODUCTOS p
CROSS JOIN (
        SELECT 'Presentación' AS CLAVE, 'Saco de 25 kg' AS VALOR, 0 AS ORDEN FROM DUAL UNION ALL
        SELECT 'Tipo', 'Cemento hidráulico alta resistencia', 1 FROM DUAL UNION ALL
        SELECT 'Norma', 'NCh148 Of.68', 2 FROM DUAL UNION ALL
        SELECT 'Rendimiento estimado', 'Aprox. 0.02 m³ de mortero por saco', 3 FROM DUAL UNION ALL
        SELECT 'Uso recomendado', 'Cimientos, sobrecimientos, losas y albañilería', 4 FROM DUAL UNION ALL
        SELECT 'Almacenamiento', 'Lugar seco y bajo techo sobre tarima', 5 FROM DUAL
    ) v
WHERE p.SKU = '5422223217'
  AND NOT EXISTS (SELECT 1 FROM PRODUCTO_ESPECIFICACIONES e WHERE e.PRODUCTO_ID = p.ID);

-- 5422223218 · especificaciones (solo si el producto aún no tiene ninguna)
INSERT INTO PRODUCTO_ESPECIFICACIONES (PRODUCTO_ID, CLAVE, VALOR, ORDEN)
SELECT p.ID, v.CLAVE, v.VALOR, v.ORDEN
FROM PRODUCTOS p
CROSS JOIN (
        SELECT 'Cantidad de piezas' AS CLAVE, '100 unidades' AS VALOR, 0 AS ORDEN FROM DUAL UNION ALL
        SELECT 'Material brocas metal', 'Acero rápido con recubrimiento de titanio', 1 FROM DUAL UNION ALL
        SELECT 'Brocas para concreto', 'Punta de carburo de tungsteno (3 a 8 mm)', 2 FROM DUAL UNION ALL
        SELECT 'Brocas para madera', 'Puntas centradoras helicoidales (3 a 10 mm)', 3 FROM DUAL UNION ALL
        SELECT 'Estuche', 'Maletín modular de plástico de alto impacto', 4 FROM DUAL UNION ALL
        SELECT 'Garantía', '6 meses', 5 FROM DUAL
    ) v
WHERE p.SKU = '5422223218'
  AND NOT EXISTS (SELECT 1 FROM PRODUCTO_ESPECIFICACIONES e WHERE e.PRODUCTO_ID = p.ID);

-- 5422223219 · especificaciones (solo si el producto aún no tiene ninguna)
INSERT INTO PRODUCTO_ESPECIFICACIONES (PRODUCTO_ID, CLAVE, VALOR, ORDEN)
SELECT p.ID, v.CLAVE, v.VALOR, v.ORDEN
FROM PRODUCTOS p
CROSS JOIN (
        SELECT 'Potencia Nominal' AS CLAVE, '2800 Watts' AS VALOR, 0 AS ORDEN FROM DUAL UNION ALL
        SELECT 'Potencia Máxima', '3000 Watts', 1 FROM DUAL UNION ALL
        SELECT 'Motor', '4 tiempos 7.0 HP OHV monocilíndrico', 2 FROM DUAL UNION ALL
        SELECT 'Capacidad estanque', '15 Litros', 3 FROM DUAL UNION ALL
        SELECT 'Autonomía', 'Aprox. 9 horas a media carga', 4 FROM DUAL UNION ALL
        SELECT 'Voltaje / Frecuencia', '220V / 50 Hz', 5 FROM DUAL UNION ALL
        SELECT 'Sistema de partida', 'Manual retráctil', 6 FROM DUAL UNION ALL
        SELECT 'Garantía', '1 año', 7 FROM DUAL
    ) v
WHERE p.SKU = '5422223219'
  AND NOT EXISTS (SELECT 1 FROM PRODUCTO_ESPECIFICACIONES e WHERE e.PRODUCTO_ID = p.ID);

-- 5422223220 · especificaciones (solo si el producto aún no tiene ninguna)
INSERT INTO PRODUCTO_ESPECIFICACIONES (PRODUCTO_ID, CLAVE, VALOR, ORDEN)
SELECT p.ID, v.CLAVE, v.VALOR, v.ORDEN
FROM PRODUCTOS p
CROSS JOIN (
        SELECT 'Material' AS CLAVE, 'Cuerpo de latón macizo y cuello flexible de silicona' AS VALOR, 0 AS ORDEN FROM DUAL UNION ALL
        SELECT 'Acabado', 'Negro Mate electrostático', 1 FROM DUAL UNION ALL
        SELECT 'Tipo de cierre', 'Cartucho cerámico 35 mm', 2 FROM DUAL UNION ALL
        SELECT 'Conexión', 'Flexibles de acero inoxidable G 1/2"', 3 FROM DUAL UNION ALL
        SELECT 'Presión de trabajo', '1 a 5 bar', 4 FROM DUAL UNION ALL
        SELECT 'Garantía', '5 años', 5 FROM DUAL
    ) v
WHERE p.SKU = '5422223220'
  AND NOT EXISTS (SELECT 1 FROM PRODUCTO_ESPECIFICACIONES e WHERE e.PRODUCTO_ID = p.ID);

-- 5422223221 · especificaciones (solo si el producto aún no tiene ninguna)
INSERT INTO PRODUCTO_ESPECIFICACIONES (PRODUCTO_ID, CLAVE, VALOR, ORDEN)
SELECT p.ID, v.CLAVE, v.VALOR, v.ORDEN
FROM PRODUCTOS p
CROSS JOIN (
        SELECT 'Formato' AS CLAVE, '1 Galón (3.785 Litros)' AS VALOR, 0 AS ORDEN FROM DUAL UNION ALL
        SELECT 'Terminación', 'Semibrillo', 1 FROM DUAL UNION ALL
        SELECT 'Rendimiento', '40 a 45 m² por mano por galón', 2 FROM DUAL UNION ALL
        SELECT 'Tiempo de secado al tacto', '30 a 60 minutos', 3 FROM DUAL UNION ALL
        SELECT 'Repintado', '4 a 6 horas', 4 FROM DUAL UNION ALL
        SELECT 'Base', 'Acuosa (Lavable con agua)', 5 FROM DUAL UNION ALL
        SELECT 'Uso', 'Interior y exterior', 6 FROM DUAL
    ) v
WHERE p.SKU = '5422223221'
  AND NOT EXISTS (SELECT 1 FROM PRODUCTO_ESPECIFICACIONES e WHERE e.PRODUCTO_ID = p.ID);

-- 5422223222 · especificaciones (solo si el producto aún no tiene ninguna)
INSERT INTO PRODUCTO_ESPECIFICACIONES (PRODUCTO_ID, CLAVE, VALOR, ORDEN)
SELECT p.ID, v.CLAVE, v.VALOR, v.ORDEN
FROM PRODUCTOS p
CROSS JOIN (
        SELECT 'Argollas' AS CLAVE, '1 dorsal anticaídas, 2 laterales de posicionamiento, 1 esternal' AS VALOR, 0 AS ORDEN FROM DUAL UNION ALL
        SELECT 'Cinta', 'Poliéster 45 mm resistencia a la tracción > 25 kN', 1 FROM DUAL UNION ALL
        SELECT 'Cabo de vida', 'Incluido con absorbedor de impacto y mosquetones doble seguro', 2 FROM DUAL UNION ALL
        SELECT 'Certificación', 'Norma Chilena NCh 1258 y certificación CESMEC', 3 FROM DUAL UNION ALL
        SELECT 'Capacidad máxima', '140 kg (usuario + herramientas)', 4 FROM DUAL UNION ALL
        SELECT 'Garantía', '1 año contra defectos de fabricación', 5 FROM DUAL
    ) v
WHERE p.SKU = '5422223222'
  AND NOT EXISTS (SELECT 1 FROM PRODUCTO_ESPECIFICACIONES e WHERE e.PRODUCTO_ID = p.ID);

-- 5422223223 · especificaciones (solo si el producto aún no tiene ninguna)
INSERT INTO PRODUCTO_ESPECIFICACIONES (PRODUCTO_ID, CLAVE, VALOR, ORDEN)
SELECT p.ID, v.CLAVE, v.VALOR, v.ORDEN
FROM PRODUCTOS p
CROSS JOIN (
        SELECT 'Medida' AS CLAVE, '6 x 1-1/4" (Aprox. 32 mm longitud)' AS VALOR, 0 AS ORDEN FROM DUAL UNION ALL
        SELECT 'Cabeza', 'Trompeta ranura Phillips N°2', 1 FROM DUAL UNION ALL
        SELECT 'Punta', 'Broca autoperforante', 2 FROM DUAL UNION ALL
        SELECT 'Recubrimiento', 'Fosfatizado negro anticorrosivo', 3 FROM DUAL UNION ALL
        SELECT 'Presentación', 'Caja de 1000 unidades', 4 FROM DUAL UNION ALL
        SELECT 'Uso recomendado', 'Tabiquería, cielos falsos y perfiles Metalcon', 5 FROM DUAL
    ) v
WHERE p.SKU = '5422223223'
  AND NOT EXISTS (SELECT 1 FROM PRODUCTO_ESPECIFICACIONES e WHERE e.PRODUCTO_ID = p.ID);

-- 5422223224 · especificaciones (solo si el producto aún no tiene ninguna)
INSERT INTO PRODUCTO_ESPECIFICACIONES (PRODUCTO_ID, CLAVE, VALOR, ORDEN)
SELECT p.ID, v.CLAVE, v.VALOR, v.ORDEN
FROM PRODUCTOS p
CROSS JOIN (
        SELECT 'Espesor' AS CLAVE, '15 mm' AS VALOR, 0 AS ORDEN FROM DUAL UNION ALL
        SELECT 'Medidas', '60 x 120 cm', 1 FROM DUAL UNION ALL
        SELECT 'Color', 'Café', 2 FROM DUAL UNION ALL
        SELECT 'Terminación', 'Melamina', 3 FROM DUAL UNION ALL
        SELECT 'Cara', 'Doble cara melamínica', 4 FROM DUAL UNION ALL
        SELECT 'Uso recomendado', 'Mueblería, closet y superficies de trabajo', 5 FROM DUAL UNION ALL
        SELECT 'Presentación', '1 cubierta', 6 FROM DUAL
    ) v
WHERE p.SKU = '5422223224'
  AND NOT EXISTS (SELECT 1 FROM PRODUCTO_ESPECIFICACIONES e WHERE e.PRODUCTO_ID = p.ID);

-- 5422223225 · especificaciones (solo si el producto aún no tiene ninguna)
INSERT INTO PRODUCTO_ESPECIFICACIONES (PRODUCTO_ID, CLAVE, VALOR, ORDEN)
SELECT p.ID, v.CLAVE, v.VALOR, v.ORDEN
FROM PRODUCTOS p
CROSS JOIN (
        SELECT 'Espesor' AS CLAVE, '8 mm' AS VALOR, 0 AS ORDEN FROM DUAL UNION ALL
        SELECT 'Medidas', '122 x 244 cm', 1 FROM DUAL UNION ALL
        SELECT 'Tipo', 'OSB (Oriented Strand Board)', 2 FROM DUAL UNION ALL
        SELECT 'Superficie', 'Aspecto natural de viruta', 3 FROM DUAL UNION ALL
        SELECT 'Certificación', 'Baja emisión de formaldehído (E1)', 4 FROM DUAL UNION ALL
        SELECT 'Uso recomendado', 'Construcción, revestimiento y mobiliario', 5 FROM DUAL UNION ALL
        SELECT 'Presentación', '1 tablero', 6 FROM DUAL
    ) v
WHERE p.SKU = '5422223225'
  AND NOT EXISTS (SELECT 1 FROM PRODUCTO_ESPECIFICACIONES e WHERE e.PRODUCTO_ID = p.ID);

-- 5422223226 · especificaciones (solo si el producto aún no tiene ninguna)
INSERT INTO PRODUCTO_ESPECIFICACIONES (PRODUCTO_ID, CLAVE, VALOR, ORDEN)
SELECT p.ID, v.CLAVE, v.VALOR, v.ORDEN
FROM PRODUCTOS p
CROSS JOIN (
        SELECT 'Espesor' AS CLAVE, '9 mm' AS VALOR, 0 AS ORDEN FROM DUAL UNION ALL
        SELECT 'Medidas', '122 x 244 cm', 1 FROM DUAL UNION ALL
        SELECT 'Tipo', 'Terciado ranurado', 2 FROM DUAL UNION ALL
        SELECT 'Ranurado', 'Vertical decorativo tipo machihembrado', 3 FROM DUAL UNION ALL
        SELECT 'Terminación', 'Natural, lista para barnizar o pintar', 4 FROM DUAL UNION ALL
        SELECT 'Uso recomendado', 'Revestimientos decorativos e interiores', 5 FROM DUAL UNION ALL
        SELECT 'Presentación', '1 tablero', 6 FROM DUAL
    ) v
WHERE p.SKU = '5422223226'
  AND NOT EXISTS (SELECT 1 FROM PRODUCTO_ESPECIFICACIONES e WHERE e.PRODUCTO_ID = p.ID);

-- 5422223227 · especificaciones (solo si el producto aún no tiene ninguna)
INSERT INTO PRODUCTO_ESPECIFICACIONES (PRODUCTO_ID, CLAVE, VALOR, ORDEN)
SELECT p.ID, v.CLAVE, v.VALOR, v.ORDEN
FROM PRODUCTOS p
CROSS JOIN (
        SELECT 'Espesor' AS CLAVE, '9 mm' AS VALOR, 0 AS ORDEN FROM DUAL UNION ALL
        SELECT 'Medidas', '122 x 244 cm', 1 FROM DUAL UNION ALL
        SELECT 'Tipo', 'Terciado estructural (plywood)', 2 FROM DUAL UNION ALL
        SELECT 'Resistencia', 'Alta resistencia a cargas mecánicas', 3 FROM DUAL UNION ALL
        SELECT 'Encolado', 'Cola estructural resistente a la humedad', 4 FROM DUAL UNION ALL
        SELECT 'Uso recomendado', 'Estructuras, refuerzos y habilitación de espacios', 5 FROM DUAL UNION ALL
        SELECT 'Presentación', '1 tablero', 6 FROM DUAL
    ) v
WHERE p.SKU = '5422223227'
  AND NOT EXISTS (SELECT 1 FROM PRODUCTO_ESPECIFICACIONES e WHERE e.PRODUCTO_ID = p.ID);

-- 5422223228 · especificaciones (solo si el producto aún no tiene ninguna)
INSERT INTO PRODUCTO_ESPECIFICACIONES (PRODUCTO_ID, CLAVE, VALOR, ORDEN)
SELECT p.ID, v.CLAVE, v.VALOR, v.ORDEN
FROM PRODUCTOS p
CROSS JOIN (
        SELECT 'Material' AS CLAVE, 'Poliéster 13 gauge' AS VALOR, 0 AS ORDEN FROM DUAL UNION ALL
        SELECT 'Recubrimiento', 'Espuma de nitrilo en la palma', 1 FROM DUAL UNION ALL
        SELECT 'Agarre', 'Alto desempeño en seco y mojado', 2 FROM DUAL UNION ALL
        SELECT 'Resistencia', 'Aceites e hidrocarburos', 3 FROM DUAL UNION ALL
        SELECT 'Uso recomendado', 'Trabajos de precisión, manipulación y mantenimiento', 4 FROM DUAL UNION ALL
        SELECT 'Presentación', '1 par', 5 FROM DUAL
    ) v
WHERE p.SKU = '5422223228'
  AND NOT EXISTS (SELECT 1 FROM PRODUCTO_ESPECIFICACIONES e WHERE e.PRODUCTO_ID = p.ID);

-- 5422223229 · especificaciones (solo si el producto aún no tiene ninguna)
INSERT INTO PRODUCTO_ESPECIFICACIONES (PRODUCTO_ID, CLAVE, VALOR, ORDEN)
SELECT p.ID, v.CLAVE, v.VALOR, v.ORDEN
FROM PRODUCTOS p
CROSS JOIN (
        SELECT 'Tipo' AS CLAVE, 'Envolvente con protección lateral' AS VALOR, 0 AS ORDEN FROM DUAL UNION ALL
        SELECT 'Lente', 'Policarbonato resistente a impactos', 1 FROM DUAL UNION ALL
        SELECT 'Certificación', 'ANSI Z87.1', 2 FROM DUAL UNION ALL
        SELECT 'Uso recomendado', 'Construcción, taller e industria', 3 FROM DUAL UNION ALL
        SELECT 'Presentación', '1 unidad', 4 FROM DUAL
    ) v
WHERE p.SKU = '5422223229'
  AND NOT EXISTS (SELECT 1 FROM PRODUCTO_ESPECIFICACIONES e WHERE e.PRODUCTO_ID = p.ID);

-- 5422223230 · especificaciones (solo si el producto aún no tiene ninguna)
INSERT INTO PRODUCTO_ESPECIFICACIONES (PRODUCTO_ID, CLAVE, VALOR, ORDEN)
SELECT p.ID, v.CLAVE, v.VALOR, v.ORDEN
FROM PRODUCTOS p
CROSS JOIN (
        SELECT 'Color' AS CLAVE, 'Amarillo' AS VALOR, 0 AS ORDEN FROM DUAL UNION ALL
        SELECT 'Material', 'Malla de poliéster', 1 FROM DUAL UNION ALL
        SELECT 'Franjas', 'Reflectantes de alta visibilidad', 2 FROM DUAL UNION ALL
        SELECT 'Tallas', 'M / L / XL', 3 FROM DUAL UNION ALL
        SELECT 'Uso recomendado', 'Obras, tránsito y trabajos nocturnos', 4 FROM DUAL UNION ALL
        SELECT 'Presentación', '1 unidad', 5 FROM DUAL
    ) v
WHERE p.SKU = '5422223230'
  AND NOT EXISTS (SELECT 1 FROM PRODUCTO_ESPECIFICACIONES e WHERE e.PRODUCTO_ID = p.ID);

-- 5422223231 · especificaciones (solo si el producto aún no tiene ninguna)
INSERT INTO PRODUCTO_ESPECIFICACIONES (PRODUCTO_ID, CLAVE, VALOR, ORDEN)
SELECT p.ID, v.CLAVE, v.VALOR, v.ORDEN
FROM PRODUCTOS p
CROSS JOIN (
        SELECT 'Material' AS CLAVE, 'Polietileno de alta densidad' AS VALOR, 0 AS ORDEN FROM DUAL UNION ALL
        SELECT 'Arnés', 'Ajustable de 6 puntos', 1 FROM DUAL UNION ALL
        SELECT 'Norma', 'EN397 / ANSI Z89.1', 2 FROM DUAL UNION ALL
        SELECT 'Ranuras', 'Universales para orejeras o viseras', 3 FROM DUAL UNION ALL
        SELECT 'Color', 'Amarillo', 4 FROM DUAL UNION ALL
        SELECT 'Presentación', '1 unidad', 5 FROM DUAL
    ) v
WHERE p.SKU = '5422223231'
  AND NOT EXISTS (SELECT 1 FROM PRODUCTO_ESPECIFICACIONES e WHERE e.PRODUCTO_ID = p.ID);
