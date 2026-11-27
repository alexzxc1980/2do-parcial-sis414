package bo.edu.uatf.activos;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;

@RestController
@RequestMapping("/api/activos")
@Tag(name = "Activos fijos")
public class ActivoController {
    private final JdbcClient jdbc;
    private final Grupos grupos;
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

    public ActivoController(JdbcClient jdbc, Grupos grupos) {
        this.jdbc = jdbc;
        this.grupos = grupos;
    }

    @GetMapping
    @Operation(summary = "Listar todos los activos")
    public List<Asignacion> listar() {
        return jdbc.sql(CONSULTA + " ORDER BY a.codigo").query(Asignacion.class).list();
    }

    @GetMapping({"/{codigo}", "/{codigo}/asignacion"})
    @Operation(summary = "Consultar la ficha de un activo")
    public ResponseEntity<Asignacion> consultar(@Parameter(example = "PROY-001") @PathVariable String codigo) {
        var activo = obtener(codigo, false);
        return ResponseEntity.ok().eTag(activo.revision().toString()).body(activo);
    }

    @PostMapping
    @Transactional
    @Operation(summary = "Nuevo: registrar un activo en estado ELABORADO")
    public ResponseEntity<Asignacion> crear(@Valid @RequestBody Activo activo) {
        validar(activo);
        var p = parametros(activo);
        var columnas = String.join(", ", p.keySet());
        var valores = p.keySet().stream().map(k -> ":" + k).toList();
        jdbc.sql("INSERT INTO segundo_parcial.activo (" + columnas + ") VALUES (" + String.join(", ", valores) + ")")
                .params(p).update();
        var creado = obtener(activo.codigo(), false);
        return ResponseEntity.created(URI.create("/api/activos/" + activo.codigo()))
                .eTag(creado.revision().toString()).body(creado);
    }

    @PutMapping("/{codigo}")
    @Transactional
    @Operation(summary = "Modificar: actualizar los datos de un activo elaborado")
    public Asignacion modificar(@PathVariable String codigo, @Valid @RequestBody Activo activo,
            @Parameter(hidden = true) @RequestHeader(value = "If-Match", required = false) String revision) {
        editable(obtener(codigo, true), revision);
        if (!codigo.equals(activo.codigo())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "El código de la URL y del cuerpo deben coincidir");
        validar(activo);
        var p = parametros(activo);
        var cambios = p.keySet().stream().filter(k -> !k.equals("codigo")).map(k -> k + " = :" + k).toList();
        jdbc.sql("UPDATE segundo_parcial.activo SET " + String.join(", ", cambios)
                + ", revision = revision + 1 WHERE codigo = :codigo").params(p).update();
        return obtener(codigo, false);
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
