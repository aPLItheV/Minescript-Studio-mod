package dev.example.mse;

/**
 * Запуск скрипта средствами самого Minescript: вызывает его публичный метод
 * Minescript.onClientChat("\команда"), то же самое происходит, когда вы вводите команду в чате.
 * Минескрипт подключается через рефлексию, поэтому мод работает и без него (запуск просто не сработает).
 */
final class MinescriptBridge {
    private MinescriptBridge() {}

    /** @return true, если Minescript принял команду */
    static boolean run(String command) {
        try {
            Class<?> c = Class.forName("net.minescript.common.Minescript");
            Object r = c.getMethod("onClientChat", String.class).invoke(null, "\\" + command);
            return Boolean.TRUE.equals(r);
        } catch (ReflectiveOperationException | LinkageError e) {
            return false;
        }
    }
}
