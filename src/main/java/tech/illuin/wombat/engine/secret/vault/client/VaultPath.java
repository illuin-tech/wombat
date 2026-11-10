package tech.illuin.wombat.engine.secret.vault.client;

import java.util.regex.Pattern;

public final class VaultPath
{
    private static final Pattern SAFE_SEGMENT = Pattern.compile("[A-Za-z0-9._~-]+");

    private VaultPath() {}

    public static String validatePrefix(String prefix)
    {
        if (!isValid(prefix))
            throw new IllegalStateException("wombat.secret.vault.path-prefix must be a relative Vault path");
        return prefix;
    }

    public static String validateMountPath(String mountPath)
    {
        if (!isValid(mountPath))
            throw new IllegalStateException("quarkus.vault.kv-secret-engine-mount-path must be a relative Vault path");
        return mountPath;
    }

    public static String keyPath(String prefix, String key)
    {
        if (!isValid(key))
            throw new VaultInvalidKeyException();
        return prefix + "/" + key;
    }

    private static boolean isValid(String path)
    {
        if (path == null || path.isBlank() || path.startsWith("/") || path.endsWith("/") || path.contains("//"))
            return false;

        for (String segment : path.split("/", -1))
        {
            if (!isValidSegment(segment))
                return false;
        }

        return true;
    }

    public static boolean isValidSegment(String segment)
    {
        return segment != null
            && !segment.equals(".")
            && !segment.equals("..")
            && SAFE_SEGMENT.matcher(segment).matches()
        ;
    }
}
