package bo.edu.uatf.activos;

import java.math.BigDecimal;
import java.time.LocalDate;

public record Asignacion(
        String codigo, String descripcion, String oficina, String responsable,
        String cargo, String codigoAdicional, LocalDate fechaIncorporacion,
        String grupo, String auxiliar, String estadoActivo, String estadoRegistro,
        String observaciones, BigDecimal costoInicial, BigDecimal depreciacionInicial,
        BigDecimal vidaUtil, Boolean depreciar, Boolean actualizar,
        String rube, String organismoFinanciador, String numeroConvenio,
        String entidad, String unidad) {
}
