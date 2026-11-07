package dev.example.mse;

import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.function.BiConsumer;

/** Проверка синтаксиса Python через ast.parse в отдельном процессе (используется python из конфига Minescript). */
final class SyntaxCheck {
    private SyntaxCheck() {}

    private static final String SCRIPT =
            "import ast,sys\n"
            + "try:\n"
            + "    ast.parse(open(sys.argv[1], encoding='utf-8').read(), sys.argv[1])\n"
            + "except SyntaxError as e:\n"
            + "    print(e.lineno or 0, e.msg)\n"
            + "    sys.exit(1)\n";

    /**
     * @param file     проверяемый файл
     * @param onResult (номер строки или 0 если ошибки нет, текст ошибки); вызывается в главном потоке.
     *                 Если Python не запустился, номер строки равен -1.
     */
    static void check(Path file, BiConsumer<Integer, String> onResult) {
        Thread t = new Thread(() -> {
            String python = MinescriptConfigFile.get("python");
            if (python == null || python.isBlank()) python = MinescriptConfigFile.defaultPython();
            try {
                ProcessBuilder pb = new ProcessBuilder(MinescriptConfigFile.expandEnv(python), "-c", SCRIPT, file.toString());
                pb.environment().remove("LD_LIBRARY_PATH");
                pb.environment().remove("LD_PRELOAD");
                Process p = pb.start();
                String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
                int code = p.waitFor();
                if (code == 0) {
                    Minecraft.getInstance().execute(() -> onResult.accept(0, ""));
                } else if (out.isEmpty()) {
                    Minecraft.getInstance().execute(() -> onResult.accept(-1, ""));
                } else {
                    int sp = out.indexOf(' ');
                    int line = 0;
                    String msg = out;
                    try {
                        line = Integer.parseInt(sp < 0 ? out : out.substring(0, sp));
                        msg = sp < 0 ? "" : out.substring(sp + 1);
                    } catch (NumberFormatException ignored) {
                        // нестандартный вывод: показываем как есть
                    }
                    final int fl = line;
                    final String fm = msg;
                    Minecraft.getInstance().execute(() -> onResult.accept(fl, fm));
                }
            } catch (IOException e) {
                // Python не найден: сообщаем вызывающему (номер строки -1)
                Minecraft.getInstance().execute(() -> onResult.accept(-1, ""));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "mse-syntax-check");
        t.setDaemon(true);
        t.start();
    }
}
