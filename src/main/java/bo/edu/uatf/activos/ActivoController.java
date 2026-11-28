package bo.edu.uatf.activos;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import jakarta.validation.Validator;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.time.LocalDate;
import java.time.ZoneId;

@RestController
@RequestMapping("/api/activos")
@Tag(name = "Activos fijos")
public class ActivoController {
    private final JdbcClient jdbc;
    private final Grupos grupos;
    private final ObjectMapper json;
    private final Validator validator;
    private final RegistroDbf registros;
    private static final String CONSULTA = """
            SELECT a.codigo, a.descripcion, o.nombre AS oficina, r.nombre AS responsable,
                   r.cargo, a.codigo_adicional, a.fecha_incorporacion, a.grupo, a.auxiliar,
                   a.estado_activo, a.estado_registro, a.observaciones, a.costo_inicial,
                   a.depreciacion_inicial, a.vida_util, a.depreciar, a.actualizar,
                   a.rube, a.organismo_financiador, a.numero_convenio, a.entidad, a.unidad,
                   a.oficina_id, a.responsable_id, a.revision
            FROM segundo_parcial.activo a
            LEFT JOIN segundo_parcial.oficina o ON o.id = a.oficina_id
            LEFT JOIN segundo_parcial.responsable r ON r.id = a.responsable_id
            """;

    public ActivoController(JdbcClient jdbc, Grupos grupos, ObjectMapper json, Validator validator, RegistroDbf registros) {
        this.jdbc = jdbc;
        this.grupos = grupos;
        this.json = json;
        this.validator = validator;
        this.registros = registros;
    }

    @GetMapping
    @ApiResponse(responseCode = "200", description = "Activos vigentes; detalleDbf=true devuelve las columnas originales de FoxPro",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = ResumenActivo.class))))
    @Operation(summary = "Consultar activos: resumen o columnas de actual.DBF",
            description = "Muestra código, descripción, costo, vida útil, fecha, estado y responsable, igual que la pantalla sencilla y la tabla de consulta activos.dbf. Activa detalleDbf para comparar todas las columnas originales de actual.DBF. Solo se muestran registros vigentes.")
    public List<?> listar(@RequestParam(defaultValue = "false") boolean detalleDbf,
            @Parameter(hidden = true) @RequestParam(defaultValue = "false") boolean interno) {
        var activos = jdbc.sql(CONSULTA + " ORDER BY a.codigo").query(Asignacion.class).list();
        if (interno) return activos;
        if (detalleDbf) return activos.stream().map(a -> registros.obtener(a.codigo())).toList();
        return activos.stream().map(ResumenActivo::de).toList();
    }

    @GetMapping("/{codigo}")
    @ApiResponse(responseCode = "200", description = "Activo encontrado", content = @Content(schema = @Schema(implementation = ResumenActivo.class)))
    @Operation(summary = "Consultar un activo por su código")
    public ResponseEntity<?> consultar(@Parameter(example = "PROY-002") @PathVariable String codigo,
            @RequestParam(defaultValue = "false") boolean detalleDbf,
            @Parameter(hidden = true) @RequestParam(defaultValue = "false") boolean interno) {
        var activo = obtener(codigo, false);
        return ResponseEntity.ok().eTag(activo.revision().toString())
                .body(interno ? activo : detalleDbf ? registros.obtener(codigo) : ResumenActivo.de(activo));
    }

    @Hidden
    @GetMapping("/{codigo}/asignacion")
    public Asignacion asignacion(@PathVariable String codigo) {
        return obtener(codigo, false);
    }

    @PostMapping
    @ApiResponse(responseCode = "201", description = "Activo creado", content = @Content(schema = @Schema(implementation = ResumenActivo.class)))
    @Transactional
    @Operation(summary = "Añadir un activo",
            description = "Escribe código, descripción y costo. Los datos administrativos se toman del último activo registrado; la fecha será la actual. Puedes indicar vida útil, fecha y estado. Se crea en ELABORADO.")
    public ResponseEntity<?> crear(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(schema = @Schema(implementation = ResumenActivo.class),
                    examples = @ExampleObject(value = "{\"codigo\":\"PROY-003\",\"descripcion\":\"Proyector 3\",\"costoInicial\":3500,\"responsable\":\"Luis Rojas\"}")))
            @RequestBody Map<String, Object> datos,
            @Parameter(hidden = true) @RequestParam(defaultValue = "false") boolean interno) {
        var activo = completar(datos, null, null);
        validar(activo);
        var p = parametros(activo);
        var columnas = String.join(", ", p.keySet());
        var valores = p.keySet().stream().map(k -> ":" + k).toList();
        jdbc.sql("INSERT INTO segundo_parcial.activo (" + columnas + ") VALUES (" + String.join(", ", valores) + ")")
                .params(p).update();
        var creado = obtener(activo.codigo(), false);
        return ResponseEntity.created(URI.create("/api/activos/" + activo.codigo()))
                .eTag(creado.revision().toString()).body(interno ? creado : ResumenActivo.de(creado));
    }

    @PutMapping("/{codigo}")
    @ApiResponse(responseCode = "200", description = "Activo modificado", content = @Content(schema = @Schema(implementation = ResumenActivo.class)))
    @Transactional
    @Operation(summary = "Modificar un activo",
            description = "Introduce el código en la URL y únicamente los campos que deseas cambiar. Los campos omitidos conservan su valor. El activo debe estar en ELABORADO.")
    public Object modificar(@Parameter(example = "PROY-002") @PathVariable String codigo,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(schema = @Schema(implementation = ResumenActivo.class),
                    examples = @ExampleObject(value = "{\"descripcion\":\"Proyector 2 actualizado\",\"costoInicial\":3200}")))
            @RequestBody Map<String, Object> datos,
            @Parameter(hidden = true) @RequestHeader(value = "If-Match", required = false) String revision,
            @Parameter(hidden = true) @RequestParam(defaultValue = "false") boolean interno) {
        var actual = obtener(codigo, true);
        editable(actual, revision);
        var activo = completar(datos, codigo, actual);
        validar(activo);
        var p = parametros(activo);
        var cambios = p.keySet().stream().filter(k -> !k.equals("codigo")).map(k -> k + " = :" + k).toList();
        jdbc.sql("UPDATE segundo_parcial.activo SET " + String.join(", ", cambios)
                + ", revision = revision + 1 WHERE codigo = :codigo").params(p).update();
        var actualizado = obtener(codigo, false);
        return interno ? actualizado : ResumenActivo.de(actualizado);
    }

    @DeleteMapping("/{codigo}")
    @Transactional
    @Operation(summary = "Eliminar: borrar un activo elaborado por su código")
    public ResponseEntity<Void> eliminar(@Parameter(example = "PROY-003") @PathVariable String codigo,
            @Parameter(hidden = true) @RequestHeader(value = "If-Match", required = false) String revision) {
        editable(obtener(codigo, true), revision);
        jdbc.sql("DELETE FROM segundo_parcial.activo WHERE codigo = :codigo").param("codigo", codigo).update();
        return ResponseEntity.noContent().build();
    }

    private Asignacion obtener(String codigo, boolean bloquear) {
        return jdbc.sql(CONSULTA + " WHERE a.codigo = :codigo" + (bloquear ? " FOR UPDATE OF a" : ""))
                .param("codigo", codigo).query(Asignacion.class).optional()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No existe el activo " + codigo));
    }

    private void editable(Asignacion activo, String revision) {
        if (revision != null && !revision.equals("\"" + activo.revision() + "\"")) {
            throw new ResponseStatusException(HttpStatus.PRECONDITION_FAILED, "El activo cambió; vuelve a consultar");
        }
        if (!"ELABORADO".equals(activo.estadoRegistro())) throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Solo se pueden modificar o eliminar activos en estado ELABORADO");
    }

    private void validar(Activo activo) {
        var grupo = grupos.listar().stream().filter(g -> g.nombre().equals(activo.grupo().strip())).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selecciona un grupo de GET /api/grupos"));
        if (grupo.depreciar() != activo.depreciar() || grupo.actualizar() != activo.actualizar()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Depreciar y actualizar deben coincidir con el grupo contable");
        }
        if (activo.fechaIncorporacion().getYear() < 1900 || activo.fechaIncorporacion().getYear() > 9999) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El año debe estar entre 1900 y 9999");
        }
        if (activo.depreciacionInicial().compareTo(activo.costoInicial()) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La depreciación inicial no puede superar el costo");
        }
        if (!jdbc.sql("SELECT EXISTS (SELECT 1 FROM segundo_parcial.oficina WHERE id = :id)")
                .param("id", activo.oficinaId()).query(Boolean.class).single()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No existe la oficina indicada");
        }
        if (!jdbc.sql("SELECT EXISTS (SELECT 1 FROM segundo_parcial.responsable WHERE id = :id)")
                .param("id", activo.responsableId()).query(Boolean.class).single()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No existe el responsable indicado");
        }
    }

    private Activo completar(Map<String, Object> datos, String codigo, Asignacion actual) {
        if (datos.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Escribe al menos un campo para guardar");
        var valores = new LinkedHashMap<String, Object>();
        var base = actual;
        if (base == null) base = jdbc.sql(CONSULTA + " ORDER BY a.codigo DESC LIMIT 1").query(Asignacion.class).optional().orElse(null);
        if (base != null) valores.putAll(json.convertValue(base, new TypeReference<Map<String, Object>>() { }));
        if (actual == null) {
            valores.remove("codigo");
            valores.remove("descripcion");
            valores.remove("costoInicial");
            valores.put("fechaIncorporacion", LocalDate.now(ZoneId.of("America/La_Paz")).toString());
            valores.put("estadoRegistro", "ELABORADO");
            valores.put("estadoActivo", "Bueno");
            valores.put("depreciacionInicial", 0);
            valores.put("observaciones", "");
            valores.put("codigoAdicional", "");
            valores.put("rube", "");
            valores.put("organismoFinanciador", "");
            valores.put("numeroConvenio", "");
            valores.putIfAbsent("vidaUtil", 8);
            valores.putIfAbsent("grupo", "MUEBLES Y ENSERES DE OFICINA");
            valores.putIfAbsent("auxiliar", "PROYECTOR");
            valores.putIfAbsent("depreciar", true);
            valores.putIfAbsent("actualizar", true);
            valores.putIfAbsent("entidad", "0143");
            valores.putIfAbsent("unidad", "1");
            if (!valores.containsKey("oficinaId")) valores.put("oficinaId", jdbc.sql("SELECT MIN(id) FROM segundo_parcial.oficina WHERE id > 0").query(Integer.class).single());
            if (!valores.containsKey("responsableId")) valores.put("responsableId", jdbc.sql("SELECT MIN(id) FROM segundo_parcial.responsable WHERE id > 0").query(Integer.class).single());
        }
        var permitidos = java.util.Arrays.stream(Activo.class.getRecordComponents()).map(java.lang.reflect.RecordComponent::getName).toList();
        var lectura = List.of("oficina", "responsable", "cargo", "revision");
        for (var campo : datos.keySet()) if (!permitidos.contains(campo) && !lectura.contains(campo))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Campo desconocido: " + campo);
        valores.putAll(datos);
        if (datos.containsKey("responsable")) {
            if (!(datos.get("responsable") instanceof String texto) || texto.isBlank() || texto.strip().length() > 35)
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Escribe el nombre del responsable, de 1 a 35 caracteres");
            var nombre = ((String) datos.get("responsable")).strip();
            var ids = jdbc.sql("SELECT id FROM segundo_parcial.responsable WHERE LOWER(TRIM(nombre)) = LOWER(:nombre)")
                    .param("nombre", nombre).query(Integer.class).list();
            if (ids.size() > 1) throw new ResponseStatusException(HttpStatus.CONFLICT, "Hay más de un responsable con ese nombre; revisa sus datos en VSIAF");
            int id;
            if (ids.isEmpty()) {
                jdbc.sql("LOCK TABLE segundo_parcial.responsable IN EXCLUSIVE MODE").update();
                id = jdbc.sql("SELECT COALESCE(MAX(id), 0) + 1 FROM segundo_parcial.responsable").query(Integer.class).single();
                if (id > 99999) throw new ResponseStatusException(HttpStatus.CONFLICT, "No quedan códigos de responsable");
                jdbc.sql("INSERT INTO segundo_parcial.responsable (id, nombre, cargo) VALUES (:id, :nombre, 'Responsable del activo')")
                        .param("id", id).param("nombre", nombre).update();
            } else id = ids.getFirst();
            if (datos.containsKey("responsableId") && !String.valueOf(id).equals(String.valueOf(datos.get("responsableId"))))
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El nombre y el ID del responsable no coinciden");
            valores.put("responsableId", id);
        }
        if (codigo != null) {
            if (datos.containsKey("codigo") && !codigo.equals(datos.get("codigo")))
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El código de la URL y del cuerpo deben coincidir");
            valores.put("codigo", codigo);
        }
        valores.keySet().retainAll(permitidos);
        Activo activo;
        try { activo = json.convertValue(valores, Activo.class); }
        catch (IllegalArgumentException error) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Revisa los tipos de datos; la fecha debe tener formato AAAA-MM-DD"); }
        var errores = validator.validate(activo);
        if (!errores.isEmpty()) throw new jakarta.validation.ConstraintViolationException(errores);
        return activo;
    }

    private LinkedHashMap<String, Object> parametros(Activo a) {
        var p = new LinkedHashMap<String, Object>();
        p.put("codigo", a.codigo());
        p.put("descripcion", a.descripcion().strip());
        p.put("oficina_id", a.oficinaId());
        p.put("responsable_id", a.responsableId());
        p.put("codigo_adicional", a.codigoAdicional());
        p.put("fecha_incorporacion", a.fechaIncorporacion());
        p.put("grupo", a.grupo().strip());
        p.put("auxiliar", a.auxiliar().strip());
        p.put("estado_activo", a.estadoActivo());
        p.put("estado_registro", a.estadoRegistro());
        p.put("observaciones", a.observaciones());
        p.put("costo_inicial", a.costoInicial());
        p.put("depreciacion_inicial", a.depreciacionInicial());
        p.put("vida_util", a.vidaUtil());
        p.put("depreciar", a.depreciar());
        p.put("actualizar", a.actualizar());
        p.put("rube", a.rube());
        p.put("organismo_financiador", a.organismoFinanciador());
        p.put("numero_convenio", a.numeroConvenio());
        p.put("entidad", a.entidad());
        p.put("unidad", a.unidad());
        return p;
    }
}
