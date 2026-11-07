package dev.example.mse;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.Consumer;

/** Список файлов с прокруткой колесом и полосой прокрутки. */
public class FileListWidget extends AbstractWidget {
    private static final int ROW = 14;

    private final Font font;
    /** Вызывается при правом клике; name == null, если клик по пустому месту. */
    interface ContextHandler {
        void open(String name, int x, int y);
    }

    private final Consumer<String> onPick;
    private final ContextHandler onContext;
    private List<String> items = List.of();
    private String selected = "";
    private int scroll;
    private boolean draggingBar;

    public FileListWidget(Font font, int x, int y, int w, int h, Consumer<String> onPick, ContextHandler onContext) {
        super(x, y, w, h, Component.empty());
        this.font = font;
        this.onPick = onPick;
        this.onContext = onContext;
    }

    public void setItems(List<String> items) {
        this.items = items;
        clampScroll();
    }

    public void setSelected(String name) {
        this.selected = name == null ? "" : name;
    }

    private int visibleRows() {
        return Math.max(1, (getHeight() - 2) / ROW);
    }

    private int maxScroll() {
        return Math.max(0, items.size() - visibleRows());
    }

    private void clampScroll() {
        scroll = Math.max(0, Math.min(scroll, maxScroll()));
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        int x = getX(), y = getY(), w = getWidth(), h = getHeight();
        g.fill(x, y, x + w, y + h, 0xFF252526);
        g.fill(x, y, x + w, y + 1, 0xFF3C3C3C);
        g.fill(x, y + h - 1, x + w, y + h, 0xFF3C3C3C);
        g.fill(x, y, x + 1, y + h, 0xFF3C3C3C);
        g.fill(x + w - 1, y, x + w, y + h, 0xFF3C3C3C);

        g.enableScissor(x + 1, y + 1, x + w - 1, y + h - 1);
        if (items.isEmpty()) {
            g.text(font, Lang.t("list.empty"), x + 6, y + 6, 0xFF7A7A7A, false);
        }
        int rows = visibleRows();
        for (int r = 0; r < rows; r++) {
            int idx = scroll + r;
            if (idx >= items.size()) break;
            int ry = y + 1 + r * ROW;
            String name = items.get(idx);
            boolean sel = name.equals(selected);
            boolean hov = mouseX >= x && mouseX < x + w - 6 && mouseY >= ry && mouseY < ry + ROW;
            if (sel) g.fill(x + 1, ry, x + w - 6, ry + ROW, 0xFF37373D);
            else if (hov) g.fill(x + 1, ry, x + w - 6, ry + ROW, 0xFF2A2D2E);
            g.text(font, LabelWidget.fit(font, name, w - 16), x + 5, ry + 3, sel ? 0xFFFFFFFF : 0xFFCCCCCC, false);
        }
        g.disableScissor();

        if (items.size() > rows) {
            int trackH = h - 2;
            int thumbH = Math.max(14, trackH * rows / items.size());
            int thumbY = y + 1 + (trackH - thumbH) * scroll / Math.max(1, maxScroll());
            g.fill(x + w - 5, thumbY, x + w - 2, thumbY + thumbH, 0xFF5A5A5A);
        }
    }

    private void dragTo(double my) {
        int rows = visibleRows();
        int trackH = getHeight() - 2;
        int thumbH = Math.max(14, trackH * rows / Math.max(1, items.size()));
        double frac = (my - getY() - 1 - thumbH / 2.0) / Math.max(1, trackH - thumbH);
        scroll = (int) Math.round(frac * maxScroll());
        clampScroll();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
        double mx = e.x(), my = e.y();
        if (!isMouseOver(mx, my)) return false;
        if (mx >= getX() + getWidth() - 6 && items.size() > visibleRows()) {
            draggingBar = true;
            dragTo(my);
            return true;
        }
        int idx = scroll + (int) ((my - getY() - 1) / ROW);
        boolean valid = my >= getY() + 1 && idx >= 0 && idx < items.size();
        if (e.button() == InputConstants.MOUSE_BUTTON_RIGHT) {
            onContext.open(valid ? items.get(idx) : null, (int) mx, (int) my);
            return true;
        }
        if (e.button() == InputConstants.MOUSE_BUTTON_LEFT && valid) {
            onPick.accept(items.get(idx));
        }
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
        if (draggingBar) {
            dragTo(e.y());
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent e) {
        boolean was = draggingBar;
        draggingBar = false;
        return was;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!isMouseOver(mouseX, mouseY)) return false;
        scroll -= (int) Math.signum(scrollY) * 2;
        clampScroll();
        return true;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
    }
}
