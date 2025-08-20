package omegadrive.savestate;

import com.google.common.collect.ImmutableMap;
import com.google.common.io.Files;
import omegadrive.Device;
import omegadrive.SystemLoader;
import omegadrive.cpu.z80.Z80Helper;
import omegadrive.util.FileUtil;
import omegadrive.util.LogHelper;
import omegadrive.util.Util;
import omegadrive.util.ZipUtil;
import org.slf4j.Logger;
import z80core.Z80;
import z80core.Z80State;

import java.io.Serializable;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.BiFunction;
import java.util.zip.ZipEntry;

import static omegadrive.util.ArrayEndianUtil.getUInt32LE;
import static omegadrive.util.ArrayEndianUtil.setUInt32LE;
import static omegadrive.util.BufferUtil.indexOf;

/**
 * Federico Berti
 * <p>
 * Copyright 2020
 */
public class StateUtil {

    public static final Map<SystemLoader.SystemType, String> fileExtensionMap = ImmutableMap.of(
            SystemLoader.SystemType.SG_1000, "sgs",
            SystemLoader.SystemType.COLECO, "cvs",
            SystemLoader.SystemType.MSX, "mss"
    );
    private static final byte[] arr4 = new byte[4];
    private static final byte[] arr2 = new byte[2];

    public static final BiFunction<ByteBuffer, Integer, Integer> getInt2Fn = (b, pos) -> {
        b.position(pos);
        b.get(arr2);
        return getUInt32LE(arr2);
    };
    private final static Logger LOG = LogHelper.getLogger(StateUtil.class.getSimpleName());

    public static final BiFunction<ByteBuffer, Integer, Integer> getInt4Fn = (b, pos) -> {
        b.position(pos);
        b.get(arr4);
        return getUInt32LE(arr4);
    };

    public static void setInt4LEFn(ByteBuffer b, int pos, int val) {
        setUInt32LE(val, arr4, 0);
        b.position(pos);
        b.put(arr4);
    }

    public static void setInt2LEFn(ByteBuffer b, int pos, int val) {
        b.put(pos, (byte) (val & 0xFF));
        b.put(pos + 1, (byte) ((val >> 8) & 0xFF));
    }

    //2 bytes for a 16 bit int
    public static void setDataAsBytes(ByteBuffer buf, int... data) {
        Arrays.stream(data).forEach(val -> buf.put((byte) (val & 0xFF)));
    }

    public static void setData(ByteBuffer buf, int... data) {
        Arrays.stream(data).forEach(buf::putInt);
    }

    public static void skip(ByteBuffer buf, int len) {
        buf.position(buf.position() + len);
    }

    public static ByteBuffer extendBuffer(ByteBuffer current, int increaseDelta) {
        ByteBuffer extBuffer = ByteBuffer.allocate(current.capacity() + increaseDelta);
        current.position(0);
        extBuffer.put(current);
        extBuffer.position(current.capacity());
        return extBuffer;
    }

    public static void saveZ80State(ByteBuffer buffer, Z80State s) {
        setDataAsBytes(buffer, s.getRegF(), s.getRegA(), s.getRegC(), s.getRegB(),
                s.getRegE(), s.getRegD(), s.getRegL(), s.getRegH());
        setDataAsBytes(buffer, s.getRegIX() & 0xFF, s.getRegIX() >> 8, s.getRegIY() & 0xFF,
                s.getRegIY() >> 8, s.getRegPC() & 0xFF, s.getRegPC() >> 8, s.getRegSP() & 0xFF,
                s.getRegSP() >> 8);
        setDataAsBytes(buffer, s.getRegFx(), s.getRegAx(), s.getRegCx(), s.getRegBx(), s.getRegEx(),
                s.getRegDx(), s.getRegLx(), s.getRegHx(), s.getRegI());
        int val = (s.isHalted() ? 1 : 0) << 8 | (s.isIFF2() ? 1 : 0) << 3 | s.getIM().ordinal() << 1 |
                (s.isIFF1() ? 1 : 0);
        setDataAsBytes(buffer, val);
    }

    public static Z80State loadZ80State(ByteBuffer data) {
        Z80State z80State = new Z80State();
        z80State.setRegAF(getUInt32LE(data.get(), data.get()));
        z80State.setRegBC(getUInt32LE(data.get(), data.get()));
        z80State.setRegDE(getUInt32LE(data.get(), data.get()));
        z80State.setRegHL(getUInt32LE(data.get(), data.get()));
        z80State.setRegIX(getUInt32LE(data.get(), data.get()));
        z80State.setRegIY(getUInt32LE(data.get(), data.get()));
        z80State.setRegPC(getUInt32LE(data.get(), data.get()));
        z80State.setRegSP(getUInt32LE(data.get(), data.get()));
        z80State.setRegAFx(getUInt32LE(data.get(), data.get()));
        z80State.setRegBCx(getUInt32LE(data.get(), data.get()));
        z80State.setRegDEx(getUInt32LE(data.get(), data.get()));
        z80State.setRegHLx(getUInt32LE(data.get(), data.get()));
        z80State.setRegI(data.get() & 0xFF); //note breaking change

        int val = data.get();
        Z80.IntMode im = Z80Helper.parseIntMode((val >> 1) & 3);
        z80State.setIM(im);
        z80State.setIFF1((val & 1) > 0);
        z80State.setIFF2((val & 8) > 0);
        z80State.setHalted((val & 0x80) > 0);
        return z80State;
    }

    public static void saveZ80StateGst(ByteBuffer buffer, Z80State z80State) {
        setInt4LEFn(buffer, 0x404, z80State.getRegAF());
        setInt4LEFn(buffer, 0x408, z80State.getRegBC());
        setInt4LEFn(buffer, 0x40C, z80State.getRegDE());
        setInt4LEFn(buffer, 0x410, z80State.getRegHL());
        setInt4LEFn(buffer, 0x414, z80State.getRegIX());
        setInt4LEFn(buffer, 0x418, z80State.getRegIY());
        setInt4LEFn(buffer, 0x41C, z80State.getRegPC());
        setInt4LEFn(buffer, 0x420, z80State.getRegSP());
        setInt4LEFn(buffer, 0x424, z80State.getRegAFx());
        setInt4LEFn(buffer, 0x428, z80State.getRegBCx());
        setInt4LEFn(buffer, 0x42C, z80State.getRegDEx());
        setInt4LEFn(buffer, 0x430, z80State.getRegHLx());
        setInt4LEFn(buffer, 0x434, z80State.getRegI());
        buffer.put(0x436, (byte) (z80State.isIFF1() ? 1 : 0));
    }

    public static Z80State loadZ80StateGst(ByteBuffer buffer) {
        Z80State z80State = new Z80State();
        z80State.setRegAF(getInt2Fn.apply(buffer, 0x404));
        z80State.setRegBC(getInt2Fn.apply(buffer, 0x408));
        z80State.setRegDE(getInt2Fn.apply(buffer, 0x40C));
        z80State.setRegHL(getInt2Fn.apply(buffer, 0x410));
        z80State.setRegIX(getInt2Fn.apply(buffer, 0x414));
        z80State.setRegIY(getInt2Fn.apply(buffer, 0x418));
        z80State.setRegPC(getInt2Fn.apply(buffer, 0x41C));
        z80State.setRegSP(getInt2Fn.apply(buffer, 0x420));
        z80State.setRegAFx(getInt2Fn.apply(buffer, 0x424));
        z80State.setRegBCx(getInt2Fn.apply(buffer, 0x428));
        z80State.setRegDEx(getInt2Fn.apply(buffer, 0x42C));
        z80State.setRegHLx(getInt2Fn.apply(buffer, 0x430));
        z80State.setRegI(getInt2Fn.apply(buffer, 0x434));
        boolean iffN = (buffer.get(0x436) & 0xFF) > 0;
        z80State.setIFF1(iffN);
        z80State.setIFF2(iffN);
        return z80State;
    }

    public static Optional<Serializable> loadSerializedData(String magicWord, int dataStart, byte[] data) {
        dataStart += magicWord.length();
        int dataEnd = getNextPosForPattern(data, dataStart, magicWord);
        return Optional.ofNullable(Util.deserializeObject(data, dataStart, dataEnd - dataStart));
    }

    public static ByteBuffer storeSerializedData(String magicWord, Serializable object, ByteBuffer buffer) {
        return storeSerializedData(magicWord, magicWord, object, buffer);
    }

    public static ByteBuffer storeSerializedData(String magicWordStart, String magicWordEnd, Serializable object,
                                                 ByteBuffer buffer) {
        int prevPos = buffer.position();
        int len = magicWordStart.length() + magicWordEnd.length();
        byte[] data = Util.serializeObject(object);
        buffer = extendBuffer(buffer, data.length + len);

        try {
            buffer.put(magicWordStart.getBytes());
            buffer.put(data);
            buffer.put(magicWordEnd.getBytes());
        } catch (Exception var10) {
            LOG.error("Unable to save {} data", magicWordStart);
        } finally {
            buffer.position(prevPos);
        }

        return buffer;
    }

    private static int getNextPosForPattern(byte[] buf, int startPos, String pattern) {
        int endPos = indexOf(buf, pattern.getBytes(), startPos);
        return endPos > startPos ? endPos : buf.length;
    }

    public static void processState(BaseStateHandler state, List<Device> list) {
        if (state.getType() == BaseStateHandler.Type.LOAD) {
            list.forEach(d -> d.loadContext(state.getDataBuffer()));
        } else {
            list.forEach(d -> d.saveContext(state.getDataBuffer()));
        }
    }

    static List<Device> getDeviceOrderList(Set<Class<? extends Device>> deviceClassSet,
                                           Set<Device> devs) {
        List<Device> sysList = new ArrayList<>(devs);
        List<Device> ds = new ArrayList<>();
        for (Iterator<Class<? extends Device>> i = deviceClassSet.iterator(); i.hasNext(); ) {
            Class<? extends Device> c = i.next();
            for (int j = 0; j < sysList.size(); j++) {
                if (c.isAssignableFrom(sysList.get(j).getClass())) {
                    ds.add(sysList.get(j));
                    break;
                }
            }
        }
        return ds;
    }

    public static String getStateFileName(String fileName, String... exts) {
        String fileNameEx = fileName;
        Path p = Paths.get(fileName);
        if (ZipUtil.isCompressedByteStream(p)) {
            Optional<? extends ZipEntry> opt = ZipUtil.getSupportedZipEntryIfAny(p, exts);
            if (opt.isPresent()) {
                fileNameEx = opt.get().getName();
            }
        }
        return fileNameEx;
    }

    public static ByteBuffer loadStateFile(String fileName, String... exts) {
        String ext = Files.getFileExtension(getStateFileName(fileName, exts));
        return ByteBuffer.wrap(FileUtil.readBinaryFile(Paths.get(fileName), ext));
    }

    public static <T extends Device> T getInstanceOrThrow(List<Device> deviceList, Class<T> clazz) {
        return Util.getDeviceIfAny(deviceList, clazz).orElseThrow(() ->
                new RuntimeException("Unable to find an instance of class: " + clazz + ", from list: " +
                        Arrays.toString(deviceList.toArray())));
    }
}
