import argparse
import copy
import json
import os
from pathlib import Path
import subprocess
import sys
import time
import urllib.error
import urllib.request

CAMPOS = ('codigo descripcion oficinaId responsableId codigoAdicional fechaIncorporacion grupo auxiliar '
          'estadoActivo estadoRegistro observaciones costoInicial depreciacionInicial vidaUtil depreciar '
          'actualizar rube organismoFinanciador numeroConvenio entidad unidad oficina responsable cargo').split()
NUMEROS = {'costoInicial', 'depreciacionInicial', 'vidaUtil'}


def normalizar(activo):
    if activo is None:
        return None
    resultado = {}
    for campo in CAMPOS:
        valor = activo.get(campo)
        if campo in NUMEROS:
            valor = round(float(valor or 0), 2)
        elif campo in ('oficinaId', 'responsableId'):
            valor = int(valor or 0)
        elif campo in ('depreciar', 'actualizar'):
            valor = bool(valor)
        else:
            valor = str(valor or '').strip()
        resultado[campo] = valor
    return resultado


def firma(activo):
    return json.dumps(normalizar(activo), sort_keys=True, ensure_ascii=False)


class FoxPro:
    def __init__(self, directorio):
        powershell = Path(os.environ['WINDIR']) / 'SysWOW64/WindowsPowerShell/v1.0/powershell.exe'
        self.proceso = subprocess.Popen(
            [str(powershell), '-NoProfile', '-NonInteractive', '-File', str(Path(__file__).with_name('tablas.ps1')),
             '-Directorio', str(directorio)], stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
            text=True, encoding='utf-8', creationflags=subprocess.CREATE_NO_WINDOW)

    def enviar(self, peticion):
        self.proceso.stdin.write(json.dumps(peticion, ensure_ascii=False) + '\n')
        self.proceso.stdin.flush()
        linea = self.proceso.stdout.readline()
        if not linea:
            raise RuntimeError('El controlador de Microsoft FoxPro se cerró: ' + self.proceso.stderr.read()[-1000:])
        respuesta = json.loads(linea)
        if not respuesta['ok']:
            raise RuntimeError(respuesta['error'])
        return respuesta['resultado']

    def leer(self):
        return {a['codigo']: a for a in self.enviar({'accion': 'leer'})}

    def guardar(self, codigo, activo, anterior):
        self.enviar({'accion': 'guardar', 'codigo': codigo, 'activo': activo, 'anterior': anterior})
        resultado = self.leer().get(codigo)
        if firma(resultado) != firma(activo):
            raise RuntimeError('FoxPro no confirmó los datos guardados de ' + codigo)
        return resultado

    def cerrar(self):
        self.proceso.stdin.close()
        try:
            self.proceso.wait(timeout=5)
        except subprocess.TimeoutExpired:
            self.proceso.terminate()


class Api:
    def __init__(self, url):
        self.url = url.rstrip('/')

    def pedir(self, ruta, metodo='GET', cuerpo=None, revision=None):
        encabezados = {'Accept': 'application/json'}
        datos = None
        if cuerpo is not None:
            datos = json.dumps(cuerpo, ensure_ascii=False).encode('utf-8')
            encabezados['Content-Type'] = 'application/json; charset=utf-8'
        if revision is not None:
            encabezados['If-Match'] = '"' + str(revision) + '"'
        peticion = urllib.request.Request(self.url + ruta, data=datos, headers=encabezados, method=metodo)
        try:
            with urllib.request.urlopen(peticion, timeout=12) as respuesta:
                texto = respuesta.read()
                return json.loads(texto) if texto else None
        except urllib.error.HTTPError as error:
            detalle = error.read().decode('utf-8', errors='replace')
            raise RuntimeError(f'{metodo} {ruta}: HTTP {error.code}: {detalle[:500]}') from error

    def leer(self):
        lista = self.pedir('/api/activos')
        if not isinstance(lista, list) or any('revision' not in a for a in lista):
            raise RuntimeError('El backend todavía no tiene la versión CRUD')
        return {a['codigo']: a for a in lista}

    def catalogo(self, tipo, deseado):
        actual = next((c for c in self.pedir('/api/' + tipo) if c['id'] == deseado['id']), None)
        if actual is None:
            self.pedir('/api/' + tipo, 'POST', deseado)
        elif actual != deseado:
            self.pedir('/api/' + tipo + '/' + str(deseado['id']), 'PUT', deseado)

    def guardar(self, codigo, activo, anterior):
        ruta = '/api/activos/' + urllib.parse.quote(codigo, safe='')
        if activo is None:
            self.pedir(ruta, 'DELETE', revision=anterior['revision'])
            return None
        self.catalogo('oficinas', {'id': activo['oficinaId'], 'nombre': activo['oficina']})
        self.catalogo('responsables', {'id': activo['responsableId'], 'nombre': activo['responsable'], 'cargo': activo['cargo']})
        cuerpo = {k: v for k, v in activo.items() if k in CAMPOS and k not in ('oficina', 'responsable', 'cargo')}
        if anterior is None:
            respuesta = self.pedir('/api/activos', 'POST', cuerpo)
        else:
            nuevo = self.pedir(ruta)
            sin_catalogos = lambda a: {k: v for k, v in normalizar(a).items() if k not in ('oficina', 'responsable', 'cargo')}
            if sin_catalogos(nuevo) != sin_catalogos(anterior):
                raise RuntimeError('El activo cambió durante la conexión; se volverá a consultar')
            respuesta = self.pedir(ruta, 'PUT', cuerpo, revision=nuevo['revision'])
        if firma(respuesta) != firma(activo):
            raise RuntimeError('El backend no confirmó los datos de ' + codigo)
        return respuesta


class Conexion:
    def __init__(self, fuentes, archivo):
        self.fuentes = fuentes
        self.archivo = Path(archivo)
        self.estado = json.loads(self.archivo.read_text(encoding='utf-8')) if self.archivo.exists() else {'deseado': {}, 'visto': {}}
        self.ultimo_aviso = {}

    def guardar_estado(self):
        self.archivo.parent.mkdir(parents=True, exist_ok=True)
        temporal = self.archivo.with_suffix('.nuevo')
        temporal.write_text(json.dumps(self.estado, ensure_ascii=False, indent=2), encoding='utf-8')
        temporal.replace(self.archivo)

    def aviso(self, clave, mensaje):
        if self.ultimo_aviso.get(clave) != mensaje:
            print(time.strftime('%H:%M:%S'), mensaje, flush=True)
            self.ultimo_aviso[clave] = mensaje

    def paso(self):
        fotos = {}
        for nombre, fuente in self.fuentes.items():
            try:
                fotos[nombre] = fuente.leer()
                self.ultimo_aviso.pop(nombre, None)
            except Exception as error:
                self.aviso(nombre, nombre + ': ' + str(error))
        if 'FoxPro' not in fotos:
            return
        codigos = set(self.estado['deseado'])
        for foto in fotos.values():
            codigos.update(foto)
        for visto in self.estado['visto'].values():
            codigos.update(visto)
        for codigo in sorted(codigos):
            cambios = {}
            for nombre, foto in fotos.items():
                actual = normalizar(foto.get(codigo))
                previo = self.estado['visto'].get(nombre, {}).get(codigo)
                if nombre not in self.estado['visto'] and codigo in self.estado['deseado']:
                    if actual != self.estado['deseado'][codigo]:
                        self.aviso(codigo, codigo + ': la nueva conexión tiene otros datos; no se reemplazó el registro')
                        cambios = {'conflicto-nuevo': actual, 'conflicto-previo': self.estado['deseado'][codigo]}
                        break
                elif actual != previo:
                    cambios[nombre] = actual
            distintas = {firma(a) for a in cambios.values()}
            if len(distintas) > 1:
                self.aviso(codigo, codigo + ': cambios simultáneos distintos; se conservaron ambas versiones')
                continue
            if cambios:
                self.estado['deseado'][codigo] = next(iter(cambios.values()))
                self.guardar_estado()
            if codigo not in self.estado['deseado']:
                continue
            objetivo = self.estado['deseado'][codigo]
            for nombre, foto in fotos.items():
                actual = foto.get(codigo)
                try:
                    if firma(actual) != firma(objetivo):
                        actualizado = self.fuentes[nombre].guardar(codigo, objetivo, actual)
                        if actualizado is None:
                            foto.pop(codigo, None)
                        else:
                            foto[codigo] = actualizado
                        self.aviso(codigo + nombre, codigo + ': ' + ('eliminado' if objetivo is None else 'guardado') + ' en ' + nombre)
                    self.estado['visto'].setdefault(nombre, {})[codigo] = copy.deepcopy(objetivo)
                    self.guardar_estado()
                except Exception as error:
                    self.aviso(codigo + nombre, codigo + ' en ' + nombre + ': ' + str(error))


def main():
    p = argparse.ArgumentParser()
    p.add_argument('--dbfs', default='C:/vsiaf/dbfs')
    p.add_argument('--api', action='append')
    p.add_argument('--estado', default=str(Path(os.environ['LOCALAPPDATA']) / 'SIS414/segundo-parcial/conexion.json'))
    p.add_argument('--una-vez', action='store_true')
    args = p.parse_args()
    import msvcrt
    candado = Path(args.estado).with_suffix('.lock')
    candado.parent.mkdir(parents=True, exist_ok=True)
    with candado.open('a+b') as bloqueo:
        bloqueo.seek(0)
        try:
            msvcrt.locking(bloqueo.fileno(), msvcrt.LK_NBLCK, 1)
        except OSError:
            print('La conexión de VSIAF ya está funcionando', flush=True)
            return
        fox = FoxPro(args.dbfs)
        urls = args.api or ['http://localhost:8088', 'https://twodo-parcial-sis414.onrender.com']
        fuentes = {'FoxPro': fox, **{url: Api(url) for url in urls}}
        conexion = Conexion(fuentes, args.estado)
        try:
            while True:
                conexion.paso()
                if args.una_vez:
                    break
                time.sleep(2)
        finally:
            fox.cerrar()


if __name__ == '__main__':
    main()
