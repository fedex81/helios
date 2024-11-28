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

import omegadrive.sound.SoundDevice.SampleBufferContext;
import omegadrive.sound.SoundProvider;
import org.slf4j.Logger;
import s32x.util.blipbuffer.BlipBufferHelper;

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

    public static int mixTwoSources(SampleBufferContext src1, SampleBufferContext src2, byte[] output) {
        return mixTwoSources(src1.lineBuffer, src2.lineBuffer, output, src1.stereoBytesLen, src2.stereoBytesLen);
    }

    public static int mixTwoSources(byte[] input1, byte[] input2, byte[] output, int inputLen1, int inputLen2) {
        int maxLen = Math.max(inputLen1, inputLen2);
        if (inputLen1 == 0) {
            if (input2 != output) System.arraycopy(input2, 0, output, 0, inputLen2);
        } else if (inputLen2 == 0) {
            if (input1 != output) System.arraycopy(input1, 0, output, 0, inputLen1);
        } else {
            int len = Math.min(inputLen1, inputLen2);
            for (int i = 0; i < len; i += 4) {
                short l1 = getSigned16LE(input1[i + 1], input1[i]);
                short l2 = getSigned16LE(input2[i + 1], input2[i]);
                short r1 = getSigned16LE(input1[i + 3], input1[i + 2]);
                short r2 = getSigned16LE(input2[i + 3], input2[i + 2]);
                short resL = (short) BlipBufferHelper.clampToShort((l1 + l2) >> 1);
                short resR = (short) BlipBufferHelper.clampToShort((r1 + r2) >> 1);
                setSigned16LE(resL, output, i);
                setSigned16LE(resR, output, i + 2);
            }
            if (inputLen1 != inputLen2) {
                fill(input1, input2, output, inputLen1, inputLen2);
            }
        }
        return maxLen;
    }

    private static void fill(byte[] input1, byte[] input2, byte[] output, int inputLen1, int inputLen2) {
        LogHelper.logWarnOnceForce(LOG, "len mismatch: {}, {}", inputLen1, inputLen2);
        int maxLen = Math.max(inputLen1, inputLen2);
        int minLen = Math.min(inputLen1, inputLen2);
        assert (Math.abs(inputLen1 - inputLen2) % 4) == 0;
        byte[] shorter = maxLen == inputLen1 ? input2 : input1;
        byte[] longer = maxLen == inputLen1 ? input1 : input2;
        short lastR = getSigned16LE(shorter[minLen - 1], shorter[minLen - 2]);
        short lastL = getSigned16LE(shorter[minLen - 3], shorter[minLen - 4]);
        for (int i = minLen; i < maxLen; i += 4) {
            short left = getSigned16LE(longer[i + 1], longer[i]);
            short right = getSigned16LE(longer[i + 3], longer[i + 2]);
            short resL = (short) BlipBufferHelper.clampToShort((left + lastL) >> 1);
            short resR = (short) BlipBufferHelper.clampToShort((right + lastR) >> 1);
            setSigned16LE(resL, output, i);
            setSigned16LE(resR, output, i + 2);
        }
    }

    public static short getSigned16LE(byte msb, byte lsb) {
        return (short) ((lsb & 0xFF) + ((msb & 0xFF) << 8));
    }

    public static void setSigned16LE(short value, byte[] data, int startIndex) {
        data[startIndex] = (byte) value;
        data[startIndex + 1] = (byte) (value >> 8);
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