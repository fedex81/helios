package omegadrive.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.helpers.MessageFormatter;

import java.util.HashSet;
import java.util.Set;

/**
 * Federico Berti
 * <p>
 * Copyright 2022
 */
public class LogHelper {

    private static Set<String> msgCache = new HashSet<>();

    public static Logger getLogger(String name) {
        return LoggerFactory.getLogger(name);
    }

    public static Logger getLogger(Class<?> c) {
        return LoggerFactory.getLogger(c.getSimpleName());
    }

    public static String formatMessage(String s, Object... o) {
        return MessageFormatter.arrayFormat(s, o).getMessage();
    }

    public static void logWarnOnce(Logger log, String str, Object... o) {
        String msg = formatMessage(str, o);
        if (msgCache.add(msg)) {
            log.warn(msg + " (ONCE)");
        }
    }

    public static void clear() {
        System.out.println("LogHelper: clearing msg cache, size: " + msgCache.size());
        msgCache.clear();
    }
}
