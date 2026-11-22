package dev.afanasy.interstellar.client;

import dev.afanasy.interstellar.InterstellarExperience;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

/** First-person Tesseract flight for single-player worlds. */
public final class InterstellarExperienceClient implements ClientModInitializer {
    private static KeyBinding toggleTesseractKey;
    private static KeyBinding captureMomentKey;
    private static KeyBinding replayQuoteKey;

    private static boolean flightActive;
    private static boolean oldAllowFlying;
    private static boolean oldFlying;
    private static boolean oldNoGravity;
    private static float oldFlySpeed;
    private static Perspective oldPerspective = Perspective.FIRST_PERSON;

    @Override
    public void onInitializeClient() {
        KeyBinding.Category category = KeyBinding.Category.create(
                Identifier.of(InterstellarExperience.MOD_ID, "tesseract")
        );

        toggleTesseractKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.interstellar_experience.open_tesseract",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_O,
                category
        ));
        captureMomentKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.interstellar_experience.capture_moment",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_P,
                category
        ));
        replayQuoteKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.interstellar_experience.replay_quote",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_R,
                category
        ));

        ClientTickEvents.END_CLIENT_TICK.register(InterstellarExperienceClient::onClientTick);
    }

    private static void onClientTick(MinecraftClient client) {
        TesseractHistory.tick(client);

        while (toggleTesseractKey.wasPressed()) {
            if (client.player == null || client.world == null) continue;
            if (!client.isInSingleplayer()) {
                client.player.sendMessage(Text.literal("§5[INTERSTELLAR] §fРежим свободного полёта доступен только в одиночном мире."), true);
                continue;
            }
            if (flightActive) {
                exitFlight(client);
            } else {
                // Open the actual 3D Tesseract scene. The old key only enabled
                // vanilla flight and left the cinematic renderer unused.
                client.setScreen(new TesseractScreen());
            }
        }

        if (flightActive) {
            if (client.player == null || client.world == null || !client.isInSingleplayer()) {
                exitFlight(client);
                return;
            }
            // Keep vanilla first-person flight active while normal mouse/WASD input controls direction.
            client.player.getAbilities().allowFlying = true;
            client.player.getAbilities().flying = true;
            client.player.setNoGravity(true);
        }

        while (captureMomentKey.wasPressed()) {
            if (TesseractHistory.capture(client) && client.player != null) {
                client.player.sendMessage(Text.literal("§5[INTERSTELLAR] §fМомент сохранён."), true);
            }
        }
        while (replayQuoteKey.wasPressed()) playQuote();
    }

    private static void enterFlight(MinecraftClient client) {
        var player = client.player;
        oldAllowFlying = player.getAbilities().allowFlying;
        oldFlying = player.getAbilities().flying;
        oldFlySpeed = player.getAbilities().getFlySpeed();
        oldNoGravity = player.hasNoGravity();
        oldPerspective = client.options.getPerspective();

        player.getAbilities().allowFlying = true;
        player.getAbilities().flying = true;
        player.getAbilities().setFlySpeed(0.035F);
        player.setNoGravity(true);
        // Apply the same temporary abilities to the integrated-server player so
        // single-player movement is accepted by the local server as well.
        var integratedServer = client.getServer();
        if (integratedServer != null) {
            String playerName = player.getName().getString();
            integratedServer.execute(() -> {
                var serverPlayer = integratedServer.getPlayerManager().getPlayer(playerName);
                if (serverPlayer != null) {
                    serverPlayer.getAbilities().allowFlying = true;
                    serverPlayer.getAbilities().flying = true;
                    serverPlayer.getAbilities().setFlySpeed(0.035F);
                    serverPlayer.setNoGravity(true);
                    serverPlayer.sendAbilitiesUpdate();
                }
            });
        }
        client.options.setPerspective(Perspective.FIRST_PERSON);
        flightActive = true;

        player.sendMessage(Text.literal("§5[INTERSTELLAR] §fСвободный полёт активирован. O — выход."), true);
        playQuote();
    }

    private static void exitFlight(MinecraftClient client) {
        if (client.player != null) {
            client.player.getAbilities().allowFlying = oldAllowFlying;
            client.player.getAbilities().flying = oldFlying;
            client.player.getAbilities().setFlySpeed(oldFlySpeed);
            client.player.setNoGravity(oldNoGravity);
            var integratedServer = client.getServer();
            if (integratedServer != null) {
                String playerName = client.player.getName().getString();
                integratedServer.execute(() -> {
                    var serverPlayer = integratedServer.getPlayerManager().getPlayer(playerName);
                    if (serverPlayer != null) {
                        serverPlayer.getAbilities().allowFlying = oldAllowFlying;
                        serverPlayer.getAbilities().flying = oldFlying;
                        serverPlayer.getAbilities().setFlySpeed(oldFlySpeed);
                        serverPlayer.setNoGravity(oldNoGravity);
                        serverPlayer.sendAbilitiesUpdate();
                    }
                });
            }
        }
        if (client.options != null) client.options.setPerspective(oldPerspective);
        flightActive = false;
        if (client.player != null) {
            client.player.sendMessage(Text.literal("§5[INTERSTELLAR] §fСвободный полёт завершён."), true);
        }
    }

    public static void playQuote() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            client.getSoundManager().play(PositionedSoundInstance.ui(
                    InterstellarExperience.TESSERACT_QUOTE, 0.88f, 0.96f
            ));
        }
    }
}
