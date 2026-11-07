package dev.example.mse;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

/**
 * /mse        открыть редактор (откроется последний файл)
 * /mse имя    открыть или создать файл имя.py
 *
 * Клавиши (Настройки > Управление > Minescript Studio):
 *   "Открыть редактор" и "Запустить последний скрипт".
 */
public class MseClient implements ClientModInitializer {
    static KeyMapping openKey;
    static KeyMapping runLastKey;

    private static volatile boolean openPending = false;
    private static volatile String pendingFile = null;

    /** Тип клавиатурной клавиши: в 26.3 имена констант InputConstants.Type изменились, выбираем по имени. */
    private static InputConstants.Type keyboardType() {
        InputConstants.Type fallback = null;
        for (InputConstants.Type t : InputConstants.Type.values()) {
            if (t.name().equals("SCANCODE")) return t;
            if (!t.name().equals("MOUSE") && fallback == null) fallback = t;
        }
        return fallback;
    }

    private static void request(String file) {
        pendingFile = file;
        openPending = true;
    }

    @Override
    public void onInitializeClient() {
        KeyMapping.Category category = KeyMapping.Category.register(
                Identifier.fromNamespaceAndPath("minescript_studio", "main"));
        openKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.minescript_studio.open", keyboardType(), InputConstants.KEY_K, category));
        // по умолчанию не назначена: случайное нажатие не должно запускать скрипты
        runLastKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.minescript_studio.run_last", keyboardType(), InputConstants.UNKNOWN.getValue(), category));

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
            dispatcher.register(
                ClientCommands.literal("mse")
                    .executes(ctx -> {
                        request(null);
                        return 1;
                    })
                    .then(ClientCommands.argument("file", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            for (String n : Scripts.list()) builder.suggest(n);
                            return builder.buildFuture();
                        })
                        .executes(ctx -> {
                            request(StringArgumentType.getString(ctx, "file"));
                            return 1;
                        }))
            )
        );

        // Экран открываем на следующем тике: иначе чат закроет его сразу после команды.
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (openKey.consumeClick()) {
                if (mc.player != null) request(null);
            }
            while (runLastKey.consumeClick()) {
                if (mc.player != null) Studio.runLast();
            }
            if (!openPending) return;
            openPending = false;
            mc.gui.setScreen(new EditorScreen(pendingFile));
        });
    }
}
