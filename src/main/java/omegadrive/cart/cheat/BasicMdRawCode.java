/*
 * BasicMdRawCode
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

import static omegadrive.util.Util.th;

public class BasicMdRawCode {

    public static final BasicMdRawCode INVALID_CODE = new BasicMdRawCode(-1, -1);

    private int address;
    private int value;

    /**
     * Creates a new BasicGenesisRawCode object.
     *
     * @param address This code's address.
     * @param value   This code's value.
     */
    public BasicMdRawCode(int address, int value) {
        this.setAddress(address);
        this.setValue(value);
    }

    /**
     * Sets the address of this code.
     *
     * @param address The code's address.
     */
    public void setAddress(int address) {
        // restrict to 24-bit addresses
        if ((address & 0xFF000000) == 0) {
            this.address = address;
        }
    }

    /**
     * Sets the value of this code.
     *
     * @param value The code's value.
     */
    public void setValue(int value) {
        // restrict to 16-bit values
        if ((value & 0xFFFF0000) == 0) {
            this.value = value;
        }
    }

    /**
     * Gets the address of this code.
     *
     * @return The code's address.
     */
    public int getAddress() {
        return this.address;
    }

    public int getValue() {
        return this.value;
    }

    public String toHexString(int number, int minLength) {
        StringBuilder hex = new StringBuilder(th(number).toUpperCase());

        while (hex.length() < minLength) {
            hex.insert(0, "0");
        }

        return hex.toString();
    }


    /**
     * Returns a String representation of this code.
     *
     * @return A String representation.
     */
    public String toString() {
        return "BasicMdRawCode[" + this.toHexString(this.value, 4) +
                ":" + this.toHexString(this.address, 6) + "]";
    }
}
