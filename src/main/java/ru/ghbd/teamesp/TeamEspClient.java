package ru.ghbd.teamesp;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
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
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;

public final class TeamEspClient implements ClientModInitializer {
    private static final double MAX_DISTANCE_LIMIT = 5000.0D;
    private static final double MIN_DISTANCE = 500.0D;
    private static final int MARKER_UPDATE_INTERVAL_TICKS = 10;
    private static final int NAME_COLOR = 0xFF7CFF8A;
    private static final int NAME_BACKGROUND = 0x66000000;
    private static final String REQUEST_PREFIX = "TF_REQUEST_";
    private static final String ACCEPT_PREFIX = "TF_ACCEPT_";
    private static final String DECLINE_PREFIX = "TF_DECLINE_";
    private static final int REQUEST_BOX_WIDTH = 250;
    private static final int REQUEST_BOX_HEIGHT = 64;

    private static KeyBinding menuKey;
    private static boolean enabled = true;
    private static double maxDistance = MAX_DISTANCE_LIMIT;
    private static int ticksUntilMarkerUpdate = 0;
    private static List<MarkerSnapshot> markerSnapshots = List.of();
    private static final Set<String> friends = new LinkedHashSet<>();
    private static final Deque<String> pendingRequests = new ArrayDeque<>();
    private static boolean mouseWasDown;
    private static String outgoingNotice;
    private static long outgoingNoticeUntil;

    @Override
    public void onInitializeClient() {
        loadFriends();

        menuKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.team_esp.menu",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_RIGHT_SHIFT,
                KeyBinding.Category.MISC
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            updateMarkers(client);
            handleRequestClick(client);

            while (menuKey.wasPressed() && client.currentScreen == null) {
                client.setScreen(new MarkerScreen(null));
            }
        });

        WorldRenderEvents.AFTER_ENTITIES.register(TeamEspClient::renderMarkers);
        HudRenderCallback.EVENT.register((context, tickDelta) -> {
            renderRequestBanner(context);
            renderOutgoingNotice(context);
        });
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> handleIncomingProtocolMessage(message));
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
        Vec3d cameraPos = camera.getCameraPos();
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
                && isFriend(target.getName().getString())
                && player.squaredDistanceTo(target) <= maxDistance * maxDistance;
    }

    public static boolean sendFriendRequest(String name) {
        String cleanName = cleanPlayerName(name);
        MinecraftClient client = MinecraftClient.getInstance();
        if (cleanName.isEmpty() || client.player == null || client.getNetworkHandler() == null
                || cleanName.equalsIgnoreCase(client.player.getName().getString())
                || isFriend(cleanName)) {
            return false;
        }

        client.getNetworkHandler().sendChatCommand(
                "msg " + cleanName + " " + REQUEST_PREFIX + client.player.getName().getString()
        );
        outgoingNotice = "Заявка отправлена игроку " + cleanName;
        outgoingNoticeUntil = System.currentTimeMillis() + 5000L;
        return true;
    }

    private static void handleIncomingProtocolMessage(Text message) {
        String raw = message.getString();
        String requestSender = readProtocolName(raw, REQUEST_PREFIX);
        if (!requestSender.isEmpty()) {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player != null && !requestSender.equalsIgnoreCase(client.player.getName().getString())
                    && !pendingRequests.contains(requestSender)) {
                pendingRequests.addLast(requestSender);
            }
            return;
        }

        String acceptedName = readProtocolName(raw, ACCEPT_PREFIX);
        if (!acceptedName.isEmpty()) {
            addFriend(acceptedName);
            return;
        }

        String declinedName = readProtocolName(raw, DECLINE_PREFIX);
        if (!declinedName.isEmpty()) {
            return;
        }
    }

    public static String getPendingRequest() {
        return pendingRequests.peekFirst();
    }

    public static void acceptPendingRequest() {
        String sender = pendingRequests.pollFirst();
        MinecraftClient client = MinecraftClient.getInstance();
        if (sender == null || client.player == null || client.getNetworkHandler() == null) {
            return;
        }

        addFriend(sender);
        client.getNetworkHandler().sendChatCommand(
                "msg " + sender + " " + ACCEPT_PREFIX + client.player.getName().getString()
        );
    }

    public static void declinePendingRequest() {
        String sender = pendingRequests.pollFirst();
        MinecraftClient client = MinecraftClient.getInstance();
        if (sender == null || client.player == null || client.getNetworkHandler() == null) {
            return;
        }

        client.getNetworkHandler().sendChatCommand(
                "msg " + sender + " " + DECLINE_PREFIX + client.player.getName().getString()
        );
    }

    private static String readProtocolName(String message, String prefix) {
        int start = message.indexOf(prefix);
        if (start < 0) {
            return "";
        }

        start += prefix.length();
        int end = start;
        while (end < message.length() && isNameCharacter(message.charAt(end))) {
            end++;
        }

        return cleanPlayerName(message.substring(start, end));
    }

    private static String cleanPlayerName(String name) {
        String cleanName = name == null ? "" : name.trim();
        if (cleanName.length() > 16 || !cleanName.matches("[A-Za-z0-9_]+")) {
            return "";
        }
        return cleanName;
    }

    private static boolean isNameCharacter(char character) {
        return character >= 'A' && character <= 'Z'
                || character >= 'a' && character <= 'z'
                || character >= '0' && character <= '9'
                || character == '_';
    }

    private static void handleRequestClick(MinecraftClient client) {
        boolean mouseDown = GLFW.glfwGetMouseButton(
                client.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_1
        ) == GLFW.GLFW_PRESS;

        if (mouseDown && !mouseWasDown && client.currentScreen == null && getPendingRequest() != null) {
            double scaleX = (double) client.getWindow().getScaledWidth() / client.getWindow().getWidth();
            double scaleY = (double) client.getWindow().getScaledHeight() / client.getWindow().getHeight();
            double mouseX = client.mouse.getX() * scaleX;
            double mouseY = client.mouse.getY() * scaleY;
            int boxX = client.getWindow().getScaledWidth() - REQUEST_BOX_WIDTH - 8;
            int boxY = 8;

            if (mouseY >= boxY + 35 && mouseY <= boxY + 56) {
                if (mouseX >= boxX + 10 && mouseX <= boxX + 120) {
                    acceptPendingRequest();
                } else if (mouseX >= boxX + 130 && mouseX <= boxX + 240) {
                    declinePendingRequest();
                }
            }
        }

        mouseWasDown = mouseDown;
    }

    private static void renderRequestBanner(net.minecraft.client.gui.DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        String sender = getPendingRequest();
        if (client.world == null || client.player == null || sender == null || client.currentScreen != null) {
            return;
        }

        int boxX = client.getWindow().getScaledWidth() - REQUEST_BOX_WIDTH - 8;
        int boxY = 8;
        context.fill(boxX, boxY, boxX + REQUEST_BOX_WIDTH, boxY + REQUEST_BOX_HEIGHT, 0xDD202020);
        context.drawTextWithShadow(client.textRenderer, Text.literal("Заявка в друзья"), boxX + 10, boxY + 8, 0xFFFFFF);
        context.drawTextWithShadow(client.textRenderer, Text.literal(sender + " хочет добавить тебя"), boxX + 10, boxY + 21, 0xD0D0D0);
        context.fill(boxX + 10, boxY + 36, boxX + 120, boxY + 56, 0xFF3D8B40);
        context.fill(boxX + 130, boxY + 36, boxX + 240, boxY + 56, 0xFF8B3D3D);
        context.drawCenteredTextWithShadow(client.textRenderer, Text.literal("Принять"), boxX + 65, boxY + 42, 0xFFFFFF);
        context.drawCenteredTextWithShadow(client.textRenderer, Text.literal("Отклонить"), boxX + 185, boxY + 42, 0xFFFFFF);
    }

    private static void renderOutgoingNotice(net.minecraft.client.gui.DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null || client.currentScreen != null
                || outgoingNotice == null || System.currentTimeMillis() > outgoingNoticeUntil) {
            return;
        }

        int boxWidth = 250;
        int boxHeight = 36;
        int boxX = client.getWindow().getScaledWidth() - boxWidth - 8;
        int boxY = 80;
        context.fill(boxX, boxY, boxX + boxWidth, boxY + boxHeight, 0xCC202020);
        context.drawTextWithShadow(client.textRenderer, Text.literal("Заявка отправлена"), boxX + 10, boxY + 8, 0xFFFFFF);
        context.drawTextWithShadow(client.textRenderer, Text.literal(outgoingNotice), boxX + 10, boxY + 21, 0x7CFF8A);
    }

    public static List<String> getFriends() {
        return List.copyOf(friends);
    }

    public static boolean addFriend(String name) {
        String cleanName = name == null ? "" : name.trim();
        if (cleanName.isEmpty() || cleanName.length() > 16 || !cleanName.matches("[A-Za-z0-9_]+")) {
            return false;
        }

        for (String friend : friends) {
            if (friend.equalsIgnoreCase(cleanName)) {
                return false;
            }
        }

        friends.add(cleanName);
        saveFriends();
        return true;
    }

    public static boolean removeFriend(String name) {
        if (name == null || name.isBlank()) {
            return false;
        }

        String found = null;
        for (String friend : friends) {
            if (friend.equalsIgnoreCase(name.trim())) {
                found = friend;
                break;
            }
        }

        if (found == null) {
            return false;
        }

        friends.remove(found);
        saveFriends();
        return true;
    }

    private static boolean isFriend(String name) {
        for (String friend : friends) {
            if (friend.equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    private static Path friendsFile() {
        return MinecraftClient.getInstance().runDirectory.toPath()
                .resolve("config")
                .resolve("team_esp_friends.txt");
    }

    private static void loadFriends() {
        try {
            Path file = friendsFile();
            if (Files.exists(file)) {
                for (String line : Files.readAllLines(file)) {
                    String cleanName = line.trim();
                    if (!cleanName.isEmpty() && cleanName.length() <= 16
                            && cleanName.matches("[A-Za-z0-9_]+")) {
                        friends.add(cleanName);
                    }
                }
            }
        } catch (IOException ignored) {
        }
    }

    private static void saveFriends() {
        try {
            Path file = friendsFile();
            Files.createDirectories(file.getParent());
            Files.write(file, friends);
        } catch (IOException ignored) {
        }
    }

    private static void renderName(MatrixStack matrices,
                                   VertexConsumerProvider consumers,
                                   MinecraftClient client,
                                   MarkerSnapshot marker,
                                   Camera camera) {
        Vec3d cameraPos = camera.getCameraPos();
        Text name = marker.label();
        TextRenderer textRenderer = client.textRenderer;
        float scale = 0.025F;
        Vec3d targetPosition = marker.target().getSyncedPos();

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
