package mcd.util;

/**
 * Federico Berti
 * <p>
 * Copyright 2024
 */
public class FixedPointUtil {

    public static double convertFixedPoint(int intPartDigits, int fractPartDigits, boolean signed, int val) {
        assert intPartDigits + fractPartDigits == 16;
        int fractMax = 1 << fractPartDigits;
        int fractMask = fractMax - 1;
        int intMask = (1 << (intPartDigits - (signed ? 1 : 0))) - 1;
        int intPart = (val >>> fractPartDigits) & intMask;
        double fpart = 1.0 * (val & fractMask) / fractMax;
        double res = intPart + fpart;
        if (signed && val != (short) val) {
            res *= -1;
        }
        return res;
    }

    public static double convert5p11FixedPointSigned(int val) {
        return convertFixedPoint(5, 11, true, val);
    }

    public static double convert13p3FixedPointUnsigned(int val) {
        return convertFixedPoint(13, 3, false, val);
    }
}
