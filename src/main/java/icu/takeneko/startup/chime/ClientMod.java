package icu.takeneko.startup.chime;

import net.fabricmc.api.ClientModInitializer;

import java.nio.file.Path;

public class ClientMod implements ClientModInitializer {
    public static final Path CHIME_DIR = Path.of("./startup_chime");
    public static final Path CHIME_PATH = CHIME_DIR.resolve("chime.ogg");
    private static boolean shouldPlayChime = false;
    @Override
    public void onInitializeClient() {
        if (!CHIME_DIR.toFile().exists()){
            CHIME_DIR.toFile().mkdir();
        }
        shouldPlayChime = CHIME_PATH.toFile().exists();
    }

    public static boolean shouldPlayChime() {
        return shouldPlayChime;
    }
}
