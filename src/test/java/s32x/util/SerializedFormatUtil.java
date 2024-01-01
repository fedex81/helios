package s32x.util;

import com.google.common.io.Files;
import omegadrive.Device;
import omegadrive.savestate.BaseStateHandler;
import omegadrive.util.FileUtil;
import omegadrive.util.LogHelper;
import omegadrive.util.Util;
import org.slf4j.Logger;
import s32x.MarsRegTestUtil;
import s32x.StaticBootstrapSupport;
import s32x.savestate.Gs32xStateHandler;
import s32x.vdp.MarsVdp;
import s32x.vdp.debug.DebugVideoRenderContext;

import java.io.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.stream.Collectors;

import static omegadrive.SystemLoader.SystemType.S32X;
import static s32x.MarsRegTestUtil.NO_OP;

/**
 * Federico Berti
 * <p>
 * Copyright 2023
 */
public class SerializedFormatUtil {

    private static final Logger LOG = LogHelper.getLogger(SerializedFormatUtil.class.getSimpleName());

    public static void main(String[] args) throws IOException {
//        VdpRenderCompareTest.getFileProvider(VdpRenderCompareFileTest.baseDataFolder).
//                forEach(TweakSerializedFormat::convertDvrc);
//        VdpRenderCompareTest.getFileProvider(VdpMarsRenderCompareFileTest.baseDataFolder).
//                forEach(s -> SerializedFormatUtil.convertDmvrc(s, VdpMarsRenderCompareFileTest.baseDataFolder));
//        S32xSavestateSerializeTest.getFileProvider(S32xSavestateSerializeTest.saveStateFolder).
//                forEach(SerializedFormatUtil::convertCpuDevice);
//        load32xSavestateFiles();
    }

    private static void load32xSavestateFiles() throws IOException {
        String s = "/home/fede/roms/savestate/s32x/blackth/";
        Path f = Path.of(s);
        Set<Path> files = java.nio.file.Files.list(f).
                filter(p -> p.getFileName().toString().contains(Gs32xStateHandler.fileExtension32x)).collect(Collectors.toSet());
        files.forEach(SerializedFormatUtil::convertCpuDevice);
    }

    private static void convertCpuDevice(Path p) {
        System.out.println(p.toAbsolutePath());
        MarsLauncherHelper.Sh2LaunchContext lc = MarsRegTestUtil.createTestInstance();
        StaticBootstrapSupport.instance = NO_OP;
        Set<Device> deviceSet = lc.bus.getAllDevices(Device.class);
        Gs32xStateHandler stateHandler = (Gs32xStateHandler) BaseStateHandler.createInstance(
                S32X, p.toAbsolutePath().toString(), BaseStateHandler.Type.LOAD, deviceSet);
        stateHandler.setData(FileUtil.readBinaryFile(p, Gs32xStateHandler.fileExtension32x));
        stateHandler.processState();
        System.out.println("deserialised ok");

        String fileName = p.getFileName().toString();
        Path p1 = Path.of(p.getParent().toAbsolutePath().toString(), fileName + ".new");
        Gs32xStateHandler saveHandler = (Gs32xStateHandler) BaseStateHandler.createInstance(
                S32X, p1.toAbsolutePath().toString(), BaseStateHandler.Type.SAVE, deviceSet);
        saveHandler.processState();
        saveHandler.storeData();
        System.out.println("saved ok");
    }

    private static void convertDmvrc(String s, Path folder) {
        System.out.println(s);
        Path datFile = Paths.get(folder.toAbsolutePath().toString(), s);
        byte[] data = FileUtil.readBinaryFile(datFile, "dat");
        Object o = deserializeObject(data, 0, data.length);
        MarsVdp.DebugMarsVdpRenderContext dvrc = (MarsVdp.DebugMarsVdpRenderContext) o;
        MarsVdp.MarsVdpRenderContext vrc = dvrc.renderContext;
        System.out.println(dvrc);

        MarsVdp.MarsVdpRenderContext vrcNew = new MarsVdp.MarsVdpRenderContext();
        vrcNew.screen = vrc.screen;
        vrcNew.vdpContext = convertVdpCtx(vrc.vdpContext);
        MarsVdp.DebugMarsVdpRenderContext dvrcNew = new MarsVdp.DebugMarsVdpRenderContext();
        dvrcNew.frameBuffer0 = dvrc.frameBuffer0;
        dvrcNew.frameBuffer1 = dvrc.frameBuffer1;
        dvrcNew.palette = dvrc.palette;
        dvrcNew.renderContext = vrcNew;

        String out = Files.getNameWithoutExtension(s);
        Path datFile2 = Paths.get(folder.toAbsolutePath().toString(), out);
        FileUtil.writeFileSafe(datFile2, Util.serializeObject(dvrcNew));
        System.out.println("File written: " + datFile2.toAbsolutePath());
    }

    private static void convertDvrc(String s, Path folder) {
        System.out.println(s);
        Path datFile = Paths.get(folder.toAbsolutePath().toString(), s);
        byte[] data = FileUtil.readBinaryFile(datFile, "dat");
        Object o = deserializeObject(data, 0, data.length);
        DebugVideoRenderContext dvrc = (DebugVideoRenderContext) o;
        MarsVdp.MarsVdpRenderContext vrc = DebugVideoRenderContext.toMarsVdpRenderContext(dvrc);
        MarsVdp.MarsVdpContext vc = vrc.vdpContext;
        System.out.println(o);
        System.out.println(vrc);


        DebugVideoRenderContext dvrcNew = new DebugVideoRenderContext();
        MarsVdp.MarsVdpContext vcNew = convertVdpCtx(vc);

        dvrcNew.mdData = dvrc.mdData;
        dvrcNew.s32xData = dvrc.s32xData;
        dvrcNew.marsVdpContext = vcNew;
        dvrcNew.mdVideoMode = dvrc.mdVideoMode;
        MarsVdp.MarsVdpRenderContext vrcNew = DebugVideoRenderContext.toMarsVdpRenderContext(dvrcNew);
        System.out.println(dvrcNew);
        System.out.println(vrcNew);

        String out = Files.getNameWithoutExtension(s);
        Path datFile2 = Paths.get(folder.toAbsolutePath().toString(), out);
        FileUtil.writeFileSafe(datFile2, Util.serializeObject(dvrcNew));
        System.out.println("File written: " + datFile2.toAbsolutePath());
    }

    private static MarsVdp.MarsVdpContext convertVdpCtx(MarsVdp.MarsVdpContext vc) {
        MarsVdp.MarsVdpContext vcNew = new MarsVdp.MarsVdpContext();
        vcNew.videoMode = vc.videoMode;
        vcNew.frameBufferDisplay = vc.frameBufferDisplay;
        vcNew.frameBufferWritable = vc.frameBufferWritable;
        vcNew.bitmapMode = MarsVdp.BitmapMode.valueOf(vc.bitmapMode.name());
        vcNew.priority = MarsVdp.VdpPriority.valueOf(vc.priority.name());
        vcNew.vBlankOn = vc.vBlankOn;
        vcNew.fsLatch = vc.fsLatch;
        vcNew.hCount = vc.hCount;
        vcNew.screenShift = vc.screenShift;
        vcNew.hBlankOn = vc.hBlankOn;
        return vcNew;
    }

    public static Serializable deserializeObject(byte[] data) {
        return deserializeObject(data, 0, data.length);
    }

    public static Serializable deserializeObject(byte[] data, int offset, int len) {
        if (data == null || data.length == 0 || offset < 0 || len > data.length) {
            LOG.error("Unable to deserialize object of len: {}", data != null ? data.length : "null");
            return null;
        }
        Serializable res = null;
        try (
                ByteArrayInputStream bis = new ByteArrayInputStream(data, offset, len);
                ObjectInput in = new ObjectInputStream(bis)
        ) {
            res = (Serializable) in.readObject();
        } catch (Exception e) {
            LOG.error("Unable to deserialize object of len: {}, {}", data.length, e.getMessage());
            e.printStackTrace();
        }
        return res;
    }
}
