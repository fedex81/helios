/*
 * CheatCodeHelper
 * Copyright (c) 2018-2019 Federico Berti
 * Last modified: 07/04/19 16:01
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

package omegadrive.cart.cheat;

import omegadrive.bus.model.GenesisBusProvider;
import omegadrive.util.LogHelper;
import org.slf4j.Logger;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CheatCodeHelper {

    private static final Logger LOG = LogHelper.getLogger(CheatCodeHelper.class.getSimpleName());

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
                LOG.error("Invalid cheat code: {}", str);
            }
        } else {
            LOG.error("Invalid cheat code: {}", str);
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
        return code.getAddress() >= GenesisBusProvider.ADDRESS_RAM_MAP_START && code.getAddress() <= GenesisBusProvider.ADDRESS_UPPER_LIMIT;
    }

    public static boolean isRomPatch(BasicGenesisRawCode code) {
        return !isRamPatch(code);
    }
}
