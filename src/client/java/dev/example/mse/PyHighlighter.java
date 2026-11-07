package dev.example.mse;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/** Простая подсветка Python: для каждой строки массив цветов по символам. */
final class PyHighlighter {
    private PyHighlighter() {}

    static final int DEFAULT = 0xFFD4D4D4;
    static final int KEYWORD = 0xFFC586C0;
    static final int CONSTANT = 0xFF569CD6;
    static final int STRING = 0xFFCE9178;
    static final int COMMENT = 0xFF6A9955;
    static final int NUMBER = 0xFFB5CEA8;
    static final int BUILTIN = 0xFF4EC9B0;
    static final int FUNC = 0xFFDCDCAA;
    static final int CLASS = 0xFF4EC9B0;
    static final int DECORATOR = 0xFFDCDCAA;
    static final int SELF = 0xFF9CDCFE;

    static final Set<String> KEYWORDS = Set.of(
            "and", "as", "assert", "async", "await", "break", "class", "continue", "def", "del",
            "elif", "else", "except", "finally", "for", "from", "global", "if", "import", "in",
            "is", "lambda", "nonlocal", "not", "or", "pass", "raise", "return", "try", "while",
            "with", "yield");
    static final Set<String> CONSTANTS = Set.of("True", "False", "None");
    static final Set<String> BUILTINS = Set.of(
            "print", "len", "range", "int", "str", "float", "bool", "list", "dict", "set", "tuple",
            "open", "enumerate", "zip", "map", "filter", "min", "max", "abs", "sum", "sorted",
            "reversed", "round", "isinstance", "type", "input", "any", "all", "super", "object",
            "Exception", "ValueError", "KeyError", "TypeError");

    static List<int[]> highlight(List<String> lines, boolean enabled) {
        List<int[]> out = new ArrayList<>(lines.size());
        String triple = null;
        for (String line : lines) {
            int n = line.length();
            int[] c = new int[n];
            Arrays.fill(c, DEFAULT);
            if (!enabled) {
                out.add(c);
                continue;
            }
            int i = 0;
            String prevWord = "";
            while (i < n) {
                if (triple != null) {
                    int end = line.indexOf(triple, i);
                    int stop = end < 0 ? n : end + 3;
                    fill(c, i, stop, STRING);
                    i = stop;
                    if (end >= 0) triple = null;
                    continue;
                }
                char ch = line.charAt(i);
                if (ch == '#') {
                    fill(c, i, n, COMMENT);
                    i = n;
                    continue;
                }
                int q = stringStart(line, i);
                if (q >= 0) {
                    char quote = line.charAt(q);
                    String three = "" + quote + quote + quote;
                    if (line.startsWith(three, q)) {
                        triple = three;
                        fill(c, i, q + 3, STRING);
                        i = q + 3;
                        continue;
                    }
                    int j = q + 1;
                    while (j < n) {
                        char d = line.charAt(j);
                        if (d == '\\') {
                            j += 2;
                            continue;
                        }
                        j++;
                        if (d == quote) break;
                    }
                    j = Math.min(j, n);
                    fill(c, i, j, STRING);
                    i = j;
                    prevWord = "";
                    continue;
                }
                if (Character.isLetter(ch) || ch == '_') {
                    int j = i + 1;
                    while (j < n && isIdent(line.charAt(j))) j++;
                    String w = line.substring(i, j);
                    int color = DEFAULT;
                    if (KEYWORDS.contains(w)) color = KEYWORD;
                    else if (CONSTANTS.contains(w)) color = CONSTANT;
                    else if (prevWord.equals("def")) color = FUNC;
                    else if (prevWord.equals("class")) color = CLASS;
                    else if (w.equals("self") || w.equals("cls")) color = SELF;
                    else if (BUILTINS.contains(w)) color = BUILTIN;
                    else if (nextNonSpace(line, j) == '(') color = FUNC;
                    fill(c, i, j, color);
                    prevWord = w;
                    i = j;
                    continue;
                }
                if (Character.isDigit(ch) || (ch == '.' && i + 1 < n && Character.isDigit(line.charAt(i + 1)))) {
                    int j = i + 1;
                    while (j < n && (Character.isLetterOrDigit(line.charAt(j)) || line.charAt(j) == '_' || line.charAt(j) == '.')) j++;
                    fill(c, i, j, NUMBER);
                    prevWord = "";
                    i = j;
                    continue;
                }
                if (ch == '@' && line.substring(0, i).isBlank()) {
                    int j = i + 1;
                    while (j < n && (isIdent(line.charAt(j)) || line.charAt(j) == '.')) j++;
                    fill(c, i, j, DECORATOR);
                    i = j;
                    continue;
                }
                if (!Character.isWhitespace(ch)) prevWord = "";
                i++;
            }
            out.add(c);
        }
        return out;
    }

    private static int stringStart(String s, int i) {
        char ch = s.charAt(i);
        if (ch == '"' || ch == '\'') return i;
        if (i > 0 && isIdent(s.charAt(i - 1))) return -1;
        int j = i;
        while (j < s.length() && j - i < 2 && "rRbBfFuU".indexOf(s.charAt(j)) >= 0) j++;
        if (j > i && j < s.length() && (s.charAt(j) == '"' || s.charAt(j) == '\'')) return j;
        return -1;
    }

    private static void fill(int[] c, int from, int to, int color) {
        for (int i = Math.max(0, from); i < Math.min(to, c.length); i++) c[i] = color;
    }

    static boolean isIdent(char ch) {
        return Character.isLetterOrDigit(ch) || ch == '_';
    }

    private static char nextNonSpace(String s, int i) {
        while (i < s.length() && s.charAt(i) == ' ') i++;
        return i < s.length() ? s.charAt(i) : '\0';
    }
}
