package bo.edu.uatf.activos;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Hidden
@RestController
@RequestMapping("/api/activos")
public class RegistroDbf {
    public static final List<String> CAMPOS = Arrays.asList(("UNIDAD ENTIDAD CODIGO CODCONT CODAUX VIDAUTIL DESCRIP COSTO DEPACU MES ANO B_REV DIA CODOFIC CODRESP OBSERV DIA_ANT MES_ANT ANO_ANT VUT_ANT COSTO_ANT BAND_UFV CODESTADO COD_RUBE NRO_CONV ORG_FIN FEULT USUAR API_ESTADO CODIGOSEC BANDERAS FEC_MOD USU_MOD").split(" "));
    private final JdbcClient jdbc;
    private final ObjectMapper json;
    public record Estado(String registro, Long version, Long revision) { }

    public RegistroDbf(JdbcClient jdbc, ObjectMapper json) { this.jdbc = jdbc; this.json = json; }

    public Map<String, Object> obtener(String codigo) {
        var estado = jdbc.sql("SELECT registro_dbf::text AS registro, dbf_revision AS version, revision FROM segundo_parcial.activo WHERE codigo = :codigo")
                .param("codigo", codigo).query(Estado.class).optional()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No existe el activo " + codigo));
        if (estado.registro() == null || !estado.revision().equals(estado.version()))
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "El detalle DBF está pendiente de confirmar en FoxPro. Mantén el conector abierto y vuelve a consultar en unos segundos");
        try {
            var guardado = json.readValue(estado.registro(), new TypeReference<Map<String, Object>>() { });
            var ordenado = new LinkedHashMap<String, Object>();
            CAMPOS.forEach(c -> ordenado.put(c, guardado.get(c)));
            return ordenado;
        } catch (java.io.IOException error) { throw new IllegalStateException(error); }
    }

    @PutMapping("/{codigo}/dbf")
    @Transactional
    public void confirmar(@PathVariable String codigo, @RequestBody Map<String, Object> datos,
            @RequestHeader("If-Match") String revision) throws java.io.IOException {
        var actual = jdbc.sql("SELECT revision FROM segundo_parcial.activo WHERE codigo = :codigo FOR UPDATE")
                .param("codigo", codigo).query(Long.class).optional()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No existe el activo"));
        if (!revision.equals("\"" + actual + "\""))
            throw new ResponseStatusException(HttpStatus.PRECONDITION_FAILED, "El activo cambió; vuelve a leer FoxPro");
        if (!codigo.equals(datos.get("CODIGO")) || datos.size() != CAMPOS.size() || !datos.keySet().containsAll(CAMPOS))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Se requieren las 33 columnas de actual.DBF y el mismo código");
        jdbc.sql("UPDATE segundo_parcial.activo SET registro_dbf = CAST(:registro AS jsonb), dbf_revision = revision WHERE codigo = :codigo")
                .param("registro", json.writeValueAsString(datos)).param("codigo", codigo).update();
    }
}
