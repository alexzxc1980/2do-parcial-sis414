package bo.edu.uatf.activos;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api")
@Tag(name = "Oficinas y responsables", description = "Catálogos de VSIAF. Con el conector de la laptop encendido, los registros nuevos y las modificaciones se reflejan en oficina.DBF y resp.DBF, aunque no tengan activos asignados. En VSIAF vuelve a abrir el catálogo para actualizar la pantalla.")
public class CatalogoController {
    public record Oficina(
            @NotNull @Min(1) @Max(9999) @Schema(description = "Código CODOFIC de oficina.DBF", example = "2") Integer id,
            @NotBlank @Size(max = 65) @Schema(description = "NOMOFIC de oficina.DBF", example = "Sala de computación") String nombre) { }
    public record Responsable(
            @NotNull @Min(0) @Max(99999) @Schema(description = "Código CODRESP de resp.DBF", example = "4") Integer id,
            @NotBlank @Size(max = 35) @Schema(description = "NOMRESP de resp.DBF", example = "Ana Pérez") String nombre,
            @NotBlank @Size(max = 35) @Schema(description = "CARGO de resp.DBF", example = "Encargada de laboratorio") String cargo,
            @Min(1) @Max(9999) @Schema(description = "CODOFIC de resp.DBF. Consulta GET /api/oficinas. Al crear, si se omite se usa la primera oficina; al modificar se conserva la actual.", example = "1") Integer oficinaId) { }
    private static final String RESPONSABLES = "SELECT r.id, r.nombre, r.cargo, COALESCE(r.oficina_id, (SELECT MIN(a.oficina_id) FROM segundo_parcial.activo a WHERE a.responsable_id=r.id), (SELECT MIN(o.id) FROM segundo_parcial.oficina o WHERE o.id>0)) AS \"oficinaId\", r.revision FROM segundo_parcial.responsable r";
    private final JdbcClient jdbc;
    private final Grupos grupos;

    public CatalogoController(JdbcClient jdbc, Grupos grupos) {
        this.jdbc = jdbc;
        this.grupos = grupos;
    }

    @GetMapping("/grupos")
    @Operation(summary = "Consultar los grupos contables y sus opciones de depreciación", description = "Grupos de codcont.DBF usados para clasificar los activos. Este catálogo se consulta sin cambiar sus reglas contables.")
    public List<Grupos.Grupo> grupos() {
        return grupos.listar();
    }

    @GetMapping("/oficinas")
    @ApiResponse(responseCode = "200", description = "Oficinas de VSIAF", content = @Content(array = @ArraySchema(schema = @Schema(implementation = Oficina.class))))
    @Operation(summary = "Listar oficinas para asignar activos", description = "id corresponde a CODOFIC y nombre a NOMOFIC de oficina.DBF, para entidad 0143 y unidad 1.")
    public Object oficinas(@Parameter(hidden = true) @RequestParam(defaultValue = "false") boolean interno) {
        var consulta = jdbc.sql("SELECT id, nombre, revision FROM segundo_parcial.oficina WHERE id > 0 ORDER BY id");
        return interno ? consulta.query().listOfRows() : consulta.query(Oficina.class).list();
    }

    @PostMapping("/oficinas")
    @Transactional
    @Operation(summary = "Registrar una oficina con un código nuevo")
    public ResponseEntity<Oficina> oficina(@Valid @RequestBody Oficina oficina) {
        var limpia = new Oficina(oficina.id(), oficina.nombre().strip());
        jdbc.sql("INSERT INTO segundo_parcial.oficina (id, nombre) VALUES (:id, :nombre)")
                .param("id", limpia.id()).param("nombre", limpia.nombre()).update();
        return ResponseEntity.created(URI.create("/api/oficinas")).body(limpia);
    }

    @GetMapping("/responsables")
    @ApiResponse(responseCode = "200", description = "Responsables de VSIAF", content = @Content(array = @ArraySchema(schema = @Schema(implementation = Responsable.class))))
    @Operation(summary = "Listar responsables para asignar activos", description = "id, nombre, cargo y oficinaId corresponden a CODRESP, NOMRESP, CARGO y CODOFIC de resp.DBF, para entidad 0143 y unidad 1.")
    public Object responsables(@Parameter(hidden = true) @RequestParam(defaultValue = "false") boolean interno) {
        var consulta = jdbc.sql(RESPONSABLES + " WHERE r.id >= 0 ORDER BY r.id");
        return interno ? consulta.query().listOfRows() : consulta.query(Responsable.class).list();
    }

    @PostMapping("/responsables")
    @Transactional
    @Operation(summary = "Registrar un responsable con un código nuevo", description = "Crea un responsable en la oficina indicada. No cambia la asignación de los activos existentes.")
    public ResponseEntity<Responsable> responsable(@Valid @RequestBody Responsable responsable) {
        int oficina = oficinaValida(responsable.oficinaId());
        var limpio = new Responsable(responsable.id(), responsable.nombre().strip(), responsable.cargo().strip(), oficina);
        jdbc.sql("INSERT INTO segundo_parcial.responsable (id, nombre, cargo, oficina_id) VALUES (:id, :nombre, :cargo, :oficina)")
                .param("id", limpio.id()).param("nombre", limpio.nombre())
                .param("cargo", limpio.cargo()).param("oficina", oficina).update();
        return ResponseEntity.created(URI.create("/api/responsables")).body(limpio);
    }

    @PutMapping("/oficinas/{id}")
    @Transactional
    @Operation(summary = "Modificar el nombre de una oficina")
    public Oficina modificarOficina(@PathVariable int id, @Valid @RequestBody Oficina o,
            @Parameter(hidden = true) @RequestHeader(value = "If-Match", required = false) String revision) {
        if (id != o.id()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Los códigos deben coincidir");
        comprobarRevision("oficina", id, revision);
        jdbc.sql("UPDATE segundo_parcial.oficina SET nombre = :nombre, revision = revision + 1 WHERE id = :id")
                .param("nombre", o.nombre().strip()).param("id", id).update();
        jdbc.sql("UPDATE segundo_parcial.activo SET revision = revision + 1 WHERE oficina_id = :id").param("id", id).update();
        return new Oficina(id, o.nombre().strip());
    }

    @PutMapping("/responsables/{id}")
    @Transactional
    @Operation(summary = "Modificar el nombre y cargo de un responsable", description = "Conserva la oficina si se omite oficinaId. Un responsable con activos asignados no se puede trasladar a otra oficina desde este catálogo.")
    public Responsable modificarResponsable(@PathVariable int id, @Valid @RequestBody Responsable r,
            @Parameter(hidden = true) @RequestHeader(value = "If-Match", required = false) String revision) {
        if (id != r.id()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Los códigos deben coincidir");
        comprobarRevision("responsable", id, revision);
        var actual = jdbc.sql(RESPONSABLES + " WHERE r.id = :id").param("id", id).query(Responsable.class).single();
        int oficina = oficinaValida(r.oficinaId() == null ? actual.oficinaId() : r.oficinaId());
        if (!java.util.Objects.equals(actual.oficinaId(), oficina) && jdbc.sql("SELECT EXISTS (SELECT 1 FROM segundo_parcial.activo WHERE responsable_id=:id)").param("id", id).query(Boolean.class).single())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El responsable tiene activos asignados; conserva su oficina");
        jdbc.sql("UPDATE segundo_parcial.responsable SET nombre = :nombre, cargo = :cargo, oficina_id = :oficina, revision = revision + 1 WHERE id = :id")
                .param("nombre", r.nombre().strip()).param("cargo", r.cargo().strip()).param("oficina", oficina).param("id", id).update();
        jdbc.sql("UPDATE segundo_parcial.activo SET revision = revision + 1 WHERE responsable_id = :id").param("id", id).update();
        return new Responsable(id, r.nombre().strip(), r.cargo().strip(), oficina);
    }

    private int oficinaValida(Integer id) {
        var oficina = id == null ? jdbc.sql("SELECT MIN(id) FROM segundo_parcial.oficina WHERE id>0").query(Integer.class).single() : id;
        if (oficina == null || !jdbc.sql("SELECT EXISTS (SELECT 1 FROM segundo_parcial.oficina WHERE id=:id)").param("id", oficina).query(Boolean.class).single())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No existe la oficina indicada; consulta GET /api/oficinas");
        return oficina;
    }

    private void comprobarRevision(String tabla, int id, String revision) {
        var actual = jdbc.sql("SELECT revision FROM segundo_parcial." + tabla + " WHERE id=:id FOR UPDATE")
                .param("id", id).query(Long.class).optional().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No existe el registro"));
        if (revision != null && !revision.equals("\"" + actual + "\""))
            throw new ResponseStatusException(HttpStatus.PRECONDITION_FAILED, "El registro cambió; vuelve a consultar");
    }
}
