package s32x.vdp;

import omegadrive.util.*;
import omegadrive.vdp.util.UpdatableViewer;
import omegadrive.vdp.util.VdpDebugView;
import org.slf4j.Logger;
import s32x.S32XMMREG;
import s32x.dict.S32xDict;
import s32x.dict.S32xMemAccessDelay;
import s32x.savestate.Gs32xStateHandler;
import s32x.sh2.device.IntControl;
import s32x.sh2.prefetch.Sh2Prefetch;
import s32x.vdp.debug.MarsVdpDebugView;

import java.io.Serial;
import java.io.Serializable;
import java.nio.ByteBuffer;
import java.nio.ShortBuffer;
import java.util.Arrays;
import java.util.Optional;

import static omegadrive.util.BufferUtil.writeBufferRaw;
import static omegadrive.util.LogHelper.logWarnOnce;
import static omegadrive.util.Util.th;
import static s32x.dict.S32xDict.*;
import static s32x.dict.S32xDict.RegSpecS32x.FBCR;
import static s32x.dict.S32xDict.RegSpecS32x.VDP_BITMAP_MODE;
import static s32x.vdp.MarsVdp.VdpPriority.MD;
import static s32x.vdp.MarsVdp.VdpPriority.S32X;

/**
 * Federico Berti
 * <p>
 * Copyright 2021
 * <p>
 */
public class MarsVdpImpl implements MarsVdp {

    private static final Logger LOG = LogHelper.getLogger(MarsVdpImpl.class.getSimpleName());

    private static class MarsVdpSaveContext implements Serializable {
        @Serial
        private static final long serialVersionUID = -7332632984301857483L;
        public MarsVdpRenderContext renderContext;

        private final byte[] fb0 = new byte[DRAM_SIZE];
        private final byte[] fb1 = new byte[DRAM_SIZE];
        private final byte[] palette = new byte[SIZE_32X_COLPAL];
        //0 - pal, 1 - NTSC
        private int pal = 1;
        //0 = palette access disabled, 1 = enabled
        private int pen = 1;

        private boolean wasBlankScreen = false;
    }

    private final ByteBuffer colorPalette = ByteBuffer.allocate(SIZE_32X_COLPAL);
    private final ByteBuffer[] dramBanks = new ByteBuffer[2];

    private final ShortBuffer[] frameBuffersWord = new ShortBuffer[NUM_FB];
    private final ShortBuffer colorPaletteWords = colorPalette.asShortBuffer();
    private final short[] fbDataWords = new short[DRAM_SIZE >> 1];
    private final int[] lineTableWords = new int[LINE_TABLE_WORDS];

    private ByteBuffer vdpRegs;
    private MarsVdpDebugView view;

    private MarsVdpSaveContext ctx;
    private MarsVdpContext vdpContext;
    private S32XMMREG s32XMMREG;
    private S32XMMREG.RegContext regContext;

    private int[] buffer;
    private static final boolean verbose = false, verboseRead = false;

    static {
        MarsVdp.initBgrMapper();
    }

    //for testing
    public static MarsVdp createInstance(MarsVdpContext vdpContext,
                                         ShortBuffer frameBuffer0, ShortBuffer frameBuffer1, ShortBuffer colorPalette) {
        MarsVdpImpl v = (MarsVdpImpl) createInstance(vdpContext, new S32XMMREG());
        v.colorPaletteWords.put(colorPalette);
        v.frameBuffersWord[0].put(frameBuffer0);
        v.frameBuffersWord[1].put(frameBuffer1);
        return v;
    }

    public static MarsVdp createInstance(MarsVdpContext vdpContext, S32XMMREG s32XMMREG) {
        MarsVdpImpl v = new MarsVdpImpl();
        v.s32XMMREG = s32XMMREG;
        v.regContext = s32XMMREG.regContext;
        v.vdpRegs = v.regContext.vdpRegs;
        v.dramBanks[0] = ByteBuffer.allocate(DRAM_SIZE);
        v.dramBanks[1] = ByteBuffer.allocate(DRAM_SIZE);
        v.frameBuffersWord[0] = v.dramBanks[0].asShortBuffer();
        v.frameBuffersWord[1] = v.dramBanks[1].asShortBuffer();
        v.view = MarsVdpDebugView.createInstance();
        MarsVdpSaveContext vsc = new MarsVdpSaveContext();
        v.ctx = vsc;
        vsc.renderContext = new MarsVdpRenderContext();
        vsc.renderContext.screen = v.buffer;
        vsc.renderContext.vdpContext = v.vdpContext = vdpContext;
        v.updateVideoModeInternal(vdpContext.videoMode);
        Gs32xStateHandler.addDevice(v);
        v.init();
        return v;
    }

    @Override
    public void init() {
        recalcPen();
        setBitFromWord(VDP_BITMAP_MODE, P32XV_PAL_POS, ctx.pal);
        setBitFromWord(FBCR, FBCR_VBLK_BIT_POS, vdpContext.vBlankOn ? 1 : 0);
        setBitFromWord(FBCR, FBCR_HBLK_BIT_POS, vdpContext.hBlankOn ? 1 : 0);
    }

    @Override
    public void write(int address, int value, Size size) {
        if (address >= S32xDict.START_32X_COLPAL_CACHE && address < S32xDict.END_32X_COLPAL_CACHE) {
            assert MdRuntimeData.getAccessTypeExt() != BufferUtil.CpuDeviceAccess.Z80;
            switch (size) {
                case WORD, LONG -> {
                    if (ctx.pen == 0) {
                        //TODO should wait for pen=1
                        LogHelper.logWarnOnce(LOG, "{} Write to palette when palette disabled, pen: {}",
                                MdRuntimeData.getAccessTypeExt(), ctx.pen);
                    }
                    writeBufferRaw(colorPalette, address & S32xDict.S32X_COLPAL_MASK, value, size);
                }
                default ->
                        LogHelper.logWarnOnce(LOG, "{} write, unable to access colorPalette as {}", MdRuntimeData.getAccessTypeExt(), size);
            }
            S32xMemAccessDelay.addWriteCpuDelay(S32xMemAccessDelay.PALETTE);
        } else if (address >= S32xDict.START_DRAM_CACHE && address < S32xDict.END_DRAM_CACHE) {
            if (size == Size.BYTE && value == 0) { //value =0 on byte is ignored
                return;
            }
            writeBufferRaw(dramBanks[vdpContext.frameBufferWritable], address & S32xDict.DRAM_MASK, value, size);
            S32xMemAccessDelay.addWriteCpuDelay(S32xMemAccessDelay.FRAME_BUFFER);
        } else if (address >= S32xDict.START_OVER_IMAGE_CACHE && address < S32xDict.END_OVER_IMAGE_CACHE) {
            //see Space Harrier, brutal, doom resurrection
            writeFrameBufferOver(address, value, size);
            S32xMemAccessDelay.addWriteCpuDelay(S32xMemAccessDelay.FRAME_BUFFER);
        } else {
            LOG.error("{} unhandled write at {}, val: {} {}", MdRuntimeData.getAccessTypeExt(), th(address),
                    th(value), size);
        }
    }

    @Override
    public int read(int address, Size size) {
        int res = 0;
        if (address >= S32xDict.START_32X_COLPAL_CACHE && address < S32xDict.END_32X_COLPAL_CACHE) {
            assert MdRuntimeData.getAccessTypeExt() != BufferUtil.CpuDeviceAccess.Z80;
            if (size == Size.WORD) {
                res = BufferUtil.readBuffer(colorPalette, address & S32xDict.S32X_COLPAL_MASK, size);
            } else {
                logWarnOnce(LOG, "{} read, unable to access colorPalette as {}", MdRuntimeData.getAccessTypeExt(), size);
            }
            S32xMemAccessDelay.addWriteCpuDelay(S32xMemAccessDelay.PALETTE);
        } else if (address >= S32xDict.START_DRAM_CACHE && address < S32xDict.END_DRAM_CACHE) {
            res = BufferUtil.readBuffer(dramBanks[vdpContext.frameBufferWritable], address & S32xDict.DRAM_MASK, size);
            S32xMemAccessDelay.addWriteCpuDelay(S32xMemAccessDelay.FRAME_BUFFER);
        } else if (address >= S32xDict.START_OVER_IMAGE_CACHE && address < S32xDict.END_OVER_IMAGE_CACHE) {
            res = BufferUtil.readBuffer(dramBanks[vdpContext.frameBufferWritable], address & S32xDict.DRAM_MASK, size);
            S32xMemAccessDelay.addWriteCpuDelay(S32xMemAccessDelay.FRAME_BUFFER);
        } else {
            LOG.error("{} unhandled read: {} {}", MdRuntimeData.getAccessTypeExt(), th(address), size);
        }
        return res;
    }

    @Override
    public void readCacheLine(int address, byte[] data) {
        dramBanks[vdpContext.frameBufferWritable].get(address & S32xDict.DRAM_MASK, data);
    }

    @Override
    public boolean vdpRegWrite(RegSpecS32x reg32x, int reg, int value, Size size) {
        switch (size) {
            case WORD:
            case BYTE:
                return handleVdpRegWriteInternal(reg32x, reg, value, size);
            case LONG:
                RegSpecS32x regSpec2 = S32xDict.getRegSpec(reg32x.regCpuType, reg32x.regSpec.fullAddr + 2);
                boolean res = handleVdpRegWriteInternal(reg32x, reg, (value >> 16) & 0xFFFF, Size.WORD);
                res |= handleVdpRegWriteInternal(regSpec2, reg + 2, value & 0xFFFF, Size.WORD);
                return res;
        }
        return false;
    }

    private boolean handleVdpRegWriteInternal(RegSpecS32x regSpec, int reg, int value, Size size) {
        assert size != Size.LONG : regSpec;
        if (size == Size.BYTE && (reg & 1) == 0) {
            LOG.warn("{} even byte write: {} {}", regSpec, th(value), size);
            return false;
        }
        boolean regChanged = false;
        switch (regSpec) {
            case VDP_BITMAP_MODE:
                regChanged = handleBitmapModeWrite(reg, value, size);
                break;
            case FBCR:
                regChanged = handleFBCRWrite(reg, value, size);
                break;
            case AFDR:
                assert size == Size.WORD;
                runAutoFill(value);
                regChanged = true;
                break;
            case SSCR:
                value &= 1;
                //fall-through
            case AFLR:
                value &= 0xFF;
                //fall-through
            default:
                int res = BufferUtil.readBufferReg(regContext, regSpec, reg, size);
                if (res != value) {
                    BufferUtil.writeBufferReg(regContext, regSpec, reg, value, size);
                    regChanged = true;
                }
                break;
        }
        return regChanged;
    }

    private boolean handleBitmapModeWrite(int reg, int value, Size size) {
        //NOTE: golf, writes on even byte, ignored
        if (size == Size.BYTE && (reg & 1) == 0) {
            logWarnOnce(LOG, "{} ignore byte-write on even byte, val: {}", VDP_BITMAP_MODE, th(value));
            return false;
        }
        int prevVal = readWordFromBuffer(RegSpecS32x.VDP_BITMAP_MODE);
        int prevOddByteVal = prevVal & 0xFF;
        int oddByteVal = value & 0xFF;

        vdpContext.bitmapMode = BitmapMode.vals[oddByteVal & 3];
        recalcPen();
        int v240 = ctx.pal == 0 && vdpContext.videoMode.isV30() ? 1 : 0;
        setBitFromByte(VDP_BITMAP_MODE, P32XV_240_POS, v240, false);
        setBitFromByte(VDP_BITMAP_MODE, P32XV_M0_POS, oddByteVal & 1, false);
        setBitFromByte(VDP_BITMAP_MODE, P32XV_M1_POS, (oddByteVal >> P32XV_M1_POS) & 1, false);
        setVdpPriority(oddByteVal, prevOddByteVal);
        return prevOddByteVal != oddByteVal;
    }

    private boolean setVdpPriority(int oddByteVal, int prevOddByteVal) {
        int newPrio = (oddByteVal >> P32XV_PRIO_POS) & 1;
        int prevPrio = (prevOddByteVal >> P32XV_PRIO_POS) & 1;
        boolean prioChanged = setBitFromWord(VDP_BITMAP_MODE, P32XV_PRIO_POS, newPrio);
        if (prioChanged) {
            vdpContext.priority = newPrio == 0 ? MD : S32X;
            if (verbose) LOG.info("Vdp priority: {} -> {}", prevPrio == 0 ? "MD" : "32x", vdpContext.priority);
            if (!vdpContext.vBlankOn) { //vf does this but I think it is harmless
                logWarnOnce(LOG, "Illegal Vdp priority change outside VBlank: {} -> {}", prevPrio == 0 ? "MD" : "32x",
                        vdpContext.priority);
            }
        }
        return prioChanged;
    }

    private boolean handleFBCRWrite(int reg, int value, Size size) {
        if (size == Size.BYTE && (reg & 1) == 0) {
            logWarnOnce(LOG, "{} ignore byte-write on even byte, val: {}", FBCR, th(value));
            return false;
        }
        //vblank, hblank, pen -> readonly
        boolean changed = false;
        changed |= setBitFromByte(FBCR, FBCR_nFEN_BIT_POS, (value >> FBCR_nFEN_BIT_POS) & 1, false);
        //during display the register always shows the current frameBuffer being displayed
        if (vdpContext.vBlankOn || vdpContext.bitmapMode == BitmapMode.BLANK) {
            changed |= setBitFromByte(FBCR, FBCR_FRAMESEL_BIT_POS, value & 1, false);
            updateFrameBuffer(value);
        }
        int psfl = vdpContext.fsLatch;
        vdpContext.fsLatch = value & 1;
        changed |= psfl != vdpContext.fsLatch;
//        LOG.info("###### FBCR write: {} {} -> {}, {}", th(value), size, th(0), vdpContext);
        assert (readWordFromBuffer(FBCR) & 0x1FFC) == 0 : th(readWordFromBuffer(FBCR));
        return changed;
    }

    private void updateFrameBuffer(int val) {
        vdpContext.frameBufferDisplay = val & 1;
        vdpContext.frameBufferWritable = (vdpContext.frameBufferDisplay + 1) & 1;
    }

    private void runAutoFill(int data) {
        writeBufferWord(RegSpecS32x.AFDR, data);
        int startAddr = readWordFromBuffer(RegSpecS32x.AFSAR);
        int len = readWordFromBuffer(RegSpecS32x.AFLR) & 0xFF;
        runAutoFillInternal(dramBanks[vdpContext.frameBufferWritable], startAddr, data, len);
    }

    //for testing
    public void runAutoFillInternal(ByteBuffer buffer, int startAddrWord, int data, int len) {
        int wordAddrFixed = startAddrWord & 0xFF00;
        int wordAddrVariable = startAddrWord & 0xFF;
        if (verbose) LOG.info("AutoFill startWord {}, len(word) {}, data {}", th(startAddrWord), th(len), th(data));
        final int dataWord = data & 0xFFFF;
        int afsarEnd = wordAddrFixed + (len & 0xFF);
//        assert ((startAddrWord + len) & 0xFF) >= wordAddrVariable;
        do {
            //TODO this should trigger an invalidate on framebuf mem?
            //TODO anyone executing code from the framebuffer?
            writeBufferRaw(buffer, (wordAddrFixed + wordAddrVariable) << 1, dataWord, Size.WORD);
            if (verbose) LOG.info("AutoFill addr(word): {}, addr(byte): {}, len(word) {}, data(word) {}",
                    th(wordAddrFixed + wordAddrVariable), th((wordAddrFixed + wordAddrVariable) << 1), th(len), th(dataWord));
            wordAddrVariable = (wordAddrVariable + 1) & 0xFF;
            len--;
        } while (len >= 0);
        assert len == -1;
        writeBufferWord(RegSpecS32x.AFSAR, wordAddrFixed + wordAddrVariable); //star wars arcade
        if (verbose)
            LOG.info("AutoFill done, startWord {}, AFSAR {}, data: {}", th(startAddrWord), th(afsarEnd), th(dataWord));
        vdpRegChange(RegSpecS32x.AFSAR);
    }

    private void recalcPen() {
        boolean penEnabled = vdpContext.vBlankOn || vdpContext.hBlankOn ||
                vdpContext.bitmapMode == BitmapMode.BLANK || vdpContext.bitmapMode == BitmapMode.DIRECT_COL;
        ctx.pen = penEnabled ? 1 : 0;
        setBitFromWord(FBCR, P32XV_PEN_WORD_POS, ctx.pen);
        vdpRegChange(FBCR);
    }

    public void setVBlank(boolean vBlankOn) {
        vdpContext.vBlankOn = vBlankOn;
        setBitFromWord(FBCR, S32xDict.FBCR_VBLK_BIT_POS, vBlankOn ? 1 : 0);
        if (vBlankOn) {
            vdpContext.screenShift = readWordFromBuffer(RegSpecS32x.SSCR) & 1;
            draw(vdpContext);
            int currentFb = readWordFromBuffer(FBCR) & 1;
            if (currentFb != vdpContext.fsLatch) {
                setBitFromWord(FBCR, S32xDict.FBCR_FRAMESEL_BIT_POS, vdpContext.fsLatch);
                updateFrameBuffer(vdpContext.fsLatch);
//                System.out.println("##### VBLANK, D" + frameBufferDisplay + "W" + frameBufferWritable + ", fsLatch: " + fsLatch + ", VB: " + vBlankOn);
            }
        }
        recalcPen();
        vdpRegChange(FBCR);
        s32XMMREG.interruptControls[0].setIntPending(IntControl.Sh2Interrupt.VINT_12, vBlankOn);
        s32XMMREG.interruptControls[1].setIntPending(IntControl.Sh2Interrupt.VINT_12, vBlankOn);
//        System.out.println("VBlank: " + vBlankOn);
    }

    public void setHBlank(boolean hBlankOn, int hen) {
        vdpContext.hBlankOn = hBlankOn;
        setBitFromWord(FBCR, S32xDict.FBCR_HBLK_BIT_POS, hBlankOn ? 1 : 0);
        //TODO hack, FEN =0 after 40 cycles @ 23Mhz
        setBitFromWord(FBCR, S32xDict.FBCR_nFEN_BIT_POS, hBlankOn ? 1 : 0);
        boolean hintOn = false;
        if (hBlankOn) {
            if (hen > 0 || !vdpContext.vBlankOn) {
                if (--vdpContext.hCount < 0) {
                    vdpContext.hCount = readWordFromBuffer(RegSpecS32x.SH2_HCOUNT_REG) & 0xFF;
                    hintOn = true;
                }
            } else {
                vdpContext.hCount = readWordFromBuffer(RegSpecS32x.SH2_HCOUNT_REG) & 0xFF;
            }
        }
        recalcPen();
        //TODO check if any poller is testing the HBlank byte
        vdpRegChange(FBCR);
        s32XMMREG.interruptControls[0].setIntPending(IntControl.Sh2Interrupt.HINT_10, hintOn);
        s32XMMREG.interruptControls[1].setIntPending(IntControl.Sh2Interrupt.HINT_10, hintOn);
    }

    private void vdpRegChange(RegSpecS32x reg32x) {
        assert reg32x.regSpec.regSize == Size.WORD;
        int val = readWordFromBuffer(reg32x); //TODO avoid the read?
        int addr = S32xDict.SH2_CACHE_THROUGH_OFFSET | S32xDict.START_32X_SYSREG_CACHE | reg32x.regSpec.fullAddr;
        assert S32xDict.getRegSpec(S32xDict.S32xRegCpuType.REG_MD, addr) == S32xDict.getRegSpec(S32xDict.S32xRegCpuType.REG_SH2, addr);
        Sh2Prefetch.checkPollersVdp(reg32x.deviceType, addr, val, Size.WORD);
    }

    private void writeBufferWord(RegSpecS32x reg, int value) {
        BufferUtil.writeBufferReg(regContext, reg, reg.addr, value, Size.WORD);
    }

    private int readWordFromBuffer(RegSpecS32x reg) {
        return BufferUtil.readWordFromBuffer(regContext, reg);
    }

    private boolean setBitFromWord(RegSpecS32x reg, int pos, int value) {
        return BufferUtil.setBit(vdpRegs, reg.addr & S32xDict.S32X_VDP_REG_MASK, pos, value, Size.WORD);
    }

    private boolean setBitFromByte(RegSpecS32x reg, int pos, int value, boolean evenByte) {
        return BufferUtil.setBit(vdpRegs, (reg.addr + (evenByte ? 0 : 1)) & S32xDict.S32X_VDP_REG_MASK, pos, value, Size.BYTE);
    }

    private void writeFrameBufferOver(int address, int value, Size size) {
        if (value == 0) {
            return;
        }
        switch (size) {
            case WORD -> {
                writeFrameBufferByte(address, (value >> 8) & 0xFF);
                writeFrameBufferByte(address + 1, value & 0xFF);
            }
            case BYTE ->
                //guaranteed not be zero
                    writeFrameBufferByte(address, value);
            case LONG -> {
//                LOG.error("Unexpected writeFrameBufferOver: {}", size);
                writeFrameBufferByte(address, (value >> 24) & 0xFF);
                writeFrameBufferByte(address + 1, (value >> 16) & 0xFF);
                writeFrameBufferByte(address + 2, (value >> 8) & 0xFF);
                writeFrameBufferByte(address + 3, (value >> 0) & 0xFF);
            }
        }
    }

    private void writeFrameBufferByte(int address, int value) {
        if (value != 0) {
            dramBanks[vdpContext.frameBufferWritable].put(address & S32xDict.DRAM_MASK, (byte) value);
        }
    }

    @Override
    public void draw(MarsVdpContext context) {
        switch (context.bitmapMode) {
            case BLANK -> drawBlank();
            case PACKED_PX -> drawPackedPixel(context);
            case RUN_LEN -> drawRunLen(context);
            case DIRECT_COL -> drawDirectColor(context);
        }
        view.update(context, buffer);
    }

    private void drawBlank() {
        if (ctx.wasBlankScreen) {
            return;
        }
        Arrays.fill(buffer, 0, buffer.length, vdpContext.priority.ordinal());
        ctx.wasBlankScreen = true;
    }

    //Mars Sample Program - Pharaoh
    //space harrier intro screen
    private void drawDirectColor(MarsVdpContext context) {
        final int w = context.videoMode.getDimension().width;
        final int h = context.videoMode.getDimension().height;
        final ShortBuffer b = frameBuffersWord[context.frameBufferDisplay];
        final int[] imgData = buffer;
        populateLineTable(b);
        b.position(0);
        b.get(fbDataWords);
        final short[] fb = fbDataWords;

        for (int row = 0; row < h; row++) {
            final int linePos = lineTableWords[row] + context.screenShift;
            final int fbBasePos = row * w;
            for (int col = 0; col < w; col++) {
                if (BufferUtil.assertionsEnabled) {
                    if (fbBasePos + col >= imgData.length) {
                        LOG.warn("row: {}, base: {}, col: {}", row, th(fbBasePos), th(col));
                        continue;
                    }
                }
                imgData[fbBasePos + col] = getDirectColorWithPriority(fb[linePos + col] & 0xFFFF);
            }
        }
        ctx.wasBlankScreen = false;
    }

    //space harrier sega intro
    private void drawRunLen(MarsVdpContext context) {
        final int h = context.videoMode.getDimension().height;
        final int w = context.videoMode.getDimension().width;
        final ShortBuffer b = frameBuffersWord[context.frameBufferDisplay];
        final int[] imgData = buffer;
        populateLineTable(b);
        b.position(0);
        b.get(fbDataWords);

        for (int row = 0; row < h; row++) {
            int col = 0;
            final int basePos = row * w;
            final int linePos = lineTableWords[row];
            int nextWord = linePos;
            if (basePos >= imgData.length) {
                break;
            }
            do {
                int rl = fbDataWords[nextWord++];
                int dotColorIdx = rl & 0xFF;
                int dotLen = ((rl >> 8) & 0xFF) + 1;
                int nextLimit = Math.min(col + dotLen, imgData.length - basePos);
                int color = getColorWithPriority(dotColorIdx);
                for (; col < nextLimit; col++) {
                    imgData[basePos + col] = color;
                }
            } while (col < w && nextWord < fbDataWords.length);
        }
        ctx.wasBlankScreen = false;
    }

    //32X Sample Program - Celtic - PWM Test
    void drawPackedPixel(MarsVdpContext context) {
        final ShortBuffer b = frameBuffersWord[context.frameBufferDisplay];
        final int[] imgData = buffer;
        populateLineTable(b);

        b.position(0);
        b.get(fbDataWords);

        final int h = context.videoMode.getDimension().height;
        final int w = context.videoMode.getDimension().width;

        for (int row = 0; row < h; row++) {
            final int linePos = lineTableWords[row] + context.screenShift;
            final int basePos = row * w;
            for (int col = 0, wordOffset = 0; col < w; col += 2, wordOffset++) {
                final int palWordIdx1 = (fbDataWords[linePos + wordOffset] >> 8) & 0xFF;
                final int palWordIdx2 = fbDataWords[linePos + wordOffset] & 0xFF;
                imgData[basePos + col] = getColorWithPriority(palWordIdx1);
                imgData[basePos + col + 1] = getColorWithPriority(palWordIdx2);
            }
        }
        ctx.wasBlankScreen = false;
    }

    @Override
    public int[] doCompositeRendering(VideoMode mdVideoMode, int[] mdData, MarsVdpRenderContext ctx) {
        int[] out = doCompositeRenderingExt(mdVideoMode, mdData, ctx);
        view.updateFinalImage(out);
        return out;
    }

    private static int[] mdStretchH40 = new int[0];

    public static int[] doCompositeRenderingExt(VideoMode mdVideoMode, int[] mdData, MarsVdpRenderContext ctx) {
        final int[] marsData = Optional.ofNullable(ctx.screen).orElse(BufferUtil.EMPTY_INT_ARRAY);
        int[] out = mdData;
        boolean md_h32 = ctx.vdpContext.videoMode.isH40() && mdVideoMode.isH32();
        if (md_h32) {
            if (mdStretchH40.length != marsData.length) {
                mdStretchH40 = new int[marsData.length];
            }
            BufferUtil.vidH32StretchToH40(mdVideoMode, mdData, mdStretchH40);
            mdData = mdStretchH40;
        }
        if (mdData.length == marsData.length) {
            final boolean prio32x = ctx.vdpContext.priority == S32X;
            final boolean s32xRegBlank = ctx.vdpContext.bitmapMode == BitmapMode.BLANK;
            final boolean s32xBgBlank = !prio32x && s32xRegBlank;
            final boolean s32xFgBlank = prio32x && s32xRegBlank;
            final int[] fg = prio32x ? marsData : mdData;
            final int[] bg = prio32x ? mdData : marsData;
            for (int i = 0; i < fg.length; i++) {
                boolean throughBit = (marsData[i] & 1) > 0;
                boolean mdBlanking = (mdData[i] & 1) > 0;
                boolean bgBlanking = (prio32x && mdBlanking) || s32xBgBlank;
                boolean fgBlanking = (!prio32x && mdBlanking) || s32xFgBlank;
                fg[i] = (fgBlanking && !bgBlanking) || (throughBit && !bgBlanking) ? bg[i] : fg[i];
            }
            out = fg;
        }
        return out;
    }

    private void populateLineTable(final ShortBuffer b) {
        b.position(0);
        for (int i = 0; i < lineTableWords.length; i++) {
            lineTableWords[i] = b.get() & 0xFFFF;
        }
    }

    //NOTE: encodes priority as the LSB (bit) of the word
    private int getColorWithPriority(int palWordIdx) {
        int palValue = colorPaletteWords.get(palWordIdx) & 0xFFFF;
        return getDirectColorWithPriority(palValue);
    }

    private int getDirectColorWithPriority(int palValue) {
        int prio = (palValue >> 15) & 1;
        int color = bgr5toRgb8Mapper[palValue];
        return (color & ~1) | prio;
    }

    public void updateVdpBitmapMode(VideoMode video) {
        ctx.pal = video.isPal() ? 0 : 1;
        //TODO mmh this is coming from the MD side, so the v240 register value, should not be affected?
        int v240 = video.isPal() && video.isV30() ? 1 : 0;
        setBitFromByte(VDP_BITMAP_MODE, P32XV_240_POS, v240, false);
        setBitFromWord(VDP_BITMAP_MODE, P32XV_PAL_POS, ctx.pal);
    }

    @Override
    public void updateVideoMode(VideoMode v) {
        if (v.equals(vdpContext.videoMode)) {
            return;
        }
        if (!v.isH40()) {
            VideoMode prev = v;
            v = VideoMode.getVideoMode(v.getRegion(), true, v.isV30(), prev);
            if (verbose) LOG.info("MD set to H32: {}, s32x using {}", prev, v);
        }
        updateVdpBitmapMode(v);
        updateVideoModeInternal(v);
        vdpContext.videoMode = v;
    }

    private void updateVideoModeInternal(VideoMode videoMode) {
        this.buffer = new int[videoMode.getDimension().width * videoMode.getDimension().height];
        ctx.renderContext.screen = buffer;
        LOG.info("Updating videoMode, {} -> {}", vdpContext.videoMode, videoMode);
    }

    @Override
    public MarsVdpRenderContext getMarsVdpRenderContext() {
        return ctx.renderContext;
    }

    @Override
    public void updateDebugView(UpdatableViewer debugView) {
        if (debugView instanceof VdpDebugView) {
            ((VdpDebugView) debugView).setAdditionalPanel(view.getPanel());
        }
    }

    @Override
    public void saveContext(ByteBuffer bb) {
        MarsVdp.super.saveContext(bb);
        ctx.renderContext.screen = buffer;
        dramBanks[0].rewind().get(ctx.fb0);
        dramBanks[1].rewind().get(ctx.fb1);
        colorPalette.rewind().get(ctx.palette);
        bb.put(Util.serializeObject(ctx));
    }

    @Override
    public void loadContext(ByteBuffer bb) {
        MarsVdp.super.loadContext(bb);
        Serializable s = Util.deserializeObject(bb);
        assert s instanceof MarsVdpSaveContext;
        ctx = (MarsVdpSaveContext) s;
        buffer = ctx.renderContext.screen;
        vdpContext = ctx.renderContext.vdpContext;
        dramBanks[0].rewind().put(ctx.fb0);
        dramBanks[1].rewind().put(ctx.fb1);
        colorPalette.rewind().put(ctx.palette);
    }

    @Override
    public void dumpMarsData() {
        DebugMarsVdpRenderContext d = new DebugMarsVdpRenderContext();
        d.renderContext = getMarsVdpRenderContext();
        frameBuffersWord[0].position(0);
        frameBuffersWord[1].position(0);
        d.frameBuffer0 = new short[frameBuffersWord[0].capacity()];
        d.frameBuffer1 = new short[frameBuffersWord[1].capacity()];
        frameBuffersWord[0].get(d.frameBuffer0);
        frameBuffersWord[1].get(d.frameBuffer1);

        colorPaletteWords.position(0);
        d.palette = new short[colorPaletteWords.capacity()];
        colorPaletteWords.get(d.palette);
        //NOTE needs to redraw as the buffer and the context might be out of sync
        draw(d.renderContext.vdpContext);
        d.renderContext.screen = buffer;
        MarsVdp.storeMarsData(d);
    }

    @Override
    public void reset() {
        view.reset();
    }
}
