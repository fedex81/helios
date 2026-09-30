/*
 * SoundUtil
 * Copyright (c) 2018-2019 Federico Berti
 * Last modified: 05/10/19 14:15
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

package omegadrive.util;

import omegadrive.sound.SoundDevice.SoundDeviceType;
import omegadrive.sound.SoundProvider;
import omegadrive.sound.javasound.AbstractSoundManager;
import org.slf4j.Logger;

import javax.sound.sampled.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Map;

import static omegadrive.sound.SoundDevice.SoundDeviceType.*;

public class SoundUtil {

    private static final Logger LOG = LogHelper.getLogger(SoundUtil.class.getSimpleName());


    public static final AudioFormat AF_16bit_Mono =
            new AudioFormat(AbstractSoundManager.SAMPLE_RATE_HZ, 16, 1, true, false);
    public static final AudioFormat AF_16bit_Stereo =
            new AudioFormat(AbstractSoundManager.SAMPLE_RATE_HZ, 16, 2, true, false);
    public static final AudioFormat AF_8bit_Mono =
            new AudioFormat(AbstractSoundManager.SAMPLE_RATE_HZ, 8, 1, true, false);
    public static final AudioFormat AF_8bit_Stereo =
            new AudioFormat(AbstractSoundManager.SAMPLE_RATE_HZ, 8, 2, true, false);

    public static final byte ZERO_BYTE = 0;

    private static final int DEFAULT_PSG_SHIFT_BITS = 6;
    public static final double PSG_ATTENUATION = Double.parseDouble(System.getProperty("sound.psg.attenuation", "1.0"));
    private static final int USER_PSG_ATT_BITS;
    private static final int PSG_SHIFT_BITS;

    static {
        double res = 4; // start from 2 bit volume increase
        int shift = -2;
        while (res > PSG_ATTENUATION && shift < 16) {
            res /= 2;
            shift++;
        }
        USER_PSG_ATT_BITS = shift;
        PSG_SHIFT_BITS = Math.max(0, DEFAULT_PSG_SHIFT_BITS - USER_PSG_ATT_BITS);
        LOG.info("PSG attenuation: {}, in bits: {}", PSG_ATTENUATION, USER_PSG_ATT_BITS);
    }

    public static int writeBufferInternal(SourceDataLine line, byte[] buffer, int start, int end) {
        int res = 0;
        try {
            // Output Stream write(byte[] b, int off, int len)
            // Small buffer to avoid latency, but more intensive CPU usage
            res = line.write(buffer, start, end);
            if (res < (end - start)) {
                LOG.warn("bytes written: {}/{}", res, end - start);
            }
        } catch (IllegalArgumentException iae) {
            LOG.error("Error writing to the audio line. "
                    + "The bytes do not represent complete frames.");
        } catch (ArrayIndexOutOfBoundsException aiobe) {
            LOG.error("Error writing to the audio line. "
                    + "The buffer does not contain the number of bytes specified.");

        }
        return res;
    }

    public static int writeBufferInternal(SourceDataLine line, byte[] buffer, int samplesPerFrame) {
        return writeBufferInternal(line, buffer, 0, samplesPerFrame);
    }

    /*
     * gets the number of bytes needed to play the specified number of milliseconds
     * @see com.sun.media.sound.Toolkit
     */
    private static long millis2bytes(AudioFormat format, long millis) {
        long result = (long) (millis * format.getFrameRate() / 1000.0f * format.getFrameSize());
        return align(result, format.getFrameSize());
    }

    /*
     * returns bytes aligned to a multiple of blocksize
     * the return value will be in the range of (bytes-blocksize+1) ... bytes
     */
    static long align(long bytes, int blockSize) {
        // prevent null pointers
        if (blockSize <= 1) {
            return bytes;
        }
        return bytes - (bytes % blockSize);
    }

    //as in bytes for the underlying dataLine
    public static int getAudioLineBufferSize(AudioFormat audioFormat) {
        return (int) millis2bytes(audioFormat, SoundProvider.AUDIO_BUFFER_LEN_MS);
    }

    public static int getMonoSamplesBufferSize(AudioFormat audioFormat) {
        return getAudioLineBufferSize(audioFormat) / audioFormat.getFrameSize();
    }

    public static int getSamplesBufferSize(AudioFormat audioFormat, int millis) {
        return (int) millis2bytes(audioFormat, millis);
    }

    public static int getMonoSamplesBufferSize(AudioFormat audioFormat, int millis) {
        return (int) millis2bytes(audioFormat, millis) / audioFormat.getFrameSize();
    }

    public static int getStereoSamplesBufferSize(AudioFormat audioFormat) {
        return getMonoSamplesBufferSize(audioFormat) << 1;
    }

    public static SourceDataLine createDataLine(AudioFormat audioFormat) {
        SourceDataLine line = null;
        DataLine.Info info = new DataLine.Info(SourceDataLine.class, audioFormat);

        if (!AudioSystem.isLineSupported(info)) {
            LOG.error("Audio not supported...");
        } else {
            try {
                line = (SourceDataLine) AudioSystem.getLine(info);
                int bufSize = getAudioLineBufferSize(audioFormat);
                line.open(audioFormat, bufSize);
                lowerLatencyHack(line);
                line.start();
                LOG.info("SourceDataLine buffer: {} ms, {} bytes, actual: {} bytes",
                        SoundProvider.AUDIO_BUFFER_LEN_MS, bufSize, line.getBufferSize());
            } catch (LineUnavailableException lue) {
                LOG.error("Unable to open audio line.");
            }
        }
        return line;
    }

    //NOTE: doesn't work in linux, check if it does something in Windows
    private static void lowerLatencyHack(SourceDataLine line) {
        String sname = line.getClass().getSuperclass().getCanonicalName();
        if (Sleeper.isWindows() && "com.sun.media.sound.DirectAudioDevice.DirectDL".equalsIgnoreCase(sname)) {
            try {
                Field f = line.getClass().getSuperclass().getDeclaredField("waitTime");
                f.setAccessible(true);
                f.set(line, 1);
                LOG.info("Setting waitTime to 1ms for SourceDataLine: {}", line.getClass().getCanonicalName());
            } catch (Exception e) {
                LOG.warn("Unable to set waitTime for SourceDataLine: {}", line.getClass().getCanonicalName());
            }
        }
    }

    public static void close(DataLine line) {
        if (line != null) {
            line.stop();
            synchronized (line) {
                line.flush();
            }
            Util.sleep(150); //avoid pulse-audio crashes on linux
            line.close();
            Util.sleep(100);
        }
    }

    public static void convertToWav(AudioFormat audioFormat, String fileName) {
        File input = new File(fileName);
        File output = new File(fileName + ".wav");

        try (
                FileInputStream fileInputStream = new FileInputStream(input);
                AudioInputStream audioInputStream = new AudioInputStream(fileInputStream, audioFormat
                        , input.length())
        ) {
            AudioSystem.write(audioInputStream, AudioFileFormat.Type.WAVE, output);
            audioInputStream.close();
            LOG.info("{} saved", output.getAbsolutePath());
        } catch (IOException ioe) {
            LOG.error("Error writing WAV file: {}", output.getAbsolutePath());
            ioe.printStackTrace();
            System.out.println("Error writing WAV file");
        }
    }

    public static short clampToShort(int value) {
        if (value > Short.MAX_VALUE) {
            return Short.MAX_VALUE;
        }
        if (value < Short.MIN_VALUE) {
            return Short.MIN_VALUE;
        }
        return (short) value;
    }

    public static byte clampToByte(int value) {
        if (value > Byte.MAX_VALUE) {
            return Byte.MAX_VALUE;
        }
        if (value < Byte.MIN_VALUE) {
            return Byte.MIN_VALUE;
        }
        return (byte) value;
    }

    public static void mix(int soundDeviceSetup, Map<SoundDeviceType, int[]> deviceAudioBuffers, byte[] psgMono8,
                           byte[] output, int inputLen) {
        int j = 0; //psg index
        int k = 0; //output index
        final int dfps = Math.max(0, PSG_SHIFT_BITS - 1); //reduce PSG volume, otherwise we get clamping
        final boolean psgEn = PSG.isEnabled(soundDeviceSetup);
        final boolean fmEn = FM.isEnabled(soundDeviceSetup);
        final boolean pwmEn = PWM.isEnabled(soundDeviceSetup);
        final boolean pcmEn = PCM.isEnabled(soundDeviceSetup);
        final boolean cddaEn = CDDA.isEnabled(soundDeviceSetup);
        final int[] fmStereo16 = deviceAudioBuffers.get(FM);
        final int[] pwmStereo16 = deviceAudioBuffers.get(PWM);
        final int[] pcmStereo16 = deviceAudioBuffers.get(PCM);
        final int[] cddaStereo16 = deviceAudioBuffers.get(CDDA);

        for (int i = 0; i < inputLen; i += 2, j++, k += 4) {
            int l = 0, r = 0;
            if (psgEn) {
                //PSG: 8 bit -> 13 bit (attenuate by 2 bit)
                int psg = psgMono8[j] << dfps;
                l += psg;
                r += psg;
            }
            if (fmEn) {
                l += fmStereo16[i];
                r += fmStereo16[i + 1];
            }
            if (pwmEn) {
                l += pwmStereo16[i];
                r += pwmStereo16[i + 1];
            }
            if (pcmEn) {
                l += pcmStereo16[i];
                r += pcmStereo16[i + 1];
            }
            if (cddaEn) {
                l += cddaStereo16[i];
                r += cddaStereo16[i + 1];
            }
            short out16L = clampToShort(l);
            short out16R = clampToShort(r);
            if (l != out16L || r != out16R) {
                LOG.info("Clamped L {} -> {}, R {} -> {}", l, out16L, r, out16R);
            }
            Util.setShortLE(output, k, out16L);
            Util.setShortLE(output, k + 2, out16R);
        }
    }
}