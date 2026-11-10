package ru.ghbd.teamesp;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

public final class MarkerScreen extends Screen {
    private final Screen parent;
    private ButtonWidget enabledButton;
    private ButtonWidget distanceButton;

    public MarkerScreen(Screen parent) {
        super(Text.literal("Метки тимейтов"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int centerX = width / 2;
        int top = height / 2 - 60;

        enabledButton = addDrawableChild(ButtonWidget.builder(enabledText(), button -> {
            TeamEspClient.toggleEnabled();
            button.setMessage(enabledText());
        }).dimensions(centerX - 100, top, 200, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("− 500 м"), button -> {
            TeamEspClient.changeMaxDistance(-500.0D);
            distanceButton.setMessage(distanceText());
        }).dimensions(centerX - 100, top + 28, 95, 20).build());

        distanceButton = addDrawableChild(ButtonWidget.builder(distanceText(), button -> {
        }).dimensions(centerX - 2, top + 28, 102, 20).build());
        distanceButton.active = false;

        addDrawableChild(ButtonWidget.builder(Text.literal("+ 500 м"), button -> {
            TeamEspClient.changeMaxDistance(500.0D);
            distanceButton.setMessage(distanceText());
        }).dimensions(centerX + 3, top + 56, 97, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Обновление: 2 раза/сек"), button -> {
        }).dimensions(centerX - 100, top + 84, 200, 20).build()).active = false;

        addDrawableChild(ButtonWidget.builder(Text.literal("Закрыть"), button -> close())
                .dimensions(centerX - 100, top + 114, 200, 20)
                .build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        renderBackground(context, mouseX, mouseY, deltaTicks);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 20, 0xFFFFFF);
        super.render(context, mouseX, mouseY, deltaTicks);
    }

    @Override
    public void close() {
        client.setScreen(parent);
    }

    private static Text enabledText() {
        return Text.literal(TeamEspClient.isEnabled()
                ? "Метки: включены"
                : "Метки: выключены");
    }

    private static Text distanceText() {
        return Text.literal("Радиус: " + Math.round(TeamEspClient.getMaxDistance()) + " м");
    }
}
