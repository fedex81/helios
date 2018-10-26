package omegadrive.bus.cheat;

/**
 * ${FILE}
 * <p>
 * Federico Berti
 * <p>
 * Copyright 2018
 */
public class BasicGenesisRawCode {

    public static BasicGenesisRawCode INVALID_CODE = new BasicGenesisRawCode(-1, -1);

    private int address;
    private int value;

    /**
     * Creates a new BasicGenesisRawCode object.
     *
     * @param address This code's address.
     * @param value   This code's value.
     */
    public BasicGenesisRawCode(int address, int value) {
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
        String hex = Integer.toHexString(number).toUpperCase();

        while (hex.length() < minLength) {
            hex = "0" + hex;
        }

        return hex;
    }


    /**
     * Returns a String representation of this code.
     *
     * @return A String representation.
     */
    public String toString() {
        return "GenesisRawCode[" + this.toHexString(this.getValue(), 4) +
                ":" + this.toHexString(this.getAddress(), 6) + "]";
    }
}
