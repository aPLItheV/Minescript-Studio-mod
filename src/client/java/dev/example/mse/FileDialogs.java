package dev.example.mse;

import net.minecraft.client.Minecraft;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Системные диалоги выбора файла или папки (проводник). Внешняя программа запускается
 * в отдельном потоке: на Linux zenity, kdialog или yad, на Windows PowerShell, на macOS osascript.
 */
final class FileDialogs {
    private FileDialogs() {}

    static void pickFolder(String start, Consumer<String> onPick, Consumer<String> onError) {
        run(true, start, onPick, onError);
    }

    static void pickFile(String start, Consumer<String> onPick, Consumer<String> onError) {
        run(false, start, onPick, onError);
    }

    private static String startDir(String start) {
        if (start != null && !start.isBlank()) {
            File f = new File(start.trim());
            if (f.isDirectory()) return f.getAbsolutePath();
            File parent = f.getParentFile();
            if (parent != null && parent.isDirectory()) return parent.getAbsolutePath();
        }
        return System.getProperty("user.home", ".");
    }

    private static List<List<String>> commands(boolean folder, String start) {
        String os = System.getProperty("os.name", "").toLowerCase();
        String dir = startDir(start);
        List<List<String>> out = new ArrayList<>();
        if (os.contains("win")) {
            String script = "[Console]::OutputEncoding = [System.Text.Encoding]::UTF8; Add-Type -AssemblyName System.Windows.Forms; "
                    + (folder
                    ? "$d = New-Object System.Windows.Forms.FolderBrowserDialog; if ($d.ShowDialog() -eq 'OK') { $d.SelectedPath }"
                    : "$d = New-Object System.Windows.Forms.OpenFileDialog; if ($d.ShowDialog() -eq 'OK') { $d.FileName }");
            out.add(List.of("powershell", "-NoProfile", "-Command", script));
        } else if (os.contains("mac")) {
            out.add(List.of("osascript", "-e", folder ? "POSIX path of (choose folder)" : "POSIX path of (choose file)"));
        } else {
            if (folder) {
                out.add(List.of("zenity", "--file-selection", "--directory", "--title=" + Lang.t("dialog.title.folder"), "--filename=" + dir + "/"));
                out.add(List.of("kdialog", "--getexistingdirectory", dir));
                out.add(List.of("yad", "--file", "--directory", "--title=" + Lang.t("dialog.title.folder"), "--filename=" + dir + "/"));
            } else {
                out.add(List.of("zenity", "--file-selection", "--title=" + Lang.t("dialog.title.file"), "--filename=" + dir + "/"));
                out.add(List.of("kdialog", "--getopenfilename", dir));
                out.add(List.of("yad", "--file", "--title=" + Lang.t("dialog.title.file"), "--filename=" + dir + "/"));
            }
        }
        return out;
    }

    private static void run(boolean folder, String start, Consumer<String> onPick, Consumer<String> onError) {
        Thread t = new Thread(() -> {
            for (List<String> cmd : commands(folder, start)) {
                try {
                    ProcessBuilder pb = new ProcessBuilder(cmd);
                    // библиотеки Minecraft не должны подмешиваться в системный диалог
                    pb.environment().remove("LD_LIBRARY_PATH");
                    pb.environment().remove("LD_PRELOAD");
                    Process p = pb.start();
                    String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
                    int code = p.waitFor();
                    if (code == 0 && !out.isEmpty()) {
                        String first = out.lines().findFirst().orElse("").trim();
                        post(() -> onPick.accept(first));
                    }
                    return;   // диалог открывался (даже если отменён)
                } catch (IOException e) {
                    // программа не найдена: пробуем следующую
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
            post(() -> onError.accept(Lang.t("dialog.no_program")));
        }, "mse-file-dialog");
        t.setDaemon(true);
        t.start();
    }

    /** Открывает папку в файловом менеджере системы. */
    static void openFolder(java.nio.file.Path dir) {
        Thread t = new Thread(() -> {
            String os = System.getProperty("os.name", "").toLowerCase();
            String opener = os.contains("win") ? "explorer" : (os.contains("mac") ? "open" : "xdg-open");
            try {
                ProcessBuilder pb = new ProcessBuilder(opener, dir.toString());
                pb.environment().remove("LD_LIBRARY_PATH");
                pb.environment().remove("LD_PRELOAD");
                pb.start();
            } catch (IOException e) {
                post(() -> Studio.log(Studio.Event.ERRORS, ConsoleLog.ERROR, Lang.t("log.open_folder_failed", dir)));
            }
        }, "mse-open-folder");
        t.setDaemon(true);
        t.start();
    }

    private static void post(Runnable r) {
        Minecraft.getInstance().execute(r);
    }
}
