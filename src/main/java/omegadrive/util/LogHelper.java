/*
 * LogHelper
 * Copyright (c) 2018-2019 Federico Berti
 * Last modified: 17/10/19 11:02
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package omegadrive.util;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.message.ParameterizedMessage;

import java.util.Objects;

public class LogHelper {

    public final static boolean printToSytemOut = false;


    public static void printLevel(Logger LOG, Level level, String str, long arg1, long arg2, long arg3, boolean verbose) {
        if (verbose) {
            ParameterizedMessage pm = new ParameterizedMessage(str, Long.toHexString(arg1),
                    Long.toHexString(arg2), Long.toHexString(arg3));
            logParamMessage(LOG, level, pm);
        }
    }

    public static void printLevel(Logger LOG, Level level, String str, long arg1, long arg2, long arg3, long arg4,
                                  Object arg5, boolean verbose) {
        if (verbose) {
            ParameterizedMessage pm = new ParameterizedMessage(str, Long.toHexString(arg1),
                    Long.toHexString(arg2), Long.toHexString(arg3), Long.toHexString(arg4), Objects.toString(arg5));
            logParamMessage(LOG, level, pm);
        }
    }

    public static void printLevel(Logger LOG, Level level, String str, long arg1, long arg2, long arg3, long arg4, boolean verbose) {
        if (verbose) {
            ParameterizedMessage pm = new ParameterizedMessage(str, Long.toHexString(arg1),
                    Long.toHexString(arg2), Long.toHexString(arg3), Long.toHexString(arg4));
            logParamMessage(LOG, level, pm);
        }
    }

    public static void printLevel(Logger LOG, Level level, String str, Object arg1, long arg2, boolean verbose) {
        if (verbose) {
            ParameterizedMessage pm = new ParameterizedMessage(str, Objects.toString(arg1),
                    Long.toHexString(arg2));
            logParamMessage(LOG, level, pm);
        }
    }

    public static void printLevel(Logger LOG, Level level, String str, Object arg1, long arg2, long arg3, boolean verbose) {
        if (verbose) {
            ParameterizedMessage pm = new ParameterizedMessage(str, Objects.toString(arg1),
                    Long.toHexString(arg2), Long.toHexString(arg3));
            logParamMessage(LOG, level, pm);
        }
    }

    public static void printLevel(Logger LOG, Level level, String str, long arg1, long arg2, Object arg3, boolean verbose) {
        if (verbose) {
            ParameterizedMessage pm = new ParameterizedMessage(str, Long.toHexString(arg1),
                    Long.toHexString(arg2), arg3);
            logParamMessage(LOG, level, pm);
        }
    }

    public static void printLevel(Logger LOG, Level level, String str, long arg1, long arg2, boolean verbose) {
        if (verbose) {
            ParameterizedMessage pm = new ParameterizedMessage(str, Long.toHexString(arg1),
                    Long.toHexString(arg2));
            logParamMessage(LOG, level, pm);
        }
    }

    public static void printLevel(Logger LOG, Level level, String str, long arg, boolean verbose) {
        if (verbose) {
            printLevel(LOG, level, str, Long.toHexString(arg));
        }
    }

    public static void printLevel(Logger LOG, Level level, String str, double arg1, double arg2, boolean verbose) {
        if (verbose) {
            ParameterizedMessage pm = new ParameterizedMessage(str, arg1, arg2);
            logParamMessage(LOG, level, pm);
        }
    }

    public static void printLevel(Logger LOG, Level level, Object str, boolean verbose) {
        if (verbose) {
            logMessage(LOG, level, Objects.toString(str));
        }
    }

    public static void printLevel(Logger LOG, Level level, String str, boolean arg1, int arg2, int arg3, boolean verbose) {
        if (verbose) {
            ParameterizedMessage pm = new ParameterizedMessage(str, arg1, arg2, arg3);
            logParamMessage(LOG, level, pm);
        }
    }

    public static void printLevel(Logger LOG, Level level, String str, boolean arg1, int arg2, boolean verbose) {
        if (verbose) {
            ParameterizedMessage pm = new ParameterizedMessage(str, arg1, arg2);
            logParamMessage(LOG, level, pm);
        }
    }

    public static void printLevel(Logger LOG, Level level, String str, int arg1, boolean verbose) {
        if (verbose) {
            ParameterizedMessage pm = new ParameterizedMessage(str, arg1);
            logParamMessage(LOG, level, pm);
        }
    }

    public static void printLevel(Logger LOG, Level level, String str, Object arg, boolean verbose) {
        if (verbose) {
            printLevel(LOG, level, str, arg);
        }
    }


    private static void printLevel(Logger LOG, Level level, String str, Object arg) {
        ParameterizedMessage pm = new ParameterizedMessage(str, arg);
        logParamMessage(LOG, level, pm);
    }

    private static void logMessage(Logger LOG, Level level, String msg) {
        LOG.log(level, msg);
        if (printToSytemOut) {
            System.out.println(msg);
        }
    }

    private static void logParamMessage(Logger LOG, Level level, ParameterizedMessage pm) {
        LOG.log(level, pm);
        if (printToSytemOut) {
            System.out.println(pm.getFormattedMessage());
        }
    }
}
