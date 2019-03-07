/*
 * SN76496.java
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

package omegadrive.sound.psg.white1;

import omegadrive.sound.psg.PsgProvider;
import omegadrive.util.SoundUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.sound.sampled.AudioFormat;
import java.io.FileWriter;
import java.io.IOException;

/**
 * Texas SN76496 Emulation.
 *
 * @author Copyright (C) 2002-2003 Chris White
 * @version 18th January 2003
 * @see "JavaGear Final Project Report"
 */
public final class SN76496 implements PsgProvider {

    private static Logger LOG = LogManager.getLogger(SN76496.class.getSimpleName());

    /**
     * Tone Generator 1.
     */
    private ToneGenerator chan0;

    /**
     * Tone Generator 2.
     */
    private ToneGenerator chan1;

    /**
     * Tone Generator 3.
     */
    private ToneGenerator chan2;

    /**
     * Noise Generator.
     */
    private NoiseGenerator chan3;

    /**
     * Pointer to current Tone Generator.
     */
    private ToneGenerator currentGenerator;

    /**
     * For Recording Sound to Disk.
     */
    private FileWriter fileWriter;

    /**
     * PSG Clock Speed.
     */
    private double clockSpeed;

    /**
     * Output Sample Rate.
     */
    private int sampleRate;

    /**
     * Samples to Generate per video frame.
     */
    private int samplesPerFrame;

    /**
     * Sound Enabled.
     */
    private boolean enabled;

    /**
     * Record Sound to Disk.
     */
    private boolean recording;

    private AudioFormat audioFormat;

    /**
     * SN76496 Constructor.
     *
     * @param c Clock Speed (Hz)
     * @param s Sample Rate (Hz)
     */
    public SN76496(double c, int s) {
        clockSpeed = c;
        sampleRate = s;

        chan0 = new ToneGenerator(clockSpeed, sampleRate);
        chan1 = new ToneGenerator(clockSpeed, sampleRate);
        chan2 = new ToneGenerator(clockSpeed, sampleRate);
        chan3 = new NoiseGenerator(clockSpeed, sampleRate, chan2);

        //AudioFormat(sample_rate(hz), sampleSizeInBits, channels, signed, bigEndian)
        audioFormat = new AudioFormat(sampleRate, PSG_OUTPUT_SAMPLE_SIZE, PSG_OUTPUT_CHANNELS, true, false);

        // Reset to Defaults
        reset();

        // Enabled by Default
        setEnabled(true);
    }


    /**
     * Reset SN76496 to Default Values.
     */
    public void reset() {
        currentGenerator = chan0;
        chan0.reset();
        chan1.reset();
        chan2.reset();
        chan3.reset();
    }


    /**
     * Returns <code>true</code> if this instance of <code>SN76496</code> is enabled and
     * <code>false</code> otherwise.
     *
     * @return <code>true</code> if enabled.
     */
    public boolean isEnabled() {
        return enabled;
    }


    /**
     * Toggle SN76496 On/Off.
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }


    /**
     * Toggle a particular channel On/Off.
     *
     * @param channel channel to toggle (0-3)
     * @throws IllegalArgumentException if the value of channels is not 1, 2 or 3.
     */
    public void setChannelEnabled(int channel) throws IllegalArgumentException {
        switch (channel) {
            case 0:
                chan0.setEnabled();
                break;
            case 1:
                chan1.setEnabled();
                break;
            case 2:
                chan2.setEnabled();
                break;
            case 3:
                chan3.setEnabled();
                break;
            default:
                throw new IllegalArgumentException("Invalid Channel: " + channel);
        }
    }


    @Override
    public void init() {
        reset();
    }

    /**
     * Program the PSG. Connected this procedure to a Z80 Port.
     *
     * @param value Value to write (0-0xFF)
     */
    public void write(int value) {
        if ((!enabled) && (!isRecording())) {
            return;
        }

        if ((value & 0x80) == 0x80) {
            switch ((value >> 4) & 0x07) {
                case 0x00: // 000 rr = 00 (Channel 0 Frequency)
                    chan0.setFirstByte(value & 0x0F);
                    currentGenerator = chan0;
                    break;
                case 0x01: // 001 rr = 00 (Channel 0 Volume)
                    chan0.setVolume(value & 0x0F);
                    currentGenerator = chan0;
                    break;
                case 0x02: // 010 rr = 01 (Channel 1 Frequency)
                    chan1.setFirstByte(value & 0x0F);
                    currentGenerator = chan1;
                    break;
                case 0x03: // 011 rr = 01 (Channel 1 Volume)
                    chan1.setVolume(value & 0x0F);
                    currentGenerator = chan1;
                    break;
                case 0x04: // 100 rr = 10 (Channel 2 Frequency)
                    chan2.setFirstByte(value & 0x0F);
                    currentGenerator = chan2;
                    break;
                case 0x05: // 101 rr = 10 (Channel 2 Volume)
                    chan2.setVolume(value & 0x0F);
                    currentGenerator = chan2;
                    break;
                case 0x06: // 110 rr = 11 (Noise Channel Frequency)
                    chan3.setFrequency(value & 0x07);
                    currentGenerator = null;
                    break;
                case 0x07: // 111 rr = 11 (Noise Channel Volume)
                    chan3.setVolume(value & 0x0F);
                    currentGenerator = null;
                    break;
                default:
                    break;
            }
        } else if (currentGenerator != null) { // Set significant bits of frequency
            currentGenerator.setFreqSigf(value);
        } else {
            chan3.setFrequency(value & 0x07);
        }
    }

    @Override
    public void output(byte[] buffer, int offset, int end) {
        if (!enabled && !isRecording()) {
            return;
        }

        // Loop for length of this video frame
        for (int i = offset; i < end; i++) {
            // Sum the channel outputs
            int join = 0;
            join += chan0.getSample();
            join += chan1.getSample();
            join += chan2.getSample();
            join += chan3.getSample();

            // Scale volume up
            join <<= 1;

            // Check boundaries
            if (join > 0x7F) {
                join = 0x7F;
            } else if (join < -0x80) {
                join = -0x80;
            }

            buffer[i] = (byte) join;

            if (isRecording()) {
                try {
                    fileWriter.write(join & 0xff); // output 8 bit signed mono
                } catch (IOException ioe) {
                    LOG.error("An error occurred while writing the"
                            + " sound file.");
                }
            }
        }
    }

    /**
     * Convert PSG settings to Java Sound.
     */
    public void output(byte[] buffer) {
        output(buffer, 0, buffer.length);
    }

    /**
     * Toggle sound recording to WAV file.
     */
    public void setRecord() {
        if (isRecording()) {
            stopRecording();
        } else {
            startRecording();
        }
    }


    /**
     * Start sound recording to WAV file.
     */
    private void startRecording() {
        if (!isRecording()) {
            try {
                fileWriter = new FileWriter("output.raw");
            } catch (IOException ioe) {
                LOG.error("Could not open file for recording.");
                System.out.println("Could not open file for recording");
            }
            setRecording(true);
        }
    }


    /**
     * Stop sound recording to WAV file.
     */
    public void stopRecording() {
        if (isRecording()) {
            try {
                fileWriter.close();
                convertToWav();
            } catch (IOException ioe) {
                LOG.error("Failed whilst closing output.raw");
                System.out.println("Failed whilst closing output.raw");
            }
            setRecording(false);
        }
    }

    public boolean isRecording() {
        return recording;
    }


    private void setRecording(boolean recording) {
        this.recording = recording;
    }

    /**
     * Convert RAW output to WAV file.
     */
    private void convertToWav() {
        SoundUtil.convertToWav(audioFormat, "ouput.raw");
    }


    public static void main(String[] args) {
        AudioFormat audioFormat = new AudioFormat(11025, 8, 1, true, false);
        SoundUtil.convertToWav(audioFormat, "ouput.raw");
    }

}
