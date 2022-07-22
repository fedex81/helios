package omegadrive.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.helpers.MessageFormatter;

/**
 * Federico Berti
 * <p>
 * Copyright 2022
 */
public class LogHelper {

    public static Logger getLogger(String name) {
        return LoggerFactory.getLogger(name);
    }

    public static Logger getLogger(Class<?> c) {
        return LoggerFactory.getLogger(c.getSimpleName());
    }

    public static String formatMessage(String s, Object... o) {
        return MessageFormatter.arrayFormat(s, o).getMessage();
    }
}
