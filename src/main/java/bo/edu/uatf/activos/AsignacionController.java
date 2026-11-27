package bo.edu.uatf.activos;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController
@Tag(name = "Activos fijos")
public class AsignacionController {
    private final JdbcClient jdbc;
    private final SincronizacionVsiaf vsiaf;
    private static final String CONSULTA = """
            SELECT a.codigo, a.descripcion, o.nombre AS oficina, r.nombre AS responsable,
                   r.cargo, a.codigo_adicional, a.fecha_incorporacion, a.grupo, a.auxiliar,
                   a.estado_activo, a.estado_registro, a.observaciones, a.costo_inicial,
                   a.depreciacion_inicial, a.vida_util, a.depreciar, a.actualizar,
                   a.rube, a.organismo_financiador, a.numero_convenio, a.entidad, a.unidad
            FROM segundo_parcial.activo a
            LEFT JOIN segundo_parcial.oficina o ON o.id = a.oficina_id
            LEFT JOIN segundo_parcial.responsable r ON r.id = a.responsable_id
            """;

    public AsignacionController(JdbcClient jdbc, SincronizacionVsiaf vsiaf) {
        this.jdbc = jdbc;
        this.vsiaf = vsiaf;
    }

    @GetMapping("/api/activos")
    @Operation(summary = "Listar todos los activos con sus datos y asignación")
    public List<Asignacion> listar() {
        vsiaf.sincronizar();
        return jdbc.sql(CONSULTA + " ORDER BY a.codigo").query(Asignacion.class).list();
    }

    @GetMapping("/api/activos/{codigo}/asignacion")
    @Operation(summary = "Consultar los datos y la asignación de un activo por código")
    public ResponseEntity<Asignacion> consultar(@Parameter(example = "PROY-001") @PathVariable String codigo) {
        vsiaf.sincronizar();
        var asignacion = jdbc.sql(CONSULTA + " WHERE a.codigo = :codigo")
                .param("codigo", codigo)
                .query(Asignacion.class)
                .optional();
        return ResponseEntity.of(asignacion);
    }
}
