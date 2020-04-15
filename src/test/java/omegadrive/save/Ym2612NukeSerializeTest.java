package omegadrive.save;

import omegadrive.savestate.GenesisStateHandler;
import omegadrive.sound.fm.ym2612.nukeykt.Ym2612Nuke;
import omegadrive.util.FileLoader;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Ym2612NukeSerializeTest
 * <p>
 * Federico Berti
 * <p>
 * Copyright 2020
 */
public class Ym2612NukeSerializeTest {

    private static final String fileFolder = "src/test/resources/savestate";
    private static final String savestateName = "quick_save.gsh";
    private Path p = Paths.get(fileFolder, savestateName);
    private byte[] data;

    @Before
    public void setup() {
        data = FileLoader.readFileSafe(p);
        Assert.assertTrue(data.length > 0);
    }

    /**
     * Has the serialization format been changed?
     * Let's try to deserialize a byte stream, if it fails something is broken.
     */
    @Test
    public void testSerialFormatUnchanged() {
        GenesisStateHandler stateHandler = GenesisStateHandler.createLoadInstance(p.toAbsolutePath().toString());
        Ym2612Nuke nuke = new Ym2612Nuke(0);
        int hashCode = nuke.getState().hashCode();
        stateHandler.loadFmState(nuke);
        Ym2612Nuke.Ym3438Context context = nuke.getState();
        Assert.assertNotNull(context);
        int hashCode2 = nuke.getState().hashCode();
        //if the data has been deserialised ok, the context instance should've been replaced
        //with a newly loaded instance
        Assert.assertNotEquals(hashCode, hashCode2);
    }
}
