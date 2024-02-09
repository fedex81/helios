package s32x.save;

import omegadrive.Device;
import omegadrive.save.MdSavestateTest;
import omegadrive.savestate.BaseStateHandler;
import omegadrive.util.Size;
import omegadrive.util.Util;
import omegadrive.util.ZipUtil;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import s32x.MarsRegTestUtil;
import s32x.StaticBootstrapSupport;
import s32x.savestate.Gs32xStateHandler;
import s32x.sh2.Sh2Helper;
import s32x.sh2.drc.Sh2Block;
import s32x.util.MarsLauncherHelper;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.stream.Stream;

import static omegadrive.SystemLoader.SystemType.S32X;
import static omegadrive.savestate.GstStateHandler.M68K_SSP_OFFSET;
import static omegadrive.savestate.GstStateHandler.M68K_USP_OFFSET;
import static s32x.MarsRegTestUtil.NO_OP;

/**
 * S32xSavestateSerializeTest
 * <p>
 * Federico Berti
 * <p>
 * Copyright 2020
 */
public class S32xSavestateSerializeTest {

    private static final String fileFolder = "src/test/resources/savestate/serialize/s32x";
    public static Path saveStateFolder = Paths.get(fileFolder);

    private static String zipExt = Gs32xStateHandler.fileExtension32x + ".zip";

    private MarsLauncherHelper.Sh2LaunchContext lc;

    @BeforeEach
    public void before() {
        lc = MarsRegTestUtil.createTestInstance();
        StaticBootstrapSupport.instance = NO_OP;
    }

    static Stream<Path> fileProvider() throws IOException {
        return getFileProvider(saveStateFolder);
    }

    public static Stream<Path> getFileProvider(Path baseDataFolder) throws IOException {
        System.out.println(baseDataFolder.toAbsolutePath());
        return MdSavestateTest.getSavestateList(saveStateFolder, zipExt).stream();
    }

    /**
     * Has the serialization format been changed?
     * Let's try to deserialize a byte stream, if it fails something is broken.
     */
    @ParameterizedTest
    @MethodSource("fileProvider")
    public void testSerialFormatUnchanged(Path p) {
        Set<Device> deviceSet = lc.bus.getAllDevices(Device.class);
        Gs32xStateHandler stateHandler = (Gs32xStateHandler) BaseStateHandler.createInstance(
                S32X, p.toAbsolutePath().toString(), BaseStateHandler.Type.LOAD, deviceSet);
        stateHandler.setData(ZipUtil.readZipFileContents(p, Gs32xStateHandler.fileExtension32x));
        stateHandler.processState();

        Gs32xStateHandler saveHandler = (Gs32xStateHandler) BaseStateHandler.createInstance(
                S32X, p.toAbsolutePath().toString(), BaseStateHandler.Type.SAVE, deviceSet);
        saveHandler.processState();

        ignoreKnownIssues(stateHandler, saveHandler);

//        FileUtil.writeFileSafe(Paths.get(p.getParent().toAbsolutePath().toString(), "test.bin"), saveHandler.getData());
        Assertions.assertArrayEquals(stateHandler.getData(), saveHandler.getData());

        //check fetchResult has been invalidated
        Gs32xStateHandler.Sh2ContextWrap scw = Gs32xStateHandler.getSh2ContextWrap();
        checkFR(scw.sh2Ctx[0].fetchResult);
        checkFR(scw.sh2Ctx[1].fetchResult);
    }

    private void checkFR(Sh2Helper.FetchResult fr) {
        Assertions.assertEquals(Sh2Block.INVALID_BLOCK, fr.block);
        Assertions.assertEquals(0, fr.pc);
        Assertions.assertEquals(0, fr.opcode);
    }

    //TODO fix
    private void ignoreKnownIssues(Gs32xStateHandler stateHandler, Gs32xStateHandler saveHandler) {
        //ignore m68k ssp, usp
        Util.writeData(stateHandler.getData(), M68K_SSP_OFFSET, 0, Size.LONG);
        Util.writeData(saveHandler.getData(), M68K_SSP_OFFSET, 0, Size.LONG);
        Util.writeData(stateHandler.getData(), M68K_USP_OFFSET, 0, Size.LONG);
        Util.writeData(saveHandler.getData(), M68K_USP_OFFSET, 0, Size.LONG);

        //FM regs
        Util.writeData(stateHandler.getData(), 0x20C, 0, Size.LONG);
        Util.writeData(saveHandler.getData(), 0x20C, 0, Size.LONG);
    }
}