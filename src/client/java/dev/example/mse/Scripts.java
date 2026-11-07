package dev.example.mse;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** Работа с файлами в папке minescript. */
final class Scripts {
    private Scripts() {}

    private static final Pattern VALID = Pattern.compile("[A-Za-z0-9_\\-]+(\\.pyj?)?");
    static final String TEMPLATE = "import minescript as m\n\nm.echo(\"Hello from Minescript!\")\n";

    /** Стандартная папка Minescript: <игра>/minescript (в ней лежит config.txt). */
    static Path defaultDir() {
        return FabricLoader.getInstance().getGameDir().resolve("minescript");
    }

    /** Папка со скриптами, с которой работает редактор (настраивается в настройках мода). */
    static Path dir() {
        String s = MseConfig.INSTANCE.scriptsDir;
        if (s != null && !s.isBlank()) {
            try {
                return Paths.get(s);
            } catch (InvalidPathException ignored) {
                // неверный путь в конфиге: используем стандартную папку
            }
        }
        return defaultDir();
    }

    /** Безопасное имя с расширением (.py по умолчанию) или null. */
    static String normalize(String raw) {
        if (raw == null) return null;
        String s = raw.trim();
        if (s.isEmpty() || !VALID.matcher(s).matches()) return null;
        if (!s.endsWith(".py") && !s.endsWith(".pyj")) s = s + ".py";
        return s;
    }

    static String baseName(String file) {
        int dot = file.lastIndexOf('.');
        return dot < 0 ? file : file.substring(0, dot);
    }

    static boolean exists(String name) {
        return Files.isRegularFile(dir().resolve(name));
    }

    static List<String> list() {
        try {
            Files.createDirectories(dir());
            try (Stream<Path> s = Files.list(dir())) {
                return s.filter(Files::isRegularFile)
                        .map(p -> p.getFileName().toString())
                        .filter(n -> n.endsWith(".py") || n.endsWith(".pyj"))
                        .sorted(String.CASE_INSENSITIVE_ORDER)
                        .toList();
            }
        } catch (IOException e) {
            return List.of();
        }
    }

    static String read(String name) {
        try {
            return Files.readString(dir().resolve(name), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }

    static boolean write(String name, String text) {
        try {
            Files.createDirectories(dir());
            Files.writeString(dir().resolve(name), text, StandardCharsets.UTF_8);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /** "Удаление" = перенос в minescript/.mse_trash, чтобы можно было вернуть. */
    static boolean trash(String name) {
        try {
            Path trash = dir().resolve(".mse_trash");
            Files.createDirectories(trash);
            Files.move(dir().resolve(name), trash.resolve(System.currentTimeMillis() + "_" + name),
                    StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /** Переименование файла без перезаписи существующего. */
    static boolean rename(String oldName, String newName) {
        try {
            Files.move(dir().resolve(oldName), dir().resolve(newName));
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /** Копия файла без перезаписи существующего. */
    static boolean copy(String from, String to) {
        try {
            Files.copy(dir().resolve(from), dir().resolve(to));
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
