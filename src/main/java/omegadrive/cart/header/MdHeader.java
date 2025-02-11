package omegadrive.cart.header;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Federico Berti
 * <p>
 * Copyright 2025
 */
public class MdHeader {

    public static Charset SHIFT_JIS;

    static {
        try {
            SHIFT_JIS = Charset.forName("SHIFT-JIS");
        } catch (Exception e) {
            System.out.println("Charset SHIFT-JIS not supported");
            SHIFT_JIS = null;
        }
    }

    public enum DeviceSupportField {
        J("3-button controller"),
        _6("6", "6-button controller"),
        _0("0", "Master System controller"),
        A("Analog joystick"),
        _4("4", "Multitap"),

        G("Lightgun"),

        L("Activator"),

        M("Mouse"),

        B("Trackball"),

        T("Tablet"),

        V("Paddle"),

        K("Keyboard or keypad"),

        R("RS-232"),

        P("Printer"),

        C("CD-ROM (Sega CD)"),

        F("Floppy drive"),

        D("Download?");

        public final String code;
        public final String explain;

        DeviceSupportField(String name) {
            this.code = name();
            this.explain = name;
        }

        DeviceSupportField(String code, String name) {
            this.code = code;
            this.explain = name;
        }

        public static Optional<DeviceSupportField> getDeviceMappingIfAny(String s) {
            Optional<DeviceSupportField> dsf = Optional.empty();
            try {
                dsf = Optional.of(DeviceSupportField.valueOf(s));
            } catch (Exception e) {
                try {
                    dsf = Optional.of(DeviceSupportField.valueOf("_" + s));
                } catch (Exception e1) { //DO NOTHING
                }
            }
            return dsf;
        }
    }

    //from https://plutiedev.com/rom-header
    public enum MdRomHeaderField {
        SYSTEM_TYPE(0x100, 16),
        COPYRIGHT_RELEASE_DATE(0x110, 16),
        TITLE_DOMESTIC(0x120, 48),
        TITLE_OVERSEAS(0x150, 48),
        SERIAL_NUMBER(0x180, 14),
        ROM_CHECKSUM(0x18E, 2, true),
        DEVICE_SUPPORT(0x190, 16),
        ROM_ADDR_RANGE(0x1A0, 8, true),
        RAM_ADDR_RANGE(0x1A8, 8, true),
        EXTRA_MEMORY(0x1B0, 12),
        MODEM_SUPPORT(0x1BC, 12),
        RESERVED1(0x1C8, 40),
        REGION_SUPPORT(0x1F0, 3),
        RESERVED2(0x1F3, 13);

        static final HexFormat hf = HexFormat.of().withSuffix(" ");

        public final int startOffset;
        public final int len;
        public final boolean rawNumber;

        MdRomHeaderField(int so, int l) {
            this(so, l, false);
        }

        MdRomHeaderField(int so, int l, boolean rn) {
            startOffset = so;
            len = l;
            rawNumber = rn;
        }

        public String getValue(byte[] data) {
            if (this == EXTRA_MEMORY) {
                return extraMemStr(data);
            }
            if (this == TITLE_DOMESTIC) {
                return titleDomesticStr(data);
            }
            return rawNumber
                    ? hf.formatHex(data, startOffset, startOffset + len).trim()
                    : new String(data, startOffset, len, StandardCharsets.US_ASCII);
        }

        public String getStringView(byte[] data) {
            return this + ": " + getValue(data);
        }

        private static String titleDomesticStr(byte[] data) {
            String s1 = new String(data, TITLE_DOMESTIC.startOffset, TITLE_DOMESTIC.len);
            if (SHIFT_JIS != null) {
                String s2 = new String(data, TITLE_DOMESTIC.startOffset, TITLE_DOMESTIC.len, SHIFT_JIS);
                if (!s1.equals(s2)) {
                    s1 = s2;
                }
            }
            return s1;
        }


        private static String extraMemStr(byte[] data) {
            int skipRaOffset = 2;
            String s = new String(data, EXTRA_MEMORY.startOffset, skipRaOffset) + " ";
            if (s.trim().isEmpty()) {
                skipRaOffset = 0;
            }
            s += hf.formatHex(data, EXTRA_MEMORY.startOffset + skipRaOffset,
                    EXTRA_MEMORY.startOffset + EXTRA_MEMORY.len).trim();
            return s;
        }
    }
}
