package dev.example.mse;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/** Настройки мода: config/minescript_studio.json (старый minescript_editor.json подхватывается один раз). */
public final class MseConfig {
    public boolean lineNumbers = true;
    public boolean syntaxHighlight = true;
    public boolean autoIndent = true;
    public boolean autoPairs = true;
    public int tabSize = 4;                 // 2, 4 или 8
    public String autocomplete = "auto";    // auto | manual | off
    public String lastFile = "";
    public String scriptsDir = "";          // пусто = <игра>/minescript
    public boolean syntaxCheck = true;      // проверять синтаксис Python при сохранении
    public boolean trimWhitespace = true;   // убирать пробелы в конце строк при сохранении
    public boolean consoleOpen = true;          // консоль развёрнута
    public boolean consoleTimestamps = true;    // время в строках консоли
    public boolean debugToChat = false;         // дублировать события в чат
    public boolean dbgBuildStart = true;        // события, которые дублируются в чат (если debugToChat включён)
    public boolean dbgBuildEnd = true;
    public boolean dbgRun = true;
    public boolean dbgSave = true;
    public boolean dbgFiles = true;
    public boolean dbgErrors = true;
    public java.util.Map<String, int[]> hotkeys = new java.util.HashMap<>();            // действие -> {клавиша, модификаторы}
    public java.util.Map<String, String> hotkeyLabels = new java.util.HashMap<>();      // действие -> название клавиши
    public java.util.Map<String, int[]> positions = new java.util.HashMap<>();     // файл -> {строка, столбец}
    public java.util.Map<String, String> launchArgs = new java.util.HashMap<>();   // файл -> параметры запуска

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public static MseConfig INSTANCE = load();

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("minescript_studio.json");
    }

    private static Path oldPath() {
        return FabricLoader.getInstance().getConfigDir().resolve("minescript_editor.json");
    }

    private static MseConfig load() {
        MseConfig c = null;
        Path p = path();
        if (!Files.isRegularFile(p) && Files.isRegularFile(oldPath())) p = oldPath();
        if (Files.isRegularFile(p)) {
            try (Reader r = Files.newBufferedReader(p)) {
                c = GSON.fromJson(r, MseConfig.class);
            } catch (Exception e) {
                c = null;
            }
        }
        if (c == null) c = new MseConfig();
        c.sanitize();
        return c;
    }

    private void sanitize() {
        if (tabSize != 2 && tabSize != 4 && tabSize != 8) tabSize = 4;
        if (!"auto".equals(autocomplete) && !"manual".equals(autocomplete) && !"off".equals(autocomplete)) {
            autocomplete = "auto";
        }
        if (lastFile == null) lastFile = "";
        if (scriptsDir == null) scriptsDir = "";
        if (positions == null) positions = new java.util.HashMap<>();
        if (hotkeys == null) hotkeys = new java.util.HashMap<>();
        if (hotkeyLabels == null) hotkeyLabels = new java.util.HashMap<>();
        if (launchArgs == null) launchArgs = new java.util.HashMap<>();
    }

    public static void save() {
        try {
            Files.createDirectories(path().getParent());
            try (Writer w = Files.newBufferedWriter(path())) {
                GSON.toJson(INSTANCE, w);
            }
        } catch (IOException ignored) {
            // конфиг не критичен
        }
    }
}
