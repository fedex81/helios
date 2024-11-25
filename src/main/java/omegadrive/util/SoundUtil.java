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

import omegadrive.sound.SoundProvider;
import org.slf4j.Logger;

import javax.sound.sampled.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.reflect.Field;

public class SoundUtil {

    private static final Logger LOG = LogHelper.getLogger(SoundUtil.class.getSimpleName());

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
        PSG_SHIFT_BITS = DEFAULT_PSG_SHIFT_BITS - USER_PSG_ATT_BITS;
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

    public static void mixTwoSources(byte[] input1, byte[] input2, byte[] output, int inputLen1, int inputLen2) {
        if (inputLen1 == 0) {
            System.arraycopy(input2, 0, output, 0, inputLen2);
        } else if (inputLen2 == 0) {
            System.arraycopy(input1, 0, output, 0, inputLen1);
        } else {
            if (inputLen1 != inputLen2) {
                LOG.error("oops, " + inputLen1 + "," + inputLen2);
            }
            //TODO ugly hack
            int len = Math.min(inputLen1, inputLen2);
            for (int i = 0; i < len; i += 4) {
                output[i] = (byte) ((input1[i] + input2[i]) >> 1);
                output[i + 1] = (byte) ((input1[i + 1] + input2[i + 1]) >> 1);
                output[i + 2] = (byte) ((input1[i + 2] + input2[i + 2]) >> 1);
                output[i + 3] = (byte) ((input1[i + 3] + input2[i + 3]) >> 1);
            }
        }
    }

    public static void intStereo16ToByteStereo16Mix(int[] input, byte[] output, int inputLen) {
        for (int i = 0, k = 0; i < inputLen; i += 2, k += 4) {
            output[k] = (byte) (input[i] & 0xFF); //left lsb
            output[k + 1] = (byte) ((input[i] >> 8) & 0xFF); //left msb
            output[k + 2] = (byte) (input[i + 1] & 0xFF); //left lsb
            output[k + 3] = (byte) ((input[i + 1] >> 8) & 0xFF); //left msb
        }
    }

    public static void intStereo14ToByteStereo16PwmMix(byte[] output, int[] fmStereo16, int[] pwmStereo16,
                                                       byte[] psgMono8, int inputLen) {
        int j = 0; //psg index
        int k = 0; //output index
        for (int i = 0; i < inputLen; i += 2, j++, k += 4) {
            //PSG: 8 bit -> 13 bit (attenuate by 2 bit)
            int psg = psgMono8[j];
            psg = DEFAULT_PSG_SHIFT_BITS > 0 ? psg << DEFAULT_PSG_SHIFT_BITS : psg >> -DEFAULT_PSG_SHIFT_BITS;
            int out16L = (fmStereo16[i] + pwmStereo16[i] + psg);
            int out16R = (fmStereo16[i + 1] + pwmStereo16[i + 1] + psg);
            out16L = Math.min(Math.max(out16L, Short.MIN_VALUE), Short.MAX_VALUE);
            out16R = Math.min(Math.max(out16R, Short.MIN_VALUE), Short.MAX_VALUE);
            output[k] = (byte) (out16L & 0xFF); //lsb left
            output[k + 1] = (byte) ((out16L >> 8) & 0xFF); //msb left
            output[k + 2] = (byte) (out16R & 0xFF); //lsb right
            output[k + 3] = (byte) ((out16R >> 8) & 0xFF); //msb right
        }
    }

    public static void intStereo14ToByteStereo16Mix(int[] input, byte[] output, byte[] psgMono8, int inputLen) {
        int j = 0; //psg index
        int k = 0; //output index
        for (int i = 0; i < inputLen; i += 2, j++, k += 4) {
            //PSG: 8 bit -> 13 bit (attenuate by 2 bit)
            int psg = psgMono8[j];
            psg = PSG_SHIFT_BITS > 0 ? psg << PSG_SHIFT_BITS : psg >> -PSG_SHIFT_BITS;
            int out16L = (input[i] + psg);
            int out16R = (input[i + 1] + psg);
            out16L = (out16L << 1) - (out16L >> 1); //mult by 1.5
            out16R = (out16R << 1) - (out16R >> 1);
            //avg fm and psg
            out16L = Math.min(Math.max(out16L, Short.MIN_VALUE), Short.MAX_VALUE);
            out16R = Math.min(Math.max(out16R, Short.MIN_VALUE), Short.MAX_VALUE);
            output[k] = (byte) (out16L & 0xFF); //lsb left
            output[k + 1] = (byte) ((out16L >> 8) & 0xFF); //msb left
            output[k + 2] = (byte) (out16R & 0xFF); //lsb right
            output[k + 3] = (byte) ((out16R >> 8) & 0xFF); //msb right
        }
    }

    public static void intStereo14ToByteStereo16MixFloat(int[] input, float[] output, byte[] psgMono8, int inputLen) {
        int j = 0; //psg index
        int k = 0; //output index
        for (int i = 0; i < inputLen; i += 2, j++, k += 2) {
            //PSG: 8 bit -> 13 bit (attenuate by 2 bit)
            int psg = psgMono8[j];
            psg = PSG_SHIFT_BITS > 0 ? psg << PSG_SHIFT_BITS : psg >> -PSG_SHIFT_BITS;
            int out16L = (input[i] + psg);
            int out16R = (input[i + 1] + psg);
            out16L = (out16L << 1) - out16L; //mult by 1.5
            out16R = (out16R << 1) - out16R;
            //avg fm and psg
            out16L = Math.min(Math.max(out16L, Short.MIN_VALUE), Short.MAX_VALUE);
            out16R = Math.min(Math.max(out16R, Short.MIN_VALUE), Short.MAX_VALUE);
            output[k] = out16L / 32768f;
            output[k + 1] = out16R / 32768f;
        }
    }

    public static void byteMono8ToByteStereo16Mix(byte[] psgMono8, byte[] output, int inputLen) {
        for (int j = 0, i = 0; j < inputLen; j++, i += 4) {
            //PSG: 8 bit -> 13 bit (attenuate by 2 bit)
            int psg16 = psgMono8[j] << 7;
            output[i] = (byte) (psg16 & 0xFF); //lsb
            output[i + 1] = (byte) ((psg16 >> 8) & 0xFF); //msb
            output[i + 2] = output[i];
            output[i + 3] = output[i + 1];
        }
    }

    @Deprecated
    public static void byteMono8ToByteStereo16Mix(byte[] psgMono8, byte[] output) {
        byteMono8ToByteStereo16Mix(psgMono8, output, psgMono8.length);
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
}