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
    const activos = '/api/activos/{codigo}';
    const rutas = new Set(['/api/responsables/{id}', '/api/oficinas/{id}', activos]);
    const parametroRuta = ruta => ruta === activos ? 'codigo' : 'id';
    const camposActivo = ['descripcion', 'codigoAdicional', 'fechaAdquisicion', 'estadoActivo', 'observaciones',
        'costoInicial', 'depreciacionInicial', 'vidaUtil', 'rube', 'numeroConvenio', 'oficinaId', 'responsableId'];
    const estados = new Map();
    const oyentes = new Map();
    const React = system.React;
    const corresponde = (ruta, metodo) => metodo === 'put' && rutas.has(ruta);
    const actual = ruta => estados.get(ruta) || {mensaje: '', listo: false};
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
        avisar(ruta, {id, control, listo: false, mensaje: id ? 'Cargando…' : ''});
        const valido = ruta === activos ? /^[A-Z0-9][A-Z0-9._-]{0,14}$/.test(id)
            : /^\d+$/.test(id) && Number(id) <= (ruta.includes('responsables') ? 99999 : 9999) && (!ruta.includes('oficinas') || Number(id) >= 1);
        if (!valido) {
            if (id) avisar(ruta, {mensaje: ruta === activos ? 'Escribe un código válido.' : 'Escribe un ID válido.'});
            return;
        }
        const temporizador = setTimeout(async () => {
            try {
                const servidor = system.oas3Selectors.selectedServer();
                const base = servidor ? system.oas3Selectors.serverEffectiveValue(servidor) : window.location.origin;
                const consulta = ruta === activos ? ruta.replace('{codigo}', encodeURIComponent(id)) + '?interno=true' : ruta.replace('/{id}', '');
                const url = new URL(base.replace(/\/$/, '') + consulta, window.location.href);
                const respuesta = await fetch(url, {signal: control.signal, cache: 'no-store', headers: {Accept: 'application/json'}});
                if (ruta === activos && respuesta.status === 404) throw new Error('No existe el activo ' + id + '.');
                if (!respuesta.ok) throw new Error('No se pudieron consultar los datos. Vuelve a cargar.');
                const registros = await respuesta.json();
                if (control.signal.aborted || actual(ruta).control !== control) return;
                const registro = ruta === activos ? registros : registros.find(fila => fila.id === Number(id));
                if (!registro) throw new Error('No existe un registro con el ID ' + id + '. Revisa el ID.');
                if (system.oas3Selectors.requestBodyValue(ruta, 'put') !== '{}') {
                    throw new Error('El texto cambió durante la carga. Pulsa Volver a cargar para recuperar los datos actuales.');
                }
                const editable = ruta === activos ? Object.fromEntries(camposActivo.map(campo =>
                    [campo, registro[campo === 'fechaAdquisicion' ? 'fechaIncorporacion' : campo]])) : registro;
                cuerpo(ruta, JSON.stringify(editable, null, 2));
                avisar(ruta, {listo: true, mensaje: ''});
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
                    avisar(this.ruta, {activo: false, listo: false, mensaje: ''});
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
                cargar(this.ruta, system.specSelectors.parameterValues([this.ruta, 'put']).get('path.' + parametroRuta(this.ruta)));
            }
            render() {
                const ruta = this.props.pathMethod?.[0] || this.props.path;
                if (!corresponde(ruta, this.props.pathMethod?.[1] || this.props.method)) return React.createElement(Original, this.props);
                const estado = actual(ruta);
                if (tipo === 'execute') return React.createElement(Original, {...this.props, disabled: this.props.disabled || !estado.listo});
                return React.createElement(React.Fragment, null,
                    estado.mensaje ? React.createElement('div', {role: 'status', 'aria-live': 'polite', style: {padding: '12px 20px'}},
                        estado.mensaje,
                        this.props.tryItOutEnabled && !estado.listo && estado.id ? React.createElement('button', {type: 'button', className: 'btn', style: {marginLeft: '12px'}, onClick: () => cargar(ruta, estado.id)}, 'Volver a cargar') : null) : null,
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
                        if (corresponde(...pathMethod) && parametro.get('name') === parametroRuta(pathMethod[0]) && parametro.get('in') === 'path' && actual(pathMethod[0]).activo) cargar(pathMethod[0], valor);
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

function AsignacionNuevoActivo(system) {
    return AsignacionActivo(system, '/api/activos', 'post');
}

function AsignacionEditarActivo(system) {
    return AsignacionActivo(system, '/api/activos/{codigo}', 'put');
}

function AsignacionActivo(system, ruta, metodo) {
    const React = system.React;
    const oyentes = new Set();
    let estado = {oficinas: [], responsables: [], cargando: false, cargado: false, error: '', activo: false};
    let control;
    const corresponde = props => (props.pathMethod?.[0] || props.path) === ruta && (props.pathMethod?.[1] || props.method) === metodo;
    function avisar(cambios = {}) {
        estado = {...estado, ...cambios};
        for (const avisar of oyentes) avisar();
    }
    function cuerpo() {
        try {
            const datos = JSON.parse(system.oas3Selectors.requestBodyValue(ruta, metodo));
            return datos && typeof datos === 'object' && !Array.isArray(datos) ? datos : null;
        } catch { return null; }
    }
    function elegir(campo, valor) {
        const datos = cuerpo();
        if (!datos) return;
        datos[campo] = valor === '' ? null : Number(valor);
        if (campo === 'oficinaId') datos.responsableId = null;
        system.oas3Actions.setRequestBodyValue({pathMethod: [ruta, metodo], value: JSON.stringify(datos, null, 2)});
    }
    function seleccionValida(datos) {
        return estado.cargado && !estado.cargando && !estado.error && datos
            && estado.oficinas.some(o => o.id === datos.oficinaId)
            && estado.responsables.some(r => r.id === datos.responsableId && r.oficinaId === datos.oficinaId);
    }
    async function cargar() {
        control?.abort();
        const peticion = control = new AbortController();
        avisar({cargando: true, cargado: false, error: ''});
        try {
            const servidor = system.oas3Selectors.selectedServer();
            const base = servidor ? system.oas3Selectors.serverEffectiveValue(servidor) : window.location.origin;
            const consultar = async catalogo => {
                const url = new URL(base.replace(/\/$/, '') + '/api/' + catalogo, window.location.href);
                const respuesta = await fetch(url, {signal: peticion.signal, cache: 'no-store', headers: {Accept: 'application/json'}});
                if (!respuesta.ok) throw new Error('No se pudieron cargar las oficinas y los responsables.');
                const datos = await respuesta.json();
                if (!Array.isArray(datos)) throw new Error('No se pudieron cargar las oficinas y los responsables.');
                return datos;
            };
            const [oficinas, responsables] = await Promise.all([consultar('oficinas'), consultar('responsables')]);
            if (!peticion.signal.aborted && control === peticion) avisar({oficinas, responsables, cargando: false, cargado: true});
        } catch (error) {
            if (!peticion.signal.aborted && control === peticion) avisar({cargando: false, cargado: false, error: error.message});
        }
    }
    function envolver(Original, tipo) {
        return class extends React.Component {
            componentDidMount() {
                if (!corresponde(this.props)) return;
                this.aviso = () => this.forceUpdate();
                oyentes.add(this.aviso);
                if (tipo === 'parameters' && this.props.tryItOutEnabled) this.iniciar();
            }
            componentDidUpdate(anterior) {
                if (!corresponde(this.props) || tipo !== 'parameters' || anterior.tryItOutEnabled === this.props.tryItOutEnabled) return;
                if (this.props.tryItOutEnabled) this.iniciar();
                else {
                    control?.abort();
                    avisar({activo: false, cargando: false, cargado: false});
                }
            }
            componentWillUnmount() {
                oyentes.delete(this.aviso);
                if (corresponde(this.props) && tipo === 'parameters') {
                    control?.abort();
                    avisar({activo: false, cargando: false, cargado: false});
                }
            }
            iniciar() {
                avisar({activo: true});
                cargar();
            }
            render() {
                if (!corresponde(this.props)) return React.createElement(Original, this.props);
                const datos = cuerpo();
                if (tipo === 'execute') return React.createElement(Original, {...this.props, disabled: this.props.disabled || !seleccionValida(datos)});
                const original = React.createElement(Original, this.props);
                if (!this.props.tryItOutEnabled) return original;
                const oficina = estado.oficinas.find(o => o.id === datos?.oficinaId);
                const responsables = oficina ? estado.responsables.filter(r => r.oficinaId === oficina.id) : [];
                const responsable = responsables.find(r => r.id === datos?.responsableId);
                const mensaje = estado.cargando ? 'Cargando…' : estado.error || (!datos ? 'Revisa el formato del JSON.'
                    : estado.cargado && !estado.oficinas.length ? 'Registra una oficina en POST /api/oficinas.'
                    : oficina && !responsables.length ? 'Registra un responsable para esta oficina en POST /api/responsables.'
                    : datos.responsableId != null && !responsable ? 'Selecciona un responsable de esta oficina.' : '');
                const deshabilitado = estado.cargando || !estado.cargado || !datos || !Object.keys(datos).length;
                const prefijo = metodo === 'post' ? 'nuevo-activo-' : 'editar-activo-';
                const selector = (campo, titulo, filas, elegido, deshabilitado) => React.createElement('label',
                    {htmlFor: prefijo + campo, style: {display: 'flex', flexDirection: 'column', gap: '8px', minWidth: '240px', flex: 1}},
                    titulo,
                    React.createElement('select', {id: prefijo + campo, value: elegido ? String(elegido.id) : '', disabled: deshabilitado,
                        onChange: evento => elegir(campo, evento.target.value), style: {width: '100%', maxWidth: '100%'}},
                        React.createElement('option', {value: ''}, 'Selecciona ' + titulo.toLowerCase()),
                        ...filas.map(fila => React.createElement('option', {key: fila.id, value: String(fila.id)}, fila.nombre + ' (' + fila.id + ')'))));
                return React.createElement(React.Fragment, null, original,
                    React.createElement('div', {style: {padding: '16px 20px'}},
                        React.createElement('div', {style: {display: 'flex', flexWrap: 'wrap', gap: '16px', alignItems: 'end'}},
                            selector('oficinaId', 'Oficina', estado.oficinas, oficina, deshabilitado),
                            selector('responsableId', 'Responsable', responsables, responsable, deshabilitado || !oficina),
                            React.createElement('button', {type: 'button', className: 'btn', disabled: estado.cargando, onClick: cargar}, 'Actualizar listas')),
                        mensaje ? React.createElement('div', {role: 'status', 'aria-live': 'polite', style: {paddingTop: '12px'}}, mensaje) : null));
            }
        };
    }
    return {
        wrapComponents: {
            parameters: Original => envolver(Original, 'parameters'),
            execute: Original => envolver(Original, 'execute')
        },
        statePlugins: {
            oas3: {
                wrapActions: {
                    setRequestBodyValue: original => datos => {
                        const resultado = original(datos);
                        if (datos.pathMethod?.[0] === ruta && datos.pathMethod?.[1] === metodo) avisar();
                        return resultado;
                    },
                    setSelectedServer: original => (...args) => {
                        const resultado = original(...args);
                        if (estado.activo) {
                            const datos = cuerpo();
                            if (datos && metodo === 'post') system.oas3Actions.setRequestBodyValue({pathMethod: [ruta, metodo], value: JSON.stringify({...datos, oficinaId: null, responsableId: null}, null, 2)});
                            cargar();
                        }
                        return resultado;
                    }
                }
            }
        }
    };
}
