package co.empresa.vivaeventos.checkin.config;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;
import java.util.Map;

@Component
public class InternalJwtIssuer {

    private final SecretKey key;
    private final long ttlSeconds;

    public InternalJwtIssuer(@Value("${jwt.secret}") String secret,
                             @Value("${checkin.internal-token.ttl-seconds:300}") long ttlSeconds) {
        byte[] bytes = Decoders.BASE64.decode(secret);
        this.key = Keys.hmacShaKeyFor(bytes);
        this.ttlSeconds = ttlSeconds;
    }

    public String issueServiceToken() {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject("checkin-service")
                .claims(Map.of("role", "ADMIN"))
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(ttlSeconds)))
                .signWith(key)
                .compact();
    }
}
