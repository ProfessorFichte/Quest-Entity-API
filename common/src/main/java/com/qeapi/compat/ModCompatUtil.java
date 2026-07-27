package com.qeapi.compat;

// Shared "is this other mod loaded" check for the optional compat integrations. Uses reflection
// into Architectury's platform API (falling back to Fabric Loader directly) so this class never
// needs a compile-time dependency on Architectury - same approach Quest.isModLoaded() uses for
// the required_mod quest field.
public final class ModCompatUtil {

    private ModCompatUtil() {}

    public static boolean isModLoaded(String modId) {
        try {
            Class<?> platformClass = Class.forName("dev.architectury.platform.Platform");
            java.lang.reflect.Method isModLoaded = platformClass.getMethod("isModLoaded", String.class);
            return (Boolean) isModLoaded.invoke(null, modId);
        } catch (Exception e) {
            try {
                Class<?> fabricLoader = Class.forName("net.fabricmc.loader.api.FabricLoader");
                java.lang.reflect.Method getInstance = fabricLoader.getMethod("getInstance");
                Object instance = getInstance.invoke(null);
                java.lang.reflect.Method isModLoadedMethod = fabricLoader.getMethod("isModLoaded", String.class);
                return (Boolean) isModLoadedMethod.invoke(instance, modId);
            } catch (Exception e2) {
                return false;
            }
        }
    }
}
