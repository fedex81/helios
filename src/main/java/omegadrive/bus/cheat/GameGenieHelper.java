package omegadrive.bus.cheat;

import java.util.regex.Pattern;

/**
 * ${FILE}
 * <p>
 *
 * @author: Federico Berti
 * Copyright 2018
 * <p>
 * Adapted from:
 * http://games.technoplaza.net/ggencoder/java/
 * @author: John David Ratliff
 */
public class GameGenieHelper {

    /**
     * Game Genie alphabet for Genesis codes.
     */
    public static final char[] GENESIS_ALPHABET = {
            'A', 'B', 'C', 'D', 'E',
            'F', 'G', 'H', 'J', 'K',
            'L', 'M', 'N', 'P', 'R',
            'S', 'T', 'V', 'W', 'X',
            'Y', 'Z', '0', '1', '2',
            '3', '4', '5', '6', '7',
            '8', '9'
    };

    public static final Pattern GENESIS_GG_PATTERN = Pattern.compile("^([A-Za-z0-9]{1,4})-([A-Za-z0-9]{1,4})$");

    public static boolean isValidGGLine(String line) {
        if (line.contains("-") && line.length() >= 9) {
            return GENESIS_GG_PATTERN.matcher(line.substring(0, 9)).matches();
        }
        return false;
    }

    /**
     * Checks a code to tell if it's valid.
     *
     * @param code The coe to check.
     * @return true if the code is a valid Genesis game genie code, false
     * otherwise.
     */
    public static boolean isValidCode(String code) {
        code = code.toUpperCase();

        int length = code.length();

        if (length != 9) {
            return false;
        }

        if (code.charAt(4) != '-') {
            return false;
        }

        code = code.substring(0, 4) + code.substring(5);
        --length;

        char[] alphabet = GENESIS_ALPHABET;

        for (int i = 0; i < length; i++) {
            boolean found = false;

            for (int j = 0; j < alphabet.length; j++) {
                if (code.charAt(i) == alphabet[j]) {
                    found = true;
                    j = alphabet.length;
                }
            }

            if (!found) {
                return false;
            }
        }

        return true;
    }

    /**
     * Decodes a Genesis game genie code.
     *
     * @param ggcode The Genesis GameGenieCode to decode.
     * @return The Genesis code in "raw" format.
     */
    public static BasicGenesisRawCode decode(String ggcode) {
        int length = ggcode.length();

        if (length != 9) {
            throw new IllegalArgumentException(ggcode);
        }

        // remove the -
        ggcode = ggcode.substring(0, 4) + ggcode.substring(5);
        --length;

        long bitstring = 0;

        for (int i = 0; i < length; i++) {
            bitstring <<= 5;
            bitstring |= toHex(ggcode.charAt(i));
        }

        int value;
        int address;
        int temp;

        // position abcd
        value = (int) (((bitstring >> 7) & 0xE) | ((bitstring >> 15) & 0x1));

        // position efgh
        temp = (int) (((bitstring >> 11) & 0xE) | ((bitstring >> 11) & 0x1));
        value <<= 4;
        value |= temp;

        // position ijklmnop
        temp = (int) (bitstring >> 32);
        value <<= 8;
        value |= temp;

        // a-p = value, a-x = addy
        // ijkl mnop IJKL MNOP ABCD EFGH defg habc QRST UVWX
        // position ABCDEFGH
        address = (int) ((bitstring >> 16) & 0xFF);

        // position IJKLMNOP
        temp = (int) ((bitstring >> 24) & 0xFF);
        address <<= 8;
        address |= temp;

        // position QRSTUVWX
        temp = (int) (bitstring & 0xFF);
        address <<= 8;
        address |= temp;

        return new BasicGenesisRawCode(address, value);
    }

    public static String encode(BasicGenesisRawCode code) {
        int temp;
        long genie;
        int value = code.getValue();
        int address = code.getAddress();

        // position ijkl
        genie = (value & 0xF0) >> 4;

        // position mnop
        temp = (value & 0xF);
        genie <<= 4;
        genie |= temp;

        // position IJKL
        temp = (address & 0xF000) >> 12;
        genie <<= 4;
        genie |= temp;

        // position MNOP
        temp = (address & 0xF00) >> 8;
        genie <<= 4;
        genie |= temp;

        // position ABCD
        temp = (address & 0xF00000) >> 20;
        genie <<= 4;
        genie |= temp;

        // position EFGH
        temp = (address & 0xF0000) >> 16;
        genie <<= 4;
        genie |= temp;

        // position defg
        temp = ((value & 0x1000) >> 9) | ((value & 0xE00) >> 9);
        genie <<= 4;
        genie |= temp;

        // position habc
        temp = ((value & 0x100) >> 5) | ((value & 0xE000) >> 13);
        genie <<= 4;
        genie |= temp;

        // position QRST
        temp = (address & 0xF0) >> 4;
        genie <<= 4;
        genie |= temp;

        // position UVWX
        temp = (address & 0xF);
        genie <<= 4;
        genie |= temp;

        String ggcode = "";
        char[] alphabet = GENESIS_ALPHABET;

        for (int i = 0; i < 8; i++) {
            if (i == 4) {
                ggcode = "-" + ggcode;
            }

            ggcode = alphabet[(int) ((genie >> (i * 5)) & 0x1F)] + ggcode;
        }

        return ggcode;
    }

    /**
     * Translates a game genie letter to hexadecimal.
     *
     * @param letter The letter to translate.
     * @return The hex value of the letter.
     */
    private static int toHex(char letter) throws IllegalArgumentException {
        letter = Character.toUpperCase(letter);

        char[] alphabet = GENESIS_ALPHABET;

        for (int i = 0; i < alphabet.length; i++) {
            if (alphabet[i] == letter) {
                return i;
            }
        }

        throw new IllegalArgumentException();
    }
}
