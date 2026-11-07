package dev.example.mse;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Главное окно: список файлов слева, редактор справа, консоль внизу. */
public class EditorScreen extends Screen {
    private static final int MODE_NONE = 0, MODE_FIND = 1, MODE_GOTO = 2;

    private final String requestedFile;
    private String currentFile;                 // null: файл не выбран
    private List<String> allFiles = List.of();

    private FileListWidget list;
    private CodeEditor editor;
    private EditBox searchBox;
    private EditBox nameBox;
    private EditBox argsBox;
    private EditBox findBox;
    private LabelWidget header;
    private ContextMenuWidget menu;
    private int findMode = MODE_NONE;

    private String flashMsg = "";
    private long flashUntil;
    private boolean focusEditorNext;
    private boolean rebuildPending;
    private int checkToken;
    private String errorMsg = "";

    // состояние, которое переживает пересборку виджетов (смена размера окна, возврат из диалогов)
    private boolean haveCarry;
    private String carryText = "";
    private boolean carryDirty;
    private int carryLine, carryCol;
    private String carrySearch = "", carryName = "", carryArgs = "", carryFind = "";
    private int carryFindMode = MODE_NONE;

    public EditorScreen(String requestedFile) {
        super(Component.literal(Lang.t("app.title")));
        this.requestedFile = requestedFile;
    }

    // ===================== построение интерфейса =====================

    @Override
    protected void init() {
        if (editor != null) {
            haveCarry = true;
            carryText = editor.getText();
            carryDirty = editor.isDirty();
            carryLine = editor.cursorLine();
            carryCol = editor.cursorCol();
            carrySearch = searchBox.getValue();
            carryName = nameBox.getValue();
            carryArgs = argsBox.getValue();
            carryFind = findBox.getValue();
            carryFindMode = findMode;
        }
        allFiles = Scripts.list();

        final int pad = 8;
        int leftW = Math.max(120, Math.min(180, this.width / 4));
        int rightX = pad + leftW + 8;
        int rightW = this.width - rightX - pad;
        int bottomY = this.height - pad - 20;
        int statusY = bottomY - 14;
        int top = pad + 22;
        boolean consoleOpen = MseConfig.INSTANCE.consoleOpen;
        int consoleH = consoleOpen ? Math.max(56, Math.min(110, this.height / 5)) : ConsoleWidget.HEADER;
        int consoleY = statusY - 2 - consoleH;
        int editorBottom = consoleY - 4;
        int argsW = Math.min(170, rightW / 3);
        int headW = rightW - argsW - 6;

        searchBox = new EditBox(this.font, pad, pad, leftW, 18, Component.literal("search"));
        searchBox.setHint(Component.literal(Lang.t("hint.search")));
        searchBox.setMaxLength(64);
        searchBox.setValue(carrySearch);

        list = new FileListWidget(this.font, pad, top, leftW, bottomY - 4 - top, this::requestOpen, this::onFileContext);

        nameBox = new EditBox(this.font, pad, bottomY, leftW - 24, 20, Component.literal("name"));
        nameBox.setHint(Component.literal(Lang.t("hint.newfile")));
        nameBox.setMaxLength(64);
        nameBox.setValue(carryName);

        header = new LabelWidget(this.font, rightX, pad + 5, headW, 12,
                this::headerText, () -> editor != null && editor.isDirty() ? 0xFFE5C07B : 0xFFFFFFFF, false);
        argsBox = new EditBox(this.font, rightX + headW + 6, pad - 1, argsW, 18, Component.literal("args"));
        argsBox.setHint(Component.literal(Lang.t("hint.args")));
        argsBox.setMaxLength(256);
        argsBox.setValue(carryArgs);

        findBox = new EditBox(this.font, rightX, pad - 1, headW, 18, Component.literal("find"));
        findBox.setMaxLength(256);
        findBox.visible = false;

        editor = new CodeEditor(this.font, rightX, top, rightW, editorBottom - top);
        ConsoleWidget console = new ConsoleWidget(this.font, rightX, consoleY, rightW, consoleH, consoleOpen, this::toggleConsole);
        LabelWidget status = new LabelWidget(this.font, rightX, statusY + 2, rightW, 12,
                this::statusText, () -> flashActive() || !errorText().isEmpty() ? 0xFFE5C07B : 0xFF9DA5B4, false);
        menu = new ContextMenuWidget(this.font);

        addRenderableWidget(list);
        addRenderableWidget(searchBox);
        addRenderableWidget(nameBox);
        addRenderableWidget(btn("+", pad + leftW - 22, bottomY, 22, this::doNew));
        addRenderableWidget(header);
        addRenderableWidget(argsBox);
        addRenderableWidget(findBox);
        addRenderableWidget(editor);
        addRenderableWidget(console);
        addRenderableWidget(status);

        int bw = (rightW - 3 * 4) / 4;
        int bx = rightX;
        addRenderableWidget(btn(Lang.t("button.save"), bx, bottomY, bw, this::doSave));
        bx += bw + 4;
        addRenderableWidget(btn(Lang.t("button.run"), bx, bottomY, bw, this::doRun));
        bx += bw + 4;
        addRenderableWidget(btn(Lang.t("button.settings"), bx, bottomY, bw, this::openSettings));
        bx += bw + 4;
        addRenderableWidget(btn(Lang.t("button.close"), bx, bottomY, bw, this::requestClose));
        addRenderableWidget(menu);   // последним: рисуется поверх всего

        if (haveCarry) {
            editor.setText(carryText);
            editor.setDirty(carryDirty);
            editor.setCursor(carryLine, carryCol);
        } else {
            openInitialFile();
        }
        list.setSelected(currentFile);
        searchBox.setResponder(s -> refreshList());
        findBox.setResponder(this::onFindChanged);
        if (haveCarry && carryFindMode != MODE_NONE) openFindBox(carryFindMode, carryFind);
        refreshList();
        setInitialFocus(editor);
    }

    private Button btn(String label, int x, int y, int w, Runnable action) {
        return Button.builder(Component.literal(label), b -> {
            action.run();
            focusEditorNext = true;
        }).bounds(x, y, w, 20).build();
    }

    private void openInitialFile() {
        String target;
        if (requestedFile != null) {
            target = Scripts.normalize(requestedFile);
            if (target == null) {
                flash(Lang.t("flash.invalid_name"));
                return;
            }
        } else {
            target = Scripts.normalize(MseConfig.INSTANCE.lastFile);
        }
        if (target == null) return;
        if (Scripts.exists(target)) {
            editor.setText(Scripts.read(target));
            currentFile = target;
            restoreFileState(target);
        } else if (requestedFile != null) {
            editor.setText(Scripts.TEMPLATE);
            editor.setDirty(true);
            currentFile = target;
            argsBox.setValue(MseConfig.INSTANCE.launchArgs.getOrDefault(target, ""));
        }
    }

    private void restoreFileState(String file) {
        int[] p = MseConfig.INSTANCE.positions.get(file);
        if (p != null && p.length == 2) editor.setCursor(p[0], p[1]);
        argsBox.setValue(MseConfig.INSTANCE.launchArgs.getOrDefault(file, ""));
    }

    @Override
    public void tick() {
        super.tick();
        if (rebuildPending) {
            rebuildPending = false;
            rebuildWidgets();
        }
        if (focusEditorNext) {
            focusEditorNext = false;
            setFocused(editor);
        }
    }

    @Override
    public void removed() {
        super.removed();
        remember();
        if (editor != null) Minecraft.getInstance().onTextInputFocusChange(editor, false);
    }

    private void toggleConsole() {
        MseConfig.INSTANCE.consoleOpen = !MseConfig.INSTANCE.consoleOpen;
        MseConfig.save();
        rebuildPending = true;
    }

    // ===================== тексты =====================

    private static long now() {
        return System.currentTimeMillis();
    }

    private boolean flashActive() {
        return now() < flashUntil;
    }

    private void flash(String msg) {
        flashMsg = msg;
        flashUntil = now() + 3500;
    }

    private String errorText() {
        return editor != null && editor.errorLine() >= 0 ? errorMsg : "";
    }

    private String headerText() {
        String name = currentFile == null ? Lang.t("header.nofile") : currentFile;
        return name + (editor != null && editor.isDirty() ? "  *" : "");
    }

    private String statusText() {
        if (flashActive()) return flashMsg;
        if (editor == null) return "";
        if (findBox != null && findBox.visible) {
            if (findMode == MODE_GOTO) return Lang.t("status.goto", editor.lineCount());
            return Lang.t("status.find", editor.matchCount());
        }
        String err = errorText();
        if (!err.isEmpty()) return err;
        return Lang.t("status.position", editor.cursorLine() + 1, editor.cursorCol() + 1, editor.lineCount())
                + "    " + Lang.t("status.hints", Hotkeys.describe("save"), Hotkeys.describe("run"),
                Hotkeys.describe("find"), Hotkeys.describe("goto"), Hotkeys.describe("comment"));
    }

    // ===================== список файлов =====================

    private List<String> filtered() {
        String q = searchBox == null ? "" : searchBox.getValue().trim().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) return allFiles;
        return allFiles.stream().filter(n -> n.toLowerCase(Locale.ROOT).contains(q)).toList();
    }

    private void refreshList() {
        if (list == null) return;
        list.setItems(filtered());
    }

    private void reload() {
        allFiles = Scripts.list();
        refreshList();
    }

    private void storePosition() {
        if (currentFile != null && editor != null) {
            MseConfig.INSTANCE.positions.put(currentFile, new int[]{editor.cursorLine(), editor.cursorCol()});
        }
    }

    private void remember() {
        storePosition();
        MseConfig.INSTANCE.lastFile = currentFile == null ? "" : currentFile;
        MseConfig.save();
    }

    // ===================== контекстное меню файлов =====================

    private void onFileContext(String name, int x, int y) {
        List<ContextMenuWidget.Item> items = new ArrayList<>();
        if (name != null) {
            items.add(new ContextMenuWidget.Item(Lang.t("ctx.open"), () -> requestOpen(name)));
            items.add(new ContextMenuWidget.Item(Lang.t("ctx.run"), () -> runFile(name)));
            items.add(new ContextMenuWidget.Item(Lang.t("ctx.rename"), () -> promptRename(name)));
            items.add(new ContextMenuWidget.Item(Lang.t("ctx.duplicate"), () -> promptDuplicate(name)));
            items.add(new ContextMenuWidget.Item(Lang.t("ctx.delete"), () -> promptDelete(name)));
        } else {
            items.add(new ContextMenuWidget.Item(Lang.t("ctx.new"), this::promptNew));
        }
        items.add(new ContextMenuWidget.Item(Lang.t("ctx.show_folder"), () -> FileDialogs.openFolder(Scripts.dir())));
        menu.show(x, y, items, this.width, this.height);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (menu != null && menu.isOpen()) {
            Runnable action = menu.actionAt(menu.itemAt(event.x(), event.y()));
            menu.hide();
            if (action != null) action.run();
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    private void promptNew() {
        Minecraft.getInstance().gui.setScreen(PromptScreen.input(this, Lang.t("prompt.new.title"), "", this::createFile));
    }

    private void promptRename(String name) {
        Minecraft.getInstance().gui.setScreen(
                PromptScreen.input(this, Lang.t("prompt.rename.title", name), name, v -> renameFile(name, v)));
    }

    private void promptDuplicate(String name) {
        String base = Scripts.baseName(name);
        String ext = name.endsWith(".pyj") ? ".pyj" : ".py";
        Minecraft.getInstance().gui.setScreen(
                PromptScreen.input(this, Lang.t("prompt.duplicate.title", name), base + "_copy" + ext, v -> duplicateFile(name, v)));
    }

    private void promptDelete(String name) {
        Minecraft.getInstance().gui.setScreen(PromptScreen.confirm(this, Lang.t("prompt.delete.title", name),
                Lang.t("prompt.delete.detail"), Lang.t("button.delete"), () -> deleteFile(name)));
    }

    private static String withExtension(String raw, String like) {
        String t = raw.trim();
        if (t.endsWith(".py") || t.endsWith(".pyj")) return t;
        return t + (like.endsWith(".pyj") ? ".pyj" : ".py");
    }

    private void renameFile(String old, String raw) {
        String name = Scripts.normalize(withExtension(raw, old));
        if (name == null) {
            flash(Lang.t("flash.invalid_name"));
            return;
        }
        if (name.equals(old)) return;
        if (Scripts.exists(name)) {
            flash(Lang.t("flash.exists", name));
            return;
        }
        if (old.equals(currentFile) && editor.isDirty() && doSave() == null) return;
        if (!Scripts.rename(old, name)) {
            Studio.log(Studio.Event.ERRORS, ConsoleLog.ERROR, Lang.t("flash.rename_failed", old));
            flash(Lang.t("flash.rename_failed", old));
            return;
        }
        MseConfig cfg = MseConfig.INSTANCE;
        int[] pos = cfg.positions.remove(old);
        if (pos != null) cfg.positions.put(name, pos);
        String a = cfg.launchArgs.remove(old);
        if (a != null) cfg.launchArgs.put(name, a);
        if (old.equals(currentFile)) currentFile = name;
        reload();
        list.setSelected(currentFile);
        remember();
        flash(Lang.t("flash.renamed", old, name));
        Studio.log(Studio.Event.FILES, ConsoleLog.INFO, Lang.t("log.renamed", old, name));
    }

    private void duplicateFile(String from, String raw) {
        String name = Scripts.normalize(withExtension(raw, from));
        if (name == null) {
            flash(Lang.t("flash.invalid_name"));
            return;
        }
        if (Scripts.exists(name)) {
            flash(Lang.t("flash.exists", name));
            return;
        }
        if (!Scripts.copy(from, name)) {
            Studio.log(Studio.Event.ERRORS, ConsoleLog.ERROR, Lang.t("flash.duplicate_failed", from));
            flash(Lang.t("flash.duplicate_failed", from));
            return;
        }
        reload();
        flash(Lang.t("flash.duplicated", name));
        Studio.log(Studio.Event.FILES, ConsoleLog.INFO, Lang.t("log.duplicated", from, name));
    }

    private void deleteFile(String name) {
        if (!Scripts.trash(name)) {
            Studio.log(Studio.Event.ERRORS, ConsoleLog.ERROR, Lang.t("flash.delete_failed", name));
            flash(Lang.t("flash.delete_failed", name));
            return;
        }
        MseConfig.INSTANCE.positions.remove(name);
        MseConfig.INSTANCE.launchArgs.remove(name);
        if (name.equals(currentFile)) {
            currentFile = null;
            editor.setText("");
            argsBox.setValue("");
            list.setSelected(null);
        }
        remember();
        reload();
        flash(Lang.t("flash.deleted", name));
        Studio.log(Studio.Event.FILES, ConsoleLog.WARN, Lang.t("log.deleted", name));
    }

    private void runFile(String name) {
        if (name.equals(currentFile)) {
            doRun();
            return;
        }
        confirmThen(() -> {
            closeNow();
            launch(name, MseConfig.INSTANCE.launchArgs.getOrDefault(name, "").trim());
        });
    }

    // ===================== поиск по файлу и переход к строке =====================

    private void openFindBox(int mode, String text) {
        findMode = mode;
        findBox.visible = true;
        header.visible = false;
        findBox.setHint(Component.literal(Lang.t(mode == MODE_GOTO ? "hint.goto" : "hint.find")));
        findBox.setValue(text);
        if (mode == MODE_FIND) editor.setFindTerm(text);
        setFocused(findBox);
    }

    private void closeFindBox() {
        findMode = MODE_NONE;
        findBox.visible = false;
        header.visible = true;
        editor.setFindTerm("");
        focusEditorNext = true;
    }

    private void onFindChanged(String text) {
        if (findMode == MODE_FIND) {
            editor.setFindTerm(text);
            if (!text.isEmpty()) editor.findIncremental();
        }
    }

    private void submitFind(boolean backwards) {
        if (findMode == MODE_FIND) {
            if (!editor.findNext(!backwards)) flash(Lang.t("flash.not_found", findBox.getValue()));
        } else if (findMode == MODE_GOTO) {
            try {
                editor.goToLine(Integer.parseInt(findBox.getValue().trim()));
            } catch (NumberFormatException e) {
                flash(Lang.t("flash.enter_line"));
                return;
            }
            closeFindBox();
        }
    }

    // ===================== действия =====================

    /** Выполняет action сразу или после вопроса "сохранить / не сохранять / отмена". */
    void confirmThen(Runnable action) {
        if (editor.isDirty()) {
            Minecraft.getInstance().gui.setScreen(new UnsavedScreen(this,
                    currentFile == null ? Lang.t("unsaved.newfile") : currentFile, action));
        } else {
            action.run();
        }
    }

    private void requestOpen(String file) {
        if (file.equals(currentFile)) return;
        confirmThen(() -> loadFile(file));
        focusEditorNext = true;
    }

    private void loadFile(String file) {
        storePosition();
        editor.setText(Scripts.read(file));
        currentFile = file;
        checkToken++;
        errorMsg = "";
        restoreFileState(file);
        list.setSelected(file);
        remember();
        focusEditorNext = true;
    }

    private void doNew() {
        if (createFile(nameBox.getValue())) nameBox.setValue("");
    }

    /** Создаёт файл по имени и открывает его (после вопроса о несохранённых изменениях). */
    private boolean createFile(String raw) {
        String name = Scripts.normalize(raw);
        if (name == null) {
            flash(Lang.t("flash.enter_new_name"));
            return false;
        }
        if (Scripts.exists(name)) {
            flash(Lang.t("flash.exists", name));
            return false;
        }
        confirmThen(() -> {
            if (!Scripts.write(name, Scripts.TEMPLATE)) {
                Studio.log(Studio.Event.ERRORS, ConsoleLog.ERROR, Lang.t("flash.create_failed", name));
                flash(Lang.t("flash.create_failed", name));
                return;
            }
            reload();
            loadFile(name);
            flash(Lang.t("flash.created", name));
            Studio.log(Studio.Event.FILES, ConsoleLog.INFO, Lang.t("log.created", name));
        });
        return true;
    }

    /** @return имя сохранённого файла или null */
    String doSave() {
        String name = currentFile;
        if (name == null) {
            name = Scripts.normalize(nameBox.getValue());
            if (name == null) {
                flash(Lang.t("flash.save_name_needed"));
                return null;
            }
        }
        MseConfig cfg = MseConfig.INSTANCE;
        if (cfg.trimWhitespace) editor.trimTrailingWhitespace();
        String text = editor.getText();
        if (!text.endsWith("\n")) text += "\n";
        if (!Scripts.write(name, text)) {
            Studio.log(Studio.Event.ERRORS, ConsoleLog.ERROR, Lang.t("flash.write_failed", name));
            flash(Lang.t("flash.write_failed", name));
            return null;
        }
        currentFile = name;
        editor.setDirty(false);
        nameBox.setValue("");
        reload();
        list.setSelected(name);
        remember();
        flash(Lang.t("flash.saved", name));
        Studio.log(Studio.Event.SAVE, ConsoleLog.OK, Lang.t("log.saved", name));
        if (cfg.syntaxCheck && name.endsWith(".py")) startSyntaxCheck(name);
        return name;
    }

    private void startSyntaxCheck(String name) {
        final int token = ++checkToken;
        Studio.log(Studio.Event.BUILD_START, ConsoleLog.INFO, Lang.t("log.build_start", name));
        SyntaxCheck.check(Scripts.dir().resolve(name), (line, msg) -> {
            if (line < 0) {
                Studio.log(Studio.Event.ERRORS, ConsoleLog.WARN, Lang.t("log.python_missing"));
                return;
            }
            boolean current = token == checkToken && name.equals(currentFile);
            if (line == 0) {
                Studio.log(Studio.Event.BUILD_END, ConsoleLog.OK, Lang.t("log.build_ok", name));
                if (current) {
                    editor.setErrorLine(-1);
                    errorMsg = "";
                    flash(Lang.t("flash.saved_ok", name));
                }
            } else {
                String text = Lang.t("flash.syntax_error", line, msg);
                Studio.log(Studio.Event.BUILD_END, ConsoleLog.ERROR, Lang.t("log.build_error", name, line, msg));
                if (current) {
                    editor.setErrorLine(line - 1);
                    errorMsg = text;
                    flash(text);
                }
            }
        });
    }

    void discardChanges() {
        editor.setDirty(false);
    }

    private void doRun() {
        String name = doSave();
        if (name == null) return;
        String args = argsBox.getValue().trim();
        if (args.isEmpty()) MseConfig.INSTANCE.launchArgs.remove(name);
        else MseConfig.INSTANCE.launchArgs.put(name, args);
        closeNow();
        launch(name, args);
    }

    private void launch(String name, String args) {
        String command = Scripts.baseName(name) + (args.isEmpty() ? "" : " " + args);
        Studio.log(Studio.Event.RUN, ConsoleLog.INFO, Lang.t("log.run", "\\" + command));
        if (!MinescriptBridge.run(command)) {
            // Minescript не принял команду напрямую: открываем чат с готовой командой, остаётся нажать Enter
            Studio.log(Studio.Event.RUN, ConsoleLog.WARN, Lang.t("log.run_fallback"));
            Minecraft.getInstance().gui.setScreen(new ChatScreen("\\" + command, false));
        }
    }

    private void openSettings() {
        Minecraft.getInstance().gui.setScreen(new ConfigScreen(this));
    }

    private void requestClose() {
        confirmThen(this::closeNow);
    }

    void closeNow() {
        remember();
        super.onClose();
    }

    @Override
    public void onClose() {
        if (menu != null && menu.isOpen()) {
            menu.hide();
            return;
        }
        if (findBox != null && findBox.visible) {
            closeFindBox();
            return;
        }
        requestClose();
    }

    // ===================== клавиши =====================

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (Hotkeys.matches("save", event)) {
            doSave();
            return true;
        }
        if (Hotkeys.matches("run", event)) {
            doRun();
            return true;
        }
        if (Hotkeys.matches("find", event)) {
            openFindBox(MODE_FIND, editor.selectedOrWord());
            return true;
        }
        if (Hotkeys.matches("goto", event)) {
            openFindBox(MODE_GOTO, "");
            return true;
        }
        if (Hotkeys.matches("console", event)) {
            toggleConsole();
            return true;
        }
        int k = event.key();
        boolean enter = k == InputConstants.KEY_RETURN || k == InputConstants.KEY_NUMPADENTER;
        if (enter && getFocused() == findBox && findBox.visible) {
            submitFind(event.hasShiftDown());
            return true;
        }
        if (enter && getFocused() == argsBox) {
            doRun();
            return true;
        }
        if (enter && getFocused() == nameBox) {
            doNew();
            return true;
        }
        if (enter && getFocused() == searchBox) {
            List<String> f = filtered();
            if (!f.isEmpty()) requestOpen(f.get(0));
            return true;
        }
        return super.keyPressed(event);
    }
}
