package bo.edu.uatf.activos;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;

@Schema(description = "Campos admitidos para modificar un activo en ELABORADO. Envía solamente los que quieras cambiar; los omitidos conservan su valor. El código se indica en la URL.")
public class CamposEditablesActivo {
    @Schema(description = "Descripción del activo. DESCRIP en actual.DBF", example = "Proyector de aula 4", maxLength = 150)
    public String descripcion;

    @Schema(description = "Código adicional. CODIGOSEC en actual.DBF", example = "", maxLength = 15)
    public String codigoAdicional;

    @Schema(description = "Fecha de adquisición; Incorporación en VSIAF. Forma DIA, MES y ANO en actual.DBF. Formato AAAA-MM-DD", example = "2026-11-28")
    public LocalDate fechaAdquisicion;

    @Schema(description = "Nombre exacto de un grupo de GET /api/grupos. Determina CODCONT, depreciar y actualizar", example = "EQUIPO EDUCACIONAL Y RECREATIVO", maxLength = 50)
    public String grupo;

    @Schema(description = "Nombre del auxiliar dentro del grupo; se relaciona con auxiliar.DBF mediante CODAUX", example = "PROYECTORES", maxLength = 50)
    public String auxiliar;

    @Schema(description = "Código de una oficina existente en GET /api/oficinas. CODOFIC en actual.DBF. El responsable elegido debe pertenecer a esta oficina", example = "1", minimum = "1")
    public Integer oficinaId;

    @Schema(description = "Código de un responsable existente de GET /api/responsables. CODRESP en actual.DBF. Puedes usar responsableId o responsable; si envías ambos, deben identificar a la misma persona", example = "1", minimum = "0")
    public Integer responsableId;

    @Schema(description = "Nombre del responsable, como alternativa a responsableId. Si no existe, se registra como hasta ahora. Para elegir a otra persona, cambia su ID y nombre juntos o envía solamente uno", example = "Luis Rojas", maxLength = 35)
    public String responsable;

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

    @Schema(description = "Debe coincidir con depreciar del grupo seleccionado en GET /api/grupos. No se modifica independientemente del grupo", example = "true")
    public Boolean depreciar;

    @Schema(description = "Debe coincidir con actualizar del grupo seleccionado en GET /api/grupos. No se modifica independientemente del grupo", example = "true")
    public Boolean actualizar;

    @Schema(description = "Identificador del bien; RUBE o ID BIEN en VSIAF. COD_RUBE en actual.DBF", example = "", maxLength = 15)
    public String rube;

    @Schema(description = "Código del organismo financiador de VSIAF. ORG_FIN en actual.DBF admite hasta 3 caracteres", example = "", maxLength = 3)
    public String organismoFinanciador;

    @Schema(description = "Número de convenio. NRO_CONV en actual.DBF", example = "", maxLength = 10)
    public String numeroConvenio;

    @Schema(name = "NuevoActivo", description = "Campos que ya admite el alta de un activo. Código, descripción y costo son obligatorios. Los demás pueden omitirse según los valores predeterminados actuales.", requiredProperties = {"codigo", "descripcion", "costoInicial"})
    public static class Nuevo extends CamposEditablesActivo {
        @Schema(description = "Código único del activo nuevo. CODIGO en actual.DBF", example = "PROY-004", maxLength = 15, pattern = "[A-Z0-9][A-Z0-9._-]{0,14}", requiredMode = Schema.RequiredMode.REQUIRED)
        public String codigo;
    }

    private static final String CAMPOS = """
            "descripcion": "Proyector de aula 4",
            "codigoAdicional": "",
            "fechaAdquisicion": "2026-11-28",
            "grupo": "EQUIPO EDUCACIONAL Y RECREATIVO",
            "auxiliar": "PROYECTORES",
            "oficinaId": 1,
            "responsableId": 1,
            "responsable": "Luis Rojas",
            "estadoActivo": "Bueno",
            "observaciones": "",
            "costoInicial": 3500,
            "depreciacionInicial": 0,
            "vidaUtil": 8,
            "depreciar": true,
            "actualizar": true,
            "rube": "",
            "organismoFinanciador": "",
            "numeroConvenio": ""
            """;
    public static final String EJEMPLO_CREAR = "{\"codigo\":\"PROY-004\"," + CAMPOS + "}";
    public static final String EJEMPLO_MODIFICAR = "{" + CAMPOS + "}";
}
