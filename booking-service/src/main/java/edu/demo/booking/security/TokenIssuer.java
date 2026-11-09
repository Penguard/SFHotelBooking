package edu.demo.booking.security;

import edu.demo.booking.api.AuthDtos.TokenView;
import edu.demo.booking.domain.User;
import java.time.Instant;
import javax.crypto.SecretKey;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;

@Service
public class TokenIssuer {
    private final JwtEncoder encoder;
    public TokenIssuer(JwtEncoder encoder) { this.encoder = encoder; }
    private String issue(String subject, String role) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer("hotel-booking-demo")
            .issuedAt(now).expiresAt(now.plusSeconds(3600)).subject(subject).claim("role", role).build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
    public TokenView userToken(User user) {
        return new TokenView(issue(user.getId().toString(), user.getRole().name()), "Bearer", 3600,
            user.getId(), user.getRole().name());
    }
    public String serviceToken() { return issue("booking-service", "SERVICE"); }
}

@Configuration
class EncoderConfiguration {
    @Bean JwtEncoder jwtEncoder(SecretKey jwtKey) { return new NimbusJwtEncoder(new ImmutableSecret<>(jwtKey)); }
}
