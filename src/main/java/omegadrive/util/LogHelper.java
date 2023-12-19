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

    private Set<String> msgCache = new HashSet<>();

    private RepeaterDetector rd = new RepeaterDetector();

    private static Set<String> msgCacheShared = new HashSet<>();

    public static Logger getLogger(String name) {
        return LoggerFactory.getLogger(name);
    }

    public static Logger getLogger(Class<?> c) {
        return LoggerFactory.getLogger(c.getSimpleName());
    }

    public static String formatMessage(String s, Object... o) {
        return MessageFormatter.arrayFormat(s, o).getMessage();
    }

    public static void logWarnOnceSh(Logger log, String str, Object... o) {
        String msg = formatMessage(str, o);
        if (msgCacheShared.add(msg)) {
            log.warn(msg + " (ONCE)");
        }
    }

    public void logWarnOnce(Logger log, String str, Object... o) {
        String msg = formatMessage(str, o);
        if (msgCache.add(msg)) {
            log.warn(msg + " (ONCE)");
        } else {
            checkRepeat(log, msg);
        }
    }

    private void checkRepeat(Logger log, String msg) {
        if (rd.msg.equals(msg)) {
            boolean logRepeat = rd.hit();
            if (logRepeat) {
                log.warn(msg + " - RP: " + rd.cnt);
            }
        } else {
            rd.reset();
            rd.msg = msg;
        }
    }

    public void clear() {
        System.out.println("LogHelper: clearing msg cache, size: " + msgCache.size());
        msgCache.clear();
        rd.reset();
    }

    private static class RepeaterDetector {
        public String msg = "";
        public long cnt = 0;
        public long nextLog = 10;

        public boolean hit() {
            if ((++cnt % nextLog) == 0) {
                nextLog *= 10;
                return true;
            }
            return false;
        }

        public void reset() {
            msg = "";
            cnt = 0;
            nextLog = 10;
        }
    }
}
