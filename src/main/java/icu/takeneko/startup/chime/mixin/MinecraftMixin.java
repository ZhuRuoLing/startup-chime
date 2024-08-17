package icu.takeneko.startup.chime.mixin;

import icu.takeneko.startup.chime.ClientMod;
import icu.takeneko.startup.chime.sound.AudioThread;
import net.minecraft.client.Minecraft;
import net.minecraft.client.main.GameConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    @Unique
    private final Logger logger = LoggerFactory.getLogger("StartupChime");
    @Unique
    private final AudioThread at = new AudioThread();
    @Inject(
            method = "<init>",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/Minecraft;setOverlay(Lnet/minecraft/client/gui/screens/Overlay;)V"
            )
    )
    void onSetOverlay(GameConfig gameConfig, CallbackInfo ci) {
        if (ClientMod.shouldPlayChime()) {
            at.start();
            try {
                at.getPreparationLatch().await();
            } catch (InterruptedException ignored) {

            }
        }
    }

    @Inject(
            method = "stop",
            at = @At("HEAD")
    )
    void onStop(CallbackInfo ci){

    }
}
