package omegadrive.savestate;

import com.google.common.collect.ImmutableMap;
import com.google.common.primitives.Bytes;
import omegadrive.SystemLoader;
import omegadrive.util.Util;
import omegadrive.z80.Z80Helper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import z80core.Z80;
import z80core.Z80State;

import java.io.Serializable;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;

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
    private static byte[] arr4 = new byte[4], arr2 = new byte[2];

    public static final BiFunction<ByteBuffer, Integer, Integer> getInt2Fn = (b, pos) -> {
        b.position(pos);
        b.get(arr2);
        return Util.getUInt32LE(arr2);
    };
    private final static Logger LOG = LogManager.getLogger(StateUtil.class.getSimpleName());

    public static final BiFunction<ByteBuffer, Integer, Integer> getInt4Fn = (b, pos) -> {
        b.position(pos);
        b.get(arr4);
        return Util.getUInt32LE(arr4);
    };

    public static void setInt4LEFn(ByteBuffer b, int pos, int val) {
        Util.setUInt32LE(val, arr4, 0);
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
        z80State.setRegAF(Util.getUInt32LE(data.get(), data.get()));
        z80State.setRegBC(Util.getUInt32LE(data.get(), data.get()));
        z80State.setRegDE(Util.getUInt32LE(data.get(), data.get()));
        z80State.setRegHL(Util.getUInt32LE(data.get(), data.get()));
        z80State.setRegIX(Util.getUInt32LE(data.get(), data.get()));
        z80State.setRegIY(Util.getUInt32LE(data.get(), data.get()));
        z80State.setRegPC(Util.getUInt32LE(data.get(), data.get()));
        z80State.setRegSP(Util.getUInt32LE(data.get(), data.get()));
        z80State.setRegAFx(Util.getUInt32LE(data.get(), data.get()));
        z80State.setRegBCx(Util.getUInt32LE(data.get(), data.get()));
        z80State.setRegDEx(Util.getUInt32LE(data.get(), data.get()));
        z80State.setRegHLx(Util.getUInt32LE(data.get(), data.get()));
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
        int prevPos = buffer.position();
        int len = magicWord.length() << 1;
        byte[] data = Util.serializeObject(object);
        buffer = extendBuffer(buffer, data.length + len);
        try {
            buffer.put(magicWord.getBytes());
            buffer.put(data);
            buffer.put(magicWord.getBytes());
        } catch (Exception e) {
            LOG.error("Unable to save {} data", magicWord);
        } finally {
            buffer.position(prevPos);
        }
        return buffer;
    }

    //TODO lastIndexOf?
    private static int getNextPosForPattern(byte[] buf, int startPos, String pattern) {
        byte[] ba2 = Arrays.copyOfRange(buf, startPos, buf.length);
        int endPos = Bytes.indexOf(ba2, pattern.getBytes()) + startPos;
        return endPos > startPos ? endPos : buf.length;
    }
}
