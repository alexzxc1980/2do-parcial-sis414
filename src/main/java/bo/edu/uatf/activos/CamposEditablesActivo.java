package bo.edu.uatf.activos;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;

@Schema(description = "Datos propios del activo en ELABORADO. Envía solamente los que quieras cambiar; los omitidos conservan su valor. El código se indica en la URL. Las asignaciones y los catálogos se conservan.")
public class CamposEditablesActivo {
    @Schema(description = "Descripción del activo. DESCRIP en actual.DBF", example = "Proyector de aula 4", maxLength = 150)
    public String descripcion;

    @Schema(description = "Código adicional. CODIGOSEC en actual.DBF", example = "", maxLength = 15)
    public String codigoAdicional;

    @Schema(description = "Fecha de adquisición; Incorporación en VSIAF. Forma DIA, MES y ANO en actual.DBF. Formato AAAA-MM-DD", example = "2026-11-28")
    public LocalDate fechaAdquisicion;

    @Schema(description = "Estado físico del activo; se relaciona con CODESTADO", example = "Bueno", allowableValues = {"Bueno", "Regular", "Malo"})
    public String estadoActivo;

    @Schema(description = "Observaciones del activo. OBSERV en actual.DBF", example = "", maxLength = 4000)
    public String observaciones;

    @Schema(description = "Costo inicial. COSTO en actual.DBF", example = "3500", minimum = "0", maximum = "999999999999.99")
    public BigDecimal costoInicial;

    @Schema(description = "Depreciación acumulada inicial. DEPACU en actual.DBF. No puede superar el costo inicial", example = "0", minimum = "0", maximum = "999999999999.99")
    public BigDecimal depreciacionInicial;

    @Schema(description = "Vida útil en años, mayor que cero. VIDAUTIL en actual.DBF", example = "8", minimum = "0", exclusiveMinimum = true, maximum = "999.99")
    public BigDecimal vidaUtil;

    @Schema(description = "Identificador del bien; RUBE o ID BIEN en VSIAF. COD_RUBE en actual.DBF", example = "", maxLength = 15)
    public String rube;

    @Schema(description = "Número de convenio. NRO_CONV en actual.DBF", example = "", maxLength = 10)
    public String numeroConvenio;

    @Schema(name = "NuevoActivo", description = "Datos propios del activo nuevo. Código, descripción y costo son obligatorios. La oficina, el responsable, el grupo y el auxiliar se toman del último activo registrado, como hasta ahora.", requiredProperties = {"codigo", "descripcion", "costoInicial"})
    public static class Nuevo extends CamposEditablesActivo {
        @Schema(description = "Código único del activo nuevo. CODIGO en actual.DBF", example = "PROY-004", maxLength = 15, pattern = "[A-Z0-9][A-Z0-9._-]{0,14}", requiredMode = Schema.RequiredMode.REQUIRED)
        public String codigo;
    }

    private static final String CAMPOS = """
            "descripcion": "Proyector de aula 4",
            "codigoAdicional": "",
            "fechaAdquisicion": "2026-11-28",
            "estadoActivo": "Bueno",
            "observaciones": "",
            "costoInicial": 3500,
            "depreciacionInicial": 0,
            "vidaUtil": 8,
            "rube": "",
            "numeroConvenio": ""
            """;
    public static final String EJEMPLO_CREAR = "{\"codigo\":\"PROY-004\"," + CAMPOS + "}";
    public static final String EJEMPLO_MODIFICAR = "{" + CAMPOS + "}";
}
