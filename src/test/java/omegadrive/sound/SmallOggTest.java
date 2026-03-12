package omegadrive.sound;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.nio.file.Path;
import java.util.Arrays;

import static omegadrive.sound.msumd.MsuMdHandler.CDDA_FORMAT;

/**
 * Federico Berti
 * <p>
 * Copyright 2026
 */
public class SmallOggTest {

    public static final String file = "src/test/resources/ogg/small.ogg";

    /**
     * java-vorbis-support fails with small files < 7Kbytes
     * Verify that the current library works.
     */
    @Test
    public void testSmallOgg() {
        int expBytes = 1165568;
        int expHc = -155918335;
        Path p = Path.of(file);
        try (AudioInputStream ais = AudioSystem.getAudioInputStream(p.toFile());
             AudioInputStream dataIn = AudioSystem.getAudioInputStream(CDDA_FORMAT, ais)) {
            byte[] b = dataIn.readAllBytes();
            Assertions.assertEquals(expBytes, b.length);
            Assertions.assertEquals(expHc, Arrays.hashCode(b));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
