INSERT INTO segundo_parcial.oficina (id, nombre) VALUES (1, 'Laboratorio de Sistemas')
ON CONFLICT (id) DO NOTHING;

INSERT INTO segundo_parcial.responsable (id, nombre, cargo) VALUES (1, 'Ana Pérez', 'Encargada de laboratorio')
ON CONFLICT (id) DO UPDATE SET cargo = EXCLUDED.cargo WHERE responsable.cargo IS NULL;

INSERT INTO segundo_parcial.responsable (id, nombre, cargo) VALUES (2, 'Luis Rojas', 'Encargado de laboratorio')
ON CONFLICT (id) DO UPDATE SET cargo = EXCLUDED.cargo WHERE responsable.cargo IS NULL;

INSERT INTO segundo_parcial.activo (
    codigo, descripcion, oficina_id, responsable_id, codigo_adicional, fecha_incorporacion, grupo, auxiliar, estado_activo, estado_registro, observaciones, costo_inicial, depreciacion_inicial, vida_util, depreciar, actualizar, rube, organismo_financiador, numero_convenio, entidad, unidad, origen_vsiaf
) VALUES (
    'PROY-001', 'Proyector de aula', 1, 1, '', '2011-11-13', 'MUEBLES Y ENSERES DE OFICINA', 'PROYECTOR', 'Bueno', 'ELABORADO', 'Registro ficticio para práctica SIS-414', 3000.0, 0.0, 8.0, TRUE, TRUE, '', '', '', '0143', '1', TRUE
)
ON CONFLICT (codigo) DO UPDATE SET
    codigo_adicional = EXCLUDED.codigo_adicional,
    fecha_incorporacion = EXCLUDED.fecha_incorporacion,
    grupo = EXCLUDED.grupo,
    auxiliar = EXCLUDED.auxiliar,
    estado_activo = EXCLUDED.estado_activo,
    estado_registro = EXCLUDED.estado_registro,
    observaciones = EXCLUDED.observaciones,
    costo_inicial = EXCLUDED.costo_inicial,
    depreciacion_inicial = EXCLUDED.depreciacion_inicial,
    vida_util = EXCLUDED.vida_util,
    depreciar = EXCLUDED.depreciar,
    actualizar = EXCLUDED.actualizar,
    rube = EXCLUDED.rube,
    organismo_financiador = EXCLUDED.organismo_financiador,
    numero_convenio = EXCLUDED.numero_convenio,
    entidad = EXCLUDED.entidad,
    unidad = EXCLUDED.unidad,
    origen_vsiaf = EXCLUDED.origen_vsiaf
WHERE activo.fecha_incorporacion IS NULL;

INSERT INTO segundo_parcial.activo (
    codigo, descripcion, oficina_id, responsable_id, codigo_adicional, fecha_incorporacion, grupo, auxiliar, estado_activo, estado_registro, observaciones, costo_inicial, depreciacion_inicial, vida_util, depreciar, actualizar, rube, organismo_financiador, numero_convenio, entidad, unidad, origen_vsiaf
) VALUES (
    'PROY-002', 'Proyector de aula', 1, 2, '', '2011-11-13', 'MUEBLES Y ENSERES DE OFICINA', 'PROYECTOR', 'Bueno', 'ELABORADO', 'Registro ficticio para práctica SIS-414', 3000.0, 0.0, 8.0, TRUE, TRUE, '', '', '', '0143', '1', TRUE
)
ON CONFLICT (codigo) DO UPDATE SET
    codigo_adicional = EXCLUDED.codigo_adicional,
    fecha_incorporacion = EXCLUDED.fecha_incorporacion,
    grupo = EXCLUDED.grupo,
    auxiliar = EXCLUDED.auxiliar,
    estado_activo = EXCLUDED.estado_activo,
    estado_registro = EXCLUDED.estado_registro,
    observaciones = EXCLUDED.observaciones,
    costo_inicial = EXCLUDED.costo_inicial,
    depreciacion_inicial = EXCLUDED.depreciacion_inicial,
    vida_util = EXCLUDED.vida_util,
    depreciar = EXCLUDED.depreciar,
    actualizar = EXCLUDED.actualizar,
    rube = EXCLUDED.rube,
    organismo_financiador = EXCLUDED.organismo_financiador,
    numero_convenio = EXCLUDED.numero_convenio,
    entidad = EXCLUDED.entidad,
    unidad = EXCLUDED.unidad,
    origen_vsiaf = EXCLUDED.origen_vsiaf
WHERE activo.fecha_incorporacion IS NULL;
