package dev.example.mse;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Чтение и правка minescript/config.txt (строки вида NAME=VALUE, # — комментарии,
 * строка, оканчивающаяся на обратный слэш, продолжается на следующей).
 */
final class MinescriptConfigFile {
    private MinescriptConfigFile() {}

    /** Пометка над строками, которые записал этот мод (только их мы потом можем удалить). */
    static final String MARK = "# minescript_editor";

    static Path file() {
        return Scripts.defaultDir().resolve("config.txt");
    }

    /** Раскрывает %ПЕРЕМЕННЫЕ% окружения (нужно на Windows: %userprofile%\\AppData\\...). */
    static String expandEnv(String value) {
        java.util.regex.Matcher m = Pattern.compile("%([^%]+)%").matcher(value);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String v = System.getenv(m.group(1));
            if (v == null) v = System.getenv(m.group(1).toUpperCase());
            m.appendReplacement(out, Matcher.quoteReplacement(v != null ? v : m.group()));
        }
        m.appendTail(out);
        return out.toString();
    }

    static String defaultPython() {
        boolean win = System.getProperty("os.name", "").toLowerCase().contains("win");
        return win ? "%userprofile%\\AppData\\Local\\Microsoft\\WindowsApps\\python3.exe" : "/usr/bin/python3";
    }

    private static Pattern varPattern(String name) {
        return Pattern.compile("^\\s*" + Pattern.quote(name) + "\\s*=\\s*(.*)$");
    }

    private static List<String> readLines() {
        try {
            if (!Files.isRegularFile(file())) return new ArrayList<>();
            return new ArrayList<>(Files.readAllLines(file(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            return new ArrayList<>();
        }
    }

    /** {первая строка, последняя строка} определения переменной или null. */
    private static int[] find(List<String> lines, String name) {
        Pattern p = varPattern(name);
        for (int i = 0; i < lines.size(); i++) {
            String l = lines.get(i);
            if (l.stripLeading().startsWith("#") || !p.matcher(l).matches()) continue;
            int end = i;
            while (end + 1 < lines.size() && lines.get(end).stripTrailing().endsWith("\\")) end++;
            return new int[]{i, end};
        }
        return null;
    }

    /** Значение переменной без кавычек или null, если её нет. */
    static String get(String name) {
        List<String> lines = readLines();
        int[] r = find(lines, name);
        if (r == null) return null;
        Matcher m = varPattern(name).matcher(lines.get(r[0]));
        if (!m.matches()) return null;
        StringBuilder v = new StringBuilder(m.group(1).strip());
        for (int i = r[0] + 1; i <= r[1]; i++) {
            v.setLength(v.length() - 1);               // убрать завершающий "\"
            v.append(lines.get(i).strip());
        }
        String s = v.toString().strip();
        if (s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"")) s = s.substring(1, s.length() - 1);
        return s;
    }

    private static void backupOnce() throws IOException {
        Path f = file();
        Path bak = f.resolveSibling("config.txt.mse-backup");
        if (Files.isRegularFile(f) && !Files.exists(bak)) Files.copy(f, bak, StandardCopyOption.COPY_ATTRIBUTES);
    }

    private static void write(List<String> lines) throws IOException {
        Files.createDirectories(file().getParent());
        Files.write(file(), (String.join("\n", lines) + "\n").getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Записывает NAME="value". Остальные строки файла не меняются.
     *
     * @param marked true — добавить пометку мода (тогда removeMarked сможет убрать строку)
     * @return null при успехе или текст ошибки
     */
    static String set(String name, String value, boolean marked) {
        if (value.contains("\"") || value.contains("\n")) return Lang.t("cfgfile.bad_chars");
        try {
            backupOnce();
            List<String> lines = readLines();
            String line = name + "=\"" + value + "\"";
            int[] r = find(lines, name);
            if (r != null) {
                for (int i = r[1]; i >= r[0]; i--) lines.remove(i);
                lines.add(r[0], line);
                if (marked && !(r[0] > 0 && lines.get(r[0] - 1).strip().equals(MARK))) lines.add(r[0], MARK);
            } else {
                if (marked) lines.add(MARK);
                lines.add(line);
            }
            write(lines);
            return null;
        } catch (IOException e) {
            return Lang.t("cfgfile.write_failed", file(), e.getMessage());
        }
    }

    /** Удаляет переменную, только если её записал этот мод (есть пометка). */
    static String removeMarked(String name) {
        try {
            List<String> lines = readLines();
            int[] r = find(lines, name);
            if (r == null || r[0] == 0 || !lines.get(r[0] - 1).strip().equals(MARK)) return null;
            backupOnce();
            for (int i = r[1]; i >= r[0] - 1; i--) lines.remove(i);
            write(lines);
            return null;
        } catch (IOException e) {
            return Lang.t("cfgfile.write_failed", file(), e.getMessage());
        }
    }

    /** Значение command_path для папки скриптов: новая папка, затем стандартные. */
    static String commandPathFor(Path dir) {
        String sep = File.pathSeparator;
        return dir.toString() + sep + "." + sep + "system/exec";
    }
}
