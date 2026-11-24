INSERT INTO segundo_parcial.oficina (id, nombre) VALUES (1, 'Laboratorio de Sistemas')
ON CONFLICT (id) DO NOTHING;

INSERT INTO segundo_parcial.responsable (id, nombre) VALUES (1, 'Ana Pérez')
ON CONFLICT (id) DO NOTHING;

INSERT INTO segundo_parcial.activo (codigo, descripcion, oficina_id, responsable_id)
VALUES ('PROY-001', 'Proyector de aula', 1, 1)
ON CONFLICT (codigo) DO NOTHING;
