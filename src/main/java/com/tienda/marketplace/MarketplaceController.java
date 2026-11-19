package com.tienda.marketplace;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class MarketplaceController {

    @Autowired
    private JdbcTemplate jdbc;

    @GetMapping("/configuracion")
    public Map<String, Object> getConfiguracion() {
        return jdbc.queryForObject(
                "SELECT ID, NOMBRE_TIENDA, TEXTO_BANNER, BANNER_ACTIVO, COMISION_PORCENTAJE FROM CONFIGURACION WHERE ID = 1",
                (rs, rowNum) -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("nombreTienda", rs.getString("NOMBRE_TIENDA"));
                    map.put("textoBanner", rs.getString("TEXTO_BANNER"));
                    map.put("bannerActivo", rs.getInt("BANNER_ACTIVO") == 1);
                    map.put("comisionPorcentaje", rs.getDouble("COMISION_PORCENTAJE"));
                    return map;
                }
        );
    }

    @PutMapping("/configuracion")
    public ResponseEntity<?> updateConfiguracion(@RequestBody Map<String, Object> body) {
        String nombre = String.valueOf(body.get("nombreTienda"));
        String banner = String.valueOf(body.get("textoBanner"));
        int bannerActivo = Boolean.TRUE.equals(body.get("bannerActivo")) ? 1 : 0;
        double comision = ((Number) body.get("comisionPorcentaje")).doubleValue();

        jdbc.update(
                "UPDATE CONFIGURACION SET NOMBRE_TIENDA = ?, TEXTO_BANNER = ?, BANNER_ACTIVO = ?, COMISION_PORCENTAJE = ? WHERE ID = 1",
                nombre, banner, bannerActivo, comision
        );
        return ResponseEntity.ok(Map.of("mensaje", "Configuracion actualizada"));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        String password = body.get("password");

        var users = jdbc.query(
                "SELECT ID, NOMBRE, EMAIL, ROL, ACTIVO FROM USUARIOS WHERE LOWER(EMAIL) = LOWER(?) AND PASSWORD = ?",
                (rs, rowNum) -> {
                    Map<String, Object> u = new HashMap<>();
                    u.put("id", rs.getLong("ID"));
                    u.put("nombre", rs.getString("NOMBRE"));
                    u.put("email", rs.getString("EMAIL"));
                    u.put("rol", rs.getString("ROL"));
                    u.put("activo", rs.getInt("ACTIVO") == 1);
                    return u;
                },
                email, password
        );

        if (users.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Credenciales incorrectas"));
        }
        Map<String, Object> user = users.get(0);
        if (!(Boolean) user.get("activo")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Cuenta suspendida por el administrador"));
        }
        return ResponseEntity.ok(user);
    }

    @PostMapping("/usuarios")
    public ResponseEntity<?> registrarUsuario(@RequestBody Map<String, String> body) {
        String nombre = body.get("nombre");
        String email = body.get("email");
        String password = body.get("password");

        Integer existe = jdbc.queryForObject(
                "SELECT COUNT(*) FROM USUARIOS WHERE LOWER(EMAIL) = LOWER(?)", Integer.class, email
        );
        if (existe != null && existe > 0) {
            return ResponseEntity.badRequest().body(Map.of("error", "El correo ya esta registrado"));
        }

        jdbc.update(
                "INSERT INTO USUARIOS (NOMBRE, EMAIL, PASSWORD, ROL, ACTIVO) VALUES (?, ?, ?, 'USER', 1)",
                nombre, email, password
        );

        Map<String, Object> creado = jdbc.queryForObject(
                "SELECT ID, NOMBRE, EMAIL, ROL, ACTIVO FROM USUARIOS WHERE LOWER(EMAIL) = LOWER(?)",
                (rs, rowNum) -> {
                    Map<String, Object> u = new HashMap<>();
                    u.put("id", rs.getLong("ID"));
                    u.put("nombre", rs.getString("NOMBRE"));
                    u.put("email", rs.getString("EMAIL"));
                    u.put("rol", rs.getString("ROL"));
                    u.put("activo", rs.getInt("ACTIVO") == 1);
                    return u;
                },
                email
        );
        return ResponseEntity.ok(creado);
    }

    @GetMapping("/usuarios")
    public ResponseEntity<?> listarUsuarios() {
        var lista = jdbc.query(
                "SELECT ID, NOMBRE, EMAIL, ROL, ACTIVO, TO_CHAR(FECHA_REGISTRO, 'YYYY-MM-DD') AS FECHA FROM USUARIOS ORDER BY ID ASC",
                (rs, rowNum) -> {
                    Map<String, Object> u = new HashMap<>();
                    u.put("id", rs.getLong("ID"));
                    u.put("nombre", rs.getString("NOMBRE"));
                    u.put("email", rs.getString("EMAIL"));
                    u.put("rol", rs.getString("ROL"));
                    u.put("activo", rs.getInt("ACTIVO") == 1);
                    u.put("fechaRegistro", rs.getString("FECHA"));
                    return u;
                }
        );
        return ResponseEntity.ok(lista);
    }

    @PatchMapping("/usuarios/{id}/estado")
    public ResponseEntity<?> toggleEstadoUsuario(@PathVariable Long id) {
        jdbc.update("UPDATE USUARIOS SET ACTIVO = CASE WHEN ACTIVO = 1 THEN 0 ELSE 1 END WHERE ID = ? AND ROL <> 'ADMIN'", id);
        return ResponseEntity.ok(Map.of("mensaje", "Estado de usuario actualizado"));
    }

    @GetMapping("/publicaciones")
    public ResponseEntity<?> listarPublicaciones() {
        String sql = """
            SELECT p.ID, p.VENDEDOR_ID, u.NOMBRE AS VENDEDOR_NOMBRE, p.TITULO, p.DESCRIPCION,
                   p.PRECIO, p.CATEGORIA, p.CONDICION, p.UBICACION, p.IMAGEN, p.ESTADO,
                   TO_CHAR(p.FECHA_CREACION, 'YYYY-MM-DD HH24:MI') AS FECHA
            FROM PUBLICACIONES p
            JOIN USUARIOS u ON p.VENDEDOR_ID = u.ID
            ORDER BY p.ID DESC
        """;
        var lista = jdbc.query(sql, (rs, rowNum) -> {
            Map<String, Object> p = new HashMap<>();
            p.put("id", rs.getLong("ID"));
            p.put("vendedorId", rs.getLong("VENDEDOR_ID"));
            p.put("vendedorNombre", rs.getString("VENDEDOR_NOMBRE"));
            p.put("titulo", rs.getString("TITULO"));
            p.put("descripcion", rs.getString("DESCRIPCION"));
            p.put("precio", rs.getDouble("PRECIO"));
            p.put("categoria", rs.getString("CATEGORIA"));
            p.put("condicion", rs.getString("CONDICION"));
            p.put("ubicacion", rs.getString("UBICACION"));
            p.put("imagen", rs.getString("IMAGEN"));
            p.put("estado", rs.getString("ESTADO"));
            p.put("fechaCreacion", rs.getString("FECHA"));
            return p;
        });
        return ResponseEntity.ok(lista);
    }

    @PostMapping("/publicaciones")
    public ResponseEntity<?> crearPublicacion(@RequestBody Map<String, Object> body) {
        long vendedorId = ((Number) body.get("vendedorId")).longValue();
        String titulo = String.valueOf(body.get("titulo"));
        String descripcion = String.valueOf(body.get("descripcion"));
        double precio = ((Number) body.get("precio")).doubleValue();
        String categoria = String.valueOf(body.get("categoria"));
        String condicion = String.valueOf(body.get("condicion"));
        String ubicacion = String.valueOf(body.get("ubicacion"));
        String imagen = body.get("imagen") != null ? String.valueOf(body.get("imagen")) : "";

        jdbc.update(
                "INSERT INTO PUBLICACIONES (VENDEDOR_ID, TITULO, DESCRIPCION, PRECIO, CATEGORIA, CONDICION, UBICACION, IMAGEN, ESTADO) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'ACTIVO')",
                vendedorId, titulo, descripcion, precio, categoria, condicion, ubicacion, imagen
        );
        return ResponseEntity.ok(Map.of("mensaje", "Publicacion guardada en Oracle"));
    }

    @PatchMapping("/publicaciones/{id}/vender")
    public ResponseEntity<?> marcarVendido(@PathVariable Long id) {
        jdbc.update("UPDATE PUBLICACIONES SET ESTADO = 'VENDIDO', FECHA_VENTA = CURRENT_TIMESTAMP WHERE ID = ?", id);
        return ResponseEntity.ok(Map.of("mensaje", "Articulo marcado como vendido"));
    }

    @DeleteMapping("/publicaciones/{id}")
    public ResponseEntity<?> eliminarPublicacion(@PathVariable Long id) {
        jdbc.update("DELETE FROM PUBLICACIONES WHERE ID = ?", id);
        return ResponseEntity.ok(Map.of("mensaje", "Publicacion eliminada"));
    }
}