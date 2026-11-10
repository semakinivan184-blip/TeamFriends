package ru.ghbd.teamesp;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

public final class MarkerScreen extends Screen {
    private final Screen parent;
    private ButtonWidget distanceButton;
    private TextFieldWidget friendNameField;
    private String status = "";

    public MarkerScreen(Screen parent) {
        super(Text.literal("Метки тимейтов"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int centerX = width / 2;
        int top = 42;

        friendNameField = new TextFieldWidget(textRenderer, centerX - 150, top, 160, 20, Text.literal("Ник игрока"));
        friendNameField.setMaxLength(16);
        friendNameField.setPlaceholder(Text.literal("Ник игрока"));
        addDrawableChild(friendNameField);

        addDrawableChild(ButtonWidget.builder(Text.literal("Отправить"), button -> {
            if (TeamEspClient.sendFriendRequest(friendNameField.getText())) {
                status = "Заявка отправлена";
                friendNameField.setText("");
            } else {
                status = "Проверь ник или уже добавлен";
            }
        }).dimensions(centerX + 15, top, 70, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Удалить"), button -> {
            if (TeamEspClient.removeFriend(friendNameField.getText())) {
                status = "Игрок удалён";
                friendNameField.setText("");
            } else {
                status = "Такого игрока нет в списке";
            }
        }).dimensions(centerX + 90, top, 70, 20).build());

        addDrawableChild(ButtonWidget.builder(enabledText(), button -> {
            TeamEspClient.toggleEnabled();
            button.setMessage(enabledText());
        }).dimensions(centerX - 100, top + 42, 200, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("− 500 м"), button -> {
            TeamEspClient.changeMaxDistance(-500.0D);
            distanceButton.setMessage(distanceText());
        }).dimensions(centerX - 100, top + 70, 95, 20).build());

        distanceButton = addDrawableChild(ButtonWidget.builder(distanceText(), button -> {
        }).dimensions(centerX - 2, top + 70, 102, 20).build());
        distanceButton.active = false;

        addDrawableChild(ButtonWidget.builder(Text.literal("+ 500 м"), button -> {
            TeamEspClient.changeMaxDistance(500.0D);
            distanceButton.setMessage(distanceText());
        }).dimensions(centerX + 3, top + 98, 97, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Обновление: 2 раза/сек"), button -> {
        }).dimensions(centerX - 100, top + 126, 200, 20).build()).active = false;

        addDrawableChild(ButtonWidget.builder(Text.literal("Закрыть"), button -> close())
                .dimensions(centerX - 100, top + 166, 200, 20)
                .build());

        setInitialFocus(friendNameField);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
         public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) { renderBackground(context, mouseX, mouseY, deltaTicks); super.render(context, mouseX, mouseY, deltaTicks);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 15, 0xFFFFFF);
        context.drawTextWithShadow(textRenderer, Text.literal("Твои тимейты:"), width / 2 - 150, 240, 0xFFFFFF);

        int y = 254;
        if (TeamEspClient.getFriends().isEmpty()) {
            context.drawTextWithShadow(textRenderer, Text.literal("Список пуст"), width / 2 - 150, y, 0xAAAAAA);
        } else {
            for (String friend : TeamEspClient.getFriends()) {
                context.drawTextWithShadow(textRenderer, Text.literal("• " + friend), width / 2 - 150, y, 0x7CFF8A);
                y += 14;
                if (y > height - 70) {
                    context.drawTextWithShadow(textRenderer, Text.literal("…"), width / 2 - 150, y, 0xAAAAAA);
                    break;
                }
            }
        }

        if (!status.isEmpty()) {
            context.drawCenteredTextWithShadow(textRenderer, Text.literal(status), width / 2, height - 42, 0xFFFF55);
        }
         
    }

    @Override
    public void close() {
        client.setScreen(parent);
    }

    private static Text enabledText() {
        return Text.literal(TeamEspClient.isEnabled() ? "Метки: включены" : "Метки: выключены");
    }

    private static Text distanceText() {
        return Text.literal("Радиус: " + Math.round(TeamEspClient.getMaxDistance()) + " м");
    }
}
