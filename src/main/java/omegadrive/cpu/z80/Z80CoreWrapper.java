/*
 * Z80CoreWrapper
 * Copyright (c) 2018-2019 Federico Berti
 * Last modified: 11/10/19 15:05
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package omegadrive.cpu.z80;

import com.google.common.annotations.VisibleForTesting;
import omegadrive.SystemLoader.SystemType;
import omegadrive.bus.model.MdZ80BusProvider;
import omegadrive.bus.model.Z80BusProvider;
import omegadrive.cpu.z80.Z80LoopHelper.LoopType;
import omegadrive.cpu.z80.debug.Z80CoreWrapperFastDebug;
import omegadrive.savestate.StateUtil;
import omegadrive.util.LogHelper;
import omegadrive.util.Size;
import omegadrive.util.Util;
import org.slf4j.Logger;
import s32x.sh2.Sh2Helper;
import z80core.Z80;
import z80core.Z80State;

import java.nio.ByteBuffer;

import static omegadrive.util.Util.th;

public class Z80CoreWrapper implements Z80Provider {

    public final static boolean STOP_ON_EXCEPTION;
    public static final boolean Z80_DEBUG;

    public static boolean Z80_POLL_EN;

    public static final int Z80_POLL_DELAY;
    private final static Logger LOG = LogHelper.getLogger(Z80CoreWrapper.class.getSimpleName());

    static {
        STOP_ON_EXCEPTION =
                Boolean.parseBoolean(System.getProperty("z80.stop.on.exception", "false"));
        Z80_DEBUG = Boolean.parseBoolean(System.getProperty("z80.debug", "false"));
        Z80_POLL_DELAY = Integer.parseInt(System.getProperty("helios.z80.poll.delay", "1000"));
        if (Z80_DEBUG) {
            LOG.info("z80 debug mode: true");
        }
        reloadPollDetection();
    }

    protected Z80 z80Core;
    protected Z80BusProvider z80BusProvider;
    protected Z80MemIoOps memIoOps;

    protected Z80LoopHelper loopHelper;

    protected final SystemType systemType;
    protected int instCyclesPenalty = 0;

    protected int loopDelay = 0;
    protected int memPtrInitVal;

    public static Z80CoreWrapper createInstance(SystemType systemType, Z80BusProvider busProvider) {
        Z80CoreWrapper w = null;
        switch (systemType) {
            case MD:
            case S32X:
            case MEGACD:
            case MEGACD_S32X:
                w = createMdInstanceInternal(systemType, busProvider);
                break;
            case GG:
            case SMS:
            case COLECO:
            case SG_1000:
            case MSX:
                w = createInstanceInternal(systemType, busProvider);
                break;
            default:
                LOG.error("Unexpected system: {}", systemType);
        }
        //sms: ace of Aces, needs SP != 0xFFFF, 0xDFF0 is commonly used
        if (systemType == SystemType.SMS) {
            w.z80Core.setRegSP(0xDFF0);
        }
        return w;
    }

    protected Z80CoreWrapper(SystemType st) {
        this.systemType = st;
    }

    protected Z80CoreWrapper setupInternal(Z80State z80State) {
        z80Core = new Z80(memIoOps, null);
        loopHelper = new Z80LoopHelper(systemType, z80Core, z80BusProvider);
        z80BusProvider.attachDevices(this, loopHelper);
        if (z80State != null) {
            z80Core.setZ80State(z80State);
        }
        z80Core.setRegSP(memIoOps.getPcUpperLimit());
        memPtrInitVal = memIoOps.getPcUpperLimit();
        reloadPollDetection();
        return this;
    }

    private static Z80CoreWrapper createInstanceInternal(SystemType systemType, Z80BusProvider busProvider) {
        assert !systemType.isMdBased();
        Z80CoreWrapper w = Z80_DEBUG ? new Z80CoreWrapperFastDebug(systemType) : new Z80CoreWrapper(systemType);
        w.z80BusProvider = busProvider;
        w.memIoOps = Z80MemIoOps.createInstance(w.z80BusProvider);
        return w.setupInternal(null);
    }

    private static Z80CoreWrapper createMdInstanceInternal(SystemType systemType, Z80BusProvider busProvider) {
        assert systemType.isMdBased();
        Z80CoreWrapper w = Z80_DEBUG ? new Z80CoreWrapperFastDebug(systemType) : new Z80CoreWrapper(systemType);
        w.z80BusProvider = MdZ80BusProvider.createInstance(busProvider);
        w.memIoOps = Z80MemIoOps.createMdInstance(w.z80BusProvider);
        return w.setupInternal(null);
    }

    //NOTE: halt sets PC = PC - 1
    @Override
    public int executeInstruction() {
        memIoOps.reset();
        instCyclesPenalty = 0;
        try {
            if (Z80_POLL_EN) {
                checkLoops();
            }
            z80Core.execute();
        } catch (Exception | Error e) {
            LOG.error("z80 exception", e);
            LOG.error("Z80State: {}", Z80Helper.toString(z80Core.getZ80State()));
            LOG.error("Halting Z80");
            z80Core.setHalted(true);
            if(STOP_ON_EXCEPTION){
                Util.waitForever();
            }
        }
        return (int) (memIoOps.getTstates()) + instCyclesPenalty + loopDelay;
    }
    private void checkLoops() {
        LoopType lt = loopHelper.checkLoops().loopType;
            /**
             * TODO busy loops on RAM/YM2612 are delayed by 10 cycles, this could affect audio playback
             */
        loopDelay = lt == LoopType.NONE ? 0 : (lt == LoopType.BUSY_LOOP ? 50 : Z80_POLL_DELAY);
    }

    //From the Z80UM.PDF document, a reset clears the interrupt enable, PC and
    //registers I and R, then sets interrupt status to mode 0.
    @Override
    public void reset() {
        z80Core.setHalted(false);
        z80Core.setINTLine(false);
        z80Core.setNMI(false);
        z80Core.setPendingEI(false);
        z80Core.setMemPtr(memPtrInitVal);

        //from GenPlusGx
        z80Core.setRegPC(0);
        z80Core.setRegI(0);
        z80Core.setRegR(0);
        z80Core.setIFF1(false);
        z80Core.setIFF2(false);
        z80Core.setIM(Z80.IntMode.IM0);
        loopDelay = 0;
        LogHelper.logWarnOnce(LOG, "Z80 Reset, PC: {}", th(z80Core.getRegPC()));
    }

    //If the Z80 has interrupts disabled when the frame interrupt is supposed
    //to occur, it will be missed, rather than made pending.
    @Override
    public boolean interrupt(boolean value) {
        loopDelay = 0;
        return memIoOps.setActiveINT(value);
    }

    @Override
    public void triggerNMI() {
        loopDelay = 0;
        z80Core.triggerNMI();
    }

    /**
     * Driven by
     * 1. helios.z80.poll.detect
     * 2. only for S32X, Sh2Config.z80LoopDetect (takes precedence over #1)
     */
    private static void reloadPollDetection() {
        Z80_POLL_EN = Boolean.parseBoolean(System.getProperty("helios.z80.poll.detect",
                "" + Sh2Helper.Sh2Config.get().z80LoopDetect));
        LOG.info("z80 poll detection: {}, sh2Config: {}, delay: {}", Z80_POLL_EN,
                Sh2Helper.Sh2Config.get().z80LoopDetect, Z80_POLL_DELAY);
    }

    @Override
    public boolean isHalted() {
        return z80Core.isHalted();
    }

    @Override
    public int readMemory(int address) {
        return z80BusProvider.read(address, Size.BYTE);
    }

    @Override
    public void writeMemory(int address, int data) {
        z80BusProvider.write(address, data, Size.BYTE);
    }

    @Override
    public Z80BusProvider getZ80BusProvider() {
        return z80BusProvider;
    }

    @Override
    public void addCyclePenalty(int value) {
        instCyclesPenalty += value;
    }

    @Override
    public void loadContext(ByteBuffer buffer) {
        z80Core.setZ80State(StateUtil.loadZ80State(buffer));
    }

    @Override
    public void saveContext(ByteBuffer buffer) {
        StateUtil.saveZ80State(buffer, z80Core.getZ80State());
    }

    @Override
    public void loadZ80State(Z80State z80State) {
        this.z80Core.setZ80State(z80State);
    }

    @Override
    public Z80State getZ80State() {
        return z80Core.getZ80State();
    }

    @VisibleForTesting
    public Z80 getZ80() {
        return z80Core;
    }
}