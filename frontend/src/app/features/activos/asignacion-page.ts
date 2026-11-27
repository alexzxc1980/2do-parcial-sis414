import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { DecimalPipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { finalize } from 'rxjs';

interface Asignacion {
  codigo: string;
  descripcion: string;
  oficina: string | null;
  responsable: string | null;
  cargo: string | null;
  codigoAdicional: string | null;
  fechaIncorporacion: string | null;
  grupo: string | null;
  auxiliar: string | null;
  estadoActivo: string | null;
  estadoRegistro: string | null;
  observaciones: string | null;
  costoInicial: number | null;
  depreciacionInicial: number | null;
  vidaUtil: number | null;
  depreciar: boolean | null;
  actualizar: boolean | null;
  rube: string | null;
  organismoFinanciador: string | null;
  numeroConvenio: string | null;
  entidad: string | null;
  unidad: string | null;
}

@Component({
  selector: 'app-asignacion-page',
  imports: [ReactiveFormsModule, DecimalPipe],
  templateUrl: './asignacion-page.html',
  styleUrl: './asignacion-page.scss',
})
export class AsignacionPage implements OnInit {
  private readonly http = inject(HttpClient);
  protected readonly codigo = new FormControl('', {
    nonNullable: true,
    validators: [Validators.required, Validators.maxLength(15), Validators.pattern(/\S/)],
  });
  protected readonly form = new FormGroup({ codigo: this.codigo });
  protected readonly activos = signal<Asignacion[]>([]);
  protected readonly asignacion = signal<Asignacion | null>(null);
  protected readonly buscando = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly secciones: { titulo: string; campos: [keyof Asignacion, string][] }[] = [
    { titulo: 'Identificación e incorporación', campos: [
      ['codigo', 'Código'], ['codigoAdicional', 'Código adicional'], ['descripcion', 'Descripción'],
      ['fechaIncorporacion', 'Fecha de incorporación'], ['entidad', 'Entidad'], ['unidad', 'Unidad'],
    ] },
    { titulo: 'Clasificación y asignación', campos: [
      ['grupo', 'Grupo'], ['auxiliar', 'Auxiliar'], ['oficina', 'Oficina'], ['responsable', 'Responsable'],
      ['cargo', 'Cargo'], ['estadoActivo', 'Estado del activo'], ['estadoRegistro', 'Estado del registro'],
    ] },
    { titulo: 'Valores registrados', campos: [
      ['costoInicial', 'Costo inicial (Bs)'], ['depreciacionInicial', 'Depreciación acumulada inicial (Bs)'],
      ['vidaUtil', 'Vida útil (años)'], ['depreciar', 'Depreciar'], ['actualizar', 'Actualizar'],
    ] },
    { titulo: 'Datos complementarios', campos: [
      ['rube', 'RUBE'], ['organismoFinanciador', 'Organismo financiador'],
      ['numeroConvenio', 'N.º de convenio'], ['observaciones', 'Observaciones'],
    ] },
  ];

  ngOnInit(): void {
    this.listar();
  }

  protected listar(): void {
    if (this.buscando()) return;
    this.error.set(null);
    this.asignacion.set(null);
    this.buscando.set(true);
    this.http.get<Asignacion[]>('/api/activos')
      .pipe(finalize(() => this.buscando.set(false)))
      .subscribe({
        next: activos => this.activos.set(activos),
        error: error => { this.activos.set([]); this.mostrarError(error); },
      });
  }

  protected ver(codigo: string): void {
    this.codigo.setValue(codigo);
    this.buscar();
  }

  protected buscar(): void {
    if (this.buscando()) return;
    this.asignacion.set(null);
    this.error.set(null);
    if (this.codigo.invalid) {
      this.codigo.markAsTouched();
      this.error.set('Escribe un código de activo de hasta 15 caracteres.');
      return;
    }
    const codigo = this.codigo.value.trim();
    this.codigo.setValue(codigo);
    this.buscando.set(true);
    this.http.get<Asignacion>(`/api/activos/${encodeURIComponent(codigo)}/asignacion`)
      .pipe(finalize(() => this.buscando.set(false)))
      .subscribe({
        next: activo => this.asignacion.set(activo),
        error: error => this.mostrarError(error),
      });
  }

  protected valor(activo: Asignacion, campo: keyof Asignacion): string {
    const valor = activo[campo];
    if (valor === null || valor === '') return 'Sin registrar';
    if (typeof valor === 'boolean') return valor ? 'Sí' : 'No';
    if (campo === 'fechaIncorporacion') return String(valor).split('-').reverse().join('/');
    if (typeof valor === 'number') return valor.toLocaleString('es-BO', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
    return String(valor);
  }

  private mostrarError(error: HttpErrorResponse): void {
    this.error.set(error.status === 404
      ? `Activo no encontrado: ${this.codigo.value}.`
      : error.status === 503
        ? 'No se pudo leer VSIAF. Guarda los cambios y vuelve a consultar.'
        : 'No se pudieron consultar los activos. Intenta nuevamente.');
  }
}
