/*
 * FileSoundPersister
 * Copyright (c) 2018-2019 Federico Berti
 * Last modified: 07/04/19 16:01
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

package omegadrive.sound.persist;

import com.google.common.io.Files;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;
import java.nio.file.Paths;

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
