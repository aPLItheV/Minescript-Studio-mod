package dev.example.mse;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

/** Небольшой диалог: ввод текста (переименовать, дублировать, новый файл) или подтверждение (удалить). */
public class PromptScreen extends Screen {
    private final Screen parent;
    private final String title;
    private final String detail;
    private final boolean hasInput;
    private final String initial;
    private final String okLabel;
    private final Consumer<String> onOk;
    private EditBox box;

    private PromptScreen(Screen parent, String title, String detail, boolean hasInput, String initial,
                         String okLabel, Consumer<String> onOk) {
        super(Component.literal(title));
        this.parent = parent;
        this.title = title;
        this.detail = detail;
        this.hasInput = hasInput;
        this.initial = initial;
        this.okLabel = okLabel;
        this.onOk = onOk;
    }

    static PromptScreen input(Screen parent, String title, String initial, Consumer<String> onOk) {
        return new PromptScreen(parent, title, "", true, initial, Lang.t("button.ok"), onOk);
    }

    static PromptScreen confirm(Screen parent, String title, String detail, String yesLabel, Runnable onYes) {
        return new PromptScreen(parent, title, detail, false, "", yesLabel, v -> onYes.run());
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int y = this.height / 2 - 24;
        addRenderableWidget(new LabelWidget(this.font, cx - 170, y, 340, 12, () -> title, () -> 0xFFFFFFFF, true));
        if (hasInput) {
            box = new EditBox(this.font, cx - 130, y + 18, 260, 20, Component.literal("input"));
            box.setMaxLength(128);
            box.setValue(initial);
            addRenderableWidget(box);
            setInitialFocus(box);
        } else {
            addRenderableWidget(new LabelWidget(this.font, cx - 170, y + 18, 340, 12, () -> detail, () -> 0xFF9DA5B4, true));
        }
        int by = y + 48;
        addRenderableWidget(Button.builder(Component.literal(okLabel), b -> ok())
                .bounds(cx - 105, by, 100, 20).build());
        addRenderableWidget(Button.builder(Component.literal(Lang.t("button.cancel")), b -> back())
                .bounds(cx + 5, by, 100, 20).build());
    }

    private void back() {
        Minecraft.getInstance().gui.setScreen(parent);
    }

    private void ok() {
        String value = box == null ? "" : box.getValue();
        back();
        onOk.accept(value);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int k = event.key();
        if (k == InputConstants.KEY_RETURN || k == InputConstants.KEY_NUMPADENTER) {
            ok();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        back();
    }
}
