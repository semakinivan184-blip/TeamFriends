package ru.ghbd.teamesp;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.Camera;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public final class TeamEspClient implements ClientModInitializer {
    private static final double MAX_DISTANCE_LIMIT = 5000.0D;
    private static final double MIN_DISTANCE = 500.0D;
    private static final int MARKER_UPDATE_INTERVAL_TICKS = 10;
    private static final int NAME_COLOR = 0xFF7CFF8A;
    private static final int NAME_BACKGROUND = 0x66000000;

    private static KeyBinding menuKey;
    private static boolean enabled = true;
    private static double maxDistance = MAX_DISTANCE_LIMIT;
    private static int ticksUntilMarkerUpdate = 0;
    private static List<MarkerSnapshot> markerSnapshots = List.of();

    @Override
    public void onInitializeClient() {
        menuKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.team_esp.menu",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_RIGHT_SHIFT,
                "category.team_esp"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            updateMarkers(client);

            while (menuKey.wasPressed() && client.currentScreen == null) {
                client.setScreen(new MarkerScreen(null));
            }
        });

        WorldRenderEvents.AFTER_ENTITIES.register(TeamEspClient::renderMarkers);
    }

    private static void renderMarkers(WorldRenderContext context) {
        if (!enabled) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null || context.matrices() == null) {
            return;
        }

        VertexConsumerProvider consumers = context.consumers();
        if (consumers == null) {
            return;
        }

        Camera camera = client.gameRenderer.getCamera();
        Vec3d cameraPos = camera.getPos();
        MatrixStack matrices = context.matrices();

        for (MarkerSnapshot marker : markerSnapshots) {
            if (!marker.target().isRemoved()) {
                renderName(matrices, consumers, client, marker, camera);
            }
        }
    }

    private static void updateMarkers(MinecraftClient client) {
        if (client.world == null || client.player == null) {
            markerSnapshots = List.of();
            ticksUntilMarkerUpdate = 0;
            return;
        }

        if (ticksUntilMarkerUpdate > 0) {
            ticksUntilMarkerUpdate--;
            return;
        }

        ticksUntilMarkerUpdate = MARKER_UPDATE_INTERVAL_TICKS - 1;
        List<MarkerSnapshot> updatedMarkers = new ArrayList<>();
        for (PlayerEntity target : client.world.getPlayers()) {
            if (!isVisibleTeammate(client.player, target)) {
                continue;
            }

            double distance = Math.sqrt(client.player.squaredDistanceTo(target));
            Text label = Text.literal("◆ " + target.getName().getString()
                    + "  " + Math.round(distance) + " м");
            updatedMarkers.add(new MarkerSnapshot(
                    target,
                    label,
                    distance
            ));
        }
        markerSnapshots = List.copyOf(updatedMarkers);
    }

    private static boolean isVisibleTeammate(PlayerEntity player, PlayerEntity target) {
        return player != target
                && player.isTeammate(target)
                && player.squaredDistanceTo(target) <= maxDistance * maxDistance;
    }

    private static void renderName(MatrixStack matrices,
                                   VertexConsumerProvider consumers,
                                   MinecraftClient client,
                                   MarkerSnapshot marker,
                                   Camera camera) {
        Vec3d cameraPos = camera.getPos();
        Text name = marker.label();
        TextRenderer textRenderer = client.textRenderer;
        float scale = 0.025F;
        Vec3d targetPosition = marker.target().getPos();

        matrices.push();
        matrices.translate(
                targetPosition.x - cameraPos.x,
                targetPosition.y + marker.target().getHeight() + 0.65D - cameraPos.y,
                targetPosition.z - cameraPos.z
        );
        matrices.multiply(camera.getRotation());
        matrices.scale(-scale, -scale, scale);

        Matrix4f positionMatrix = matrices.peek().getPositionMatrix();
        float x = -textRenderer.getWidth(name) / 2.0F;
        textRenderer.draw(
                name,
                x,
                0.0F,
                NAME_COLOR,
                false,
                positionMatrix,
                consumers,
                TextRenderer.TextLayerType.SEE_THROUGH,
                NAME_BACKGROUND,
                LightmapTextureManager.MAX_LIGHT_COORDINATE
        );
        matrices.pop();
    }

    private record MarkerSnapshot(PlayerEntity target, Text label, double distance) {
    }

    public static boolean toggleEnabled() {
        enabled = !enabled;
        return enabled;
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static double getMaxDistance() {
        return maxDistance;
    }

    public static double changeMaxDistance(double amount) {
        maxDistance = Math.max(MIN_DISTANCE, Math.min(MAX_DISTANCE_LIMIT, maxDistance + amount));
        return maxDistance;
    }
}
