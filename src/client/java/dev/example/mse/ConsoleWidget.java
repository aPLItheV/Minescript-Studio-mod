package dev.example.mse;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/** Сворачиваемая консоль внизу редактора: события сборки, запуска, работы с файлами. */
public class ConsoleWidget extends AbstractWidget {
    static final int HEADER = 14;

    private final Font font;
    private final boolean expanded;
    private final Runnable onToggle;
    private final int lineH;
    private int scroll;                       // на сколько строк отмотано вверх от конца
    private final SimpleDateFormat time = new SimpleDateFormat("HH:mm:ss");

    public ConsoleWidget(Font font, int x, int y, int w, int h, boolean expanded, Runnable onToggle) {
        super(x, y, w, h, Component.empty());
        this.font = font;
        this.expanded = expanded;
        this.onToggle = onToggle;
        this.lineH = font.lineHeight + 2;
    }

    private static int levelColor(int level) {
        return switch (level) {
            case ConsoleLog.OK -> 0xFF98C379;
            case ConsoleLog.WARN -> 0xFFE5C07B;
            case ConsoleLog.ERROR -> 0xFFE06C75;
            default -> 0xFFB0B0B0;
        };
    }

    private int rows() {
        return Math.max(1, (getHeight() - HEADER - 4) / lineH);
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        int x = getX(), y = getY(), w = getWidth(), h = getHeight();
        g.fill(x, y, x + w, y + h, 0xFF181818);
        g.fill(x, y, x + w, y + HEADER, 0xFF2A2A2A);
        g.fill(x, y, x + w, y + 1, 0xFF3C3C3C);
        g.fill(x, y + h - 1, x + w, y + h, 0xFF3C3C3C);
        g.fill(x, y, x + 1, y + h, 0xFF3C3C3C);
        g.fill(x + w - 1, y, x + w, y + h, 0xFF3C3C3C);

        String title = Lang.t(expanded ? "console.title_open" : "console.title_closed", ConsoleLog.size());
        g.text(font, title, x + 6, y + 3, 0xFFD0D0D0, false);
        String clear = Lang.t("console.clear");
        int cw = font.width(clear);
        g.text(font, clear, x + w - cw - 6, y + 3, 0xFF8A8A8A, false);

        if (!expanded) return;
        List<ConsoleLog.Entry> all = ConsoleLog.snapshot();
        int rows = rows();
        int maxScroll = Math.max(0, all.size() - rows);
        scroll = Math.max(0, Math.min(scroll, maxScroll));
        int end = all.size() - scroll;
        int start = Math.max(0, end - rows);
        boolean stamps = MseConfig.INSTANCE.consoleTimestamps;
        g.enableScissor(x + 1, y + HEADER, x + w - 1, y + h - 1);
        for (int i = start; i < end; i++) {
            ConsoleLog.Entry e = all.get(i);
            String line = (stamps ? "[" + time.format(new Date(e.time())) + "] " : "") + e.text();
            g.text(font, LabelWidget.fit(font, line, w - 16), x + 6, y + HEADER + 3 + (i - start) * lineH,
                    levelColor(e.level()), false);
        }
        g.disableScissor();
        if (maxScroll > 0) {
            int trackH = h - HEADER - 4;
            int thumbH = Math.max(10, trackH * rows / all.size());
            int thumbY = y + HEADER + 2 + (trackH - thumbH) * (maxScroll - scroll) / maxScroll;
            g.fill(x + w - 5, thumbY, x + w - 2, thumbY + thumbH, 0xFF5A5A5A);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
        double mx = e.x(), my = e.y();
        if (!isMouseOver(mx, my) || my > getY() + HEADER) return false;
        int cw = font.width(Lang.t("console.clear"));
        if (mx >= getX() + getWidth() - cw - 10) {
            ConsoleLog.clear();
            scroll = 0;
        } else {
            onToggle.run();
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!expanded || !isMouseOver(mouseX, mouseY)) return false;
        scroll += (int) Math.signum(scrollY) * 3;
        return true;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
    }
}
