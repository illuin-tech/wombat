package tech.illuin.wombat.module.extension;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.illuin.wombat.core.module.WombatModule;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

import static java.util.Collections.emptyList;

public class DynamicExtensionLoader implements ExtensionLoader
{
    private final Path extensionsPath;
    private final ClassLoader parent;
    private URLClassLoader classLoader;
    private List<WombatModule> loadedModules;

    private static final Logger logger = LoggerFactory.getLogger(DynamicExtensionLoader.class);

    public DynamicExtensionLoader(Path extensionsPath)
    {
        this(extensionsPath, WombatModule.class.getClassLoader());
    }

    DynamicExtensionLoader(Path extensionsPath, ClassLoader parent)
    {
        if (extensionsPath == null)
            throw new IllegalArgumentException("extensionsPath must not be null");

        this.extensionsPath = extensionsPath;
        this.parent = parent;
    }

    @Override
    public synchronized List<WombatModule> load()
    {
        if (this.loadedModules != null)
            return this.loadedModules;
        if (!Files.exists(this.extensionsPath))
        {
            logger.warn("Extensions directory {} does not exist, skipping extension loading", this.extensionsPath);
            return this.setAndGet(emptyList());
        }
        if (!Files.isDirectory(this.extensionsPath))
        {
            logger.warn("Extensions path {} is not a directory, skipping extension loading", this.extensionsPath);
            return this.setAndGet(emptyList());
        }

        try {
            URL[] jarUrls = this.gatherJARURLs();
            logger.info("Loading extensions from {} ({} jar(s))", this.extensionsPath.toAbsolutePath(), jarUrls.length);

            this.classLoader = new ExtensionClassLoader(jarUrls, this.parent);
            verifyCoreVisibility(this.classLoader);

            ServiceLoader<WombatModule> serviceLoader = ServiceLoader.load(WombatModule.class, this.classLoader);

            List<WombatModule> modules = new ArrayList<>();
            Set<String> seenTypes = new HashSet<>();

            for (WombatModule module : serviceLoader)
            {
                if (module.type() == null || module.type().name().isBlank())
                    throw new IllegalArgumentException("Extension module " + module.getClass().getName() + " has invalid AssetType");
                if (module.assetClass() == null)
                    throw new IllegalArgumentException("Extension module " + module.getClass().getName() + " has null assetClass");

                String typeName = module.type().name();
                if (!seenTypes.add(typeName))
                    throw new IllegalArgumentException("Duplicate extension module registered for asset-type: " + typeName);

                ExtensionContractCheck.check(module).ifPresent(logger::warn);

                modules.add(module);
                logger.info("Discovered extension module {} for asset-type {}", module.getClass().getName(), typeName);
            }

            return this.setAndGet(Collections.unmodifiableList(modules));
        }
        catch (IOException e) {
            logger.error("Failed to list files in extensions directory {}", this.extensionsPath, e);
            return this.setAndGet(emptyList());
        }
    }

    private List<WombatModule> setAndGet(List<WombatModule> modules)
    {
        this.loadedModules = modules;
        return this.loadedModules;
    }

    private URL[] gatherJARURLs() throws IOException
    {
        try (Stream<Path> stream = Files.list(this.extensionsPath))
        {
            return stream
                .filter(Files::isRegularFile)
                .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar"))
                .map(Path::toUri)
                .map(DynamicExtensionLoader::convertToURL)
                .filter(Objects::nonNull)
                .toArray(URL[]::new);
        }
    }

    private static URL convertToURL(URI uri)
    {
        try {
            return uri.toURL();
        }
        catch (MalformedURLException e) {
            logger.error("Failed to convert path {} to URL", uri, e);
            return null;
        }
    }

    /**
     * Fails fast when the extension classloader does not resolve {@link WombatModule} to the app's own class: extension
     * objects would otherwise fail later with a {@link ClassCastException} on core types.
     */
    private static void verifyCoreVisibility(ClassLoader extensionClassLoader)
    {
        String coreClassName = WombatModule.class.getName();
        try {
            Class<?> resolved = extensionClassLoader.loadClass(coreClassName);
            if (resolved != WombatModule.class)
                throw new IllegalStateException("Extension classloader resolves " + coreClassName + " from " + resolved.getClassLoader()
                    + " instead of the app classloader " + WombatModule.class.getClassLoader() + ": extensions would not be castable to core types");
        }
        catch (ClassNotFoundException e) {
            throw new IllegalStateException("Extension classloader " + extensionClassLoader + " cannot see " + coreClassName
                + " defined by the app classloader " + WombatModule.class.getClassLoader(), e);
        }
    }

    @Override
    public synchronized void close() throws IOException
    {
        if (this.classLoader != null)
        {
            this.classLoader.close();
            this.classLoader = null;
        }
    }

    private static final class ExtensionClassLoader extends URLClassLoader
    {
        private static final String SERVICES_PREFIX = "META-INF/services/";

        static {
            ClassLoader.registerAsParallelCapable();
        }

        private ExtensionClassLoader(URL[] urls, ClassLoader parent)
        {
            super(urls, parent);
        }

        @Override
        public Enumeration<URL> getResources(String name) throws IOException
        {
            if (name.startsWith(SERVICES_PREFIX))
                return findResources(name);
            return super.getResources(name);
        }
    }
}
