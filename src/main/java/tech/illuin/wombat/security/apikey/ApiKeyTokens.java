package tech.illuin.wombat.security.apikey;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

public final class ApiKeyTokens
{
    /** Tells API keys apart from identity provider tokens, and lets secret scanners spot leaked ones. */
    public static final String PREFIX = "wmb_";
    private static final int RANDOM_BYTES = 32;
    private static final SecureRandom RANDOM = new SecureRandom();

    private ApiKeyTokens() {}

    public static String generate()
    {
        byte[] bytes = new byte[RANDOM_BYTES];
        RANDOM.nextBytes(bytes);
        return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static boolean isApiKey(String token)
    {
        return token != null && token.startsWith(PREFIX);
    }

    public static String hash(String token)
    {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        }
        catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
