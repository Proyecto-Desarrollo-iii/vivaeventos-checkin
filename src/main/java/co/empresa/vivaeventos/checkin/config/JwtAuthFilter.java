package co.empresa.vivaeventos.checkin.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.crypto.SecretKey;
import java.io.IOException;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final SecretKey key;

    public JwtAuthFilter(@Value("${jwt.secret}") String secret) {
        byte[] bytes = Decoders.BASE64.decode(secret);
        this.key = Keys.hmacShaKeyFor(bytes);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest req) {
        String path = req.getRequestURI();
        return path.startsWith("/actuator") || "OPTIONS".equalsIgnoreCase(req.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req,
                                    HttpServletResponse res,
                                    FilterChain chain) throws ServletException, IOException {
        String auth = req.getHeader("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) {
            writeError(res, HttpStatus.UNAUTHORIZED, "Token JWT requerido");
            return;
        }

        String token = auth.substring(7);
        Claims claims;
        try {
            claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            writeError(res, HttpStatus.UNAUTHORIZED, "Token expirado");
            return;
        } catch (JwtException | IllegalArgumentException e) {
            writeError(res, HttpStatus.UNAUTHORIZED, "Token JWT invalido");
            return;
        }

        String role = claims.get("role", String.class);
        String username = claims.getSubject();

        if (!hasAccess(req, role)) {
            writeError(res, HttpStatus.FORBIDDEN,
                    "Rol '" + role + "' no autorizado para " + req.getMethod() + " " + req.getRequestURI());
            return;
        }

        req.setAttribute("userRole", role);
        req.setAttribute("username", username);
        chain.doFilter(req, res);
    }

    /**
     * Politica de acceso por endpoint:
     *  - POST /validate         -> ORGANIZER, ADMIN o LOGISTICA (logistica de puerta)
     *  - POST /sync             -> ORGANIZER, ADMIN o LOGISTICA (sincronizacion offline)
     *  - GET /validations/**    -> ORGANIZER, ADMIN o LOGISTICA
     */
    private boolean hasAccess(HttpServletRequest req, String role) {
        if (role == null || role.isBlank()) return false;
        String path = req.getRequestURI();
        String method = req.getMethod();

        if (!path.startsWith("/api/v1/checkin")) {
            return false;
        }

        boolean isAdmin = "ADMIN".equalsIgnoreCase(role);
        boolean isOrganizer = "ORGANIZER".equalsIgnoreCase(role);
        boolean isLogistica = "LOGISTICA".equalsIgnoreCase(role);

        if ("POST".equalsIgnoreCase(method) || "GET".equalsIgnoreCase(method)) {
            return isAdmin || isOrganizer || isLogistica;
        }
        return false;
    }

    private void writeError(HttpServletResponse res, HttpStatus status, String message) throws IOException {
        res.setStatus(status.value());
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        res.getWriter().write("{\"error\":\"" + message.replace("\"", "\\\"") + "\"}");
    }
}
