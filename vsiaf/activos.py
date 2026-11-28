import argparse
import copy
from concurrent.futures import ThreadPoolExecutor
from datetime import date, datetime
from decimal import Decimal, InvalidOperation
import json
import os
from pathlib import Path
import re
import time
import tkinter as tk
from tkinter import ttk, messagebox
from conectar import FoxPro, normalizar


class Activos:
    columnas = ('codigo', 'descripcion', 'costoInicial', 'vidaUtil', 'fechaIncorporacion', 'estadoActivo', 'responsable')

    def __init__(self, raiz, directorio, configuracion):
        self.raiz = raiz
        self.directorio = directorio
        self.configuracion = Path(configuracion)
        self.plantilla = json.loads(self.configuracion.read_text(encoding='utf-8')) if self.configuracion.exists() else None
        self.registros = {}
        self.responsables = []
        self.fox = None
        self.executor = ThreadPoolExecutor(max_workers=1)
        self.trabajo = None
        self.dialogo = None
        self.siguiente = 0
        self.cerrado = False
        raiz.title('Activos fijos - SIS-414')
        raiz.geometry('1100x570')
        raiz.minsize(950, 450)
        raiz.configure(background='#f3f5f8')
        estilo = ttk.Style()
        estilo.theme_use('vista')
        estilo.configure('Treeview', rowheight=34, font=('Segoe UI', 11))
        estilo.configure('Treeview.Heading', font=('Segoe UI', 10, 'bold'))
        estilo.configure('TButton', font=('Segoe UI', 11), padding=(12, 8))
        marco = ttk.Frame(raiz, padding=24)
        marco.pack(fill='both', expand=True)
        ttk.Label(marco, text='Activos fijos', font=('Segoe UI', 24, 'bold')).pack(anchor='w')
        ttk.Label(marco, text='Añade, modifica y consulta tus activos.', font=('Segoe UI', 11)).pack(anchor='w', pady=(4, 18))
        barra = ttk.Frame(marco)
        barra.pack(fill='x', pady=(0, 16))
        self.botones = []
        for texto, accion in [('Añadir', lambda: self.editar(True)), ('Modificar', lambda: self.editar(False)),
                              ('Eliminar', self.eliminar), ('Actualizar lista', self.actualizar)]:
            b = ttk.Button(barra, text=texto, command=accion)
            b.pack(side='left', padx=(0, 10))
            self.botones.append(b)
        tabla = ttk.Frame(marco)
        tabla.pack(fill='both', expand=True)
        self.lista = ttk.Treeview(tabla, columns=self.columnas, show='headings', selectmode='browse', height=9)
        titulos = ['Código', 'Descripción', 'Costo (Bs)', 'Vida útil (años)', 'Fecha', 'Estado', 'Responsable']
        anchos = [100, 240, 110, 110, 110, 90, 175]
        for campo, titulo, ancho in zip(self.columnas, titulos, anchos):
            self.lista.heading(campo, text=titulo)
            self.lista.column(campo, width=ancho, minwidth=80, stretch=campo == 'descripcion', anchor='w')
        scroll = ttk.Scrollbar(tabla, orient='vertical', command=self.lista.yview)
        self.lista.configure(yscrollcommand=scroll.set)
        self.lista.pack(side='left', fill='both', expand=True)
        scroll.pack(side='right', fill='y')
        self.lista.bind('<Double-1>', lambda evento: self.editar(False))
        self.estado = tk.StringVar(value='Leyendo activos…')
        ttk.Label(marco, textvariable=self.estado, font=('Segoe UI', 10), wraplength=950).pack(anchor='w', pady=(16, 0))
        raiz.protocol('WM_DELETE_WINDOW', self.cerrar)
        raiz.after(100, self.procesar)

    def motor(self):
        if self.fox is None:
            self.fox = FoxPro(self.directorio)
        return self.fox

    def enviar(self, accion, al_terminar):
        if self.trabajo is not None:
            return False
        self.trabajo = (self.executor.submit(accion), al_terminar)
        for boton in self.botones:
            boton.state(['disabled'])
        return True

    def procesar(self):
        if self.cerrado:
            return
        if self.trabajo is not None and self.trabajo[0].done():
            futuro, terminado = self.trabajo
            self.trabajo = None
            for boton in self.botones:
                boton.state(['!disabled'])
            try:
                terminado(futuro.result())
            except Exception as error:
                self.estado.set('No se guardaron cambios: ' + str(error))
                if self.dialogo is not None:
                    self.guardar_boton.state(['!disabled'])
                messagebox.showerror('Activos fijos', str(error), parent=self.dialogo or self.raiz)
            self.siguiente = time.monotonic() + 3
        if self.trabajo is None and self.dialogo is None and time.monotonic() >= self.siguiente:
            self.actualizar()
        self.raiz.after(100, self.procesar)

    def actualizar(self):
        self.enviar(lambda: (self.motor().leer(), self.motor().responsables()), self.mostrar)

    def mostrar(self, resultado):
        registros, self.responsables = resultado
        seleccion = self.lista.selection()
        self.registros = registros
        for item in self.lista.get_children():
            if item not in registros:
                self.lista.delete(item)
        for posicion, codigo in enumerate(sorted(registros)):
            a = registros[codigo]
            valores = (codigo, a['descripcion'], f"{float(a['costoInicial']):,.2f}", f"{float(a['vidaUtil']):g}",
                       datetime.strptime(a['fechaIncorporacion'], '%Y-%m-%d').strftime('%d/%m/%Y'), a['estadoActivo'], a['responsable'])
            if self.lista.exists(codigo):
                self.lista.item(codigo, values=valores)
                self.lista.move(codigo, '', posicion)
            else:
                self.lista.insert('', posicion, iid=codigo, values=valores)
        if seleccion and seleccion[0] in registros:
            self.lista.selection_set(seleccion[0])
        elif registros:
            self.lista.selection_set(sorted(registros)[0])
        if registros:
            self.plantilla = normalizar(registros[sorted(registros)[-1]])
            self.configuracion.parent.mkdir(parents=True, exist_ok=True)
            self.configuracion.write_text(json.dumps(self.plantilla, ensure_ascii=False), encoding='utf-8')
        self.estado.set(f'{len(registros)} activo(s). Los cambios guardados se reflejan también en Swagger mientras la conexión esté activa.')

    def seleccionado(self):
        seleccion = self.lista.selection()
        if not seleccion:
            messagebox.showinfo('Selecciona un activo', 'Selecciona una fila de la lista.', parent=self.raiz)
            return None
        return copy.deepcopy(self.registros[seleccion[0]])

    def editar(self, nuevo):
        if self.trabajo is not None or self.dialogo is not None:
            return
        anterior = None if nuevo else self.seleccionado()
        if not nuevo and anterior is None:
            return
        base = copy.deepcopy(self.plantilla if nuevo else normalizar(anterior))
        if base is None:
            messagebox.showinfo('Datos de referencia', 'Registra primero un activo en el VSIAF original para tomar sus datos administrativos.', parent=self.raiz)
            return
        if nuevo:
            numeros = [int(m.group(1)) for c in self.registros if (m := re.fullmatch(r'PROY-(\d+)', c))]
            siguiente = max(numeros, default=0) + 1
            base.update(codigo=f'PROY-{siguiente:03d}', descripcion=f'Proyector {siguiente}', costoInicial=0,
                        fechaIncorporacion=date.today().isoformat(), estadoActivo='Bueno', estadoRegistro='ELABORADO',
                        depreciacionInicial=0, observaciones='', codigoAdicional='', rube='', organismoFinanciador='', numeroConvenio='')
        dialogo = tk.Toplevel(self.raiz)
        self.dialogo = dialogo
        dialogo.title('Añadir activo' if nuevo else 'Modificar activo')
        dialogo.resizable(False, False)
        dialogo.transient(self.raiz)
        dialogo.grab_set()
        contenedor = ttk.Frame(dialogo, padding=24)
        contenedor.pack(fill='both', expand=True)
        ttk.Label(contenedor, text=dialogo.title(), font=('Segoe UI', 18, 'bold')).grid(row=0, column=0, columnspan=2, sticky='w', pady=(0, 18))
        variables = {}
        nombres = ['Código', 'Descripción', 'Costo (Bs)', 'Vida útil (años)', 'Fecha (día/mes/año)', 'Estado', 'Responsable']
        for fila, (campo, nombre) in enumerate(zip(self.columnas, nombres), 1):
            ttk.Label(contenedor, text=nombre, font=('Segoe UI', 11)).grid(row=fila, column=0, sticky='w', padx=(0, 18), pady=7)
            valor = base[campo]
            if campo == 'fechaIncorporacion':
                valor = datetime.strptime(valor, '%Y-%m-%d').strftime('%d/%m/%Y')
            variable = tk.StringVar(value=str(valor))
            variables[campo] = variable
            if campo == 'estadoActivo':
                entrada = ttk.Combobox(contenedor, textvariable=variable, values=['Bueno', 'Regular', 'Malo'], state='readonly', width=35)
            elif campo == 'responsable':
                entrada = ttk.Combobox(contenedor, textvariable=variable, values=sorted({r['responsable'] for r in self.responsables}), width=35)
            else:
                entrada = ttk.Entry(contenedor, textvariable=variable, width=38, font=('Segoe UI', 11))
                if campo == 'codigo' and not nuevo:
                    entrada.state(['readonly'])
            entrada.grid(row=fila, column=1, sticky='ew', pady=7)
        botones = ttk.Frame(contenedor)
        botones.grid(row=8, column=0, columnspan=2, sticky='e', pady=(20, 0))
        ttk.Button(botones, text='Cancelar', command=self.cancelar).pack(side='left', padx=(0, 10))
        self.guardar_boton = ttk.Button(botones, text='Guardar', command=lambda: self.guardar(base, anterior, variables))
        self.guardar_boton.pack(side='left')
        dialogo.protocol('WM_DELETE_WINDOW', self.cancelar)

    def cancelar(self):
        if self.trabajo is not None:
            return
        if self.dialogo is not None:
            self.dialogo.destroy()
            self.dialogo = None

    def guardar(self, base, anterior, variables):
        if self.trabajo is not None:
            return
        try:
            activo = dict(base)
            for campo in self.columnas:
                activo[campo] = variables[campo].get().strip()
            if not re.fullmatch(r'[A-Z0-9][A-Z0-9._-]{0,14}', activo['codigo']):
                raise ValueError('Usa un código de hasta 15 caracteres: letras mayúsculas, números o guiones.')
            if not activo['descripcion'] or len(activo['descripcion']) > 150:
                raise ValueError('Escribe una descripción de 1 a 150 caracteres.')
            for campo in ('costoInicial', 'vidaUtil'):
                numero = Decimal(activo[campo].replace(',', '.'))
                limite = Decimal('9999999999999.99') if campo == 'costoInicial' else Decimal('9999.99')
                if not numero.is_finite() or numero < 0 or numero > limite or numero != numero.quantize(Decimal('.01')):
                    raise ValueError('Revisa costo y vida útil: usa números válidos con hasta dos decimales.')
                activo[campo] = float(numero)
            if activo['vidaUtil'] <= 0:
                raise ValueError('La vida útil debe ser mayor que cero.')
            if activo['costoInicial'] < float(activo['depreciacionInicial'] or 0):
                raise ValueError('El costo no puede ser menor que la depreciación inicial registrada.')
            fecha = datetime.strptime(activo['fechaIncorporacion'], '%d/%m/%Y').date()
            if fecha.year < 1900:
                raise ValueError('El año debe ser 1900 o posterior.')
            activo['fechaIncorporacion'] = fecha.isoformat()
            if activo['estadoActivo'] not in ('Bueno', 'Regular', 'Malo'):
                raise ValueError('Selecciona un estado válido.')
            if not activo['responsable'] or len(activo['responsable']) > 35:
                raise ValueError('Escribe un nombre de responsable de 1 a 35 caracteres.')
            candidatos = [r for r in self.responsables if r['responsable'].casefold() == activo['responsable'].casefold()]
            if len(candidatos) > 1:
                raise ValueError('Hay más de un responsable con ese nombre; revisa sus datos en VSIAF.')
            if candidatos:
                activo.update(candidatos[0])
            else:
                activo['responsableId'] = max((r['responsableId'] for r in self.responsables), default=0) + 1
                if activo['responsableId'] > 99999:
                    raise ValueError('No quedan códigos de responsable disponibles.')
                activo['cargo'] = 'Responsable del activo'
        except (ValueError, InvalidOperation) as error:
            messagebox.showerror('Revisa los datos', str(error), parent=self.dialogo)
            return
        self.guardar_boton.state(['disabled'])
        self.enviar(lambda: self.motor().guardar(activo['codigo'], activo, anterior), self.guardado)

    def guardado(self, resultado):
        self.cancelar()
        self.estado.set('Guardado en VSIAF. Actualizando la lista…')
        self.actualizar()

    def eliminar(self):
        if self.trabajo is not None or self.dialogo is not None:
            return
        activo = self.seleccionado()
        if activo is None:
            return
        if messagebox.askyesno('Eliminar activo', f"¿Eliminar {activo['codigo']} — {activo['descripcion']}?\n\nTambién desaparecerá de Swagger al conectarse.", parent=self.raiz):
            self.enviar(lambda: self.motor().guardar(activo['codigo'], None, activo), self.guardado)

    def cerrar(self):
        if self.trabajo is not None:
            self.estado.set('Espera a que termine la operación para cerrar.')
            return
        self.cerrado = True
        if self.fox is not None:
            self.executor.submit(self.fox.cerrar)
        self.executor.shutdown(wait=False)
        self.raiz.destroy()


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--dbfs', default='C:/vsiaf/dbfs')
    parser.add_argument('--configuracion', default=str(Path(os.environ['LOCALAPPDATA']) / 'SIS414/segundo-parcial/plantilla.json'))
    args = parser.parse_args()
    raiz = tk.Tk()
    Activos(raiz, args.dbfs, args.configuracion)
    raiz.mainloop()
