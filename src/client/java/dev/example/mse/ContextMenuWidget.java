package dev.example.mse;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

import java.util.List;

/** Контекстное меню (правая кнопка мыши). Клики обрабатывает экран, виджет только рисует. */
public class ContextMenuWidget extends AbstractWidget {
    record Item(String label, Runnable action) {}

    private static final int ROW = 14;

    private final Font font;
    private List<Item> items = List.of();
    private int menuX, menuY, menuW, menuH;

    public ContextMenuWidget(Font font) {
        super(0, 0, 0, 0, Component.empty());
        this.font = font;
        this.visible = false;
        this.active = false;
    }

    boolean isOpen() {
        return visible;
    }

    void hide() {
        visible = false;
    }

    void show(int x, int y, List<Item> items, int screenW, int screenH) {
        this.items = items;
        int w = 110;
        for (Item it : items) w = Math.max(w, font.width(it.label()) + 20);
        menuW = w;
        menuH = items.size() * ROW + 4;
        menuX = Math.max(2, Math.min(x, screenW - menuW - 2));
        menuY = Math.max(2, Math.min(y, screenH - menuH - 2));
        visible = true;
    }

    int itemAt(double mx, double my) {
        if (!visible || mx < menuX || mx >= menuX + menuW || my < menuY + 2 || my >= menuY + menuH - 2) return -1;
        return (int) ((my - menuY - 2) / ROW);
    }

    Runnable actionAt(int index) {
        return index >= 0 && index < items.size() ? items.get(index).action() : null;
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        if (!visible) return;
        g.fill(menuX - 1, menuY - 1, menuX + menuW + 1, menuY + menuH + 1, 0xFF454545);
        g.fill(menuX, menuY, menuX + menuW, menuY + menuH, 0xFF252526);
        int hover = itemAt(mouseX, mouseY);
        for (int i = 0; i < items.size(); i++) {
            int ry = menuY + 2 + i * ROW;
            if (i == hover) g.fill(menuX + 1, ry, menuX + menuW - 1, ry + ROW, 0xFF04395E);
            g.text(font, items.get(i).label(), menuX + 8, ry + 3, 0xFFE0E0E0, false);
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
    }
}
