package dev.example.mse;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/** Настройки мода с вкладками: общие, пути, горячие клавиши, отладка. */
public class ConfigScreen extends Screen {
    private static final int GREEN = 0xFF98C379;
    private static final int RED = 0xFFE06C75;
    private static final int TAB_GENERAL = 0, TAB_PATHS = 1, TAB_HOTKEYS = 2, TAB_DEBUG = 3;
    private static final List<String> GLOBAL_KEYS = List.of("open", "run_last");

    private final Screen parent;
    private int tab = TAB_GENERAL;
    private boolean rebuildPending;

    private EditBox dirBox;
    private EditBox pyBox;
    private String pendingDir;
    private String pendingPython;
    private final String shownDir;
    private final String shownPython;

    private String message = "";
    private int messageColor = GREEN;
    private String capturing;                                  // id действия, ожидающего клавишу
    private final Map<String, Button> hotkeyButtons = new HashMap<>();

    public ConfigScreen(Screen parent) {
        super(Component.literal(Lang.t("cfg.title")));
        this.parent = parent;
        this.shownDir = Scripts.dir().toString();
        String py = MinescriptConfigFile.get("python");
        this.shownPython = py != null ? py : MinescriptConfigFile.defaultPython();
        this.pendingDir = shownDir;
        this.pendingPython = shownPython;
    }

    private static String acText(String v) {
        return Lang.t(switch (v) {
            case "auto" -> "cfg.ac.auto";
            case "manual" -> "cfg.ac.manual";
            default -> "cfg.ac.off";
        });
    }

    private void say(String text, int color) {
        message = text;
        messageColor = color;
    }

    private void captureBoxes() {
        if (dirBox != null) pendingDir = dirBox.getValue();
        if (pyBox != null) pendingPython = pyBox.getValue();
    }

    @Override
    public void tick() {
        super.tick();
        if (rebuildPending) {
            rebuildPending = false;
            rebuildWidgets();
        }
    }

    // ===================== построение =====================

    @Override
    protected void init() {
        dirBox = null;
        pyBox = null;
        hotkeyButtons.clear();
        int w = Math.min(420, this.width - 20);
        int x = this.width / 2 - w / 2;
        int colW = (w - 10) / 2;
        int x2 = x + colW + 10;
        int y0 = Math.max(6, (this.height - 240) / 2);

        addRenderableWidget(new LabelWidget(this.font, x, y0, w, 12,
                () -> Lang.t("cfg.title"), () -> 0xFFFFFFFF, true));

        // вкладки
        String[] tabKeys = {"cfg.tab.general", "cfg.tab.paths", "cfg.tab.hotkeys", "cfg.tab.debug"};
        int tw = (w - 3 * 4) / 4;
        for (int i = 0; i < 4; i++) {
            final int target = i;
            Button b = Button.builder(Component.literal(Lang.t(tabKeys[i])), btn -> {
                captureBoxes();
                capturing = null;
                tab = target;
                rebuildPending = true;
            }).bounds(x + i * (tw + 4), y0 + 16, tw, 20).build();
            b.active = i != tab;
            addRenderableWidget(b);
        }

        int y = y0 + 44;
        switch (tab) {
            case TAB_GENERAL -> buildGeneral(x, x2, colW, y);
            case TAB_PATHS -> buildPaths(x, w, y);
            case TAB_HOTKEYS -> buildHotkeys(x, x2, colW, w, y);
            default -> buildDebug(x, x2, colW, w, y);
        }

        int doneY = Math.min(this.height - 26, y0 + 44 + 5 * 24 + 40);
        addRenderableWidget(new LabelWidget(this.font, x, doneY - 14, w, 12,
                () -> message, () -> messageColor, false));
        addRenderableWidget(Button.builder(Component.literal(Lang.t("cfg.done")), b -> applyAndClose())
                .bounds(x, doneY, w, 20).build());
    }

    private void buildGeneral(int x, int x2, int colW, int y) {
        final MseConfig c = MseConfig.INSTANCE;
        toggle(x, y, colW, () -> Lang.t("cfg.line_numbers", Lang.onOff(c.lineNumbers)), () -> c.lineNumbers = !c.lineNumbers);
        toggle(x2, y, colW, () -> Lang.t("cfg.auto_indent", Lang.onOff(c.autoIndent)), () -> c.autoIndent = !c.autoIndent);
        y += 24;
        toggle(x, y, colW, () -> Lang.t("cfg.highlight", Lang.onOff(c.syntaxHighlight)), () -> c.syntaxHighlight = !c.syntaxHighlight);
        toggle(x2, y, colW, () -> Lang.t("cfg.auto_pairs", Lang.onOff(c.autoPairs)), () -> c.autoPairs = !c.autoPairs);
        y += 24;
        toggle(x, y, colW, () -> Lang.t("cfg.tab_size", c.tabSize),
                () -> c.tabSize = c.tabSize == 2 ? 4 : (c.tabSize == 4 ? 8 : 2));
        toggle(x2, y, colW, () -> Lang.t("cfg.suggestions", acText(c.autocomplete)), () -> c.autocomplete =
                c.autocomplete.equals("auto") ? "manual" : (c.autocomplete.equals("manual") ? "off" : "auto"));
        y += 24;
        toggle(x, y, colW, () -> Lang.t("cfg.syntax_check", Lang.onOff(c.syntaxCheck)), () -> c.syntaxCheck = !c.syntaxCheck);
        toggle(x2, y, colW, () -> Lang.t("cfg.trim", Lang.onOff(c.trimWhitespace)), () -> c.trimWhitespace = !c.trimWhitespace);
    }

    private void buildPaths(int x, int w, int y) {
        int boxW = w - 86;
        addRenderableWidget(new LabelWidget(this.font, x, y, w, 12,
                () -> Lang.t("cfg.dir"), () -> 0xFF9DA5B4, false));
        dirBox = new EditBox(this.font, x, y + 12, boxW, 20, Component.literal("dir"));
        dirBox.setMaxLength(1024);
        dirBox.setValue(pendingDir);
        addRenderableWidget(dirBox);
        addRenderableWidget(Button.builder(Component.literal(Lang.t("cfg.browse")), b ->
                FileDialogs.pickFolder(dirBox.getValue(), dirBox::setValue, e -> say(e, RED)))
                .bounds(x + boxW + 6, y + 12, 80, 20).build());

        y += 44;
        addRenderableWidget(new LabelWidget(this.font, x, y, w, 12,
                () -> Lang.t("cfg.python"), () -> 0xFF9DA5B4, false));
        pyBox = new EditBox(this.font, x, y + 12, boxW, 20, Component.literal("python"));
        pyBox.setMaxLength(1024);
        pyBox.setValue(pendingPython);
        addRenderableWidget(pyBox);
        addRenderableWidget(Button.builder(Component.literal(Lang.t("cfg.browse")), b ->
                FileDialogs.pickFile(pyBox.getValue(), pyBox::setValue, e -> say(e, RED)))
                .bounds(x + boxW + 6, y + 12, 80, 20).build());
    }

    private void buildDebug(int x, int x2, int colW, int w, int y) {
        final MseConfig c = MseConfig.INSTANCE;
        toggle(x, y, w, () -> Lang.t("cfg.debug.master", Lang.onOff(c.debugToChat)), () -> c.debugToChat = !c.debugToChat);
        y += 24;
        toggle(x, y, colW, () -> Lang.t("cfg.debug.build_start", Lang.onOff(c.dbgBuildStart)), () -> c.dbgBuildStart = !c.dbgBuildStart);
        toggle(x2, y, colW, () -> Lang.t("cfg.debug.build_end", Lang.onOff(c.dbgBuildEnd)), () -> c.dbgBuildEnd = !c.dbgBuildEnd);
        y += 24;
        toggle(x, y, colW, () -> Lang.t("cfg.debug.run", Lang.onOff(c.dbgRun)), () -> c.dbgRun = !c.dbgRun);
        toggle(x2, y, colW, () -> Lang.t("cfg.debug.save", Lang.onOff(c.dbgSave)), () -> c.dbgSave = !c.dbgSave);
        y += 24;
        toggle(x, y, colW, () -> Lang.t("cfg.debug.files", Lang.onOff(c.dbgFiles)), () -> c.dbgFiles = !c.dbgFiles);
        toggle(x2, y, colW, () -> Lang.t("cfg.debug.errors", Lang.onOff(c.dbgErrors)), () -> c.dbgErrors = !c.dbgErrors);
        y += 24;
        toggle(x, y, w, () -> Lang.t("cfg.debug.timestamps", Lang.onOff(c.consoleTimestamps)), () -> c.consoleTimestamps = !c.consoleTimestamps);
    }

    // ---------- горячие клавиши ----------

    private KeyMapping globalMapping(String id) {
        return id.equals("open") ? MseClient.openKey : MseClient.runLastKey;
    }

    private String hotkeyLabel(String id) {
        String name = Lang.t("hotkey." + id);
        if (id.equals(capturing)) return name + ": " + Lang.t("cfg.hotkeys.press");
        if (GLOBAL_KEYS.contains(id)) {
            KeyMapping m = globalMapping(id);
            return name + ": " + (m == null ? "?" : m.getTranslatedKeyMessage().getString());
        }
        return name + ": " + Hotkeys.describe(id);
    }

    private void refreshHotkeyLabels() {
        for (Map.Entry<String, Button> e : hotkeyButtons.entrySet()) {
            e.getValue().setMessage(Component.literal(hotkeyLabel(e.getKey())));
        }
    }

    private void hotkeyButton(String id, int x, int y, int w) {
        Button b = Button.builder(Component.literal(hotkeyLabel(id)), btn -> {
            capturing = id;
            say("", GREEN);
            refreshHotkeyLabels();
        }).bounds(x, y, w, 20).build();
        hotkeyButtons.put(id, b);
        addRenderableWidget(b);
    }

    private void buildHotkeys(int x, int x2, int colW, int w, int y) {
        List<String> ids = new java.util.ArrayList<>(GLOBAL_KEYS);
        for (Hotkeys.Action a : Hotkeys.ACTIONS) ids.add(a.id());
        for (int i = 0; i < ids.size(); i++) {
            hotkeyButton(ids.get(i), i % 2 == 0 ? x : x2, y + (i / 2) * 24, colW);
        }
        int ny = y + ((ids.size() + 1) / 2) * 24;
        addRenderableWidget(new LabelWidget(this.font, x, ny + 2, w, 12,
                () -> Lang.t("cfg.hotkeys.note"), () -> 0xFF7A7A7A, false));
        addRenderableWidget(Button.builder(Component.literal(Lang.t("cfg.hotkeys.reset")), b -> resetHotkeys())
                .bounds(x, ny + 16, colW, 20).build());
    }

    private void resetHotkeys() {
        Hotkeys.resetAll();
        for (String id : GLOBAL_KEYS) {
            KeyMapping m = globalMapping(id);
            if (m != null) m.setKey(m.getDefaultKey());
        }
        KeyMapping.resetMapping();
        Minecraft.getInstance().options.save();
        capturing = null;
        refreshHotkeyLabels();
        say(Lang.t("cfg.hotkeys.reset_done"), GREEN);
    }

    private static boolean isModifierKey(int k) {
        return k == InputConstants.KEY_LCONTROL || k == InputConstants.KEY_RCONTROL
                || k == InputConstants.KEY_LSHIFT || k == InputConstants.KEY_RSHIFT
                || k == InputConstants.KEY_LALT || k == InputConstants.KEY_RALT;
    }

    private void assignCaptured(KeyEvent event) {
        String id = capturing;
        capturing = null;
        InputConstants.Key key = InputConstants.getKey(event);
        if (GLOBAL_KEYS.contains(id)) {
            KeyMapping m = globalMapping(id);
            if (m != null) {
                m.setKey(key);
                KeyMapping.resetMapping();
                Minecraft.getInstance().options.save();
            }
        } else {
            int mods = Hotkeys.modsOf(event);
            String problem = Hotkeys.conflict(id, event.key(), mods);
            if (problem == null) {
                Hotkeys.set(id, event.key(), mods, key.getDisplayName().getString());
                say("", GREEN);
            } else if (problem.equals("reserved")) {
                say(Lang.t("cfg.hotkeys.reserved"), RED);
            } else if (problem.equals("need_modifier")) {
                say(Lang.t("cfg.hotkeys.need_modifier"), RED);
            } else {
                say(Lang.t("cfg.hotkeys.conflict", Lang.t("hotkey." + problem)), RED);
            }
        }
        refreshHotkeyLabels();
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (capturing != null) {
            int k = event.key();
            if (k == InputConstants.KEY_ESCAPE) {
                capturing = null;
                refreshHotkeyLabels();
                return true;
            }
            if (!isModifierKey(k)) assignCaptured(event);
            return true;
        }
        return super.keyPressed(event);
    }

    // ===================== общие =====================

    private void toggle(int x, int y, int w, Supplier<String> label, Runnable action) {
        addRenderableWidget(Button.builder(Component.literal(label.get()), btn -> {
            action.run();
            MseConfig.save();
            btn.setMessage(Component.literal(label.get()));
        }).bounds(x, y, w, 20).build());
    }

    /** Применяет пути; при ошибке остаётся на экране и показывает причину. */
    private void applyAndClose() {
        captureBoxes();
        MseConfig cfg = MseConfig.INSTANCE;

        String dirText = pendingDir.trim();
        if (!dirText.equals(shownDir)) {
            Path defaultDir = Scripts.defaultDir();
            if (dirText.isEmpty() || samePath(dirText, defaultDir)) {
                cfg.scriptsDir = "";
                String err = MinescriptConfigFile.removeMarked("command_path");
                if (err != null) { say(err, RED); return; }
            } else {
                Path p;
                try {
                    p = Paths.get(dirText).toAbsolutePath().normalize();
                    Files.createDirectories(p);
                } catch (InvalidPathException | java.io.IOException e) {
                    say(Lang.t("cfg.dir_err", dirText), RED);
                    return;
                }
                String err = MinescriptConfigFile.set("command_path", MinescriptConfigFile.commandPathFor(p), true);
                if (err != null) { say(err, RED); return; }
                cfg.scriptsDir = p.toString();
            }
        }

        String py = pendingPython.trim();
        if (!py.isEmpty() && !py.equals(shownPython)) {
            boolean hasSeparator = py.contains("/") || py.contains("\\");
            if (hasSeparator && !Files.isRegularFile(Paths.get(MinescriptConfigFile.expandEnv(py)))) {
                say(Lang.t("cfg.python_missing", py), RED);
                return;
            }
            String err = MinescriptConfigFile.set("python", py, false);
            if (err != null) { say(err, RED); return; }
        }

        MseConfig.save();
        Minecraft.getInstance().gui.setScreen(parent);
    }

    private static boolean samePath(String text, Path other) {
        try {
            return Paths.get(text).toAbsolutePath().normalize().equals(other.toAbsolutePath().normalize());
        } catch (InvalidPathException e) {
            return false;
        }
    }

    /** Esc: выйти без применения путей (переключатели и клавиши уже сохранены). */
    @Override
    public void onClose() {
        MseConfig.save();
        Minecraft.getInstance().gui.setScreen(parent);
    }
}
