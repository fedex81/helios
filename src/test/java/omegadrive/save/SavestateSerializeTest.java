package omegadrive.save;

import omegadrive.bus.gen.SvpMapper;
import omegadrive.cart.mapper.RomMapper;
import omegadrive.memory.IMemoryProvider;
import omegadrive.memory.MemoryProvider;
import omegadrive.savestate.GenesisStateHandler;
import omegadrive.sound.fm.ym2612.nukeykt.Ym2612Nuke;
import omegadrive.ssp16.Ssp16;
import omegadrive.ssp16.Ssp16Types;
import org.junit.Assert;
import org.junit.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

/**
 * Ym2612NukeSerializeTest
 * <p>
 * Federico Berti
 * <p>
 * Copyright 2020
 */
public class SavestateSerializeTest {

    private static final String fileFolder = "src/test/resources/savestate/serialize";
    private static final String nukeSavestateName = "nuke_serialized_test.gsh";
    private static final String svpSavestateName = "svp_serialized_test.gsh";

    private static final IMemoryProvider NO_MEMORY = MemoryProvider.createInstance(new int[2], 0);

    /**
     * Has the serialization format been changed?
     * Let's try to deserialize a byte stream, if it fails something is broken.
     */
    @Test
    public void testSerialFormatUnchangedNuke() {
        Path p = Paths.get(fileFolder, nukeSavestateName);
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

    /**
     * Has the serialization format been changed?
     * Let's try to deserialize a byte stream, if it fails something is broken.
     */
    @Test
    public void testSerialFormatUnchangedSvp() {
        Path p = Paths.get(fileFolder, svpSavestateName);
        GenesisStateHandler stateHandler = GenesisStateHandler.createLoadInstance(p.toAbsolutePath().toString());
        SvpMapper svpMapper = SvpMapper.createInstance(RomMapper.NO_OP_MAPPER, NO_MEMORY);
        Ssp16 ssp16 = SvpMapper.ssp16;
        int hc1 = Arrays.hashCode(ssp16.getSvpContext().iram_rom) + Arrays.hashCode(ssp16.getSvpContext().dram);
        stateHandler.loadSvpState(ssp16);
        Ssp16Types.Svp_t svpCtx = ssp16.getSvpContext();
        Assert.assertNotNull(svpCtx);
        int hc2 = Arrays.hashCode(svpCtx.iram_rom) + Arrays.hashCode(svpCtx.dram);
        //if the data has been deserialised ok, the context instance should've been replaced
        //with a newly loaded instance
        Assert.assertNotEquals(hc1, hc2);
    }
}
