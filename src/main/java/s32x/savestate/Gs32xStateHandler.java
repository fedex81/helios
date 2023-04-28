package s32x.savestate;

import com.google.common.primitives.Bytes;
import omegadrive.Device;
import omegadrive.savestate.BaseStateHandler;
import omegadrive.savestate.GshStateHandler;
import omegadrive.savestate.StateUtil;
import omegadrive.util.LogHelper;
import omegadrive.util.Util;
import org.slf4j.Logger;
import s32x.StaticBootstrapSupport;
import s32x.sh2.Sh2Context;
import s32x.sh2.cache.Sh2Cache;

import java.io.Serial;
import java.io.Serializable;
import java.nio.ByteBuffer;
import java.util.*;

import static omegadrive.savestate.StateUtil.storeSerializedData;

/**
 * Federico Berti
 * <p>
 * Copyright 2023
 */
public class Gs32xStateHandler extends GshStateHandler {

    private static final Logger LOG = LogHelper.getLogger(Gs32xStateHandler.class.getSimpleName());
    protected static final String MAGIC_WORD_32X = "G32";

    protected static final String START_32X_TOKEN = "START_GS32X";
    protected static final String END_32X_TOKEN = "END_GS32X";
    public static final String fileExtension32x = "gs32x";

    private static final Map<String, Device> s32xDeviceSet = new WeakHashMap<>();
    private static final Sh2ContextWrap wrap = new Sh2ContextWrap();

    private ByteBuffer b = ByteBuffer.allocate(FILE_SIZE << 4);

    static class S32xContainer implements Serializable {
        @Serial
        private static final long serialVersionUID = 5388883919206790206L;
        public Map<String, byte[]> dataMap = new LinkedHashMap<>();
    }

    public static BaseStateHandler createInstance(String fileName, Type type, Set<Device> deviceSet) {
        Gs32xStateHandler h = new Gs32xStateHandler();
        h.type = type;
        h.init(fileName);
        h.setDevicesWithContext(deviceSet);
        return h;
    }


    protected Gs32xStateHandler() {
    }

    protected static String handleFileExtension(String fileName) {
        boolean hasExtension = fileName.toLowerCase().contains("." + fileExtension32x);
        return fileName + (!hasExtension ? "." + fileExtension32x : "");
    }

    protected void init(String fileNameEx) {
        this.fileName = handleFileExtension(fileNameEx);
        if (this.type == Type.SAVE) {
            this.buffer = ByteBuffer.allocate(FILE_SIZE);
            this.buffer.put(MAGIC_WORD_32X.getBytes());
        } else {
            this.buffer = StateUtil.loadStateFile(this.fileName, new String[]{".gs"});
            this.detectStateFileType();
        }
    }

    protected BaseStateHandler detectStateFileType() {
        byte[] magicWord = new byte[MAGIC_WORD_32X.length()];
        buffer.get(magicWord);
        String fileType = Util.toStringValue(magicWord);
        boolean isSupported = MAGIC_WORD_32X.equalsIgnoreCase(fileType);
        if (!isSupported || buffer.capacity() < FILE_SIZE) {
            LOG.error("Unable to load save state of type: {}, size: {}", fileType, buffer.capacity());
            return BaseStateHandler.EMPTY_STATE;
        }
        version = buffer.get(0x50) & 0xFF;
        softwareId = buffer.get(0x51) & 0xFF;
        LOG.info("Savestate type {}, version: {}, softwareId: {}", fileType, version, softwareId);
        return this;
    }

    public static class Sh2ContextWrap implements Serializable {
        @Serial
        private static final long serialVersionUID = -208048769448048274L;

        public Sh2Context[] sh2Ctx = new Sh2Context[2];
        public byte[][] sh2CacheCtx = new byte[2][];
        public transient Sh2Cache[] sh2Cache = new Sh2Cache[2];
    }

    public static void addDevice(Device d) {
        if (d instanceof Sh2Context s) {
            wrap.sh2Ctx[s.cpuAccess.ordinal()] = s;
            return;
        }
        if (d instanceof Sh2Cache s) {
            wrap.sh2Cache[s.getCacheContext().cpu.ordinal()] = s;
            return;
        }
        s32xDeviceSet.put(d.getClass().getSimpleName(), d);
    }

    @Override
    public void processState() {
        super.processState(); //do MD stuff
        Arrays.fill(b.array(), (byte) 0);
        assert !s32xDeviceSet.isEmpty();
        if (type == Type.SAVE) {
            S32xContainer container = new S32xContainer();
            for (int i = 0; i < 2; i++) {
                b.rewind();
                wrap.sh2Cache[i].saveContext(b);
                wrap.sh2CacheCtx[i] = new byte[b.position()];
                b.rewind().get(wrap.sh2CacheCtx[i]).rewind();
            }
            byte[] dt = Util.serializeObject(wrap);
            container.dataMap.put(wrap.getClass().getSimpleName(), dt);
            for (Device d : s32xDeviceSet.values()) {
                d.saveContext(b);
                byte[] data = new byte[b.position()];
                b.rewind().get(data).rewind();
                container.dataMap.put(d.getClass().getSimpleName(), data);
            }
            buffer = storeSerializedData(START_32X_TOKEN, END_32X_TOKEN, container, buffer);
        } else {
            int s32xStart = Bytes.indexOf(buffer.array(), START_32X_TOKEN.getBytes()) + START_32X_TOKEN.length();
            int s32xEnd = Bytes.indexOf(buffer.array(), END_32X_TOKEN.getBytes());
            if (s32xStart > 0 && s32xEnd > 0) {
                Serializable s = Util.deserializeObject(buffer.array(), s32xStart, s32xEnd);
                assert s instanceof S32xContainer;
                S32xContainer container = (S32xContainer) s;
                byte[] data = container.dataMap.get(Sh2ContextWrap.class.getSimpleName());
                s = Util.deserializeObject(data);
                assert s instanceof Sh2ContextWrap;
                Sh2ContextWrap w = (Sh2ContextWrap) s;
                for (int i = 0; i < 2; i++) {
                    wrap.sh2Ctx[i].loadContext(w.sh2Ctx[i]);
                    wrap.sh2Cache[i].loadContext(ByteBuffer.wrap(w.sh2CacheCtx[i]));
                }
                for (Device d : s32xDeviceSet.values()) {
                    data = container.dataMap.get(d.getClass().getSimpleName());
                    d.loadContext(ByteBuffer.wrap(data));
                }
            }
            StaticBootstrapSupport.afterStateLoad();
        }
    }

    public static Sh2ContextWrap getSh2ContextWrap() {
        return wrap;
    }
}
