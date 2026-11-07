package dev.example.mse;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

import java.util.function.IntSupplier;
import java.util.function.Supplier;

/** Неинтерактивная надпись (в Minecraft нет готового Label). */
public class LabelWidget extends AbstractWidget {
    private final Font font;
    private final Supplier<String> text;
    private final IntSupplier color;
    private final boolean center;

    public LabelWidget(Font font, int x, int y, int w, int h, Supplier<String> text, IntSupplier color, boolean center) {
        super(x, y, w, h, Component.empty());
        this.font = font;
        this.text = text;
        this.color = color;
        this.center = center;
        this.active = false;
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        String s = fit(font, text.get(), getWidth());
        int tx = center ? getX() + (getWidth() - font.width(s)) / 2 : getX();
        g.text(font, s, tx, getY(), color.getAsInt(), false);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
    }

    static String fit(Font font, String s, int maxW) {
        if (font.width(s) <= maxW) return s;
        int ew = font.width("...");
        int n = s.length();
        while (n > 0 && font.width(s.substring(0, n)) + ew > maxW) n--;
        return s.substring(0, n) + "...";
    }
}
