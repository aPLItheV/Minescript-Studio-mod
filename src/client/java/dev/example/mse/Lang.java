package dev.example.mse;

import net.minecraft.client.resources.language.I18n;

/** Перевод строк интерфейса через языковые файлы мода (assets/minescript_studio/lang). */
final class Lang {
    private Lang() {}

    static String t(String key, Object... args) {
        return I18n.get(key, args);
    }

    static String onOff(boolean value) {
        return t(value ? "cfg.on" : "cfg.off");
    }
}
