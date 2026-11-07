package dev.example.mse;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Вопрос "Сохранить изменения?": Сохранить / Не сохранять / Отмена. */
public class UnsavedScreen extends Screen {
    private final EditorScreen parent;
    private final String fileLabel;
    private final Runnable proceed;

    public UnsavedScreen(EditorScreen parent, String fileLabel, Runnable proceed) {
        super(Component.literal(Lang.t("unsaved.heading")));
        this.parent = parent;
        this.fileLabel = fileLabel;
        this.proceed = proceed;
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int y = this.height / 2;
        addRenderableWidget(new LabelWidget(this.font, cx - 170, y - 34, 340, 12,
                () -> Lang.t("unsaved.title", fileLabel), () -> 0xFFFFFFFF, true));
        addRenderableWidget(Button.builder(Component.literal(Lang.t("button.save")), b -> {
            if (parent.doSave() != null) {
                back();
                proceed.run();
            } else {
                back();
            }
        }).bounds(cx - 160, y, 100, 20).build());
        addRenderableWidget(Button.builder(Component.literal(Lang.t("button.dont_save")), b -> {
            parent.discardChanges();
            back();
            proceed.run();
        }).bounds(cx - 50, y, 100, 20).build());
        addRenderableWidget(Button.builder(Component.literal(Lang.t("button.cancel")), b -> back())
                .bounds(cx + 60, y, 100, 20).build());
    }

    private void back() {
        Minecraft.getInstance().gui.setScreen(parent);
    }

    @Override
    public void onClose() {
        back();
    }
}
