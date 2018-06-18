/*
 * NoiseGenerator.java
 *
 *  This file is part of JavaGear.
 *
 *  JavaGear is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation; either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  JavaGear is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with JavaGear; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package omegadrive.sound.psg;

/**
 * Texas SN76496 noise generator emulation.
 *
 * @author Copyright (C) 2002-2003 Chris White
 * @version 18th January 2003
 * @see "JavaGear Final Project Report"
 */
public final class NoiseGenerator {

    private static final int[] PARITY = {1, 0, 0, 1, 0, 1, 1, 0, 0, 1, 1, 0, 1, 0, 0, 1};

    /**
     * Shift Register Reset Value SMS Specific (Bit 13).
     */
    private static final int RESET = 0x4000;

    /**
     * White Noise (values from Maxim).
     */
    private static final int WHITE_NOISE = 0x09;

    /**
     * Sync/Periodic Noise (values from Maxim).
     */
    private static final int SYNC_NOISE = 0x01;

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
     * PSG Cycles Per Sample.
     */
    private double psgCycles;

    /**
     * Channel Enabled.
     */
    private boolean enabled;

    /**
     * Noise Source.
     */
    private int shift;

    private int tappedBits;

    /**
     * Pointer to Noise.
     */
    private int noise;

    /**
     * Pointer to Channel 2 / Tone Generator 3.
     */
    private ToneGenerator chan2;

    /**
     * Use Channel 2's Frequency.
     */
    private boolean useChan2Freq;


    /**
     * NoiseGenerator Constructor.
     *
     * @param clockSpeed Clock Speed (Hz)
     * @param sampleRate Sample Rate (Hz)
     * @param t          Pointer to Tone Generator
     */
    public NoiseGenerator(double clockSpeed, int sampleRate, ToneGenerator t) {
        this.chan2 = t;
        psgCycles = (clockSpeed / sampleRate) * 2;
        reset();
    }


    /**
     * Reset Noise Generator to Default Values.
     */
    public void reset() {
        tappedBits = WHITE_NOISE;
        volume = 0x0F;
        frequency = 1;
        firstByte = 0;
        counter = 0;
        noise = RESET;
        amplitudeFlipFlop = false;
        useChan2Freq = false;
        enabled = true;
    }

    /**
     * Toggle channel On/Off.
     */
    public void setEnabled() {
        enabled = !enabled;
    }

    /**
     * Set channel frequency.
     *
     * @param f frequency.
     */
    public void setFrequency(int f) {
        // Bit 2 Selects White/Sync noise
        if ((f & 0x04) == 0x04) {
            tappedBits = WHITE_NOISE;
        } else {
            tappedBits = SYNC_NOISE;
        }

        // Reset shift register to preset
        shift = RESET;

        switch (f & 0x03) {
            case 0x00:
                frequency = 0x10; // 16
                useChan2Freq = false;
                break;
            case 0x01:
                frequency = 0x20; // 32
                useChan2Freq = false;
                break;
            case 0x02:
                frequency = 0x40; // 64
                useChan2Freq = false;
                break;
            // Channel 2 Frequency
            case 0x03:
                frequency = chan2.getFrequency();
                useChan2Freq = true;
                break;
            default:
                break;
        }
    }


    /**
     * Return a single sample from noise generator.
     *
     * @return sample (0 to +15)
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

            // Update Frequency from Channel 2
            if (useChan2Freq) {
                frequency = chan2.getFrequency();
            }

            // Reload counter
            while (counter < 0) {
                if (frequency > 0) {
                    counter += frequency;
                } else {
                    counter = 1; // so we don't get stuck in an endless loop
                }
            }

            // We only want to do this once per cycle
            if (amplitudeFlipFlop) {
                shift = (shift >> 1) | (PARITY[(shift & tappedBits) & 0x0F] << 15);
            }
        }

        if ((shift & 0x01) == 0x00) {
            return 0; // Noise channel does not output negative values
        } else {
            return (15 - (volume & 0x0f)); // ie 0 is 15, 1 is 14 etc
        }
    }

    /**
     * Returns the value of property <code>volume</code>. The channel's volume.
     *
     * @return the value of property <code>volume</code>. The channel's volume.
     */
    public int getVolume() {
        return this.volume;
    }

    /**
     * Sets the value of property <code>volume</code>. The channel's volume.
     *
     * @param volume the new value of property <code>volume</code>.
     */
    public void setVolume(int volume) {
        this.volume = volume;
    }

}
