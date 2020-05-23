package omegadrive.sound;

import omegadrive.util.SoundUtil;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.SourceDataLine;

/**
 * SoundTest
 * <p>
 * Federico Berti
 * <p>
 * Copyright 2019
 */
public class SoundTest {

    public static void main(String[] args) {
        final AudioFormat audioFormat = new AudioFormat(44100, 8, 1, true, true);
        SourceDataLine soundLine = SoundUtil.createDataLine(audioFormat);
        byte counter = 0;
        int bufferSize = soundLine.getBufferSize();
        final byte[] buffer = new byte[bufferSize];
        byte sign = 1;
        double value = bufferSize;
        while (true) {
            byte threshold = (byte) (audioFormat.getFrameRate() / value);
            for (int i = 0; i < bufferSize; i++) {
                if (counter > threshold) {
                    sign = (byte) -sign;
                    counter = 0;
                }
                buffer[i] = (byte) (sign * 30);
                counter++;
            }
            // the next call is blocking until the entire buffer is
            // sent to the SourceDataLine
            soundLine.write(buffer, 0, bufferSize);
            value *= 0.99;
        }
    }
}
