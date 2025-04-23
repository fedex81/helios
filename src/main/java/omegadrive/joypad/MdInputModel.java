package omegadrive.joypad;

import java.util.StringJoiner;
import java.util.function.BiFunction;
import java.util.function.Predicate;

import static omegadrive.util.Util.getBitFromByte;

/**
 * Federico Berti
 * <p>
 * Copyright 2025
 */
public class MdInputModel {


    public static final int DATA_TH_LOW = 0, DATA_TH_HIGH = 0x40, TH_MASK = 0x40, CTRL_PIN_INPUT = 0, CTRL_PIN_OUTPUT = 0x40;

    public static final Predicate<Integer> isCtrlThInput = v -> (v & TH_MASK) == CTRL_PIN_INPUT;
    public static final Predicate<Integer> isDataThHigh = v -> (v & TH_MASK) == DATA_TH_HIGH;


    /**
     * 0 TH = 1 : ?1CBRLDU    3-button pad return value (default state)
     * 1 TH = 0 : ?0SA00DU    3-button pad return value (D3,D2 are forced to 0, indicates the presence of a controller)
     * 2 TH = 1 : ?1CBRLDU    3-button pad return value
     * 3 TH = 0 : ?0SA00DU    3-button pad return value
     * 4 TH = 1 : ?1CBRLDU    3-button pad return value
     * 5 TH = 0 : ?0SA0000    D3-0 are forced to '0' (indicate 6 buttons)
     * 6 TH = 1 : ?1CBMXYZ    Extra buttons returned in D3-0
     * 7 TH = 0 : ?0SA1111    D3-0 are forced to '1'
     * (0 TH = 1 : ?1CBRLDU    3-button pad return value) (default state)
     */
    public enum SixButtonState {
        CBRLDU_0,
        SA00DU_1,
        CBRLDU_2,
        SA00DU_3,
        CBRLDU_4,
        SA0000_5,
        CBMXYZ_6,
        SA1111_7;

        public static final SixButtonState[] vals = SixButtonState.values();
    }

    public static class MdPadContext {
        int control = 0, //SGDK needs 0 here, otherwise it is considered a RESET
                data, readMask, readStep;
        final int player;
        int latestWriteCycleCounter;

        MdPadContext(int p) {
            this.player = p;
        }

        @Override
        public String toString() {
            return new StringJoiner(", ", MdPadContext.class.getSimpleName() + "[", "]")
                    .add("control=" + control)
                    .add("data=" + data)
                    .add("readMask=" + readMask)
                    .add("readStep=" + readStep)
                    .add("player=" + player)
                    .add("latestWriteCycleCounter=" + latestWriteCycleCounter)
                    .toString();
        }
    }

    /**
     * ID	Peripheral
     * 1111 ($0F)	(undetectable)
     * 1101 ($0D)	Mega Drive controller
     * 1100 ($0C)	Mega Drive controller (see note)
     * 1011 ($0B)	Saturn controller
     * 1010 ($0A)	Printer
     * 0111 ($07)	Sega multitap
     * 0101 ($05)	Other Saturn peripherals
     * 0011 ($03)	Mouse
     * 0001 ($01)	Justifier
     * 0000 ($00)	Menacer
     */
    public enum PeripheralId {
        MENACER,
        JUSTIFIER,
        NONE2(false),
        MOUSE,
        NONE4(false),
        SATURN_OTHER,
        NONE6(false),
        SEGA_MULTITAP,
        NONE8(false), NONE9(false),
        PRINTER,
        SATURN_PAD,
        MD_PAD11,
        MD_PAD,
        NONE15(false),
        UNDETECTABLE(false);
        public final boolean valid;

        PeripheralId(boolean v) {
            this.valid = v;
        }

        PeripheralId() {
            this(true);
        }
    }

    //see MdJoypadTest::testPeripheralId
    public static final BiFunction<Integer, Integer, PeripheralId> toPeripheralId = (b1, b2) -> {
        final byte res1 = (byte) b1.intValue();
        final byte res2 = (byte) b2.intValue();
        int val = ((getBitFromByte(res1, 3) | getBitFromByte(res1, 2)) << 3) |
                ((getBitFromByte(res1, 1) | getBitFromByte(res1, 0)) << 2) |
                ((getBitFromByte(res2, 3) | getBitFromByte(res2, 2)) << 1) |
                ((getBitFromByte(res2, 1) | getBitFromByte(res2, 0)) << 0);
        return MdInputModel.PeripheralId.values()[val];
    };
}
