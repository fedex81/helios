package s32x.sh2.prefetch;

import omegadrive.util.LogHelper;
import omegadrive.util.Size;
import org.slf4j.Logger;
import s32x.sh2.Sh2Helper;
import s32x.sh2.Sh2Instructions;
import s32x.sh2.cache.Sh2Cache;
import s32x.util.S32xUtil;

import java.util.StringJoiner;

/**
 * Federico Berti
 * <p>
 * Copyright 2022
 */
public interface Sh2Prefetcher {

    Logger LOG = LogHelper.getLogger(Sh2Prefetcher.class.getSimpleName());

    boolean verbose = false;

    void fetch(Sh2Helper.FetchResult ft, S32xUtil.CpuDeviceAccess cpu);

    int fetchDelaySlot(int pc, Sh2Helper.FetchResult ft, S32xUtil.CpuDeviceAccess cpu);

    void invalidateCachePrefetch(Sh2Cache.CacheInvalidateContext ctx);

    default void dataWrite(S32xUtil.CpuDeviceAccess cpu, int addr, int val, Size size) {
    }

    default void newFrame() {
    }

    class Sh2BlockUnit extends Sh2Instructions.Sh2InstructionWrapper {
        public Sh2BlockUnit next;
        public int pc;

        public Sh2BlockUnit(Sh2Instructions.Sh2InstructionWrapper i) {
            super(i.opcode, i.inst, i.runnable);
        }

        @Override
        public String toString() {
            return new StringJoiner(", ", Sh2BlockUnit.class.getSimpleName() + "[", "]")
                    .add("pc=" + pc)
                    .add("inst=" + inst)
                    .add("opcode=" + opcode)
                    .toString();
        }
    }

    class Stats {
        static String format = "%s pfTot: %d, pfMissPerc: %f, pfDsMissPerc: %f";
        public long pfMiss, pfTotal, pfDsMiss;
        private final S32xUtil.CpuDeviceAccess cpu;

        public Stats(S32xUtil.CpuDeviceAccess cpu) {
            this.cpu = cpu;
        }

        @Override
        public String toString() {
            return String.format(format, cpu, pfTotal, 1.0 * pfMiss / pfTotal, 1.0 * pfDsMiss / pfTotal);
        }

        public void addMiss() {
            if ((++pfMiss & 0xFF) == 0) {
                LOG.info(toString());
            }
        }

        public void addDelaySlotMiss() {
            addMiss();
            ++pfDsMiss;
        }
    }
}