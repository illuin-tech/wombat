package tech.illuin.wombat.module.extension;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tech.illuin.wombat.core.asset.type.ActivityRegime;
import tech.illuin.wombat.core.asset.Asset;
import tech.illuin.wombat.core.asset.AssetIdentity;
import tech.illuin.wombat.core.asset.type.AssetType;
import tech.illuin.wombat.core.asset.type.ServiceFamily;
import tech.illuin.wombat.core.asset.profile.LLMProfile;
import tech.illuin.wombat.core.asset.profile.LLMProvider;
import tech.illuin.wombat.core.asset.profile.AssetProfile;
import tech.illuin.wombat.core.module.WombatModule;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class DynamicExtensionLoaderTest
{
    @Test
    void loadExtensions_nonExistentDirectory_returnsEmptyList(@TempDir Path tempDir)
    {
        Path nonExistent = tempDir.resolve("does-not-exist");
        try (DynamicExtensionLoader loader = new DynamicExtensionLoader(nonExistent))
        {
            List<WombatModule> modules = loader.load();
            assertTrue(modules.isEmpty());
        }
        catch (IOException e) {
            fail(e);
        }
    }

    @Test
    void loadExtensions_emptyDirectory_returnsEmptyList(@TempDir Path tempDir)
    {
        try (DynamicExtensionLoader loader = new DynamicExtensionLoader(tempDir))
        {
            List<WombatModule> modules = loader.load();
            assertTrue(modules.isEmpty());
        }
        catch (IOException e) {
            fail(e);
        }
    }

    @Test
    void loadExtensions_loadsModuleFromJar(@TempDir Path tempDir) throws Exception
    {
        Path jarFile = tempDir.resolve("custom-module.jar");
        createTestModuleJar(jarFile, CustomTestModule.class, CustomTestAsset.class);

        try (DynamicExtensionLoader loader = new DynamicExtensionLoader(tempDir))
        {
            List<WombatModule> modules = loader.load();
            assertEquals(1, modules.size());
            WombatModule module = modules.getFirst();
            assertEquals("tech.custom.test-module.custom-asset", module.type().name());
        }
        catch (IOException e) {
             fail(e);
        }
    }

    @Test
    void loadExtensions_rejectsDuplicateModuleNames(@TempDir Path tempDir) throws Exception
    {
        Path jar1 = tempDir.resolve("custom-module1.jar");
        createTestModuleJar(jar1, CustomTestModule.class, CustomTestAsset.class);
        Path jar2 = tempDir.resolve("custom-module2.jar");
        createTestModuleJar(jar2, DuplicateTestModule.class, CustomTestAsset.class);

        try (DynamicExtensionLoader loader = new DynamicExtensionLoader(tempDir))
        {
            assertThrows(IllegalArgumentException.class, loader::load);
        }
        catch (IOException e) {
             fail(e);
        }
    }

    @Test
    void loadExtensions_castsProfileToCoreInterfaceSafely(@TempDir Path tempDir) throws Exception
    {
        testProfileCasting(tempDir, false);
    }

    @Test
    void loadExtensions_extensionBundlesCoreInterface_resolvesToHostInterface(@TempDir Path tempDir) throws Exception
    {
        testProfileCasting(tempDir, true);
    }

    @Test
    void loadExtensions_coreNotVisibleFromParent_failsFast(@TempDir Path tempDir) throws Exception
    {
        Path jarFile = tempDir.resolve("custom-module.jar");
        createTestModuleJar(jarFile, CustomTestModule.class, CustomTestAsset.class);

        try (DynamicExtensionLoader loader = new DynamicExtensionLoader(tempDir, ClassLoader.getPlatformClassLoader()))
        {
            IllegalStateException e = assertThrows(IllegalStateException.class, loader::load);
            assertTrue(e.getMessage().contains(WombatModule.class.getName()));
        }
    }

    @Test
    void loadExtensions_coreResolvedToForeignCopy_failsFast(@TempDir Path tempDir) throws Exception
    {
        Path jarFile = tempDir.resolve("custom-module.jar");
        createTestModuleJar(jarFile, CustomTestModule.class, CustomTestAsset.class, WombatModule.class);

        try (DynamicExtensionLoader loader = new DynamicExtensionLoader(tempDir, ClassLoader.getPlatformClassLoader()))
        {
            IllegalStateException e = assertThrows(IllegalStateException.class, loader::load);
            assertTrue(e.getMessage().contains("instead of the app classloader"));
        }
    }

    private void testProfileCasting(Path tempDir, boolean bundleCoreInterface) throws Exception
    {
        Path jarFile = tempDir.resolve("llm-extension" + (bundleCoreInterface ? "-bundled" : "") + ".jar");
        if (bundleCoreInterface)
        {
            createTestModuleJar(
                jarFile,
                CustomLLMTestModule.class,
                CustomLLMTestAsset.class,
                CustomLLMTestProfile.class,
                LLMProfile.class
            );
        }
        else
        {
            createTestModuleJar(
                jarFile,
                CustomLLMTestModule.class,
                CustomLLMTestAsset.class,
                CustomLLMTestProfile.class
            );
        }

        try (DynamicExtensionLoader loader = new DynamicExtensionLoader(tempDir))
        {
            List<WombatModule> modules = loader.load();
            assertEquals(1, modules.size());
            WombatModule module = modules.getFirst();
            assertEquals("tech.custom.llm-module.custom-llm-asset", module.type().name());

            Class<? extends Asset> assetClass = module.assetClass();
            Constructor<? extends Asset> constructor = assetClass.getConstructor(String.class);
            Asset asset = constructor.newInstance("asset-1");

            AssetProfile profile = asset.profile();
            assertNotNull(profile);
            assertInstanceOf(LLMProfile.class, profile);

            LLMProfile llmProfile = (LLMProfile) profile;
            assertEquals(LLMProvider.mistralai, llmProfile.provider());
            assertEquals("mistral-large", llmProfile.model());
            assertEquals("FRA", llmProfile.location());
        }
    }

    @Test
    void loadExtensions_unaffectedByContextClassLoader(@TempDir Path tempDir) throws Exception
    {
        Path jarFile = tempDir.resolve("custom-module.jar");
        createTestModuleJar(jarFile, CustomTestModule.class, CustomTestAsset.class);

        ClassLoader originalContextClassLoader = Thread.currentThread().getContextClassLoader();
        try (URLClassLoader unrelatedClassLoader = new URLClassLoader(new URL[0], null))
        {
            Thread.currentThread().setContextClassLoader(unrelatedClassLoader);

            try (DynamicExtensionLoader loader = new DynamicExtensionLoader(tempDir))
            {
                List<WombatModule> modules = loader.load();
                assertEquals(1, modules.size());
                assertEquals("tech.custom.test-module.custom-asset", modules.getFirst().type().name());
            }
        }
        finally
        {
            Thread.currentThread().setContextClassLoader(originalContextClassLoader);
        }
    }

    @Test
    void close_releasesResourcesWithoutError(@TempDir Path tempDir) throws Exception
    {
        Path jarFile = tempDir.resolve("custom-module.jar");
        createTestModuleJar(jarFile, CustomTestModule.class, CustomTestAsset.class);

        DynamicExtensionLoader loader = new DynamicExtensionLoader(tempDir);
        List<WombatModule> modules = loader.load();
        assertFalse(modules.isEmpty());

        assertDoesNotThrow(loader::close);
        assertDoesNotThrow(loader::close);
    }

    private static void createTestModuleJar(Path jarPath, Class<? extends WombatModule> moduleClass, Class<?>... classes) throws Exception
    {
        try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(jarPath.toFile())))
        {
            addServiceEntry(jos, WombatModule.class.getName(), moduleClass.getName());
            addClassEntry(jos, moduleClass);
            if (classes != null)
            {
                for (Class<?> clazz : classes)
                {
                    if (clazz != null)
                        addClassEntry(jos, clazz);
                }
            }
        }
    }

    private static void addServiceEntry(JarOutputStream jos, String serviceInterface, String implementation) throws Exception
    {
        JarEntry entry = new JarEntry("META-INF/services/" + serviceInterface);
        jos.putNextEntry(entry);
        jos.write((implementation + "\n").getBytes(StandardCharsets.UTF_8));
        jos.closeEntry();
    }

    private static void addClassEntry(JarOutputStream jos, Class<?> clazz) throws Exception
    {
        String path = clazz.getName().replace('.', '/') + ".class";
        JarEntry entry = new JarEntry(path);
        jos.putNextEntry(entry);
        try (InputStream is = clazz.getClassLoader().getResourceAsStream(path))
        {
            if (is != null)
                is.transferTo(jos);
        }
        jos.closeEntry();
    }

    public static final class CustomTestModule implements WombatModule
    {
        public static final AssetType TYPE = AssetType.of("tech.custom.test-module", "custom-asset", ActivityRegime.MODELED, ServiceFamily.LLM);

        @Override
        public AssetType type()
        {
            return TYPE;
        }

        @Override
        public Class<? extends Asset> assetClass()
        {
            return CustomTestAsset.class;
        }
    }

    public static final class DuplicateTestModule implements WombatModule
    {
        @Override
        public AssetType type()
        {
            return CustomTestModule.TYPE;
        }

        @Override
        public Class<? extends Asset> assetClass()
        {
            return CustomTestAsset.class;
        }
    }

    public record CustomTestAsset(String assetId) implements Asset
    {
        @Override
        public AssetIdentity identity()
        {
            return AssetIdentity.of(this.assetId, "env", this.assetId);
        }

        @Override
        public AssetType type()
        {
            return CustomTestModule.TYPE;
        }

        @Override
        public AssetProfile profile()
        {
            return null;
        }
    }

    public static final class CustomLLMTestModule implements WombatModule
    {
        public static final AssetType TYPE = AssetType.of("tech.custom.llm-module", "custom-llm-asset", ActivityRegime.MODELED, ServiceFamily.LLM);

        @Override
        public AssetType type()
        {
            return TYPE;
        }

        @Override
        public Class<? extends Asset> assetClass()
        {
            return CustomLLMTestAsset.class;
        }
    }

    public record CustomLLMTestProfile(LLMProvider provider, String model, String location) implements LLMProfile
    {
    }

    public record CustomLLMTestAsset(String assetId) implements Asset
    {
        @Override
        public AssetIdentity identity()
        {
            return AssetIdentity.of(this.assetId, "env", this.assetId);
        }

        @Override
        public AssetType type()
        {
            return CustomLLMTestModule.TYPE;
        }

        @Override
        public AssetProfile profile()
        {
            return new CustomLLMTestProfile(LLMProvider.mistralai, "mistral-large", "FRA");
        }
    }
}
