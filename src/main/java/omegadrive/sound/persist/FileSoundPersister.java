package omegadrive.sound.persist;

import com.google.common.io.Files;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;
import java.nio.file.Paths;

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
    private OutputStream fileStream;
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
                try {
                    //16 bit signed mono (little endian)
                    fileStream.write(buffer);
                } catch (IOException ioe) {
                    LOG.error("An error occurred while writing the"
                            + " sound file.");
                }
            }
    }


    /**
     * Start sound recording to WAV file.
     */
    private void startRecordingInternal(SoundType type) {
        String name = "output_" + type.name() + "_" + System.currentTimeMillis() + ".raw";
        try {
            Path file = Paths.get(".", name);
            fileStream = Files.asByteSink(file.toFile()).openBufferedStream();
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
                fileStream.flush();
                fileStream.close();
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
//        AudioFormat audioFormat = new AudioFormat(SAMPLE_RATE_HZ, OUTPUT_SAMPLE_SIZE, OUTPUT_CHANNELS, true, false);
//        SoundUtil.convertToWav(audioFormat, "output_1523804269641.raw");
    }

}
