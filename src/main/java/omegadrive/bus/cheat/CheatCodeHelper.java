package omegadrive.bus.cheat;

import omegadrive.bus.BusProvider;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ${FILE}
 * <p>
 * Federico Berti
 * <p>
 * Copyright 2018
 */
public class CheatCodeHelper {

    private static Logger LOG = LogManager.getLogger(CheatCodeHelper.class.getSimpleName());

    /**
     * Pattern for recognizing Genesis Raw Codes.
     */
    public static final Pattern GENESIS_RAW_PATTERN = Pattern.compile("^([A-Fa-f0-9]{1,6}):([A-Fa-f0-9]{1,4})$");

    public static BasicGenesisRawCode parseCheatCode(String str) {
        BasicGenesisRawCode result = BasicGenesisRawCode.INVALID_CODE;
        if (GameGenieHelper.isValidGGLine(str) && GameGenieHelper.isValidCode(str.substring(0, 9))) {
            result = GameGenieHelper.decode(str.substring(0, 9));
        } else if (isValidRawCode(str)) {
            Matcher m = GENESIS_RAW_PATTERN.matcher(str.substring(0, 10));
            if (m.matches()) {
                int address = Integer.parseInt(m.group(1), 16);
                int value = Integer.parseInt(m.group(2), 16);
                result = new BasicGenesisRawCode(address, value);
            } else {
                LOG.error("Invalid cheat code: " + str);
            }
        } else {
            LOG.error("Invalid cheat code: " + str);
        }
        return result;
    }

    private static boolean isValidRawCode(String str) {
        if (str.contains(":") && str.length() >= 11) {
            return GENESIS_RAW_PATTERN.matcher(str.substring(0, 10)).matches();
        }
        return false;
    }

    public static boolean isRamPatch(BasicGenesisRawCode code) {
        return code.getAddress() >= BusProvider.ADDRESS_RAM_MAP_START && code.getAddress() <= BusProvider.ADDRESS_UPPER_LIMIT;
    }

    public static boolean isRomPatch(BasicGenesisRawCode code) {
        return !isRamPatch(code);
    }
}
