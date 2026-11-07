package dev.example.mse;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Подсказки для стандартной библиотеки Python и методов встроенных типов. */
final class StdLib {
    private StdLib() {}

    /** Модули: имя -> члены. Тип "<std:имя>" используется при разборе выражений. */
    static final Map<String, List<McApi.Member>> MODULES = new LinkedHashMap<>();
    /** Методы встроенных типов: str, list, dict, set, tuple, int, float. */
    static final Map<String, List<McApi.Member>> BUILTIN_TYPES = new LinkedHashMap<>();

    private static List<McApi.Member> cur;

    private static void module(String name) {
        cur = new ArrayList<>();
        MODULES.put(name, cur);
    }

    private static void type(String name) {
        cur = new ArrayList<>();
        BUILTIN_TYPES.put(name, cur);
    }

    /** функция/метод: имя, сигнатура, тип результата */
    private static void fn(String name, String sig, String ret) {
        cur.add(new McApi.Member(name, "mth", sig, ret));
    }

    /** константа/атрибут */
    private static void c(String name, String detail, String ret) {
        cur.add(new McApi.Member(name, "fld", detail, ret));
    }

    static {
        module("math");
        c("pi", "pi = 3.14159...", "float");
        c("e", "e = 2.71828...", "float");
        c("tau", "tau = 2*pi", "float");
        c("inf", "infinity", "float");
        c("nan", "not a number", "float");
        fn("sqrt", "math.sqrt(x) -> float", "float");
        fn("sin", "math.sin(x_radians) -> float", "float");
        fn("cos", "math.cos(x_radians) -> float", "float");
        fn("tan", "math.tan(x_radians) -> float", "float");
        fn("asin", "math.asin(x) -> float", "float");
        fn("acos", "math.acos(x) -> float", "float");
        fn("atan", "math.atan(x) -> float", "float");
        fn("atan2", "math.atan2(y, x) -> float  # angle in radians", "float");
        fn("sinh", "math.sinh(x) -> float", "float");
        fn("cosh", "math.cosh(x) -> float", "float");
        fn("tanh", "math.tanh(x) -> float", "float");
        fn("degrees", "math.degrees(x_radians) -> float", "float");
        fn("radians", "math.radians(x_degrees) -> float", "float");
        fn("floor", "math.floor(x) -> int", "int");
        fn("ceil", "math.ceil(x) -> int", "int");
        fn("trunc", "math.trunc(x) -> int", "int");
        fn("fabs", "math.fabs(x) -> float", "float");
        fn("fmod", "math.fmod(x, y) -> float", "float");
        fn("hypot", "math.hypot(*coords) -> float", "float");
        fn("dist", "math.dist(p, q) -> float  # distance between points", "float");
        fn("log", "math.log(x, base=e) -> float", "float");
        fn("log2", "math.log2(x) -> float", "float");
        fn("log10", "math.log10(x) -> float", "float");
        fn("exp", "math.exp(x) -> float", "float");
        fn("pow", "math.pow(x, y) -> float", "float");
        fn("isclose", "math.isclose(a, b, *, rel_tol=1e-09, abs_tol=0.0) -> bool", "bool");
        fn("copysign", "math.copysign(x, y) -> float", "float");
        fn("gcd", "math.gcd(*ints) -> int", "int");
        fn("factorial", "math.factorial(n) -> int", "int");
        fn("comb", "math.comb(n, k) -> int", "int");
        fn("perm", "math.perm(n, k=None) -> int", "int");
        fn("prod", "math.prod(iterable, *, start=1)", "");
        fn("fsum", "math.fsum(iterable) -> float", "float");
        fn("isnan", "math.isnan(x) -> bool", "bool");
        fn("isinf", "math.isinf(x) -> bool", "bool");
        fn("isfinite", "math.isfinite(x) -> bool", "bool");

        module("time");
        fn("sleep", "time.sleep(seconds)", "");
        fn("time", "time.time() -> float  # seconds since 1970", "float");
        fn("time_ns", "time.time_ns() -> int", "int");
        fn("monotonic", "time.monotonic() -> float  # for measuring intervals", "float");
        fn("perf_counter", "time.perf_counter() -> float", "float");
        fn("process_time", "time.process_time() -> float", "float");
        fn("strftime", "time.strftime(format, t=None) -> str  # '%H:%M:%S'", "str");
        fn("strptime", "time.strptime(string, format)", "");
        fn("localtime", "time.localtime(seconds=None)", "");
        fn("gmtime", "time.gmtime(seconds=None)", "");
        fn("mktime", "time.mktime(t) -> float", "float");
        fn("ctime", "time.ctime(seconds=None) -> str", "str");
        fn("asctime", "time.asctime(t=None) -> str", "str");

        module("random");
        fn("random", "random.random() -> float  # [0, 1)", "float");
        fn("randint", "random.randint(a, b) -> int  # inclusive", "int");
        fn("randrange", "random.randrange(start, stop=None, step=1) -> int", "int");
        fn("uniform", "random.uniform(a, b) -> float", "float");
        fn("gauss", "random.gauss(mu, sigma) -> float", "float");
        fn("choice", "random.choice(seq)", "");
        fn("choices", "random.choices(population, weights=None, *, k=1)", "list");
        fn("sample", "random.sample(population, k)", "list");
        fn("shuffle", "random.shuffle(x)  # in place", "");
        fn("seed", "random.seed(a=None)", "");
        fn("getrandbits", "random.getrandbits(k) -> int", "int");

        module("sys");
        c("argv", "argv: List[str]  # script arguments: sys.argv[1], sys.argv[2]...", "List[str]");
        fn("exit", "sys.exit(code=0)", "");
        c("stdout", "output stream (goes to chat)", "");
        c("stderr", "error stream", "");
        c("stdin", "input stream", "");
        c("version", "Python version", "str");
        c("platform", "platform", "str");
        c("path", "module search paths", "list");
        c("maxsize", "maximum int", "int");
        c("executable", "path to python", "str");

        module("re");
        fn("match", "re.match(pattern, string, flags=0)  # from the start of the string", "");
        fn("search", "re.search(pattern, string, flags=0)  # anywhere", "");
        fn("fullmatch", "re.fullmatch(pattern, string, flags=0)", "");
        fn("findall", "re.findall(pattern, string, flags=0) -> list", "list");
        fn("finditer", "re.finditer(pattern, string, flags=0)", "");
        fn("sub", "re.sub(pattern, repl, string, count=0) -> str", "str");
        fn("subn", "re.subn(pattern, repl, string, count=0)", "");
        fn("split", "re.split(pattern, string, maxsplit=0) -> list", "list");
        fn("compile", "re.compile(pattern, flags=0)", "");
        fn("escape", "re.escape(pattern) -> str", "str");
        c("IGNORECASE", "flag: ignore case", "int");
        c("MULTILINE", "flag: ^ and $ match on every line", "int");
        c("DOTALL", "flag: dot matches newline", "int");
        c("VERBOSE", "flag: verbose patterns with comments", "int");
        c("I", "= IGNORECASE", "int");
        c("M", "= MULTILINE", "int");
        c("S", "= DOTALL", "int");
        c("X", "= VERBOSE", "int");

        module("json");
        fn("loads", "json.loads(string)", "");
        fn("dumps", "json.dumps(obj, indent=None, ensure_ascii=True) -> str", "str");
        fn("load", "json.load(file)", "");
        fn("dump", "json.dump(obj, file, indent=None, ensure_ascii=True)", "");

        module("os");
        c("path", "os.path: join, exists, isfile, basename...", "<std:os.path>");
        c("environ", "environment variables", "dict");
        c("sep", "path separator", "str");
        c("name", "OS name", "str");
        fn("getcwd", "os.getcwd() -> str", "str");
        fn("listdir", "os.listdir(path='.') -> list", "list");
        fn("makedirs", "os.makedirs(path, exist_ok=False)", "");
        fn("remove", "os.remove(path)", "");
        fn("rename", "os.rename(src, dst)", "");
        fn("getenv", "os.getenv(key, default=None)", "");

        module("os.path");
        fn("join", "os.path.join(*parts) -> str", "str");
        fn("exists", "os.path.exists(path) -> bool", "bool");
        fn("isfile", "os.path.isfile(path) -> bool", "bool");
        fn("isdir", "os.path.isdir(path) -> bool", "bool");
        fn("basename", "os.path.basename(path) -> str", "str");
        fn("dirname", "os.path.dirname(path) -> str", "str");
        fn("abspath", "os.path.abspath(path) -> str", "str");
        fn("splitext", "os.path.splitext(path)", "");
        fn("getsize", "os.path.getsize(path) -> int", "int");
        fn("expanduser", "os.path.expanduser(path) -> str", "str");

        module("datetime");
        c("datetime", "class: datetime.datetime.now()", "<std:datetime.datetime>");
        c("date", "class: datetime.date.today()", "");
        c("timedelta", "timedelta(days=0, seconds=0, ...)", "");
        c("timezone", "timezone(offset)", "");

        module("datetime.datetime");
        fn("now", "datetime.now(tz=None)", "");
        fn("utcnow", "datetime.utcnow()", "");
        fn("today", "datetime.today()", "");
        fn("fromtimestamp", "datetime.fromtimestamp(seconds)", "");
        fn("strptime", "datetime.strptime(string, format)", "");
        fn("fromisoformat", "datetime.fromisoformat(string)", "");

        module("collections");
        c("deque", "deque(iterable=(), maxlen=None)", "");
        c("defaultdict", "defaultdict(default_factory)", "");
        c("Counter", "Counter(iterable)", "");
        c("OrderedDict", "OrderedDict()", "");
        c("namedtuple", "namedtuple(name, fields)", "");
        c("ChainMap", "ChainMap(*maps)", "");

        module("itertools");
        for (String s : new String[]{"count", "cycle", "repeat", "chain", "islice", "product", "permutations",
                "combinations", "groupby", "zip_longest", "accumulate", "takewhile", "dropwhile"}) {
            fn(s, "itertools." + s + "(...)", "");
        }

        module("threading");
        c("Thread", "Thread(target=..., args=(), daemon=False)", "");
        c("Lock", "Lock()", "");
        c("RLock", "RLock()", "");
        c("Event", "Event()  # set / wait / is_set / clear", "");
        c("Timer", "Timer(seconds, function)", "");
        c("Semaphore", "Semaphore(value=1)", "");
        fn("current_thread", "threading.current_thread()", "");
        fn("active_count", "threading.active_count() -> int", "int");

        module("statistics");
        fn("mean", "statistics.mean(data) -> float", "float");
        fn("median", "statistics.median(data)", "");
        fn("mode", "statistics.mode(data)", "");
        fn("stdev", "statistics.stdev(data) -> float", "float");
        fn("variance", "statistics.variance(data) -> float", "float");
        fn("pstdev", "statistics.pstdev(data) -> float", "float");

        module("string");
        c("ascii_letters", "'a-zA-Z'", "str");
        c("ascii_lowercase", "'a-z'", "str");
        c("ascii_uppercase", "'A-Z'", "str");
        c("digits", "'0123456789'", "str");
        c("punctuation", "punctuation marks", "str");
        c("whitespace", "whitespace characters", "str");

        type("str");
        for (String s : new String[]{"capitalize", "casefold", "lower", "upper", "title", "swapcase", "strip",
                "lstrip", "rstrip", "replace", "format", "zfill", "center", "ljust", "rjust", "removeprefix",
                "removesuffix", "join"}) fn(s, "str." + s + "(...) -> str", "str");
        for (String s : new String[]{"find", "rfind", "index", "count"}) fn(s, "str." + s + "(sub) -> int", "int");
        for (String s : new String[]{"startswith", "endswith", "isalnum", "isalpha", "isdigit", "islower",
                "isnumeric", "isspace", "isupper"}) fn(s, "str." + s + "(...) -> bool", "bool");
        fn("split", "str.split(sep=None, maxsplit=-1) -> List[str]", "List[str]");
        fn("rsplit", "str.rsplit(sep=None, maxsplit=-1) -> List[str]", "List[str]");
        fn("splitlines", "str.splitlines() -> List[str]", "List[str]");
        fn("partition", "str.partition(sep) -> (before, sep, after)", "tuple");
        fn("rpartition", "str.rpartition(sep) -> (before, sep, after)", "tuple");
        fn("encode", "str.encode(encoding='utf-8')", "");

        type("list");
        fn("append", "list.append(x)", "");
        fn("extend", "list.extend(iterable)", "");
        fn("insert", "list.insert(i, x)", "");
        fn("remove", "list.remove(x)", "");
        fn("pop", "list.pop(i=-1)", "");
        fn("clear", "list.clear()", "");
        fn("index", "list.index(x) -> int", "int");
        fn("count", "list.count(x) -> int", "int");
        fn("sort", "list.sort(*, key=None, reverse=False)", "");
        fn("reverse", "list.reverse()", "");
        fn("copy", "list.copy() -> list", "list");

        type("dict");
        fn("get", "dict.get(key, default=None)", "");
        fn("keys", "dict.keys()", "");
        fn("values", "dict.values()", "");
        fn("items", "dict.items()", "");
        fn("pop", "dict.pop(key[, default])", "");
        fn("popitem", "dict.popitem()", "");
        fn("setdefault", "dict.setdefault(key, default=None)", "");
        fn("update", "dict.update(other)", "");
        fn("clear", "dict.clear()", "");
        fn("copy", "dict.copy() -> dict", "dict");
        fn("fromkeys", "dict.fromkeys(keys, value=None)", "dict");

        type("set");
        for (String s : new String[]{"add", "remove", "discard", "pop", "clear", "update"}) fn(s, "set." + s + "(...)", "");
        for (String s : new String[]{"union", "intersection", "difference", "symmetric_difference", "copy"}) {
            fn(s, "set." + s + "(...) -> set", "set");
        }
        fn("issubset", "set.issubset(other) -> bool", "bool");
        fn("issuperset", "set.issuperset(other) -> bool", "bool");

        type("tuple");
        fn("count", "tuple.count(x) -> int", "int");
        fn("index", "tuple.index(x) -> int", "int");

        type("int");
        fn("bit_length", "int.bit_length() -> int", "int");
        fn("to_bytes", "int.to_bytes(length, byteorder)", "");

        type("float");
        fn("is_integer", "float.is_integer() -> bool", "bool");
        fn("as_integer_ratio", "float.as_integer_ratio()", "");
        fn("hex", "float.hex() -> str", "str");
    }

    static List<McApi.Member> membersOfBuiltin(String type) {
        if (type.equals("List[str]") || type.startsWith("List[") || type.equals("list")) return BUILTIN_TYPES.get("list");
        if (type.equals("Vector3f") || type.equals("BlockPos")) return BUILTIN_TYPES.get("tuple");
        if (type.equals("bool")) return BUILTIN_TYPES.get("int");
        return BUILTIN_TYPES.get(type);
    }
}
