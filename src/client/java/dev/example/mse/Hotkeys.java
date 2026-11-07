package dev.example.mse;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.input.KeyEvent;

import java.util.List;

/**
 * Сочетания клавиш внутри редактора (хранятся в конфиге мода).
 * Две глобальные клавиши (открыть редактор, запустить последний скрипт) живут в настройках управления Minecraft.
 */
final class Hotkeys {
    private Hotkeys() {}

    static final int CTRL = 1, SHIFT = 2, ALT = 4;

    record Action(String id, int key, int mods, String label) {}

    static final List<Action> ACTIONS = List.of(
            new Action("save", InputConstants.KEY_S, CTRL, "S"),
            new Action("run", InputConstants.KEY_R, CTRL, "R"),
            new Action("find", InputConstants.KEY_F, CTRL, "F"),
            new Action("goto", InputConstants.KEY_G, CTRL, "G"),
            new Action("comment", InputConstants.KEY_SLASH, CTRL, "/"),
            new Action("duplicate", InputConstants.KEY_D, CTRL, "D"),
            new Action("delete_line", InputConstants.KEY_K, CTRL | SHIFT, "K"),
            new Action("console", InputConstants.KEY_J, CTRL, "J"));

    /** Зарезервированные сочетания (работают всегда): {клавиша, модификаторы}. */
    private static final int[][] RESERVED = {
            {InputConstants.KEY_A, CTRL}, {InputConstants.KEY_C, CTRL}, {InputConstants.KEY_X, CTRL},
            {InputConstants.KEY_V, CTRL}, {InputConstants.KEY_Z, CTRL}, {InputConstants.KEY_Z, CTRL | SHIFT},
            {InputConstants.KEY_Y, CTRL}, {InputConstants.KEY_U, CTRL}, {InputConstants.KEY_SPACE, CTRL},
            {InputConstants.KEY_BACKSPACE, CTRL}, {InputConstants.KEY_LEFT, CTRL}, {InputConstants.KEY_RIGHT, CTRL},
            {InputConstants.KEY_HOME, CTRL}, {InputConstants.KEY_END, CTRL}, {InputConstants.KEY_RETURN, CTRL},
            {InputConstants.KEY_UP, ALT}, {InputConstants.KEY_DOWN, ALT}, {InputConstants.KEY_TAB, 0},
            {InputConstants.KEY_ESCAPE, 0}, {InputConstants.KEY_RETURN, 0}, {InputConstants.KEY_BACKSPACE, 0},
            {InputConstants.KEY_DELETE, 0}};

    static Action action(String id) {
        for (Action a : ACTIONS) if (a.id().equals(id)) return a;
        throw new IllegalArgumentException(id);
    }

    static int key(String id) {
        int[] o = MseConfig.INSTANCE.hotkeys.get(id);
        return o != null && o.length == 2 ? o[0] : action(id).key();
    }

    static int mods(String id) {
        int[] o = MseConfig.INSTANCE.hotkeys.get(id);
        return o != null && o.length == 2 ? o[1] : action(id).mods();
    }

    static String label(String id) {
        String l = MseConfig.INSTANCE.hotkeyLabels.get(id);
        return l != null && MseConfig.INSTANCE.hotkeys.containsKey(id) ? l : action(id).label();
    }

    /** Например "Ctrl+Shift+K". */
    static String describe(String id) {
        int m = mods(id);
        return ((m & CTRL) != 0 ? "Ctrl+" : "") + ((m & SHIFT) != 0 ? "Shift+" : "")
                + ((m & ALT) != 0 ? "Alt+" : "") + label(id);
    }

    static int modsOf(KeyEvent e) {
        return (e.hasControlDown() ? CTRL : 0) | (e.hasShiftDown() ? SHIFT : 0) | (e.hasAltDown() ? ALT : 0);
    }

    static boolean matches(String id, KeyEvent e) {
        return e.key() == key(id) && modsOf(e) == mods(id);
    }

    static boolean isFunctionKey(int key) {
        return key >= InputConstants.KEY_F1 && key <= InputConstants.KEY_F12;
    }

    /**
     * Проверка нового сочетания.
     *
     * @return null если можно; "reserved" если занято системой редактора; "need_modifier" если нет Ctrl/Alt;
     *         иначе id действия, которое уже использует это сочетание
     */
    static String conflict(String id, int key, int mods) {
        if (mods == 0 && !isFunctionKey(key)) return "need_modifier";
        if ((mods & (CTRL | ALT)) == 0 && !isFunctionKey(key)) return "need_modifier";
        for (int[] r : RESERVED) if (r[0] == key && r[1] == mods) return "reserved";
        for (Action a : ACTIONS) {
            if (!a.id().equals(id) && key(a.id()) == key && mods(a.id()) == mods) return a.id();
        }
        return null;
    }

    static void set(String id, int key, int mods, String label) {
        MseConfig.INSTANCE.hotkeys.put(id, new int[]{key, mods});
        MseConfig.INSTANCE.hotkeyLabels.put(id, label);
        MseConfig.save();
    }

    static void resetAll() {
        MseConfig.INSTANCE.hotkeys.clear();
        MseConfig.INSTANCE.hotkeyLabels.clear();
        MseConfig.save();
    }
}
