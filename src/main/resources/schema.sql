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
