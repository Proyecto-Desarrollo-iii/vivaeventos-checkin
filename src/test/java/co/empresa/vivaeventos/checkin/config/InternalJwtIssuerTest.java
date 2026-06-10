package co.empresa.vivaeventos.checkin.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class InternalJwtIssuerTest {

    private static final String SECRET = "dGhpcyBpcyBhIHZlcnkgc2VjdXJlIGJhc2U2NCBzZWNyZXQga2V5IGZvciB0ZXN0aW5n";
    private InternalJwtIssuer issuer;
    private SecretKey key;

    @BeforeEach
    void setUp() {
        issuer = new InternalJwtIssuer(SECRET, 300);
        byte[] bytes = Decoders.BASE64.decode(SECRET);
        key = Keys.hmacShaKeyFor(bytes);
    }

    @Test
    void issueServiceToken_createsValidJwt_withAdminRole() {
        String token = issuer.issueServiceToken();

        assertNotNull(token);

        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        assertEquals("checkin-service", claims.getSubject());
        assertEquals("ADMIN", claims.get("role"));
        assertNotNull(claims.getIssuedAt());
        assertNotNull(claims.getExpiration());
    }
}
