package bo.edu.uatf.activos;

import io.swagger.v3.oas.annotations.Operation;
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
@Tag(name = "Oficinas y responsables")
public class CatalogoController {
    public record Oficina(@NotNull @Min(1) @Max(9999) Integer id, @NotBlank @Size(max = 65) String nombre) { }
    public record Responsable(@NotNull @Min(1) @Max(99999) Integer id,
            @NotBlank @Size(max = 35) String nombre, @NotBlank @Size(max = 35) String cargo) { }
    private final JdbcClient jdbc;
    private final Grupos grupos;

    public CatalogoController(JdbcClient jdbc, Grupos grupos) { this.jdbc = jdbc; this.grupos = grupos; }

    @GetMapping("/grupos")
    @Operation(summary = "Consultar los grupos contables y sus opciones de depreciación")
    public List<Grupos.Grupo> grupos() { return grupos.listar(); }

    @GetMapping("/oficinas")
    @Operation(summary = "Listar oficinas para asignar activos")
    public List<Oficina> oficinas() {
        return jdbc.sql("SELECT id, nombre FROM segundo_parcial.oficina WHERE id > 0 ORDER BY id").query(Oficina.class).list();
    }

    @PostMapping("/oficinas")
    @Operation(summary = "Registrar una oficina con un código nuevo")
    public ResponseEntity<Oficina> oficina(@Valid @RequestBody Oficina oficina) {
        jdbc.sql("INSERT INTO segundo_parcial.oficina (id, nombre) VALUES (:id, :nombre)")
                .param("id", oficina.id()).param("nombre", oficina.nombre().strip()).update();
        return ResponseEntity.created(URI.create("/api/oficinas")).body(oficina);
    }

    @GetMapping("/responsables")
    @Operation(summary = "Listar responsables para asignar activos")
    public List<Responsable> responsables() {
        return jdbc.sql("SELECT id, nombre, cargo FROM segundo_parcial.responsable WHERE id > 0 ORDER BY id")
                .query(Responsable.class).list();
    }

    @PostMapping("/responsables")
    @Operation(summary = "Registrar un responsable con un código nuevo")
    public ResponseEntity<Responsable> responsable(@Valid @RequestBody Responsable responsable) {
        jdbc.sql("INSERT INTO segundo_parcial.responsable (id, nombre, cargo) VALUES (:id, :nombre, :cargo)")
                .param("id", responsable.id()).param("nombre", responsable.nombre().strip())
                .param("cargo", responsable.cargo().strip()).update();
        return ResponseEntity.created(URI.create("/api/responsables")).body(responsable);
    }

    @PutMapping("/oficinas/{id}")
    @Transactional
    @Operation(summary = "Modificar el nombre de una oficina")
    public Oficina modificarOficina(@PathVariable int id, @Valid @RequestBody Oficina o) {
        if (id != o.id()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Los códigos deben coincidir");
        if (jdbc.sql("UPDATE segundo_parcial.oficina SET nombre = :nombre WHERE id = :id")
                .param("nombre", o.nombre().strip()).param("id", id).update() == 0)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No existe la oficina");
        jdbc.sql("UPDATE segundo_parcial.activo SET revision = revision + 1 WHERE oficina_id = :id").param("id", id).update();
        return o;
    }

    @PutMapping("/responsables/{id}")
    @Transactional
    @Operation(summary = "Modificar el nombre y cargo de un responsable")
    public Responsable modificarResponsable(@PathVariable int id, @Valid @RequestBody Responsable r) {
        if (id != r.id()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Los códigos deben coincidir");
        if (jdbc.sql("UPDATE segundo_parcial.responsable SET nombre = :nombre, cargo = :cargo WHERE id = :id")
                .param("nombre", r.nombre().strip()).param("cargo", r.cargo().strip()).param("id", id).update() == 0)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No existe el responsable");
        jdbc.sql("UPDATE segundo_parcial.activo SET revision = revision + 1 WHERE responsable_id = :id").param("id", id).update();
        return r;
    }
}
