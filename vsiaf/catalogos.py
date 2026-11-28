import copy


CAMPOS = {'oficinas': ('id', 'nombre'), 'responsables': ('id', 'nombre', 'cargo', 'oficinaId')}


def normalizar(tipo, registro):
    if registro is None:
        return None
    return {k: registro.get(k) for k in CAMPOS[tipo]}


class Catalogos:
    def __init__(self, conexion):
        self.conexion = conexion
        self.estado = conexion.estado.setdefault('catalogos', {})

    def leer(self, nombre, fuente):
        if nombre == 'FoxPro':
            return fuente.enviar({'accion': 'catalogos'})
        return {tipo: fuente.pedir('/api/' + tipo + '?interno=true') for tipo in CAMPOS}

    def guardar(self, nombre, fuente, tipo, objetivo, anterior):
        id = objetivo['id'] if objetivo is not None else anterior['id']
        if nombre == 'FoxPro':
            fuente.enviar({'accion': 'eliminar-catalogo' if objetivo is None else 'guardar-catalogo', 'tipo': tipo, 'registro': objetivo,
                           'anterior': normalizar(tipo, anterior)})
        else:
            ruta = '/api/' + tipo
            if anterior is None:
                fuente.pedir(ruta, 'POST', objetivo)
            else:
                if 'revision' not in anterior:
                    raise RuntimeError('El backend debe actualizarse para conectar los catálogos')
                fuente.pedir(ruta + '/' + str(id), 'DELETE' if objetivo is None else 'PUT', objetivo, revision=anterior['revision'])
        actualizado = next((r for r in self.leer(nombre, fuente)[tipo] if r['id'] == id), None)
        if normalizar(tipo, actualizado) != objetivo:
            raise RuntimeError('El catálogo no confirmó los datos guardados')
        return actualizado

    def paso(self):
        self.pendientes = []
        fotos = {}
        for nombre, fuente in self.conexion.fuentes.items():
            try:
                datos = self.leer(nombre, fuente)
                fotos[nombre] = {tipo: {str(r['id']): r for r in datos[tipo]} for tipo in CAMPOS}
            except Exception as error:
                self.conexion.aviso('catalogos-' + nombre, 'Catálogos en ' + nombre + ': ' + str(error))
        if 'FoxPro' not in fotos:
            return False
        completo = True
        for tipo in CAMPOS:
            estado = self.estado.setdefault(tipo, {'deseado': {}, 'visto': {}})
            ids = set(estado['deseado'])
            for foto in fotos.values():
                ids.update(foto[tipo])
            for id in sorted(ids, key=int):
                clave = tipo + ':' + id
                cambios = []
                for nombre, foto in fotos.items():
                    actual = normalizar(tipo, foto[tipo].get(id))
                    previo = estado['visto'].get(nombre, {}).get(id)
                    if nombre not in estado['visto'] and id in estado['deseado']:
                        if actual is not None and actual != estado['deseado'][id]:
                            cambios.extend([actual, estado['deseado'][id]])
                    elif actual != previo:
                        cambios.append(actual)
                if cambios and any(c != cambios[0] for c in cambios):
                    self.conexion.aviso(clave, clave + ': cambios simultáneos distintos en un catálogo; se conservaron las versiones para revisarlas')
                    completo = False
                    continue
                if cambios:
                    estado['deseado'][id] = copy.deepcopy(cambios[0])
                    self.conexion.guardar_estado()
                if id not in estado['deseado']:
                    continue
                objetivo = estado['deseado'][id]
                if objetivo is None:
                    for nombre, foto in fotos.items():
                        actual = foto[tipo].get(id)
                        if actual is not None:
                            self.pendientes.append((tipo, id, nombre, actual))
                        else:
                            estado['visto'].setdefault(nombre, {})[id] = None
                    self.conexion.guardar_estado()
                    continue
                for nombre, foto in fotos.items():
                    actual = foto[tipo].get(id)
                    try:
                        if normalizar(tipo, actual) != objetivo:
                            foto[tipo][id] = self.guardar(nombre, self.conexion.fuentes[nombre], tipo, objetivo, actual)
                            self.conexion.aviso(clave + nombre, clave + ': guardado en ' + nombre)
                        estado['visto'].setdefault(nombre, {})[id] = copy.deepcopy(objetivo)
                        self.conexion.guardar_estado()
                    except Exception as error:
                        completo = False
                        self.conexion.aviso(clave + nombre, clave + ' en ' + nombre + ': ' + str(error))
        return completo

    def referencia_eliminada(self, activo):
        return activo is not None and any(
            str(activo[campo]) in self.estado.get(tipo, {}).get('deseado', {}) and
            self.estado[tipo]['deseado'][str(activo[campo])] is None
            for tipo, campo in (('oficinas', 'oficinaId'), ('responsables', 'responsableId')))

    def eliminar_pendientes(self):
        for tipo, id, nombre, anterior in sorted(self.pendientes, key=lambda p: p[0] == 'oficinas'):
            clave = tipo + ':' + id + nombre
            try:
                self.guardar(nombre, self.conexion.fuentes[nombre], tipo, None, anterior)
                self.estado[tipo]['visto'].setdefault(nombre, {})[id] = None
                self.conexion.guardar_estado()
                self.conexion.aviso(clave, tipo + ':' + id + ': eliminado en ' + nombre)
            except Exception as error:
                self.conexion.aviso(clave, tipo + ':' + id + ' en ' + nombre + ': ' + str(error))
