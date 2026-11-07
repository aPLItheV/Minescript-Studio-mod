package dev.example.mse;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Подсказки: ключевые слова, встроенные функции, API Minescript, стандартная библиотека
 * и члены объектов после точки (по типу, выведенному из кода файла).
 */
final class Completions {
    private Completions() {}

    /** kind: kw, fn, mc, cls, std, fld, mth, var */
    record Item(String text, String kind, String detail) {}

    private static final Pattern WORD = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
    private static final Pattern IMPORT_MC = Pattern.compile("^\\s*import\\s+minescript(?:\\s+as\\s+(\\w+))?\\s*$");
    private static final Pattern IMPORT_STD = Pattern.compile("^\\s*import\\s+([\\w.]+)(?:\\s+as\\s+(\\w+))?\\s*$");
    private static final Pattern FROM_IMPORT = Pattern.compile("^\\s*from\\s+([\\w.]+)\\s+import\\s+(.+?)\\s*$");
    private static final Pattern ASSIGN = Pattern.compile("^\\s*(\\w+)\\s*(?::\\s*[^=]+?)?=\\s*(?!=)(.+?)\\s*$");
    private static final Pattern FOR_LOOP = Pattern.compile("^\\s*for\\s+(\\w+)\\s+in\\s+(.+?)\\s*:\\s*$");
    private static final Pattern WITH_AS = Pattern.compile("^\\s*with\\s+(.+?)\\s+as\\s+(\\w+)\\s*:");
    private static final Pattern DEF_PARAMS = Pattern.compile("^\\s*def\\s+\\w+\\((.*)\\)");
    private static final Pattern ELEMENT = Pattern.compile("^(\\w+)(.*)$", Pattern.DOTALL);
    private static final Pattern SELF_ATTR = Pattern.compile("\\bself\\.(\\w+)");
    private static final Pattern SELF_DEF = Pattern.compile("^\\s*def\\s+(\\w+)\\(\\s*self\\b");

    /** Шаблоны: триггер -> {описание, текст}. $0 — место курсора. */
    private static final Map<String, String[]> SNIPPETS = new LinkedHashMap<>();

    static {
        snip("main", "snippet.main", "def main():\n    $0\n\n\nif __name__ == \"__main__\":\n    main()");
        snip("ifmain", "snippet.ifmain", "if __name__ == \"__main__\":\n    $0");
        snip("mcheader", "snippet.mcheader", "import minescript as m\nimport time\n\n\n$0");
        snip("forr", "snippet.forr", "for i in range($0):\n    ");
        snip("fore", "snippet.fore", "for e in m.entities(max_distance=20):\n    $0");
        snip("whilet", "snippet.whilet", "while True:\n    $0\n    time.sleep(0.1)");
        snip("trye", "snippet.trye", "try:\n    $0\nexcept Exception as e:\n    m.echo(str(e))");
        snip("deff", "snippet.deff", "def $0():\n    pass");
        snip("args", "snippet.args", "args = sys.argv[1:]\n$0");
        snip("chatloop", "snippet.chatloop", "with m.EventQueue() as events:\n    events.register_chat_listener()\n    while True:\n        event = events.get()\n        message = event.message\n        $0");
        snip("clickuse", "snippet.clickuse", "m.player_press_use(True)\ntime.sleep(0.1)\nm.player_press_use(False)\n$0");
        snip("clickatk", "snippet.clickatk", "m.player_press_attack(True)\ntime.sleep(0.1)\nm.player_press_attack(False)\n$0");
        snip("lookat", "snippet.lookat", "m.player_look_at($0x, y, z)");
    }

    private static void snip(String trigger, String desc, String body) {
        SNIPPETS.put(trigger, new String[]{desc, body});
    }

    static String snippetBody(String trigger) {
        String[] s = SNIPPETS.get(trigger);
        return s == null ? trigger : s[1];
    }

    private static final Map<String, McApi.Fn> MC_FN = new HashMap<>();
    private static final Set<String> BUILTIN_CTORS = Set.of("str", "int", "float", "list", "dict", "set", "tuple", "bool");

    static {
        for (McApi.Fn f : McApi.FUNCTIONS) MC_FN.put(f.name(), f);
    }

    // ===================== контекст файла =====================

    private static final class Ctx {
        final Set<String> mcAliases = new HashSet<>(Set.of("minescript", "m"));
        final Map<String, String> stdAliases = new HashMap<>();
        final Set<String> mcNames = new HashSet<>();
        final Map<String, String> vars = new HashMap<>();
        final Map<String, McApi.Member> fromStd = new LinkedHashMap<>();
        boolean mcStar;
    }

    private static Ctx buildCtx(List<String> lines) {
        Ctx cx = new Ctx();
        for (String line : lines) {
            String t = stripComment(line);
            Matcher m;
            if ((m = IMPORT_MC.matcher(t)).matches()) {
                if (m.group(1) != null) cx.mcAliases.add(m.group(1));
                continue;
            }
            if ((m = IMPORT_STD.matcher(t)).matches()) {
                String mod = m.group(1);
                if (m.group(2) != null) cx.stdAliases.put(m.group(2), mod);
                else cx.stdAliases.put(mod.contains(".") ? mod.substring(0, mod.indexOf('.')) : mod, mod.contains(".") ? mod.substring(0, mod.indexOf('.')) : mod);
                continue;
            }
            if ((m = FROM_IMPORT.matcher(t)).matches()) {
                String mod = m.group(1);
                String names = m.group(2).replace("(", "").replace(")", "");
                if (mod.equals("minescript")) {
                    for (String n : names.split(",")) {
                        String nm = n.trim().split("\\s+as\\s+")[0].trim();
                        if (nm.equals("*")) cx.mcStar = true;
                        else if (!nm.isEmpty()) cx.mcNames.add(nm);
                    }
                } else if (StdLib.MODULES.containsKey(mod)) {
                    for (String n : names.split(",")) {
                        String nm = n.trim().split("\\s+as\\s+")[0].trim();
                        for (McApi.Member mem : StdLib.MODULES.get(mod)) {
                            if (nm.equals("*") || mem.name().equals(nm)) cx.fromStd.putIfAbsent(mem.name(), mem);
                        }
                    }
                }
                continue;
            }
            if ((m = DEF_PARAMS.matcher(t)).find()) {
                for (String p : splitTop(m.group(1), ',')) {
                    int c = p.indexOf(':');
                    if (c < 0) continue;
                    String name = p.substring(0, c).trim();
                    String ann = p.substring(c + 1);
                    int eq = ann.indexOf('=');
                    if (eq >= 0) ann = ann.substring(0, eq);
                    ann = ann.trim();
                    int dot = ann.lastIndexOf('.');
                    if (dot >= 0 && !ann.contains("[")) ann = ann.substring(dot + 1);
                    if (isKnownType(ann)) cx.vars.put(name, ann);
                }
                continue;
            }
            if ((m = FOR_LOOP.matcher(t)).matches()) {
                cx.vars.put(m.group(1), elementOf(evalExpr(m.group(2), cx)));
                continue;
            }
            if ((m = WITH_AS.matcher(t)).find()) {
                cx.vars.put(m.group(2), evalExpr(m.group(1), cx));
                continue;
            }
            if ((m = ASSIGN.matcher(t)).matches()) {
                cx.vars.put(m.group(1), evalExpr(m.group(2), cx));
            }
        }
        return cx;
    }

    private static boolean isKnownType(String t) {
        return McApi.TYPES.containsKey(t) || StdLib.BUILTIN_TYPES.containsKey(t) || t.startsWith("List[");
    }

    private static String stripComment(String line) {
        boolean sq = false, dq = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '\'' && !dq) sq = !sq;
            else if (c == '"' && !sq) dq = !dq;
            else if (c == '#' && !sq && !dq) return line.substring(0, i);
        }
        return line;
    }

    private static List<String> splitTop(String s, char sep) {
        List<String> out = new ArrayList<>();
        int depth = 0, start = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '(' || c == '[' || c == '{') depth++;
            else if (c == ')' || c == ']' || c == '}') depth--;
            else if (c == sep && depth == 0) {
                out.add(s.substring(start, i));
                start = i + 1;
            }
        }
        out.add(s.substring(start));
        return out;
    }

    // ===================== вывод типов =====================

    /** Тип выражения: имя типа, "<mc>" (модуль minescript), "<std:модуль>" или "" (неизвестно). */
    static String evalExpr(String expr, Ctx cx) {
        expr = expr.trim();
        if (expr.isEmpty()) return "";
        char c0 = expr.charAt(0);
        if (c0 == '"' || c0 == '\'') return "str";
        if (expr.length() > 1 && "fFrRbBuU".indexOf(c0) >= 0 && (expr.charAt(1) == '"' || expr.charAt(1) == '\'')) return "str";
        if (c0 == '[') return "list";
        if (c0 == '{') return (expr.equals("{}") || expr.contains(":")) ? "dict" : "set";
        if (Character.isDigit(c0)) return expr.contains(".") ? "float" : "int";

        List<String> parts = splitTop(expr, '.');
        String cur = "";
        for (int i = 0; i < parts.size(); i++) {
            Matcher mm = ELEMENT.matcher(parts.get(i).trim());
            if (!mm.matches()) return "";
            String name = mm.group(1), rest = mm.group(2).trim();
            boolean call = rest.startsWith("(");
            int idx = countIndexes(rest);
            String next = i == 0 ? first(name, call, cx) : member(cur, name, call);
            for (int k = 0; k < idx; k++) next = elementOf(next);
            cur = next;
            if (cur.isEmpty()) return "";
        }
        return cur;
    }

    private static int countIndexes(String rest) {
        int depth = 0, n = 0;
        for (int i = 0; i < rest.length(); i++) {
            char c = rest.charAt(i);
            if (c == '(') depth++;
            else if (c == ')') depth--;
            else if (c == '[') {
                if (depth == 0) n++;
                depth++;
            } else if (c == ']') depth--;
        }
        return n;
    }

    private static String first(String name, boolean call, Ctx cx) {
        if (!call && cx.vars.containsKey(name)) return cx.vars.get(name);
        if (!call && cx.mcAliases.contains(name)) return "<mc>";
        if (cx.stdAliases.containsKey(name) && !call) return "<std:" + cx.stdAliases.get(name) + ">";
        if (!call && StdLib.MODULES.containsKey(name)) return "<std:" + name + ">";
        if (call && BUILTIN_CTORS.contains(name)) return name;
        if (call && name.equals("EventQueue")) return "EventQueue";
        if (call && (cx.mcStar || cx.mcNames.contains(name)) && MC_FN.containsKey(name)) return MC_FN.get(name).ret();
        return "";
    }

    private static String member(String cur, String name, boolean call) {
        if (cur.equals("<mc>")) {
            McApi.Fn f = MC_FN.get(name);
            if (f != null) return f.ret();
            if (call && name.equals("EventQueue")) return "EventQueue";
            if (call && name.equals("BlockRegion")) return "BlockRegion";
            if (!call && name.equals("EventType")) return "EventType";
            return "";
        }
        if (cur.startsWith("<std:")) {
            String mod = cur.substring(5, cur.length() - 1);
            List<McApi.Member> list = StdLib.MODULES.get(mod);
            if (list != null) for (McApi.Member m : list) if (m.name().equals(name)) return m.ret();
            return "";
        }
        List<McApi.Member> list = membersOf(cur);
        if (list != null) for (McApi.Member m : list) if (m.name().equals(name)) return m.ret();
        return "";
    }

    static String elementOf(String type) {
        if (type.startsWith("List[") && type.endsWith("]")) {
            String inner = type.substring(5, type.length() - 1);
            return inner.contains("Union") ? "" : inner;
        }
        if (type.equals("str")) return "str";
        if (type.equals("Vector3f")) return "float";
        if (type.equals("BlockPos")) return "int";
        return "";
    }

    private static List<McApi.Member> membersOf(String type) {
        List<McApi.Member> l = McApi.TYPES.get(type);
        if (l != null) return l;
        return StdLib.membersOfBuiltin(type);
    }

    // ===================== подсказки =====================

    private static int rank(String kind) {
        return switch (kind) {
            case "var", "fld", "mth" -> 0;
            case "mc", "std", "cls", "snp" -> 1;
            case "fn" -> 2;
            default -> 3;
        };
    }

    private record Scored(Item item, int score, int rank) {}

    /**
     * @param prefix      уже набранное начало слова
     * @param qualExpr    выражение перед точкой ("m.player()", "event", ...) или null, если точки нет
     * @param currentLine текст текущей строки (нужен для строк import/from)
     */
    static List<Item> suggest(String prefix, String qualExpr, List<String> lines, boolean forced, String currentLine) {
        Ctx cx = buildCtx(lines);
        Map<String, Item> pool = new LinkedHashMap<>();
        boolean member = qualExpr != null;

        if (member) {
            String ty = qualExpr.equals("self") ? "<self>" : evalExpr(qualExpr, cx);
            if (ty.equals("<self>")) {
                for (String l : lines) {
                    Matcher m = SELF_ATTR.matcher(l);
                    while (m.find()) pool.putIfAbsent(m.group(1), new Item(m.group(1), "fld", ""));
                    Matcher d = SELF_DEF.matcher(l);
                    if (d.find()) pool.put(d.group(1), new Item(d.group(1), "mth", ""));
                }
            } else if (ty.equals("<mc>")) {
                for (McApi.Fn f : McApi.FUNCTIONS) pool.put(f.name(), new Item(f.name(), "mc", f.sig()));
                for (String[] c : McApi.CLASSES) pool.put(c[0], new Item(c[0], "cls", c[1]));
            } else if (ty.startsWith("<std:")) {
                List<McApi.Member> list = StdLib.MODULES.get(ty.substring(5, ty.length() - 1));
                if (list != null) for (McApi.Member m : list) pool.put(m.name(), new Item(m.name(), m.kind(), m.detail()));
            } else if (!ty.isEmpty() && membersOf(ty) != null) {
                for (McApi.Member m : membersOf(ty)) pool.put(m.name(), new Item(m.name(), m.kind(), m.detail()));
            } else {
                addWords(pool, lines, prefix);
            }
        } else {
            String cl = currentLine == null ? "" : currentLine.stripLeading();
            if (cl.startsWith("import ") || cl.startsWith("from ")) {
                pool.put("minescript", new Item("minescript", "std", Lang.t("detail.module", "minescript")));
                for (String mod : StdLib.MODULES.keySet()) {
                    pool.put(mod, new Item(mod, "std", Lang.t("detail.module", mod)));
                }
                if (cl.startsWith("from ") && cl.contains(" import ")) {
                    pool.clear();
                    for (McApi.Fn f : McApi.FUNCTIONS) pool.put(f.name(), new Item(f.name(), "mc", f.sig()));
                }
            } else {
                addWords(pool, lines, prefix);
                for (String s : PyHighlighter.KEYWORDS) pool.put(s, new Item(s, "kw", ""));
                for (String s : PyHighlighter.CONSTANTS) pool.put(s, new Item(s, "kw", ""));
                for (String s : PyHighlighter.BUILTINS) pool.put(s, new Item(s, "fn", Lang.t("detail.builtin")));
                for (McApi.Fn f : McApi.FUNCTIONS) pool.put(f.name(), new Item(f.name(), "mc", f.sig()));
                for (McApi.Member m : cx.fromStd.values()) pool.put(m.name(), new Item(m.name(), m.kind(), m.detail()));
                for (Map.Entry<String, String[]> e : SNIPPETS.entrySet()) {
                    pool.put(e.getKey(), new Item(e.getKey(), "snp", Lang.t(e.getValue()[0])));
                }
            }
        }

        String lp = prefix.toLowerCase();
        List<Scored> scored = new ArrayList<>();
        for (Item it : pool.values()) {
            String name = it.text();
            if (name.equals(prefix) && !it.kind().equals("snp")) continue;
            String ln = name.toLowerCase();
            int score;
            if (lp.isEmpty()) score = 0;
            else if (name.startsWith(prefix)) score = 0;
            else if (ln.startsWith(lp)) score = 1;
            else if (lp.length() >= 3 && ln.contains(lp)) score = 2;
            else continue;
            scored.add(new Scored(it, score, rank(it.kind())));
        }
        Comparator<Scored> cmp = Comparator.<Scored>comparingInt(Scored::score).thenComparingInt(Scored::rank);
        if (!member) cmp = cmp.thenComparingInt(s -> s.item().text().length());
        cmp = cmp.thenComparing(s -> s.item().text());
        scored.sort(cmp);

        List<Item> out = new ArrayList<>();
        for (Scored s : scored) {
            out.add(s.item());
            if (out.size() >= 100) break;
        }
        return out;
    }

    private static void addWords(Map<String, Item> pool, List<String> lines, String prefix) {
        for (String l : lines) {
            Matcher m = WORD.matcher(l);
            while (m.find()) {
                String w = m.group();
                if (w.length() >= 2 && !w.equals(prefix)) pool.putIfAbsent(w, new Item(w, "var", ""));
            }
        }
    }
}
