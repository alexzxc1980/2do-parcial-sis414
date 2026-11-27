CREATE SCHEMA IF NOT EXISTS segundo_parcial;

CREATE TABLE IF NOT EXISTS segundo_parcial.oficina (
    id INTEGER PRIMARY KEY,
    nombre VARCHAR(65) NOT NULL
);

CREATE TABLE IF NOT EXISTS segundo_parcial.responsable (
    id INTEGER PRIMARY KEY,
    nombre VARCHAR(35) NOT NULL
);

CREATE TABLE IF NOT EXISTS segundo_parcial.activo (
    codigo VARCHAR(15) PRIMARY KEY,
    descripcion VARCHAR(150) NOT NULL,
    oficina_id INTEGER NOT NULL REFERENCES segundo_parcial.oficina(id),
    responsable_id INTEGER NOT NULL REFERENCES segundo_parcial.responsable(id)
);

ALTER TABLE segundo_parcial.responsable ADD COLUMN IF NOT EXISTS cargo VARCHAR(35);

ALTER TABLE segundo_parcial.activo
    ADD COLUMN IF NOT EXISTS codigo_adicional VARCHAR(15),
    ADD COLUMN IF NOT EXISTS fecha_incorporacion DATE,
    ADD COLUMN IF NOT EXISTS grupo VARCHAR(50),
    ADD COLUMN IF NOT EXISTS auxiliar VARCHAR(50),
    ADD COLUMN IF NOT EXISTS estado_activo VARCHAR(10),
    ADD COLUMN IF NOT EXISTS estado_registro VARCHAR(20),
    ADD COLUMN IF NOT EXISTS observaciones TEXT,
    ADD COLUMN IF NOT EXISTS costo_inicial NUMERIC(15,2),
    ADD COLUMN IF NOT EXISTS depreciacion_inicial NUMERIC(15,2),
    ADD COLUMN IF NOT EXISTS vida_util NUMERIC(6,2),
    ADD COLUMN IF NOT EXISTS depreciar BOOLEAN,
    ADD COLUMN IF NOT EXISTS actualizar BOOLEAN,
    ADD COLUMN IF NOT EXISTS rube VARCHAR(15),
    ADD COLUMN IF NOT EXISTS organismo_financiador VARCHAR(10),
    ADD COLUMN IF NOT EXISTS numero_convenio VARCHAR(10),
    ADD COLUMN IF NOT EXISTS entidad VARCHAR(4),
    ADD COLUMN IF NOT EXISTS unidad VARCHAR(5),
    ADD COLUMN IF NOT EXISTS origen_vsiaf BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE segundo_parcial.activo ALTER COLUMN oficina_id DROP NOT NULL;
ALTER TABLE segundo_parcial.activo ALTER COLUMN responsable_id DROP NOT NULL;
