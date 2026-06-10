package co.empresa.vivaeventos.checkin.config;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;

class JwtAuthFilterTest {

    private static final String SECRET = "dGhpcyBpcyBhIHZlcnkgc2VjdXJlIGJhc2U2NCBzZWNyZXQga2V5IGZvciB0ZXN0aW5n";
    private JwtAuthFilter filter;
    private SecretKey key;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthFilter(SECRET);
        byte[] bytes = Decoders.BASE64.decode(SECRET);
        key = Keys.hmacShaKeyFor(bytes);
    }

    @Test
    void shouldNotFilter_actuatorPaths() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRequestURI("/actuator/health");

        boolean result = filter.shouldNotFilter(req);

        assertEquals(true, result);
    }

    @Test
    void shouldNotFilter_optionsMethod() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setMethod("OPTIONS");
        req.setRequestURI("/api/v1/checkin/validate");

        boolean result = filter.shouldNotFilter(req);

        assertEquals(true, result);
    }

    @Test
    void shouldNotFilter_otherPaths() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setMethod("POST");
        req.setRequestURI("/api/v1/checkin/validate");

        boolean result = filter.shouldNotFilter(req);

        assertEquals(false, result);
    }

    @Test
    void doFilter_returns401_whenNoAuthHeader() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRequestURI("/api/v1/checkin/validate");
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(req, res, chain);

        assertEquals(401, res.getStatus());
    }

    @Test
    void doFilter_returns401_whenInvalidToken() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRequestURI("/api/v1/checkin/validate");
        req.addHeader("Authorization", "Bearer invalid-token");
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(req, res, chain);

        assertEquals(401, res.getStatus());
    }

    @Test
    void doFilter_returns403_whenRoleNotAuthorized() throws Exception {
        String token = Jwts.builder()
                .subject("user")
                .claims(Map.of("role", "USER"))
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plusSeconds(60)))
                .signWith(key)
                .compact();

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setMethod("POST");
        req.setRequestURI("/api/v1/checkin/validate");
        req.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(req, res, chain);

        assertEquals(403, res.getStatus());
    }

    @Test
    void doFilter_returns401_whenExpiredToken() throws Exception {
        String token = Jwts.builder()
                .subject("admin")
                .claims(Map.of("role", "ADMIN"))
                .issuedAt(Date.from(Instant.now().minusSeconds(120)))
                .expiration(Date.from(Instant.now().minusSeconds(60)))
                .signWith(key)
                .compact();

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setMethod("POST");
        req.setRequestURI("/api/v1/checkin/validate");
        req.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(req, res, chain);

        assertEquals(401, res.getStatus());
    }

    @Test
    void doFilter_setsAttributesAndContinues_whenValidAdminToken() throws Exception {
        String token = Jwts.builder()
                .subject("admin-user")
                .claims(Map.of("role", "ADMIN"))
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plusSeconds(60)))
                .signWith(key)
                .compact();

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setMethod("POST");
        req.setRequestURI("/api/v1/checkin/validate");
        req.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(req, res, chain);

        assertEquals(200, res.getStatus());
        assertEquals("ADMIN", req.getAttribute("userRole"));
        assertEquals("admin-user", req.getAttribute("username"));
    }

    @Test
    void doFilter_setsAttributesAndContinues_whenValidOrganizerToken() throws Exception {
        String token = Jwts.builder()
                .subject("organizer-user")
                .claims(Map.of("role", "ORGANIZER"))
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plusSeconds(60)))
                .signWith(key)
                .compact();

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setMethod("GET");
        req.setRequestURI("/api/v1/checkin/stats/event/" + java.util.UUID.randomUUID());
        req.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(req, res, chain);

        assertEquals(200, res.getStatus());
    }

    @Test
    void doFilter_setsAttributesAndContinues_whenValidLogisticaToken() throws Exception {
        String token = Jwts.builder()
                .subject("logistica-user")
                .claims(Map.of("role", "LOGISTICA"))
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plusSeconds(60)))
                .signWith(key)
                .compact();

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setMethod("POST");
        req.setRequestURI("/api/v1/checkin/sync");
        req.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(req, res, chain);

        assertEquals(200, res.getStatus());
    }
}
