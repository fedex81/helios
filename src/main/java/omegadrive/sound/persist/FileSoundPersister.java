package omegadrive.sound.persist;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.FileWriter;
import java.io.IOException;

/**
 * ${FILE}
 * <p>
 * Federico Berti
 * <p>
 * Copyright 2018
 */
public class FileSoundPersister implements SoundPersister {

    private static Logger LOG = LogManager.getLogger(FileSoundPersister.class.getSimpleName());

    /**
     * For Recording Sound to Disk.
     */
    private FileWriter fileWriter;
    private boolean recording;

    @Override
    public void persistSound(SoundType type, byte[] output) {
        if (!isRecording()) {
            startRecording(type);
        }
        recordSound(output);
    }

    private void recordSound(byte[] buffer) {
        if (isRecording()) {
            for (byte b : buffer) {
                try {
                    // output 8 bit signed mono
                    // TODO doesnt work: 16 bit signed mono (little endian)
                    fileWriter.write(b & 0xff);
                } catch (IOException ioe) {
                    LOG.error("An error occurred while writing the"
                            + " sound file.");
                }
            }
        }
    }


    /**
     * Start sound recording to WAV file.
     */
    private void startRecordingInternal(SoundType type) {
        String name = "output_" + type.name() + "_" + System.currentTimeMillis() + ".raw";
        try {
            fileWriter = new FileWriter(name);
            LOG.info("Started recording file: " + name);
            recording = true;
        } catch (IOException ioe) {
            LOG.error("Could not open file for recording.");
            System.out.println("Could not open file for recording");
        }
    }

    @Override
    public boolean isRecording() {
        return recording;
    }

    /**
     * Stop sound recording to WAV file.
     */
    public void stopRecording() {
        if (isRecording()) {
            try {
                fileWriter.close();
                LOG.info("Stopped recording");
                recording = false;
            } catch (IOException ioe) {
                LOG.error("Failed whilst closing output.raw");
            }
        }
    }

    @Override
    public void startRecording(SoundType type) {
        if (!isRecording()) {
            startRecordingInternal(type);
        }
    }

    public static void main(String[] args) {
//        AudioFormat audioFormat = new AudioFormat(SAMPLE_RATE, OUTPUT_SAMPLE_SIZE, OUTPUT_CHANNELS, true, false);
//        SoundUtil.convertToWav(audioFormat, "output_1523804269641.raw");
    }

}
