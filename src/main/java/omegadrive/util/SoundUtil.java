package omegadrive.util;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.sound.sampled.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

/**
 * ${FILE}
 * <p>
 * Federico Berti
 * <p>
 * Copyright 2018
 */
public class SoundUtil {

    private static Logger LOG = LogManager.getLogger(SoundUtil.class.getSimpleName());

    public static byte[] EMPTY_SAMPLE = new byte[0];
    public static byte ZERO_BYTE = (byte) 0;
    public static int[] EMPTY_SAMPLE_INT = new int[0];
    public static byte[] ONE_STEREO_SAMPLE = {0, 0, 0, 0}; //16 bit Stereo

    public static long DEFAULT_BUFFER_SIZE_MS = 100;

    public static void writeBufferInternal(SourceDataLine line, byte[] buffer, int samplesPerFrame) {
        try {
            // Output Stream write(byte[] b, int off, int len)
            // Small buffer to avoid latency, but more intensive CPU usage
            int res = line.write(buffer, 0, samplesPerFrame);
            if (res < samplesPerFrame) {
                LOG.warn("bytes written: " + res + "/" + samplesPerFrame);
            }
        } catch (IllegalArgumentException iae) {
            LOG.error("Error writing to the audio line. "
                    + "The bytes do not represent complete frames.");
        } catch (ArrayIndexOutOfBoundsException aiobe) {
            LOG.error("Error writing to the audio line. "
                    + "The buffer does not contain the number of bytes specified.");

        }
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

    public static int getAudioLineBufferSize(AudioFormat audioFormat, RegionDetector.Region r) {
        return (int) millis2bytes(audioFormat, DEFAULT_BUFFER_SIZE_MS);
    }

    public static SourceDataLine createDataLine(AudioFormat audioFormat) {
        SourceDataLine line = null;
        DataLine.Info info = new DataLine.Info(SourceDataLine.class, audioFormat);

        if (!AudioSystem.isLineSupported(info)) {
            LOG.error("Audio not supported...");
        } else {
            try {
                line = (SourceDataLine) AudioSystem.getLine(info);
                line.open(audioFormat, getAudioLineBufferSize(audioFormat, RegionDetector.Region.EUROPE));
                line.start();
            } catch (LineUnavailableException lue) {
                LOG.error("Unable to open audio line.");
            }
        }
        return line;
    }

    public static void intStereo14ToByteMono16Mix(int[] input, byte[] output, byte[] psgMono8) {
        int j = 0;
        for (int i = 0; i < input.length; i += 2) {
            //fm: avg 2 channels -> mono
            // avg = (16 bit + 16 bit) >> (1 + 1) = 15 bit
            int fm = (input[i] + input[i + 1]) >> 2;
            //PSG: 8 bit -> 13 bit (attenuate by 2 bit)
            int psg = ((int) psgMono8[j]) << 5;
            //avg fm and psg
            int out16 = fm + psg;
            output[i] = (byte) (out16 & 0xFF); //lsb
            output[i + 1] = (byte) ((out16 >> 8) & 0xFF); //msb
            j++;
        }
    }

    public static void convertToWav(AudioFormat audioFormat, String fileName) {
        File input = new File(fileName);
        File output = new File(fileName + ".wav");

        try {
            FileInputStream fileInputStream = new FileInputStream(input);
            AudioInputStream audioInputStream = new AudioInputStream(fileInputStream, audioFormat
                    , input.length());
            AudioSystem.write(audioInputStream, AudioFileFormat.Type.WAVE, output);
            audioInputStream.close();
            System.out.println("OUTPUT.WAV recorded");
        } catch (IOException ioe) {
            LOG.error("Error writing WAV file.");
            ioe.printStackTrace();
            System.out.println("Error writing WAV file");
        }
    }
}