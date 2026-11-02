package tech.illuin.wombat.security.apikey;

import io.smallrye.jwt.build.Jwt;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.jwt.Claims;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.illuin.wombat.security.AuthProperties;

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.time.Instant;
import java.util.Base64;

@ApplicationScoped
public class ApiKeySigningKey
{
    public static final String ISSUER = "wombat";
    public static final String AUDIENCE = "wombat-api";
    // Quarkus requires an exp claim on bearer tokens: keys that never expire get one no client will reach, the api_keys row decides
    static final Instant NEVER = Instant.parse("9999-12-31T23:59:59Z");

    private static final Logger logger = LoggerFactory.getLogger(ApiKeySigningKey.class);

    private final KeyPair keyPair;

    public ApiKeySigningKey(AuthProperties properties)
    {
        this.keyPair = properties.apiKeys().signingKey()
            .map(ApiKeySigningKey::fromPem)
            .orElseGet(ApiKeySigningKey::generate);
    }

    public String sign(ApiKeyEntity key, String subject)
    {
        return Jwt.issuer(ISSUER)
            .audience(AUDIENCE)
            .subject(subject)
            .claim(Claims.jti.name(), key.id)
            .issuedAt(key.createdAt)
            .expiresAt(key.expiresAt == null ? NEVER : key.expiresAt)
            .sign(this.keyPair.getPrivate());
    }

    public String publicKey()
    {
        return Base64.getEncoder().encodeToString(this.keyPair.getPublic().getEncoded());
    }

    byte[] privateKeyMaterial()
    {
        return this.keyPair.getPrivate().getEncoded();
    }

    private static KeyPair fromPem(String pem)
    {
        try {
            String body = pem.replaceAll("-----[A-Z ]+-----", "").replaceAll("\\s", "");
            KeyFactory factory = KeyFactory.getInstance("RSA");
            PrivateKey privateKey = factory.generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(body)));
            if (!(privateKey instanceof RSAPrivateCrtKey crt))
                throw new IllegalStateException("auth.api-keys.signing-key must be an RSA private key with its public exponent");

            PublicKey publicKey = factory.generatePublic(new RSAPublicKeySpec(crt.getModulus(), crt.getPublicExponent()));
            return new KeyPair(publicKey, privateKey);
        }
        catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("auth.api-keys.signing-key is not a PKCS#8 PEM RSA private key", e);
        }
    }

    private static KeyPair generate()
    {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            logger.warn("No auth.api-keys.signing-key configured, using a temporary one: API keys will stop working on restart");
            return generator.generateKeyPair();
        }
        catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
