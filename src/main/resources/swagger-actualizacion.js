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
