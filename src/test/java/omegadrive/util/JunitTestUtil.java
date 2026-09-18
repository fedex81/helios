package omegadrive.util;

import org.junit.jupiter.api.Assertions;

/**
 * Federico Berti
 * <p>
 * Copyright 2026
 */
public class JunitTestUtil {

    public static void assertEquals(String s, Object o1, Object o2) {
        Assertions.assertEquals(o1, o2, s);
    }

    public static void assertEquals(String s, byte o1, byte o2) {
        Assertions.assertEquals(o1, o2, s);
    }

    public static void assertEquals(String s, int o1, int o2) {
        Assertions.assertEquals(o1, o2, s);
    }

    public static void assertEquals(String s, boolean o1, boolean o2) {
        Assertions.assertEquals(o1, o2, s);
    }

    public static void assertTrue(String s, boolean b) {
        Assertions.assertTrue(b, s);
    }
}
