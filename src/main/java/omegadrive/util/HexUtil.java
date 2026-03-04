package omegadrive.util;

import com.google.common.base.Ascii;
import org.slf4j.Logger;

import java.util.HexFormat;

/**
 * Federico Berti
 * <p>
 * Copyright 2026
 */
public class HexUtil {

    private static final Logger LOG = LogHelper.getLogger(HexUtil.class.getSimpleName());

    private static final int BYTES_PER_LINE = 0x10;


    public static void fillFormattedString(StringBuilder sb, byte[] data, boolean withAscii, boolean withByteIndex) {
        fillFormattedString(sb, data, 0, data.length, withAscii, withByteIndex);
    }

    public static void fillFormattedString(StringBuilder sb, byte[] data) {
        fillFormattedString(sb, data, true, true);
    }

    public static void fillFormattedString(StringBuilder sb, byte[] data, int start, int end) {
        fillFormattedString(sb, data, start, end, true, true);
    }

    public static void fillFormattedString(StringBuilder sb, byte[] data, int start, int end, boolean withAscii, boolean withByteIndex) {
        try {
            HexFormat hf = HexFormat.of().withSuffix(" ");
            if (withByteIndex) {
                sb.append(String.format("%8x", start)).append(": ");
            }
            int len = end - start;
            int startZero = start > 0 ? 0 : start; //zero based
            int endZero = startZero + len;
            for (int i = startZero; i < endZero; i += BYTES_PER_LINE) {
                int slen = Math.min(len, BYTES_PER_LINE);
                hf.formatHex(sb, data, i, i + slen).append("  ");
                if (withAscii) {
                    for (int j = i; j < i + slen; j++) {
                        sb.append(toAsciiChar(data[j])).append(" ");
                    }
                }
                if ((i - startZero) + BYTES_PER_LINE < len) {
                    sb.append("\n").append(String.format("%8x", i + BYTES_PER_LINE)).append(": ");
                }
            }
        } catch (Exception e) {
            LOG.error("Error", e);
            e.printStackTrace();
        }
    }

    public static char toAsciiChar(int val) {
        return val >= Ascii.SPACE && val < Ascii.MAX ? (char) val : '.';
    }

}
