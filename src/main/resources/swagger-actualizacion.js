function SeguimientoActivos() {
    const consultas = new Map();
    let activado = true;
    const clave = (ruta, metodo) => metodo.toLowerCase() + ' ' + ruta;
    function cancelar(id) {
        const anterior = consultas.get(id);
        if (anterior) clearTimeout(anterior.temporizador);
        consultas.delete(id);
    }
    function programar(id, consulta) {
        if (consultas.get(id) !== consulta) return;
        consulta.temporizador = setTimeout(async () => {
            try {
                if (activado && !document.hidden && consultas.get(id) === consulta) {
                    await consulta.ejecutar({...consulta.peticion, parameters: {...consulta.peticion.parameters}});
                }
            } finally {
                programar(id, consulta);
            }
        }, 3000);
    }
    return {
        afterLoad() {
            const etiqueta = document.createElement('label');
            etiqueta.style.cssText = 'display:block;padding:12px 20px;background:#e8f4ff;color:#16324f;font:15px sans-serif';
            const control = document.createElement('input');
            control.type = 'checkbox';
            control.checked = true;
            control.addEventListener('change', () => { activado = control.checked; });
            etiqueta.append(control, ' Actualizar automáticamente el GET consultado cada 3 segundos');
            document.getElementById('swagger-ui').before(etiqueta);
        },
        statePlugins: {
            spec: {
                wrapActions: {
                    executeRequest: original => peticion => {
                        if (peticion.method.toLowerCase() !== 'get' || !/^\/api\/activos(?:\/|$)/.test(peticion.pathName)) {
                            return original(peticion);
                        }
                        const id = clave(peticion.pathName, peticion.method);
                        cancelar(id);
                        const consulta = {peticion: {...peticion, parameters: {...peticion.parameters}}, ejecutar: original};
                        consultas.set(id, consulta);
                        return Promise.resolve(original(peticion)).finally(() => programar(id, consulta));
                    },
                    clearResponse: original => (ruta, metodo) => {
                        cancelar(clave(ruta, metodo));
                        return original(ruta, metodo);
                    }
                }
            }
        }
    };
}

function CatalogosReales(system) {
    const rutas = new Set(['/api/responsables/{id}', '/api/oficinas/{id}']);
    const estados = new Map();
    const oyentes = new Map();
    const React = system.React;
    const corresponde = (ruta, metodo) => metodo === 'put' && rutas.has(ruta);
    const actual = ruta => estados.get(ruta) || {mensaje: 'Pulsa Try it out y escribe el ID para cargar sus datos actuales.', listo: false};
    function avisar(ruta, datos) {
        estados.set(ruta, {...actual(ruta), ...datos});
        for (const avisar of oyentes.get(ruta) || []) avisar();
    }
    function cuerpo(ruta, valor) {
        system.oas3Actions.setRequestBodyValue({pathMethod: [ruta, 'put'], value: valor});
    }
    function detener(ruta) {
        const estado = actual(ruta);
        clearTimeout(estado.temporizador);
        estado.control?.abort();
    }
    function cargar(ruta, valor) {
        detener(ruta);
        const id = String(valor ?? '').trim();
        const control = new AbortController();
        cuerpo(ruta, '{}');
        avisar(ruta, {id, control, listo: false, mensaje: id ? 'Cargando los datos actuales…' : 'Escribe el ID arriba para cargar sus datos actuales.'});
        if (!/^\d+$/.test(id) || Number(id) > (ruta.includes('responsables') ? 99999 : 9999) || (ruta.includes('oficinas') && Number(id) < 1)) {
            if (id) avisar(ruta, {mensaje: 'Escribe un ID válido.'});
            return;
        }
        const temporizador = setTimeout(async () => {
            try {
                const servidor = system.oas3Selectors.selectedServer();
                const base = servidor ? system.oas3Selectors.serverEffectiveValue(servidor) : window.location.origin;
                const url = new URL(base.replace(/\/$/, '') + ruta.replace('/{id}', ''), window.location.href);
                const respuesta = await fetch(url, {signal: control.signal, cache: 'no-store', headers: {Accept: 'application/json'}});
                if (!respuesta.ok) throw new Error('No se pudieron consultar los datos. Vuelve a cargar.');
                const registros = await respuesta.json();
                if (control.signal.aborted || actual(ruta).control !== control) return;
                const registro = registros.find(fila => fila.id === Number(id));
                if (!registro) throw new Error('No existe un registro con el ID ' + id + '. Revisa el ID.');
                if (system.oas3Selectors.requestBodyValue(ruta, 'put') !== '{}') {
                    throw new Error('El texto cambió durante la carga. Pulsa Volver a cargar para recuperar los datos actuales.');
                }
                cuerpo(ruta, JSON.stringify(registro, null, 2));
                avisar(ruta, {listo: true, mensaje: 'Datos actuales de ' + registro.nombre + '. Edita ' + (ruta.includes('responsables') ? 'nombre o cargo; conserva id y oficinaId.' : 'nombre; conserva id.') + ' Pulsa Execute para guardar.'});
            } catch (error) {
                if (!control.signal.aborted && actual(ruta).control === control) avisar(ruta, {listo: false, mensaje: error.message});
            }
        }, 300);
        estados.get(ruta).temporizador = temporizador;
    }
    function envolver(Original, tipo) {
        return class extends React.Component {
            componentDidMount() {
                const ruta = this.props.pathMethod?.[0] || this.props.path;
                if (!rutas.has(ruta) || (this.props.pathMethod?.[1] || this.props.method) !== 'put') return;
                this.ruta = ruta;
                this.aviso = () => this.forceUpdate();
                if (!oyentes.has(ruta)) oyentes.set(ruta, new Set());
                oyentes.get(ruta).add(this.aviso);
                if (tipo === 'parameters' && this.props.tryItOutEnabled) this.iniciar();
            }
            componentDidUpdate(anterior) {
                if (tipo !== 'parameters' || !this.ruta || anterior.tryItOutEnabled === this.props.tryItOutEnabled) return;
                if (this.props.tryItOutEnabled) this.iniciar();
                else {
                    detener(this.ruta);
                    avisar(this.ruta, {activo: false, listo: false, mensaje: 'Pulsa Try it out para volver a cargar los datos.'});
                }
            }
            componentWillUnmount() {
                oyentes.get(this.ruta)?.delete(this.aviso);
                if (this.ruta && tipo === 'parameters') {
                    detener(this.ruta);
                    estados.delete(this.ruta);
                }
            }
            iniciar() {
                avisar(this.ruta, {activo: true});
                cargar(this.ruta, system.specSelectors.parameterValues([this.ruta, 'put']).get('path.id'));
            }
            render() {
                const ruta = this.props.pathMethod?.[0] || this.props.path;
                if (!corresponde(ruta, this.props.pathMethod?.[1] || this.props.method)) return React.createElement(Original, this.props);
                const estado = actual(ruta);
                if (tipo === 'execute') return React.createElement(Original, {...this.props, disabled: this.props.disabled || !estado.listo});
                return React.createElement(React.Fragment, null,
                    React.createElement('div', {role: 'status', 'aria-live': 'polite', style: {padding: '12px 20px', color: '#17354d', background: '#e8f4ff'}},
                        estado.mensaje,
                        this.props.tryItOutEnabled && !estado.listo && estado.id ? React.createElement('button', {type: 'button', className: 'btn', style: {marginLeft: '12px'}, onClick: () => cargar(ruta, estado.id)}, 'Volver a cargar') : null),
                    React.createElement(Original, {...this.props, onResetClick: () => cargar(ruta, estado.id)}));
            }
        };
    }
    return {
        wrapComponents: {
            parameters: Original => envolver(Original, 'parameters'),
            execute: Original => envolver(Original, 'execute')
        },
        statePlugins: {
            spec: {
                wrapActions: {
                    changeParamByIdentity: original => (pathMethod, parametro, valor, ...otros) => {
                        const resultado = original(pathMethod, parametro, valor, ...otros);
                        if (corresponde(...pathMethod) && parametro.get('name') === 'id' && parametro.get('in') === 'path' && actual(pathMethod[0]).activo) cargar(pathMethod[0], valor);
                        return resultado;
                    }
                }
            },
            oas3: {
                wrapActions: {
                    setSelectedServer: original => (...args) => {
                        const resultado = original(...args);
                        for (const ruta of rutas) if (actual(ruta).activo) cargar(ruta, actual(ruta).id);
                        return resultado;
                    }
                }
            }
        }
    };
}
