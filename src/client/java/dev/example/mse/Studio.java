package dev.example.mse;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** Единая точка для событий: пишет в консоль и, если включено в настройках отладки, в чат. */
final class Studio {
    private Studio() {}

    enum Event { SAVE, BUILD_START, BUILD_END, RUN, FILES, ERRORS }

    static void log(Event event, int level, String text) {
        ConsoleLog.add(level, text);
        MseConfig c = MseConfig.INSTANCE;
        if (!c.debugToChat || !enabled(event, c)) return;
        var player = Minecraft.getInstance().player;
        if (player != null) player.sendSystemMessage(Component.literal("[Studio] " + text));
    }

    /** Сообщение только в чат (когда редактор закрыт и консоли нет). */
    static void chat(String text) {
        var player = Minecraft.getInstance().player;
        if (player != null) player.sendSystemMessage(Component.literal("[Studio] " + text));
    }

    /** Запуск последнего открытого скрипта по горячей клавише (без редактора). */
    static void runLast() {
        MseConfig c = MseConfig.INSTANCE;
        String name = Scripts.normalize(c.lastFile);
        if (name == null || !Scripts.exists(name)) {
            chat(Lang.t("log.no_last"));
            return;
        }
        String args = c.launchArgs.getOrDefault(name, "").trim();
        String command = Scripts.baseName(name) + (args.isEmpty() ? "" : " " + args);
        log(Event.RUN, ConsoleLog.INFO, Lang.t("log.run", "\\" + command));
        if (!MinescriptBridge.run(command)) chat(Lang.t("log.run_failed", name));
    }

    private static boolean enabled(Event event, MseConfig c) {
        return switch (event) {
            case SAVE -> c.dbgSave;
            case BUILD_START -> c.dbgBuildStart;
            case BUILD_END -> c.dbgBuildEnd;
            case RUN -> c.dbgRun;
            case FILES -> c.dbgFiles;
            case ERRORS -> c.dbgErrors;
        };
    }
}
