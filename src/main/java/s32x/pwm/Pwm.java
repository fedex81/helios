package s32x.pwm;

import com.google.common.primitives.Ints;
import omegadrive.sound.PwmProvider;
import omegadrive.util.Fifo;
import omegadrive.util.LogHelper;
import omegadrive.util.Size;
import omegadrive.util.Util;
import org.slf4j.Logger;
import s32x.S32XMMREG;
import s32x.dict.S32xDict;
import s32x.dict.S32xDict.RegSpecS32x;
import s32x.event.PollSysEventManager;
import s32x.savestate.Gs32xStateHandler;
import s32x.sh2.device.DmaC;
import s32x.sh2.device.IntControl;
import s32x.sh2.drc.Sh2DrcBlockOptimizer;

import java.io.Serial;
import java.io.Serializable;
import java.nio.ByteBuffer;

import static omegadrive.util.BufferUtil.*;
import static omegadrive.util.BufferUtil.CpuDeviceAccess.MASTER;
import static omegadrive.util.BufferUtil.CpuDeviceAccess.SLAVE;
import static omegadrive.util.Util.readBufferWord;
import static omegadrive.util.Util.th;
import static s32x.dict.S32xDict.RegSpecS32x.*;
import static s32x.event.PollSysEventManager.SysEvent.PWM;
import static s32x.pwm.Pwm.PwmChannel.LEFT;
import static s32x.pwm.Pwm.PwmChannel.RIGHT;
import static s32x.pwm.Pwm.PwmChannelSetup.OFF;

/**
 * Federico Berti
 * <p>
 * Copyright 2022
 * <p>
 */
public class Pwm implements StepDevice {

    private static final Logger LOG = LogHelper.getLogger(Pwm.class.getSimpleName());

    enum PwmChannelSetup {
        OFF, SAME, FLIP, INVALID;

        public boolean isValid() {
            return this == SAME || this == FLIP;
        }
    }

    enum PwmChannel {LEFT, RIGHT}

    static class PwmChannelMap {
        public Fifo<Integer> fifo;
        public PwmChannel channel;
    }

    public static boolean PWM_USE_BLIP = false;

    private static final int PWM_DMA_CHANNEL = 1;
    private static final int chLeft = LEFT.ordinal(), chRight = RIGHT.ordinal();
    private static final int PWM_FIFO_SIZE = 3;
    private static final int PWM_FIFO_FULL_BIT_POS = 15;
    private static final int PWM_FIFO_EMPTY_BIT_POS = 14;
    public static final int CYCLE_LIMIT = 400; //57khz @ 60hz
    public static final int CYCLE_22khz = 1042;

    //used to clamp samples [0+sld, cycle-sld]
    public static final int SAMPLE_LIMIT_DELTA = 5;


    private static final PwmChannelSetup[] chanVals = PwmChannelSetup.values();
    private final ByteBuffer sysRegsMd;
    private final ByteBuffer sysRegsSh2;
    private IntControl[] intControls;
    private DmaC[] dmac;
    private PwmContext ctx;
    private int pwmSamplesPerFrame = 0, stepsPerFrame = 0, dreqPerFrame = 0;
    private static final boolean verbose = false;
    private PwmProvider playSupport = PwmProvider.NO_SOUND;

    private final PwmChannelMap fifoMapLeft = new PwmChannelMap();
    private final PwmChannelMap fifoMapRight = new PwmChannelMap();

    static class PwmContext implements Serializable {
        @Serial
        private static final long serialVersionUID = -4421577765971810509L;
        private final Fifo<Integer> fifoLeft = Fifo.createIntegerFixedSizeFifo(PWM_FIFO_SIZE);
        private final Fifo<Integer> fifoRight = Fifo.createIntegerFixedSizeFifo(PWM_FIFO_SIZE);
        private final PwmChannelSetup[] channelMap = {OFF, OFF};
        private boolean pwmEnable, dreqEn;
        private int cycle = 0, interruptInterval;
        private int sh2TicksToNextPwmSample, sh2ticksToNextPwmInterrupt, sh2TicksToNext22khzSample = CYCLE_22khz;
        private int rs, ls;
        private final int[] latestPwmValue = new int[PwmChannel.values().length];
    }

    public Pwm(S32XMMREG.RegContext regContext) {
        this.sysRegsMd = regContext.sysRegsMd;
        this.sysRegsSh2 = regContext.sysRegsSh2;
        this.ctx = new PwmContext();
        Gs32xStateHandler.addDevice(this);
        init();
    }

    public int read(CpuDeviceAccess cpu, RegSpecS32x regSpec, int address, Size size) {
        int res = readBuffer(sysRegsMd, address, size);
        assert res == readBuffer(sysRegsSh2, address, size);
        if (verbose) LOG.info("{} PWM read {}: {} {}", cpu, regSpec.getName(), th(res), size);
        return res;
    }

    public void write(CpuDeviceAccess cpu, RegSpecS32x regSpec, int reg, int value, Size size) {
        if (verbose) LOG.info("{} PWM write {}: {} {}", cpu, regSpec.getName(), th(value), size);
        switch (size) {
            case BYTE -> writeByte(cpu, regSpec, reg, value);
            case WORD -> writeWord(cpu, regSpec, reg, value);
            case LONG -> {
                writeWord(cpu, regSpec, reg, (value >> 16) & 0xFFFF);
                writeWord(cpu, S32xDict.getRegSpec(cpu, regSpec.addr + 2), reg + 2, value & 0xFFFF);
            }
        }
    }

    public void writeByte(CpuDeviceAccess cpu, RegSpecS32x regSpec, int reg, int value) {
        switch (regSpec) {
            case PWM_CTRL -> handlePwmControl(cpu, reg, value, Size.BYTE);
            case PWM_CYCLE -> {
                assert cpu.regSide == S32xRegSide.MD : regSpec;
                handlePartialByteWrite(reg, value);
                if (regSpec == PWM_CYCLE) {
                    int val = readBufferWord(sysRegsMd, regSpec.addr);
                    handlePwmCycleWord(cpu, val);
                }
            }
            case PWM_RCH_PW, PWM_LCH_PW, PWM_MONO -> {
                //Mars check test1
                //NOTE: z80 writes MSB then LSB, we trigger a wordWrite when setting the LSB
                handlePartialByteWrite(reg, value);
                if ((reg & 1) == 1) {
                    int val = readBufferWord(sysRegsMd, regSpec.addr);
                    writeWord(cpu, regSpec, regSpec.addr, val);
                    //we store the partial write in the register, we then need to overwrite it with the EMPTY/FULL bits
                    updateFifoRegs();
                }
            }
            default -> LOG.error("{} PWM write {} {}: {} {}", cpu, regSpec.getName(), th(reg), th(value), Size.BYTE);
        }
    }

    private void handlePartialByteWrite(int reg, int value) {
        boolean even = (reg & 1) == 0;
        if (even) {
            writeBuffers(sysRegsMd, sysRegsSh2, reg, value & 0xF, Size.BYTE);
            return;
        }
        writeBuffers(sysRegsMd, sysRegsSh2, reg, value & 0xFF, Size.BYTE);
    }

    private void writeWord(CpuDeviceAccess cpu, RegSpecS32x regSpec, int reg, int value) {
        switch (regSpec) {
            case PWM_CTRL -> handlePwmControl(cpu, reg, value, Size.WORD);
            case PWM_CYCLE -> handlePwmCycleWord(cpu, value);
            case PWM_MONO -> writeMono(value);
            case PWM_LCH_PW -> writeFifo(ctx.fifoLeft, value);
            case PWM_RCH_PW -> writeFifo(ctx.fifoRight, value);
            default -> writeBuffers(sysRegsMd, sysRegsSh2, regSpec.addr, value, Size.WORD);
        }
    }

    private void handlePwmCycleWord(CpuDeviceAccess cpu, int value) {
        value &= 0xFFF;
        writeBuffers(sysRegsMd, sysRegsSh2, PWM_CYCLE.addr, value, Size.WORD);
        int prevCycle = ctx.cycle;
        ctx.cycle = (value - 1) & 0xFFF;
        if (ctx.cycle < CYCLE_LIMIT) {
            LOG.warn("PWM cycle not supported: {}, limit: {}", ctx.cycle, CYCLE_LIMIT);
        }
        if (prevCycle != ctx.cycle) {
            handlePwmEnable(true);
        }
    }

    private void handlePwmControl(CpuDeviceAccess cpu, int reg, int value, Size size) {
        switch (cpu.regSide) {
            case MD -> handlePwmControlMd(cpu, reg, value, size);
            default -> handlePwmControlSh2(cpu, reg, value, size);
        }
        handlePwmEnable(false);
    }

    private void handlePwmControlMd(CpuDeviceAccess cpu, int reg, int value, Size size) {
        assert size != Size.LONG;
        if (size == Size.BYTE && ((reg & 1) == 0)) {
            LOG.info("{} ignored write to {} {}, read only byte: val {} {}", cpu, PWM_CTRL, th(reg), th(value), size);
            return;
        }
        int val = readBufferWord(sysRegsMd, PWM_CTRL.addr) & 0xFFF0;
        val |= value & 0xF;
        writeBuffers(sysRegsMd, sysRegsSh2, reg, val, size);

        value = readBufferWord(sysRegsMd, PWM_CTRL.addr);
        ctx.channelMap[chLeft] = chanVals[value & 3];
        ctx.channelMap[chRight] = chanVals[(value >> 2) & 3];
        updateChannelMap();
    }

    private void handlePwmControlSh2(CpuDeviceAccess cpu, int reg, int val, Size size) {
        int mask =
                switch (size) {
                    case WORD -> 0xF8F;
                    case BYTE -> (reg & 1) == 1 ? 0x8F : 0xF;
                    default -> 0;
                };
        //Primal Rage, Sh2 write bytes
        assert size != Size.LONG;
        writeBuffers(sysRegsMd, sysRegsSh2, reg, val & mask, size);
        int value = readBufferWord(sysRegsMd, PWM_CTRL.addr);
        ctx.dreqEn = ((value >> 7) & 1) > 0;
        int ival = (value >> 8) & 0xF;
        ctx.interruptInterval = ival == 0 ? 0x10 : ival;
        ctx.channelMap[chLeft] = chanVals[value & 3];
        ctx.channelMap[chRight] = chanVals[(value >> 2) & 3];
        updateChannelMap();
    }

    private void handlePwmEnable(boolean cycleChanged) {
        boolean wasEnabled = ctx.pwmEnable;
        ctx.pwmEnable = ctx.cycle > 0 && (ctx.channelMap[chLeft].isValid() || ctx.channelMap[chRight].isValid());
        if (!wasEnabled && ctx.pwmEnable || cycleChanged) {
            assert ctx.interruptInterval > 0;
            ctx.sh2TicksToNextPwmSample = ctx.cycle;
            ctx.sh2ticksToNextPwmInterrupt = ctx.interruptInterval;
            ctx.latestPwmValue[0] = ctx.latestPwmValue[1] = ctx.cycle >> 1; //TODO check
            resetFifo();
            updateFifoRegs();
            playSupport.updatePwmCycle(ctx.cycle);
            updateChannelMap();
        }
    }

    private void updateChannelMap() {
        switch (ctx.channelMap[chLeft]) {
            case SAME -> {
                fifoMapLeft.fifo = ctx.fifoLeft;
                fifoMapLeft.channel = LEFT;
            }
            case FLIP -> {
                fifoMapLeft.fifo = ctx.fifoRight;
                fifoMapLeft.channel = RIGHT;
            }
            default -> {
                fifoMapLeft.fifo = PwmUtil.EMPTY_FIFO;
                fifoMapLeft.channel = null;
            }
        }
        ;
        switch (ctx.channelMap[chRight]) {
            case SAME -> {
                fifoMapRight.fifo = ctx.fifoRight;
                fifoMapRight.channel = RIGHT;
            }
            case FLIP -> {
                fifoMapRight.fifo = ctx.fifoLeft;
                fifoMapRight.channel = LEFT;
            }
            default -> {
                fifoMapRight.fifo = PwmUtil.EMPTY_FIFO;
                fifoMapRight.channel = null;
            }
        }
        ;
    }

    private void resetFifo() {
        ctx.fifoRight.clear();
        ctx.fifoLeft.clear();
    }

    private void writeFifo(Fifo<Integer> fifo, int value) {
        if (!ctx.pwmEnable) {
            return;
        }
        if (fifo.isFull()) {
            fifo.pop();
            if (verbose) LOG.warn("PWM FIFO push when fifo full: {}", th(value));
            return;
        }
        //Darxide, Mars Check v01 do this
        value = Ints.constrainToRange(value, SAMPLE_LIMIT_DELTA, 0xFFF - SAMPLE_LIMIT_DELTA);
        assert value >= 0;
        fifo.push(Util.getFromIntegerCache((value - 1) & 0xFFF));
        updateFifoRegs();
    }

    private int readMono() {
        return (readFifo(ctx.fifoLeft, LEFT) + readFifo(ctx.fifoRight, RIGHT)) >> 1;
    }

    private int readFifo(Fifo<Integer> fifo, PwmChannel chan) {
        if (fifo.isEmpty()) {
            if (verbose) LOG.warn("PWM FIFO pop when ctx.fifo empty: {}", th(ctx.latestPwmValue[chan.ordinal()]));
            return chan != null ? ctx.latestPwmValue[chan.ordinal()] : ctx.cycle >> 1;
        }
        int res = fifo.pop();
        ctx.latestPwmValue[chan.ordinal()] = res;
        updateFifoRegs();
        return res;
    }

    //TODO update on read instead??
    //TODO keep the existing value instead of replacing it
    private void updateFifoRegs() {
        int regValue = (ctx.fifoLeft.isFullBit() << PWM_FIFO_FULL_BIT_POS) | (ctx.fifoLeft.isEmptyBit() << PWM_FIFO_EMPTY_BIT_POS);
        writeBuffers(sysRegsMd, sysRegsSh2, PWM_LCH_PW.addr, regValue, Size.WORD);
        regValue = (ctx.fifoRight.isFullBit() << PWM_FIFO_FULL_BIT_POS) | (ctx.fifoRight.isEmptyBit() << PWM_FIFO_EMPTY_BIT_POS);
        writeBuffers(sysRegsMd, sysRegsSh2, PWM_RCH_PW.addr, regValue, Size.WORD);
        updateMono();
    }

    private void updateMono() {
        int fifoFull = ((ctx.fifoLeft.isFullBit() | ctx.fifoRight.isFullBit()) << PWM_FIFO_FULL_BIT_POS);
        int fifoEmpty = ((ctx.fifoLeft.isEmptyBit() & ctx.fifoRight.isEmptyBit()) << PWM_FIFO_EMPTY_BIT_POS);
        int regValue = fifoFull | fifoEmpty;
        writeBuffers(sysRegsMd, sysRegsSh2, PWM_MONO.addr, regValue, Size.WORD);
        updatePolling();
    }

    private void updatePolling() {
        if (!Sh2DrcBlockOptimizer.PollType.PWM.supported) {
            return;
        }
        switch (PollSysEventManager.instance.anyPollerActive()) {
            case 1 -> checkPoller(MASTER);
            case 2 -> checkPoller(SLAVE);
            case 3 -> {
                checkPoller(MASTER);
                checkPoller(SLAVE);
            }
        }
    }

    private void checkPoller(CpuDeviceAccess cpu) {
        Sh2DrcBlockOptimizer.PollerCtx c = PollSysEventManager.instance.getPoller(cpu);
        if (c.isPollingActive() && PWM == c.event) {
            PollSysEventManager.instance.fireSysEvent(cpu, c.event);
        }
    }

    public void writeMono(int value) {
        writeFifo(ctx.fifoLeft, value);
        writeFifo(ctx.fifoRight, value);
    }

    public void newFrame() {
        if (verbose)
            LOG.info("Samples per frame: {}, stepsPerFrame: {}, dreqPerFrame: {}",
                    pwmSamplesPerFrame, stepsPerFrame, dreqPerFrame);
        pwmSamplesPerFrame = 0;
        stepsPerFrame = 0;
        dreqPerFrame = 0;
        playSupport.onNewFrame();
    }

    @Override
    public void step(int cycles) {
        if (verbose) stepsPerFrame += cycles;
        if (!ctx.pwmEnable) return;
        stepInternal(cycles);
    }

    private void stepInternal(int cycles) {
        ctx.sh2TicksToNextPwmSample -= cycles;
        if (ctx.sh2TicksToNextPwmSample <= 0) {
            ctx.sh2TicksToNextPwmSample = ctx.cycle;
            assert ctx.sh2TicksToNextPwmSample > 0;
            if (verbose) pwmSamplesPerFrame++;
            //sample range should be [0,cycle], let's clamp to [sld, cycle - sld]
            ctx.ls = Math.min(ctx.cycle - SAMPLE_LIMIT_DELTA, readFifo(fifoMapLeft.fifo, fifoMapLeft.channel) + SAMPLE_LIMIT_DELTA);
            ctx.rs = Math.min(ctx.cycle - SAMPLE_LIMIT_DELTA, readFifo(fifoMapRight.fifo, fifoMapRight.channel) + SAMPLE_LIMIT_DELTA);
            assert ctx.ls >= SAMPLE_LIMIT_DELTA && ctx.rs >= SAMPLE_LIMIT_DELTA;
            if (PWM_USE_BLIP) {
                playSupport.playSample(ctx.ls, ctx.rs);
            }
            if (--ctx.sh2ticksToNextPwmInterrupt == 0) {
                intControls[MASTER.ordinal()].setIntPending(IntControl.Sh2Interrupt.PWM_06, true);
                intControls[SLAVE.ordinal()].setIntPending(IntControl.Sh2Interrupt.PWM_06, true);
                ctx.sh2ticksToNextPwmInterrupt = ctx.interruptInterval;
                dreq();
            }
        }
        if (!PWM_USE_BLIP) {
            ctx.sh2TicksToNextPwmSample -= cycles;
            if (ctx.sh2TicksToNext22khzSample <= 0) {
                playSupport.playSample(ctx.ls, ctx.rs);
                ctx.sh2TicksToNext22khzSample = CYCLE_22khz;
                assert ctx.sh2TicksToNext22khzSample > 0;
            }
        }
    }

    private void stepOne() {
        if (--ctx.sh2TicksToNextPwmSample == 0) {
            ctx.sh2TicksToNextPwmSample = ctx.cycle;
            pwmSamplesPerFrame++;
            //sample range should be [0,cycle], let's clamp to [sld, cycle - sld]
            ctx.ls = Math.min(ctx.cycle - SAMPLE_LIMIT_DELTA, readFifo(fifoMapLeft.fifo, fifoMapLeft.channel) + SAMPLE_LIMIT_DELTA);
            ctx.rs = Math.min(ctx.cycle - SAMPLE_LIMIT_DELTA, readFifo(fifoMapRight.fifo, fifoMapRight.channel) + SAMPLE_LIMIT_DELTA);
            assert ctx.ls >= SAMPLE_LIMIT_DELTA && ctx.rs >= SAMPLE_LIMIT_DELTA;
            if (PWM_USE_BLIP) {
                playSupport.playSample(ctx.ls, ctx.rs);
            }
            if (--ctx.sh2ticksToNextPwmInterrupt == 0) {
                intControls[MASTER.ordinal()].setIntPending(IntControl.Sh2Interrupt.PWM_06, true);
                intControls[SLAVE.ordinal()].setIntPending(IntControl.Sh2Interrupt.PWM_06, true);
                ctx.sh2ticksToNextPwmInterrupt = ctx.interruptInterval;
                dreq();
            }
        }
        if (!PWM_USE_BLIP) {
            if (--ctx.sh2TicksToNext22khzSample == 0) {
                playSupport.playSample(ctx.ls, ctx.rs);
                ctx.sh2TicksToNext22khzSample = CYCLE_22khz;
            }
        }
    }

    private void dreq() {
        if (ctx.dreqEn) {
            //NOTE this should trigger on channel one for BOTH sh2s
            dmac[MASTER.ordinal()].dmaReqTriggerPwm(PWM_DMA_CHANNEL, true);
            dmac[SLAVE.ordinal()].dmaReqTriggerPwm(PWM_DMA_CHANNEL, true);
            dreqPerFrame++;
        }
    }

    public void setIntControls(IntControl... intControls) {
        this.intControls = intControls;
    }

    public void setDmac(DmaC... dmac) {
        this.dmac = dmac;
    }

    public void setPwmProvider(PwmProvider p) {
        this.playSupport = p;
        playSupport.updatePwmCycle(ctx.cycle);
    }

    @Override
    public void saveContext(ByteBuffer buffer) {
        StepDevice.super.saveContext(buffer);
        buffer.put(Util.serializeObject(ctx));
    }

    @Override
    public void loadContext(ByteBuffer buffer) {
        StepDevice.super.loadContext(buffer);
        Serializable s = Util.deserializeObject(buffer);
        assert s instanceof PwmContext;
        ctx = (PwmContext) s;
        updateChannelMap();
    }

    @Override
    public void init() {
        writeBuffers(sysRegsMd, sysRegsSh2, PWM_LCH_PW.addr, (1 << PWM_FIFO_EMPTY_BIT_POS), Size.WORD);
        writeBuffers(sysRegsMd, sysRegsSh2, PWM_RCH_PW.addr, (1 << PWM_FIFO_EMPTY_BIT_POS), Size.WORD);
        writeBuffers(sysRegsMd, sysRegsSh2, PWM_MONO.addr, (1 << PWM_FIFO_EMPTY_BIT_POS), Size.WORD);
        handlePwmControlSh2(MASTER, PWM_CTRL.addr, 0, Size.WORD); //init interruptInterval
    }

    @Override
    public void reset() {
        init();
        playSupport.reset();
        LOG.info("PWM reset done");
    }
}
