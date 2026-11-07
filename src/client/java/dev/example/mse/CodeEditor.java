package dev.example.mse;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * Многострочный редактор кода: подсветка Python, номера строк, автоотступ, автозакрытие скобок,
 * подсказки (автодополнение), undo/redo, выделение мышью и клавиатурой.
 */
public class CodeEditor extends AbstractWidget {

    private static final int BG = 0xFF1E1E1E;
    private static final int GUTTER_FG = 0xFF858585;
    private static final int GUTTER_FG_ACTIVE = 0xFFC6C6C6;
    private static final int CURRENT_LINE = 0xFF282828;
    private static final int SELECTION = 0xFF264F78;
    private static final int BORDER = 0xFF3C3C3C;
    private static final int SCROLL = 0xFF5A5A5A;
    private static final int POPUP_BG = 0xFF252526;
    private static final int POPUP_BORDER = 0xFF454545;
    private static final int POPUP_SEL = 0xFF04395E;
    private static final int POPUP_ROWS = 8;

    private record Snapshot(String text, int line, int col) {}

    private final Font font;
    private final int lineH;
    private final List<String> lines = new ArrayList<>();
    private List<int[]> colors = new ArrayList<>();
    private boolean colorsStale = true;

    private int curLine, curCol, goalCol;
    private int anchorLine = -1, anchorCol = -1;
    private int topLine, scrollX;
    private boolean dirty;

    private final ArrayDeque<Snapshot> undoStack = new ArrayDeque<>();
    private final ArrayDeque<Snapshot> redoStack = new ArrayDeque<>();
    private final ArrayDeque<int[]> cursorHistory = new ArrayDeque<>();
    private String lastKind = "";

    private boolean dragText, dragBar;

    private int version;                       // растёт при любом изменении текста
    private String findTerm = "";
    private int matchCountVersion = -1;
    private String matchCountTerm = "";
    private int matchCountCached;
    private int errorLine = -1;                // строка с синтаксической ошибкой (с нуля)

    private static final int FIND_BG = 0xFF5A4A12;
    private static final int OCC_BG = 0xFF30363D;
    private static final int BRACKET_BG = 0xFF4D5D6E;
    private static final int GUIDE = 0xFF3A3A3A;

    // подсказки
    private List<Completions.Item> popup = List.of();
    private int popupSel, popupScroll, popupStartCol;
    private int popX, popY, popW, popH;

    public CodeEditor(Font font, int x, int y, int w, int h) {
        super(x, y, w, h, Component.literal("editor"));
        this.font = font;
        this.lineH = font.lineHeight + 3;
        lines.add("");
    }

    // ===================== публичный API =====================

    public String getText() {
        return String.join("\n", lines);
    }

    public void setText(String text) {
        loadLines(text);
        curLine = 0;
        curCol = 0;
        goalCol = 0;
        clearSel();
        topLine = 0;
        scrollX = 0;
        undoStack.clear();
        redoStack.clear();
        cursorHistory.clear();
        dirty = false;
        lastKind = "";
        hidePopup();
    }

    public boolean isDirty() { return dirty; }
    public void setDirty(boolean d) { dirty = d; }
    public int cursorLine() { return curLine; }
    public int cursorCol() { return curCol; }
    public int lineCount() { return lines.size(); }

    public void setCursor(int line, int col) {
        curLine = clamp(line, 0, lines.size() - 1);
        curCol = clamp(col, 0, lines.get(curLine).length());
        goalCol = curCol;
        clearSel();
        ensureCursorVisible();
    }

    // ===================== вспомогательное =====================

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static boolean isWordChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }

    private int unit() { return MseConfig.INSTANCE.tabSize; }

    private void loadLines(String text) {
        lines.clear();
        String t = text.replace("\r\n", "\n").replace('\r', '\n').replace("\t", "    ");
        for (String l : t.split("\n", -1)) lines.add(l);
        if (lines.isEmpty()) lines.add("");
        colorsStale = true;
        version++;
        errorLine = -1;
    }

    private void markEdited() {
        dirty = true;
        colorsStale = true;
        version++;
        errorLine = -1;
    }

    private void afterEdit() {
        markEdited();
        goalCol = curCol;
        ensureCursorVisible();
    }

    private void ensureColors() {
        if (colorsStale || colors.size() != lines.size()) {
            colors = PyHighlighter.highlight(lines, MseConfig.INSTANCE.syntaxHighlight);
            colorsStale = false;
        }
    }

    // ---------- выделение ----------

    private boolean hasSel() {
        return anchorLine >= 0 && (anchorLine != curLine || anchorCol != curCol);
    }

    private void clearSel() {
        anchorLine = -1;
        anchorCol = -1;
    }

    /** {строка1, столбец1, строка2, столбец2}, начало <= конец. */
    private int[] ordered() {
        if (anchorLine < curLine || (anchorLine == curLine && anchorCol <= curCol)) {
            return new int[]{anchorLine, anchorCol, curLine, curCol};
        }
        return new int[]{curLine, curCol, anchorLine, anchorCol};
    }

    private String selectedText() {
        if (!hasSel()) return "";
        int[] o = ordered();
        if (o[0] == o[2]) return lines.get(o[0]).substring(o[1], o[3]);
        StringBuilder sb = new StringBuilder(lines.get(o[0]).substring(o[1]));
        for (int l = o[0] + 1; l < o[2]; l++) sb.append('\n').append(lines.get(l));
        sb.append('\n').append(lines.get(o[2]), 0, o[3]);
        return sb.toString();
    }

    private boolean deleteSelection() {
        if (!hasSel()) {
            clearSel();
            return false;
        }
        int[] o = ordered();
        String first = lines.get(o[0]).substring(0, o[1]);
        String last = lines.get(o[2]).substring(o[3]);
        for (int i = o[2]; i > o[0]; i--) lines.remove(i);
        lines.set(o[0], first + last);
        curLine = o[0];
        curCol = o[1];
        clearSel();
        return true;
    }

    private void selectAll() {
        anchorLine = 0;
        anchorCol = 0;
        curLine = lines.size() - 1;
        curCol = lines.get(curLine).length();
        hidePopup();
    }

    // ---------- перемещение ----------

    private void moveTo(int line, int col, boolean extend) {
        if (extend) {
            if (anchorLine < 0) {
                anchorLine = curLine;
                anchorCol = curCol;
            }
        } else {
            clearSel();
        }
        curLine = clamp(line, 0, lines.size() - 1);
        curCol = clamp(col, 0, lines.get(curLine).length());
        goalCol = curCol;
        lastKind = "";
        hidePopup();
        ensureCursorVisible();
    }

    private void moveVertical(int delta, boolean extend) {
        int nl = clamp(curLine + delta, 0, lines.size() - 1);
        int nc = Math.min(goalCol, lines.get(nl).length());
        int keep = goalCol;
        moveTo(nl, nc, extend);
        goalCol = keep;
    }

    private static int firstNonSpace(String s) {
        int i = 0;
        while (i < s.length() && s.charAt(i) == ' ') i++;
        return i;
    }

    private static int wordLeft(String s, int col) {
        int i = col;
        while (i > 0 && !isWordChar(s.charAt(i - 1))) i--;
        while (i > 0 && isWordChar(s.charAt(i - 1))) i--;
        return i;
    }

    private static int wordRight(String s, int col) {
        int i = col, n = s.length();
        while (i < n && !isWordChar(s.charAt(i))) i++;
        while (i < n && isWordChar(s.charAt(i))) i++;
        return i;
    }

    private void rememberCursor() {
        int[] top = cursorHistory.peek();
        if (top == null || top[0] != curLine || top[1] != curCol) {
            cursorHistory.push(new int[]{curLine, curCol});
            if (cursorHistory.size() > 100) cursorHistory.removeLast();
        }
    }

    /** Ctrl+U: вернуть курсор на предыдущее "заметное" место (как Cursor Undo в VS Code). */
    private void cursorUndo() {
        while (!cursorHistory.isEmpty()) {
            int[] p = cursorHistory.pop();
            if (p[0] >= lines.size()) continue;
            if (p[0] == curLine && p[1] == curCol) continue;
            moveTo(p[0], p[1], false);
            return;
        }
    }

    // ---------- undo / redo ----------

    private void snapshot(String kind) {
        redoStack.clear();
        boolean coalesce = kind.equals(lastKind) && (kind.equals("type") || kind.equals("back") || kind.equals("del"));
        if (coalesce) return;
        undoStack.push(new Snapshot(getText(), curLine, curCol));
        if (undoStack.size() > 300) undoStack.removeLast();
        lastKind = kind;
    }

    private void restore(Snapshot s) {
        loadLines(s.text());
        curLine = clamp(s.line(), 0, lines.size() - 1);
        curCol = clamp(s.col(), 0, lines.get(curLine).length());
        clearSel();
        hidePopup();
        lastKind = "";
        afterEdit();
    }

    private void undo() {
        if (undoStack.isEmpty()) return;
        redoStack.push(new Snapshot(getText(), curLine, curCol));
        restore(undoStack.pop());
    }

    private void redo() {
        if (redoStack.isEmpty()) return;
        undoStack.push(new Snapshot(getText(), curLine, curCol));
        restore(redoStack.pop());
    }

    // ---------- редактирование ----------

    private void insertRaw(String s) {
        s = s.replace("\r\n", "\n").replace('\r', '\n').replace("\t", "    ");
        String[] parts = s.split("\n", -1);
        String line = lines.get(curLine);
        String before = line.substring(0, curCol);
        String after = line.substring(curCol);
        if (parts.length == 1) {
            lines.set(curLine, before + parts[0] + after);
            curCol += parts[0].length();
        } else {
            lines.set(curLine, before + parts[0]);
            int base = curLine;
            for (int i = 1; i < parts.length; i++) {
                if (i == parts.length - 1) {
                    lines.add(base + i, parts[i] + after);
                    curLine = base + i;
                    curCol = parts[i].length();
                } else {
                    lines.add(base + i, parts[i]);
                }
            }
        }
    }

    private void typeText(String s) {
        snapshot("type");
        deleteSelection();
        insertRaw(s);
        afterEdit();
    }

    private static String pairFor(char c) {
        return switch (c) {
            case '(' -> ")";
            case '[' -> "]";
            case '{' -> "}";
            case '"' -> "\"";
            case '\'' -> "'";
            default -> null;
        };
    }

    private void handleTyped(String s) {
        MseConfig cfg = MseConfig.INSTANCE;
        if (cfg.autoPairs && s.length() == 1 && !hasSel()) {
            char ch = s.charAt(0);
            String line = lines.get(curLine);
            char next = curCol < line.length() ? line.charAt(curCol) : '\0';
            char prev = curCol > 0 ? line.charAt(curCol - 1) : '\0';
            boolean closer = ch == ')' || ch == ']' || ch == '}' || ch == '"' || ch == '\'';
            if (closer && next == ch) {
                moveTo(curLine, curCol + 1, false);
                return;
            }
            String close = pairFor(ch);
            if (close != null) {
                boolean quote = ch == '"' || ch == '\'';
                boolean ok = quote
                        ? (!isWordChar(prev) && !isWordChar(next) && prev != ch)
                        : !isWordChar(next);
                if (ok) {
                    snapshot("type");
                    insertRaw(s + close);
                    curCol--;
                    afterEdit();
                    updatePopup(false);
                    return;
                }
            }
        }
        typeText(s);
        if (s.equals(":")) autoDedentKeyword();
        updatePopup(false);
    }

    /** После ввода ':' в строке else/elif/except/finally сдвигает её на один уровень влево. */
    private void autoDedentKeyword() {
        if (!MseConfig.INSTANCE.autoIndent) return;
        String line = lines.get(curLine);
        String t = line.trim();
        boolean kw = t.equals("else:") || t.startsWith("elif ") || t.startsWith("except") || t.equals("finally:");
        if (!kw) return;
        int ci = firstNonSpace(line);
        int u = unit();
        if (ci < u) return;
        int pl = curLine - 1;
        while (pl >= 0 && lines.get(pl).isBlank()) pl--;
        if (pl < 0) return;
        String prev = lines.get(pl);
        int pi = firstNonSpace(prev);
        if (ci == pi && !prev.stripTrailing().endsWith(":")) {
            lines.set(curLine, line.substring(u));
            curCol = Math.max(0, curCol - u);
            afterEdit();
        }
    }

    private void newline() {
        snapshot("enter");
        deleteSelection();
        String line = lines.get(curLine);
        String before = line.substring(0, curCol);
        String after = line.substring(curCol);
        if (!MseConfig.INSTANCE.autoIndent) {
            insertRaw("\n");
            afterEdit();
            return;
        }
        String indent = " ".repeat(firstNonSpace(before));
        String trimmed = before.stripTrailing();
        char last = trimmed.isEmpty() ? '\0' : trimmed.charAt(trimmed.length() - 1);
        boolean opener = last == ':' || last == '(' || last == '[' || last == '{';
        String unit = " ".repeat(unit());
        char nextCh = after.isEmpty() ? '\0' : after.charAt(0);
        boolean between = (last == '(' && nextCh == ')') || (last == '[' && nextCh == ']') || (last == '{' && nextCh == '}');
        if (between) {
            insertRaw("\n" + indent + unit + "\n" + indent);
            curLine -= 1;
            curCol = indent.length() + unit.length();
        } else {
            insertRaw("\n" + indent + (opener ? unit : ""));
        }
        afterEdit();
    }

    private void backspace(boolean ctrl) {
        hidePopupIfNeededAfterDelete();
        if (hasSel()) {
            snapshot("back");
            deleteSelection();
            afterEdit();
            return;
        }
        if (curCol > 0) {
            String line = lines.get(curLine);
            snapshot("back");
            if (MseConfig.INSTANCE.autoPairs && curCol < line.length()) {
                String c = pairFor(line.charAt(curCol - 1));
                if (c != null && c.charAt(0) == line.charAt(curCol)) {
                    lines.set(curLine, line.substring(0, curCol - 1) + line.substring(curCol + 1));
                    curCol--;
                    afterEdit();
                    return;
                }
            }
            int n;
            if (ctrl) {
                n = curCol - wordLeft(line, curCol);
            } else if (line.substring(0, curCol).isBlank()) {
                int u = unit();
                n = (curCol - 1) % u + 1;
            } else {
                n = 1;
            }
            lines.set(curLine, line.substring(0, curCol - n) + line.substring(curCol));
            curCol -= n;
            afterEdit();
        } else if (curLine > 0) {
            snapshot("back");
            String prev = lines.get(curLine - 1);
            lines.set(curLine - 1, prev + lines.get(curLine));
            lines.remove(curLine);
            curLine--;
            curCol = prev.length();
            afterEdit();
        }
    }

    private void hidePopupIfNeededAfterDelete() {
        // подсказки после удаления обновляются в keyPressed
    }

    private void deleteForward() {
        if (hasSel()) {
            snapshot("del");
            deleteSelection();
            afterEdit();
            return;
        }
        String line = lines.get(curLine);
        if (curCol < line.length()) {
            snapshot("del");
            lines.set(curLine, line.substring(0, curCol) + line.substring(curCol + 1));
            afterEdit();
        } else if (curLine < lines.size() - 1) {
            snapshot("del");
            lines.set(curLine, line + lines.get(curLine + 1));
            lines.remove(curLine + 1);
            afterEdit();
        }
    }

    private int[] rangeLines() {
        if (hasSel()) {
            int[] o = ordered();
            int b = (o[3] == 0 && o[2] > o[0]) ? o[2] - 1 : o[2];
            return new int[]{o[0], b};
        }
        return new int[]{curLine, curLine};
    }

    private void shiftCols(int a, int b, int[] delta) {
        if (curLine >= a && curLine <= b) {
            curCol = clamp(curCol + delta[curLine - a], 0, lines.get(curLine).length());
        }
        if (anchorLine >= a && anchorLine <= b) {
            anchorCol = clamp(anchorCol + delta[anchorLine - a], 0, lines.get(anchorLine).length());
        }
    }

    private void indentLines(boolean out) {
        int[] r = rangeLines();
        int a = r[0], b = r[1];
        snapshot("indent");
        int u = unit();
        int[] delta = new int[b - a + 1];
        for (int l = a; l <= b; l++) {
            String s = lines.get(l);
            if (!out) {
                if (s.isEmpty()) continue;
                lines.set(l, " ".repeat(u) + s);
                delta[l - a] = u;
            } else {
                int k = 0;
                while (k < u && k < s.length() && s.charAt(k) == ' ') k++;
                lines.set(l, s.substring(k));
                delta[l - a] = -k;
            }
        }
        shiftCols(a, b, delta);
        afterEdit();
    }

    private void toggleComment() {
        int[] r = rangeLines();
        int a = r[0], b = r[1];
        boolean any = false, allCommented = true;
        int minIndent = Integer.MAX_VALUE;
        for (int l = a; l <= b; l++) {
            String s = lines.get(l);
            if (s.isBlank()) continue;
            any = true;
            int in = firstNonSpace(s);
            minIndent = Math.min(minIndent, in);
            if (!s.startsWith("#", in)) allCommented = false;
        }
        if (!any) return;
        snapshot("comment");
        int[] delta = new int[b - a + 1];
        for (int l = a; l <= b; l++) {
            String s = lines.get(l);
            if (s.isBlank()) continue;
            if (allCommented) {
                int in = firstNonSpace(s);
                int rm = s.startsWith("# ", in) ? 2 : 1;
                lines.set(l, s.substring(0, in) + s.substring(in + rm));
                delta[l - a] = -rm;
            } else {
                lines.set(l, s.substring(0, minIndent) + "# " + s.substring(minIndent));
                delta[l - a] = 2;
            }
        }
        shiftCols(a, b, delta);
        afterEdit();
    }

    private void duplicateLine() {
        snapshot("dup");
        clearSel();
        lines.add(curLine + 1, lines.get(curLine));
        curLine++;
        afterEdit();
    }

    private void moveLine(int dir) {
        int to = curLine + dir;
        if (to < 0 || to >= lines.size()) return;
        snapshot("move");
        clearSel();
        String tmp = lines.get(curLine);
        lines.set(curLine, lines.get(to));
        lines.set(to, tmp);
        curLine = to;
        afterEdit();
    }

    private void copy(boolean cut) {
        String s = hasSel() ? selectedText() : lines.get(curLine) + "\n";
        Minecraft.getInstance().keyboardHandler.setClipboard(s);
        if (cut) {
            snapshot("cut");
            if (hasSel()) {
                deleteSelection();
            } else if (lines.size() == 1) {
                lines.set(0, "");
                curCol = 0;
            } else {
                lines.remove(curLine);
                curLine = Math.min(curLine, lines.size() - 1);
                curCol = Math.min(curCol, lines.get(curLine).length());
            }
            afterEdit();
        }
    }

    private void paste() {
        String clip = Minecraft.getInstance().keyboardHandler.getClipboard();
        if (clip == null || clip.isEmpty()) return;
        snapshot("paste");
        deleteSelection();
        insertRaw(clip);
        afterEdit();
    }

    // ===================== поиск, переход, служебное =====================

    public void setFindTerm(String t) {
        findTerm = t == null ? "" : t;
    }

    public int matchCount() {
        if (findTerm.isEmpty()) return 0;
        if (matchCountVersion != version || !matchCountTerm.equals(findTerm)) {
            int n = 0;
            String lt = findTerm.toLowerCase();
            for (String l : lines) {
                String ll = l.toLowerCase();
                int i = 0;
                while ((i = ll.indexOf(lt, i)) >= 0) {
                    n++;
                    i += lt.length();
                }
            }
            matchCountCached = n;
            matchCountVersion = version;
            matchCountTerm = findTerm;
        }
        return matchCountCached;
    }

    /** Выделяет следующее (или предыдущее) вхождение искомого текста с переходом по кругу. */
    public boolean findNext(boolean forward) {
        if (findTerm.isEmpty()) return false;
        String lt = findTerm.toLowerCase();
        int n = lines.size();
        int startLine = curLine, startCol = curCol;
        if (hasSel()) {
            int[] o = ordered();
            startLine = forward ? o[2] : o[0];
            startCol = forward ? o[3] : o[1];
        }
        for (int step = 0; step <= n; step++) {
            int li = forward ? (startLine + step) % n : ((startLine - step) % n + n) % n;
            String ll = lines.get(li).toLowerCase();
            int idx;
            if (forward) {
                idx = ll.indexOf(lt, step == 0 ? startCol : 0);
            } else {
                int from = step == 0 ? startCol - lt.length() : ll.length();
                idx = from < 0 ? -1 : ll.lastIndexOf(lt, from);
            }
            if (idx >= 0) {
                anchorLine = li;
                anchorCol = idx;
                curLine = li;
                curCol = idx + lt.length();
                goalCol = curCol;
                hidePopup();
                lastKind = "";
                ensureCursorVisible();
                return true;
            }
        }
        return false;
    }

    /** Поиск при вводе: ищет от начала текущего выделения, чтобы не "убегать" с найденного. */
    public boolean findIncremental() {
        if (hasSel()) {
            int[] o = ordered();
            curLine = o[0];
            curCol = o[1];
            clearSel();
        }
        return findNext(true);
    }

    public int errorLine() {
        return errorLine;
    }

    public void goToLine(int oneBased) {
        rememberCursor();
        moveTo(clamp(oneBased - 1, 0, lines.size() - 1), 0, false);
    }

    public void setErrorLine(int zeroBased) {
        errorLine = zeroBased;
    }

    public String selectedOrWord() {
        if (hasSel()) {
            String t = selectedText();
            return t.contains("\n") ? "" : t;
        }
        return "";
    }

    /** Убирает пробелы в конце строк; возвращает true, если что-то изменилось. */
    public boolean trimTrailingWhitespace() {
        boolean any = false;
        for (String l : lines) {
            if (!l.equals(l.stripTrailing())) {
                any = true;
                break;
            }
        }
        if (!any) return false;
        snapshot("trim");
        for (int i = 0; i < lines.size(); i++) lines.set(i, lines.get(i).stripTrailing());
        curCol = Math.min(curCol, lines.get(curLine).length());
        if (anchorLine >= 0) anchorCol = Math.min(anchorCol, lines.get(anchorLine).length());
        markEdited();
        return true;
    }

    private void deleteLine() {
        snapshot("delline");
        clearSel();
        if (lines.size() == 1) {
            lines.set(0, "");
            curCol = 0;
        } else {
            lines.remove(curLine);
            curLine = Math.min(curLine, lines.size() - 1);
            curCol = Math.min(curCol, lines.get(curLine).length());
        }
        afterEdit();
    }

    private void insertLineBelow() {
        snapshot("newline");
        clearSel();
        String line = lines.get(curLine);
        String indent = "";
        if (MseConfig.INSTANCE.autoIndent) {
            indent = " ".repeat(firstNonSpace(line));
            if (line.stripTrailing().endsWith(":")) indent += " ".repeat(unit());
        }
        lines.add(curLine + 1, indent);
        curLine++;
        curCol = indent.length();
        afterEdit();
    }

    // ---------- украшения: скобки, вхождения, направляющие ----------

    private static boolean isBracket(char c) {
        return c == '(' || c == ')' || c == '[' || c == ']' || c == '{' || c == '}';
    }

    private boolean skipColor(int line, int col) {
        int[] cs = colors.get(line);
        return col < cs.length && (cs[col] == PyHighlighter.COMMENT || cs[col] == PyHighlighter.STRING);
    }

    /** {строка1, столбец1, строка2, столбец2} пары скобок рядом с курсором или null. */
    private int[] bracketMatch() {
        String s = lines.get(curLine);
        int col = -1;
        if (curCol < s.length() && isBracket(s.charAt(curCol)) && !skipColor(curLine, curCol)) col = curCol;
        else if (curCol > 0 && isBracket(s.charAt(curCol - 1)) && !skipColor(curLine, curCol - 1)) col = curCol - 1;
        if (col < 0) return null;
        char c = s.charAt(col);
        boolean open = c == '(' || c == '[' || c == '{';
        char target = switch (c) {
            case '(' -> ')';
            case ')' -> '(';
            case '[' -> ']';
            case ']' -> '[';
            case '{' -> '}';
            default -> '{';
        };
        int depth = 0, budget = 20000;
        if (open) {
            for (int li = curLine; li < lines.size() && budget > 0; li++) {
                String t = lines.get(li);
                for (int ci = (li == curLine ? col + 1 : 0); ci < t.length(); ci++, budget--) {
                    char d = t.charAt(ci);
                    if (!isBracket(d) || skipColor(li, ci)) continue;
                    if (d == c) depth++;
                    else if (d == target) {
                        if (depth == 0) return new int[]{curLine, col, li, ci};
                        depth--;
                    }
                }
            }
        } else {
            for (int li = curLine; li >= 0 && budget > 0; li--) {
                String t = lines.get(li);
                for (int ci = (li == curLine ? col - 1 : t.length() - 1); ci >= 0; ci--, budget--) {
                    char d = t.charAt(ci);
                    if (!isBracket(d) || skipColor(li, ci)) continue;
                    if (d == c) depth++;
                    else if (d == target) {
                        if (depth == 0) return new int[]{curLine, col, li, ci};
                        depth--;
                    }
                }
            }
        }
        return null;
    }

    private String occurrenceWord() {
        if (hasSel() || !findTerm.isEmpty()) return "";
        String s = lines.get(curLine);
        int a = curCol, b = curCol;
        while (a > 0 && isWordChar(s.charAt(a - 1))) a--;
        while (b < s.length() && isWordChar(s.charAt(b))) b++;
        if (b - a < 2) return "";
        String w = s.substring(a, b);
        if (Character.isDigit(w.charAt(0)) || PyHighlighter.KEYWORDS.contains(w)) return "";
        return w;
    }

    private int blankIndent(int i) {
        int up = 0, down = 0;
        for (int k = i - 1, n = 0; k >= 0 && n < 40; k--, n++) {
            if (!lines.get(k).isBlank()) {
                up = firstNonSpace(lines.get(k));
                break;
            }
        }
        for (int k = i + 1, n = 0; k < lines.size() && n < 40; k++, n++) {
            if (!lines.get(k).isBlank()) {
                down = firstNonSpace(lines.get(k));
                break;
            }
        }
        return Math.min(up, down);
    }

    private void drawDecorations(GuiGraphicsExtractor g, int i, int ly, int tl, int[] br, String occ) {
        String s = lines.get(i);
        if (!findTerm.isEmpty()) {
            String ls = s.toLowerCase(), lt = findTerm.toLowerCase();
            int p = 0;
            while ((p = ls.indexOf(lt, p)) >= 0) {
                int sx = tl + font.width(s.substring(0, p)) - scrollX;
                int ex = tl + font.width(s.substring(0, p + lt.length())) - scrollX;
                g.fill(sx, ly, ex, ly + lineH, FIND_BG);
                p += Math.max(1, lt.length());
            }
        } else if (!occ.isEmpty()) {
            int p = 0;
            while ((p = s.indexOf(occ, p)) >= 0) {
                int e = p + occ.length();
                boolean okL = p == 0 || !isWordChar(s.charAt(p - 1));
                boolean okR = e >= s.length() || !isWordChar(s.charAt(e));
                if (okL && okR) {
                    int sx = tl + font.width(s.substring(0, p)) - scrollX;
                    int ex = tl + font.width(s.substring(0, e)) - scrollX;
                    g.fill(sx, ly, ex, ly + lineH, OCC_BG);
                }
                p = e;
            }
        }
        if (br != null) {
            if (i == br[0]) bracketBox(g, s, br[1], ly, tl);
            if (i == br[2]) bracketBox(g, s, br[3], ly, tl);
        }
        int ind = s.isBlank() ? blankIndent(i) : firstNonSpace(s);
        int u = unit();
        int spaceW = Math.max(1, font.width(" "));
        for (int k = u; k < ind; k += u) {
            int gx = tl + k * spaceW - scrollX;
            g.fill(gx, ly, gx + 1, ly + lineH, GUIDE);
        }
    }

    private void bracketBox(GuiGraphicsExtractor g, String s, int col, int ly, int tl) {
        int sx = tl + font.width(s.substring(0, col)) - scrollX;
        int ex = tl + font.width(s.substring(0, col + 1)) - scrollX;
        g.fill(sx, ly, ex, ly + lineH, BRACKET_BG);
    }

    // ===================== подсказки =====================

    private void hidePopup() {
        popup = List.of();
        popupSel = 0;
        popupScroll = 0;
    }

    private boolean popupVisible() {
        return !popup.isEmpty();
    }

    private boolean inCommentOrString(int line, int col) {
        ensureColors();
        int[] cs = colors.get(line);
        int idx = Math.min(col, cs.length) - 1;
        if (idx < 0) return false;
        return cs[idx] == PyHighlighter.COMMENT || cs[idx] == PyHighlighter.STRING;
    }

    private void updatePopup(boolean forced) {
        MseConfig cfg = MseConfig.INSTANCE;
        if (cfg.autocomplete.equals("off") || (!forced && !cfg.autocomplete.equals("auto"))) {
            hidePopup();
            return;
        }
        String line = lines.get(curLine);
        int start = curCol;
        while (start > 0 && isWordChar(line.charAt(start - 1))) start--;
        String prefix = line.substring(start, curCol);
        String qual = null;
        if (start > 0 && line.charAt(start - 1) == '.') {
            int dot = start - 1;
            char before = dot > 0 ? line.charAt(dot - 1) : '\0';
            if (before == '"' || before == '\'') {
                qual = "\"\"";
            } else {
                int qs = dot, depth = 0;
                while (qs > 0) {
                    char c = line.charAt(qs - 1);
                    if (c == ')' || c == ']') { depth++; qs--; continue; }
                    if (c == '(' || c == '[') { if (depth == 0) break; depth--; qs--; continue; }
                    if (depth > 0 || isWordChar(c) || c == '.') { qs--; continue; }
                    break;
                }
                qual = line.substring(qs, dot).trim();
            }
        }
        if (!forced) {
            if (qual == null && prefix.length() < 2) { hidePopup(); return; }
            if (!prefix.isEmpty() && Character.isDigit(prefix.charAt(0))) { hidePopup(); return; }
        }
        if (inCommentOrString(curLine, curCol) && !forced) {
            hidePopup();
            return;
        }
        List<Completions.Item> items = Completions.suggest(prefix, qual, lines, forced, line);
        if (items.isEmpty()) {
            hidePopup();
            return;
        }
        popup = items;
        popupSel = 0;
        popupScroll = 0;
        popupStartCol = start;
    }

    private void acceptPopup() {
        if (!popupVisible()) return;
        Completions.Item it = popup.get(popupSel);
        snapshot("complete");
        if (it.kind().equals("snp")) {
            expandSnippet(it.text());
            return;
        }
        String line = lines.get(curLine);
        lines.set(curLine, line.substring(0, popupStartCol) + it.text() + line.substring(curCol));
        curCol = popupStartCol + it.text().length();
        clearSel();
        hidePopup();
        afterEdit();
    }

    /** Вставляет шаблон; $0 в шаблоне — место курсора после вставки. */
    private void expandSnippet(String trigger) {
        String body = Completions.snippetBody(trigger);
        String line = lines.get(curLine);
        String indent = " ".repeat(firstNonSpace(line));
        lines.set(curLine, line.substring(0, popupStartCol) + line.substring(curCol));
        curCol = popupStartCol;
        clearSel();
        hidePopup();
        String[] bl = body.split("\n", -1);
        StringBuilder sb = new StringBuilder();
        int mLine = -1, mCol = 0;
        for (int i = 0; i < bl.length; i++) {
            String l = bl[i];
            int mi = l.indexOf("$0");
            if (mi >= 0) {
                mLine = i;
                mCol = (i == 0 ? popupStartCol : indent.length()) + mi;
                l = l.replace("$0", "");
            }
            if (i > 0) sb.append('\n').append(indent);
            sb.append(l);
        }
        int startLine = curLine;
        insertRaw(sb.toString());
        if (mLine >= 0) {
            curLine = startLine + mLine;
            curCol = mCol;
        }
        afterEdit();
    }

    private void popupMove(int d) {
        int n = popup.size();
        popupSel = ((popupSel + d) % n + n) % n;
        if (popupSel < popupScroll) popupScroll = popupSel;
        if (popupSel >= popupScroll + POPUP_ROWS) popupScroll = popupSel - POPUP_ROWS + 1;
    }

    // ===================== геометрия =====================

    private int gutterWidth() {
        if (!MseConfig.INSTANCE.lineNumbers) return 6;
        return Math.max(font.width(Integer.toString(lines.size())), font.width("000")) + 12;
    }

    private int textLeft() { return getX() + gutterWidth() + 4; }
    private int viewWidth() { return getWidth() - gutterWidth() - 4 - 7; }
    private int visibleLines() { return Math.max(1, (getHeight() - 4) / lineH); }
    private int maxTop() { return Math.max(0, lines.size() - visibleLines()); }
    private int xOf(int line, int col) { return font.width(lines.get(line).substring(0, col)); }

    private void ensureCursorVisible() {
        int vis = visibleLines();
        if (curLine < topLine) topLine = curLine;
        else if (curLine >= topLine + vis) topLine = curLine - vis + 1;
        topLine = clamp(topLine, 0, maxTop());
        int x = xOf(curLine, curCol);
        int vw = viewWidth();
        if (x - scrollX < 0) scrollX = Math.max(0, x - 30);
        else if (x - scrollX > vw - 8) scrollX = x - vw + 40;
    }

    private int lineAt(double my) {
        int l = topLine + (int) Math.floor((my - (getY() + 2)) / lineH);
        return clamp(l, 0, lines.size() - 1);
    }

    private int colAt(int line, double mx) {
        String s = lines.get(line);
        double rel = mx - textLeft() + scrollX;
        if (rel <= 0) return 0;
        int acc = 0;
        for (int i = 0; i < s.length(); i++) {
            int w = font.width(String.valueOf(s.charAt(i)));
            if (rel < acc + w / 2.0) return i;
            acc += w;
        }
        return s.length();
    }

    private int thumbHeight() {
        return Math.max(16, getHeight() * visibleLines() / Math.max(1, lines.size()));
    }

    private void dragBarTo(double my) {
        int th = thumbHeight();
        double frac = (my - getY() - th / 2.0) / Math.max(1, getHeight() - th);
        topLine = clamp((int) Math.round(frac * maxTop()), 0, maxTop());
    }

    // ===================== отрисовка =====================

    @Override
    public void setFocused(boolean focused) {
        super.setFocused(focused);
        Minecraft.getInstance().onTextInputFocusChange(this, focused);
        if (!focused) hidePopup();
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        ensureColors();
        MseConfig cfg = MseConfig.INSTANCE;
        int x = getX(), y = getY(), w = getWidth(), h = getHeight();
        int gw = gutterWidth();
        int tl = textLeft();
        int vis = visibleLines();

        g.fill(x, y, x + w, y + h, BG);
        g.fill(x, y, x + w, y + 1, BORDER);
        g.fill(x, y + h - 1, x + w, y + h, BORDER);
        g.fill(x, y, x + 1, y + h, BORDER);
        g.fill(x + w - 1, y, x + w, y + h, BORDER);

        int last = Math.min(lines.size(), topLine + vis + 1);
        boolean sel = hasSel();
        int[] o = sel ? ordered() : null;
        boolean blink = isFocused() && (System.currentTimeMillis() / 530) % 2 == 0;
        int[] br = bracketMatch();
        String occ = occurrenceWord();

        // строки текста
        g.enableScissor(x + gw, y + 1, x + w - 7, y + h - 1);
        for (int i = topLine; i < last; i++) {
            int ly = y + 2 + (i - topLine) * lineH;
            String s = lines.get(i);
            if (i == curLine && !sel && isFocused()) g.fill(x + gw, ly, x + w - 7, ly + lineH, CURRENT_LINE);
            if (sel && i >= o[0] && i <= o[2]) {
                int a = (i == o[0]) ? o[1] : 0;
                int b = (i == o[2]) ? o[3] : s.length();
                int sx = tl + font.width(s.substring(0, a)) - scrollX;
                int ex = tl + font.width(s.substring(0, b)) - scrollX + (i < o[2] ? 5 : 0);
                g.fill(sx, ly, ex, ly + lineH, SELECTION);
            }
            if (i == errorLine) g.fill(x + gw, ly, x + w - 7, ly + lineH, 0xFF3B1F23);
            drawDecorations(g, i, ly, tl, br, occ);
            int[] cs = colors.get(i);
            int pos = 0, cx = tl - scrollX;
            while (pos < s.length()) {
                int end = pos + 1;
                while (end < s.length() && cs[end] == cs[pos]) end++;
                String seg = s.substring(pos, end);
                int sw = font.width(seg);
                if (cx + sw >= x + gw && cx <= x + w) g.text(font, seg, cx, ly + 2, cs[pos], false);
                cx += sw;
                pos = end;
            }
            if (blink && i == curLine) {
                int cxp = tl + xOf(i, curCol) - scrollX;
                g.fill(cxp, ly + 1, cxp + 1, ly + lineH - 1, 0xFFFFFFFF);
            }
        }
        g.disableScissor();

        // номера строк
        g.fill(x + 1, y + 1, x + gw, y + h - 1, BG);
        if (errorLine >= topLine && errorLine < last) {
            int ey = y + 2 + (errorLine - topLine) * lineH;
            g.fill(x + 1, ey, x + 4, ey + lineH, 0xFFE06C75);
        }
        if (cfg.lineNumbers) {
            g.enableScissor(x + 1, y + 1, x + gw, y + h - 1);
            for (int i = topLine; i < last; i++) {
                int ly = y + 2 + (i - topLine) * lineH;
                String num = Integer.toString(i + 1);
                g.text(font, num, x + gw - 6 - font.width(num), ly + 2,
                        i == curLine ? GUTTER_FG_ACTIVE : GUTTER_FG, false);
            }
            g.disableScissor();
            g.fill(x + gw, y + 1, x + gw + 1, y + h - 1, BORDER);
        }

        // полоса прокрутки
        if (lines.size() > vis) {
            int th = thumbHeight();
            int ty = y + (h - th) * topLine / Math.max(1, maxTop());
            g.fill(x + w - 6, ty, x + w - 2, ty + th, SCROLL);
        }

        renderPopup(g);
    }

    private int kindColor(String kind) {
        return switch (kind) {
            case "kw" -> PyHighlighter.KEYWORD;
            case "fn" -> PyHighlighter.BUILTIN;
            case "mc", "mth" -> PyHighlighter.FUNC;
            case "cls", "std" -> PyHighlighter.CLASS;
            case "snp" -> PyHighlighter.DECORATOR;
            default -> PyHighlighter.SELF;
        };
    }

    private void renderPopup(GuiGraphicsExtractor g) {
        if (!popupVisible()) return;
        int rows = Math.min(POPUP_ROWS, popup.size());
        int pw = 150;
        boolean anyDetail = false;
        for (Completions.Item it : popup) if (!it.detail().isEmpty()) { anyDetail = true; break; }
        for (int i = 0; i < rows; i++) {
            Completions.Item it = popup.get(popupScroll + i);
            pw = Math.max(pw, font.width(it.text()) + font.width(it.kind()) + 24);
        }
        String detail = popup.get(popupSel).detail();
        int maxW = Math.max(150, getWidth() - gutterWidth() - 20);
        if (anyDetail) pw = Math.max(pw, Math.min(maxW, font.width(detail) + 14));
        pw = Math.min(pw, maxW);
        int ph = rows * lineH + 4 + (anyDetail ? lineH + 3 : 0);
        int px = textLeft() + xOf(curLine, popupStartCol) - scrollX;
        px = clamp(px, getX() + gutterWidth(), Math.max(getX() + gutterWidth(), getX() + getWidth() - pw - 2));
        int ly = getY() + 2 + (curLine - topLine) * lineH;
        int py = ly + lineH + 1;
        if (py + ph > getY() + getHeight()) py = ly - ph - 1;
        popX = px;
        popY = py;
        popW = pw;
        popH = rows * lineH + 4;

        g.fill(px - 1, py - 1, px + pw + 1, py + ph + 1, POPUP_BORDER);
        g.fill(px, py, px + pw, py + ph, POPUP_BG);
        for (int i = 0; i < rows; i++) {
            int idx = popupScroll + i;
            Completions.Item it = popup.get(idx);
            int ry = py + 2 + i * lineH;
            if (idx == popupSel) g.fill(px, ry, px + pw, ry + lineH, POPUP_SEL);
            g.text(font, it.text(), px + 6, ry + 2, kindColor(it.kind()), false);
            g.text(font, it.kind(), px + pw - 6 - font.width(it.kind()), ry + 2, GUTTER_FG, false);
        }
        if (anyDetail) {
            int dy = py + 2 + rows * lineH;
            g.fill(px, dy, px + pw, dy + 1, POPUP_BORDER);
            g.text(font, LabelWidget.fit(font, detail, pw - 12), px + 6, dy + 3, 0xFFB0B0B0, false);
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
    }

    // ===================== ввод =====================

    @Override
    public boolean charTyped(CharacterEvent ev) {
        if (!isFocused()) return false;
        int cp = ev.codepoint();
        if (cp < 32 || cp == 127) return false;
        handleTyped(new String(Character.toChars(cp)));
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent e) {
        if (!isFocused()) return false;
        int k = e.key();
        boolean ctrl = e.hasControlDown(), shift = e.hasShiftDown(), alt = e.hasAltDown();

        // подсказки
        if (popupVisible()) {
            if (k == InputConstants.KEY_DOWN) { popupMove(1); return true; }
            if (k == InputConstants.KEY_UP) { popupMove(-1); return true; }
            if (k == InputConstants.KEY_RETURN || k == InputConstants.KEY_NUMPADENTER || k == InputConstants.KEY_TAB) {
                acceptPopup();
                return true;
            }
            if (k == InputConstants.KEY_ESCAPE) { hidePopup(); return true; }
        }

        if (Hotkeys.matches("comment", e)) { toggleComment(); return true; }
        if (Hotkeys.matches("duplicate", e)) { duplicateLine(); return true; }
        if (Hotkeys.matches("delete_line", e)) { deleteLine(); return true; }

        if (ctrl && !alt) {
            if (k == InputConstants.KEY_SPACE) { updatePopup(true); return true; }
            if (k == InputConstants.KEY_A) { selectAll(); return true; }
            if (k == InputConstants.KEY_C) { copy(false); return true; }
            if (k == InputConstants.KEY_X) { copy(true); updatePopup(false); return true; }
            if (k == InputConstants.KEY_V) { paste(); hidePopup(); return true; }
            if (k == InputConstants.KEY_Z) { if (shift) redo(); else undo(); return true; }
            if (k == InputConstants.KEY_Y) { redo(); return true; }
            if (k == InputConstants.KEY_U) { cursorUndo(); return true; }
            if (k == InputConstants.KEY_HOME) { rememberCursor(); moveTo(0, 0, shift); return true; }
            if (k == InputConstants.KEY_END) {
                rememberCursor();
                int l = lines.size() - 1;
                moveTo(l, lines.get(l).length(), shift);
                return true;
            }
            if (k == InputConstants.KEY_LEFT) {
                if (curCol == 0 && curLine > 0) moveTo(curLine - 1, lines.get(curLine - 1).length(), shift);
                else moveTo(curLine, wordLeft(lines.get(curLine), curCol), shift);
                return true;
            }
            if (k == InputConstants.KEY_RIGHT) {
                String s = lines.get(curLine);
                if (curCol == s.length() && curLine < lines.size() - 1) moveTo(curLine + 1, 0, shift);
                else moveTo(curLine, wordRight(s, curCol), shift);
                return true;
            }
            if (k == InputConstants.KEY_RETURN || k == InputConstants.KEY_NUMPADENTER) { insertLineBelow(); return true; }
            if (k == InputConstants.KEY_BACKSPACE) { backspace(true); return true; }
            return false;
        }
        if (alt && (k == InputConstants.KEY_UP || k == InputConstants.KEY_DOWN)) {
            moveLine(k == InputConstants.KEY_UP ? -1 : 1);
            return true;
        }

        if (k == InputConstants.KEY_LEFT) {
            if (hasSel() && !shift) { int[] o = ordered(); moveTo(o[0], o[1], false); }
            else if (curCol > 0) moveTo(curLine, curCol - 1, shift);
            else if (curLine > 0) moveTo(curLine - 1, lines.get(curLine - 1).length(), shift);
            return true;
        }
        if (k == InputConstants.KEY_RIGHT) {
            if (hasSel() && !shift) { int[] o = ordered(); moveTo(o[2], o[3], false); }
            else if (curCol < lines.get(curLine).length()) moveTo(curLine, curCol + 1, shift);
            else if (curLine < lines.size() - 1) moveTo(curLine + 1, 0, shift);
            return true;
        }
        if (k == InputConstants.KEY_UP) { moveVertical(-1, shift); return true; }
        if (k == InputConstants.KEY_DOWN) { moveVertical(1, shift); return true; }
        if (k == InputConstants.KEY_PAGEUP) { rememberCursor(); moveVertical(-visibleLines(), shift); return true; }
        if (k == InputConstants.KEY_PAGEDOWN) { rememberCursor(); moveVertical(visibleLines(), shift); return true; }
        if (k == InputConstants.KEY_HOME) {
            int fn = firstNonSpace(lines.get(curLine));
            moveTo(curLine, curCol == fn ? 0 : fn, shift);
            return true;
        }
        if (k == InputConstants.KEY_END) { moveTo(curLine, lines.get(curLine).length(), shift); return true; }

        if (k == InputConstants.KEY_RETURN || k == InputConstants.KEY_NUMPADENTER) {
            newline();
            return true;
        }
        if (k == InputConstants.KEY_TAB) {
            boolean multi = hasSel() && ordered()[0] != ordered()[2];
            if (shift) indentLines(true);
            else if (multi) indentLines(false);
            else typeText(" ".repeat(unit() - (curCol % unit())));
            return true;
        }
        if (k == InputConstants.KEY_BACKSPACE) {
            boolean wasVisible = popupVisible();
            backspace(false);
            if (wasVisible) updatePopup(false);
            return true;
        }
        if (k == InputConstants.KEY_DELETE) { deleteForward(); return true; }
        return false;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
        double mx = e.x(), my = e.y();
        if (popupVisible() && mx >= popX && mx < popX + popW && my >= popY && my < popY + popH) {
            int row = (int) ((my - popY - 2) / lineH);
            int idx = popupScroll + clamp(row, 0, Math.min(POPUP_ROWS, popup.size()) - 1);
            popupSel = idx;
            acceptPopup();
            return true;
        }
        if (!isMouseOver(mx, my)) return false;
        hidePopup();
        if (mx >= getX() + getWidth() - 7 && lines.size() > visibleLines()) {
            dragBar = true;
            dragBarTo(my);
            return true;
        }
        int l = lineAt(my);
        int c = colAt(l, mx);
        rememberCursor();
        if (doubleClick) {
            String s = lines.get(l);
            int a = c, b = c;
            while (a > 0 && isWordChar(s.charAt(a - 1))) a--;
            while (b < s.length() && isWordChar(s.charAt(b))) b++;
            anchorLine = l;
            anchorCol = a;
            curLine = l;
            curCol = b;
            goalCol = b;
        } else {
            moveTo(l, c, e.hasShiftDown());
            dragText = true;
        }
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
        if (dragBar) {
            dragBarTo(e.y());
            return true;
        }
        if (dragText) {
            int l = lineAt(e.y());
            moveTo(l, colAt(l, e.x()), true);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent e) {
        boolean was = dragBar || dragText;
        dragBar = false;
        dragText = false;
        return was;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollXd, double scrollY) {
        if (!isMouseOver(mouseX, mouseY)) return false;
        if (scrollY != 0) topLine = clamp(topLine - (int) Math.signum(scrollY) * 3, 0, maxTop());
        if (scrollXd != 0) scrollX = Math.max(0, scrollX - (int) Math.signum(scrollXd) * 30);
        return true;
    }
}
