package bo.edu.uatf.activos;

import com.linuxense.javadbf.DBFReader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class SincronizacionVsiaf {
    private final JdbcClient jdbc;
    private final TransactionTemplate transaccion;
    private final String directorio;
    private final String entidad;
    private final String unidad;

    public SincronizacionVsiaf(JdbcClient jdbc, TransactionTemplate transaccion,
            @Value("${vsiaf.directorio}") String directorio,
            @Value("${vsiaf.entidad}") String entidad,
            @Value("${vsiaf.unidad}") String unidad) {
        this.jdbc = jdbc;
        this.transaccion = transaccion;
        this.directorio = directorio;
        this.entidad = entidad;
        this.unidad = unidad;
    }

    public synchronized void sincronizar() {
        if (directorio.isBlank()) return;
        try {
            var antes = versiones();
            var activos = leer("actual").stream().filter(this::pertenece).toList();
            var oficinas = leer("oficina").stream().filter(this::pertenece).toList();
            var responsables = leer("resp").stream().filter(this::pertenece).toList();
            var grupos = leer("codcont");
            var auxiliares = leer("auxiliar").stream().filter(this::pertenece).toList();
            var estados = leer("estado");
            if (!antes.equals(versiones())) throw new IOException("VSIAF cambió durante la lectura");
            var datos = new ArrayList<Map<String, Object>>();
            for (var a : activos) {
                var codigo = texto(a, "CODIGO");
                if (codigo.isBlank()) throw new IOException("Activo sin código");
                var grupo = buscar(grupos, "CODCONT", a.get("CODCONT"));
                var auxiliar = auxiliares.stream()
                        .filter(x -> Objects.equals(x.get("CODCONT"), a.get("CODCONT")))
                        .filter(x -> Objects.equals(x.get("CODAUX"), a.get("CODAUX")))
                        .findFirst().orElse(Map.of());
                var oficina = buscar(oficinas, "CODOFIC", a.get("CODOFIC"));
                var responsable = responsables.stream()
                        .filter(x -> Objects.equals(x.get("CODOFIC"), a.get("CODOFIC")))
                        .filter(x -> Objects.equals(x.get("CODRESP"), a.get("CODRESP")))
                        .findFirst().orElse(Map.of());
                var p = new LinkedHashMap<String, Object>();
                p.put("codigo", codigo);
                p.put("descripcion", texto(a, "DESCRIP"));
                p.put("oficina_id", oficina.get("CODOFIC"));
                p.put("responsable_id", responsable.get("CODRESP"));
                p.put("codigo_adicional", texto(a, "CODIGOSEC"));
                p.put("fecha_incorporacion", LocalDate.of(numero(a, "ANO"), numero(a, "MES"), numero(a, "DIA")));
                p.put("grupo", texto(grupo, "NOMBRE"));
                p.put("auxiliar", texto(auxiliar, "NOMAUX"));
                p.put("estado_activo", texto(buscar(estados, "CODESTADO", a.get("CODESTADO")), "NOMESTADO"));
                p.put("estado_registro", numero(a, "API_ESTADO") == 1 ? "ELABORADO" : "Código " + numero(a, "API_ESTADO"));
                p.put("observaciones", texto(a, "OBSERV"));
                p.put("costo_inicial", a.get("COSTO"));
                p.put("depreciacion_inicial", a.get("DEPACU"));
                p.put("vida_util", a.get("VIDAUTIL"));
                p.put("depreciar", grupo.get("DEPRECIAR"));
                p.put("actualizar", grupo.get("ACTUALIZAR"));
                p.put("rube", texto(a, "COD_RUBE"));
                p.put("organismo_financiador", texto(a, "ORG_FIN"));
                p.put("numero_convenio", texto(a, "NRO_CONV"));
                p.put("entidad", entidad);
                p.put("unidad", unidad);
                p.put("origen_vsiaf", true);
                datos.add(p);
            }
            if (datos.stream().map(a -> a.get("codigo")).distinct().count() != datos.size()) {
                throw new IOException("Códigos duplicados en VSIAF");
            }
            transaccion.executeWithoutResult(estado -> {
                for (var o : oficinas) {
                    jdbc.sql("""
                            INSERT INTO segundo_parcial.oficina (id, nombre) VALUES (:id, :nombre)
                            ON CONFLICT (id) DO UPDATE SET nombre = EXCLUDED.nombre
                            """).param("id", o.get("CODOFIC")).param("nombre", texto(o, "NOMOFIC")).update();
                }
                for (var r : responsables) {
                    jdbc.sql("""
                            INSERT INTO segundo_parcial.responsable (id, nombre, cargo) VALUES (:id, :nombre, :cargo)
                            ON CONFLICT (id) DO UPDATE SET nombre = EXCLUDED.nombre, cargo = EXCLUDED.cargo
                            """).param("id", r.get("CODRESP")).param("nombre", texto(r, "NOMRESP"))
                            .param("cargo", texto(r, "CARGO")).update();
                }
                for (var p : datos) {
                    var columnas = String.join(", ", p.keySet());
                    var parametros = p.keySet().stream().map(k -> ":" + k).collect(Collectors.joining(", "));
                    var cambios = p.keySet().stream().filter(k -> !k.equals("codigo"))
                            .map(k -> k + " = EXCLUDED." + k).collect(Collectors.joining(", "));
                    jdbc.sql("INSERT INTO segundo_parcial.activo (" + columnas + ") VALUES (" + parametros
                            + ") ON CONFLICT (codigo) DO UPDATE SET " + cambios).params(p).update();
                }
                var borrar = "DELETE FROM segundo_parcial.activo WHERE origen_vsiaf AND entidad = :entidad AND unidad = :unidad";
                if (!datos.isEmpty()) borrar += " AND codigo NOT IN (:codigos)";
                var consulta = jdbc.sql(borrar).param("entidad", entidad).param("unidad", unidad);
                if (!datos.isEmpty()) consulta = consulta.param("codigos", datos.stream().map(a -> a.get("codigo")).toList());
                consulta.update();
            });
        } catch (IOException | IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "No se pudo leer VSIAF. Guarda los cambios y vuelve a consultar.", ex);
        }
    }

    private List<Map<String, Object>> leer(String nombre) throws IOException {
        var archivo = archivo(nombre + ".dbf");
        var filas = new ArrayList<Map<String, Object>>();
        try (var entrada = Files.newInputStream(archivo); var lector = new DBFReader(entrada)) {
            if (List.of("actual", "oficina", "resp", "auxiliar", "codcont").contains(nombre)) {
                lector.setMemoFile(archivo(nombre + ".fpt").toFile());
            }
            Object[] valores;
            while ((valores = lector.nextRecord()) != null) {
                var fila = new HashMap<String, Object>();
                for (int i = 0; i < lector.getFieldCount(); i++) fila.put(lector.getField(i).getName(), valores[i]);
                filas.add(fila);
            }
        }
        return filas;
    }

    private Path archivo(String nombre) throws IOException {
        try (var archivos = Files.list(Path.of(directorio))) {
            return archivos.filter(p -> p.getFileName().toString().equalsIgnoreCase(nombre))
                    .findFirst().orElseThrow(() -> new IOException("Falta " + nombre));
        }
    }

    private Map<String, String> versiones() throws IOException {
        var resultado = new HashMap<String, String>();
        for (var nombre : List.of("actual.dbf", "actual.fpt", "oficina.dbf", "oficina.fpt", "resp.dbf",
                "resp.fpt", "auxiliar.dbf", "auxiliar.fpt", "codcont.dbf", "codcont.fpt", "estado.dbf")) {
            var p = archivo(nombre);
            resultado.put(nombre, Files.getLastModifiedTime(p) + ":" + Files.size(p));
        }
        return resultado;
    }

    private boolean pertenece(Map<String, Object> fila) {
        return entidad.equals(texto(fila, "ENTIDAD")) && unidad.equals(texto(fila, "UNIDAD"));
    }

    private static Map<String, Object> buscar(List<Map<String, Object>> filas, String campo, Object valor) {
        return filas.stream().filter(f -> Objects.equals(f.get(campo), valor)).findFirst().orElse(Map.of());
    }

    private static String texto(Map<String, Object> fila, String campo) {
        return Objects.toString(fila.get(campo), "").strip();
    }

    private static int numero(Map<String, Object> fila, String campo) {
        return ((Number) fila.get(campo)).intValue();
    }
}
