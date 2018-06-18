/*
 * ToneGenerator.java
 *
 * This file is part of JavaGear.
 *
 * JavaGear is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * JavaGear is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with JavaGear; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package omegadrive.sound.psg;

/**
 * Texas SN76496 Tone Generator Emulation.
 *
 * @author Copyright (C) 2002-2003 Chris White
 * @version 18th January 2003
 * @see "JavaGear Final Project Report"
 */
public final class ToneGenerator {

    /**
     * Volume of channel.
     */
    private int volume;

    /**
     * Frequency of channel.
     */
    private int frequency;

    /**
     * First byte of frequency.
     */
    private int firstByte;

    /**
     * Counter for square waves.
     */
    private double counter;

    /**
     * Positive/Negative output.
     */
    private boolean amplitudeFlipFlop;

    /**
     * PSG cycles per sample.
     */
    private double psgCycles;

    /**
     * Channel enabled.
     */
    private boolean enabled;


    /**
     * ToneGenerator Constructor.
     *
     * @param clockSpeed clock speed (Hz)
     * @param sampleRate sample rate (Hz)
     */
    public ToneGenerator(double clockSpeed, int sampleRate) {
        psgCycles = ((clockSpeed / sampleRate) * 2); // *2 as we cycle for half wave
        enabled = true;
        reset();
    }


    /**
     * Reset Tone Generator to Default Values.
     */
    public void reset() {
        amplitudeFlipFlop = true;
        volume = 0x0F;
        frequency = 1;
        firstByte = 0;
        counter = 0;
    }


    /**
     * Toggle Channel On/Off.
     */
    public void setEnabled() {
        enabled = !enabled;
    }

    /**
     * Set significant bits of frequency.
     *
     * @param value high byte.
     */
    public void setFreqSigf(int value) {
        frequency = firstByte | ((value & 0x3F) << 4);
    }

    /**
     * Return a single sample from Tone Generator.
     *
     * @return Sample (-15 to +15)
     */
    public int getSample() {
        if (!enabled) {
            return 0;
        }

        // Decrement Counter
        counter -= psgCycles;

        // Counter Expired
        if (counter < 0) {
            // Toggle Amplitude Flip Flop
            amplitudeFlipFlop = !amplitudeFlipFlop;

            // Reload counter
            while (counter < 0) {
                if (frequency > 0) {
                    counter += frequency;
                } else {
                    counter = 1; // so we don't get stuck in an endless loop
                }
            }
        }

        int outputVolume = 15 - (volume & 0x0F); // ie 0 is 15, 1 is 14 etc

        // Real SMS Only Outputs Values Over 4?
        if (frequency > 4) {
            if (amplitudeFlipFlop) {
                return +outputVolume;
            } else {
                return -outputVolume;
            }
        } else {
            return 0;
        }
    }

    /**
     * Getter for property <code>volume</code>. The channel's volume.
     *
     * @return Value of property <code>volume</code>. The channel's volume.
     */
    public int getVolume() {
        return this.volume;
    }

    /**
     * Setter for property <code>volume</code>. The channel's volume.
     *
     * @param volume New value of property <code>volume</code>. The channel's volume.
     */
    public void setVolume(int volume) {
        this.volume = volume;
    }

    /**
     * Getter for property <code>frequency</code>. The channel's frequency.
     *
     * @return Value of property <code>frequency</code>. The channel's frequency.
     */
    public int getFrequency() {
        return this.frequency;
    }

    /**
     * Setter for property <code>frequency</code>. The channel's frequency.
     *
     * @param frequency New value of property <code>frequency</code>. The channel's frequency.
     */
    public void setFrequency(int frequency) {
        this.frequency = frequency;
    }

    /**
     * Getter for property <code>firstByte</code>. First byte of frequency.
     *
     * @return Value of property <code>firstByte</code>. First byte of frequency.
     */
    public int getFirstByte() {
        return this.firstByte;
    }

    /**
     * Setter for property <code>firstByte</code>. First byte of frequency.
     *
     * @param firstByte New value of property <code>firstByte</code>. First byte of frequency.
     */
    public void setFirstByte(int firstByte) {
        this.firstByte = firstByte;
    }

}
