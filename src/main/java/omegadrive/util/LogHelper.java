package omegadrive.util;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.message.ParameterizedMessage;

/**
 * ${FILE}
 * <p>
 * Federico Berti
 * <p>
 * Copyright 2018
 */
public class LogHelper {

    private static Logger LOG = LogManager.getLogger(LogHelper.class.getSimpleName());


    public static void printLevel(Logger LOG, Level level, String str, long arg1, long arg2, long arg3, boolean verbose) {
        if (verbose) {
            LOG.log(level, new ParameterizedMessage(str, Long.toHexString(arg1),
                    Long.toHexString(arg2), Long.toHexString(arg3)));
        }
    }

    public static void printLevel(Logger LOG, Level level, String str, long arg1, long arg2, long arg3, long arg4, boolean verbose) {
        if (verbose) {
            LOG.log(level, new ParameterizedMessage(str, Long.toHexString(arg1),
                    Long.toHexString(arg2), Long.toHexString(arg3), Long.toHexString(arg4)));
        }
    }

    public static void printLevel(Logger LOG, Level level, String str, long arg1, long arg2, Object arg3, boolean verbose) {
        if (verbose) {
            LOG.log(level, new ParameterizedMessage(str, Long.toHexString(arg1),
                    Long.toHexString(arg2), arg3));
        }
    }

    public static void printLevel(Logger LOG, Level level, String str, long arg1, long arg2, boolean verbose) {
        if (verbose) {
            LOG.log(level, new ParameterizedMessage(str, Long.toHexString(arg1),
                    Long.toHexString(arg2)));
        }
    }

    public static void printLevel(Logger LOG, Level level, String str, long arg, boolean verbose) {
        if (verbose) {
            printLevel(LOG, level, str, Long.toHexString(arg));
        }
    }

    public static void printLevel(Logger LOG, Level level, String str, Object arg, boolean verbose) {
        if (verbose) {
            printLevel(LOG, level, str, arg);
        }
    }


    public static void printLevel(Logger LOG, Level level, String str, Object arg) {
        LOG.log(level, str, arg);
    }
}
