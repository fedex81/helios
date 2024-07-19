package mcd.cdd;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.Table;
import m68k.cpu.MC68000;
import omegadrive.util.LogHelper;
import omegadrive.vdp.util.MemView;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;

import static omegadrive.util.Util.th;

/**
 * Federico Berti
 * <p>
 * Copyright 2024
 */
public class CdBiosHelper {

    private static final Logger LOG = LogHelper.getLogger(CdBiosHelper.class.getSimpleName());
    public static final String CDBIOS = "_CDBIOS";
    public static final String CDBOOT = "_CDBOOT";
    public static final String BURAM = "_BURAM";

    public static final String NO_ENTRY_POINT = "NONE";
    public static final String NO_FUNCTION = "ERROR";
    private static final Map<String, Integer> cdBiosEntryPointMap = new HashMap<>();
    private static final Map<Integer, String> invCdBiosEntryPointMap;
    private static final Table<Integer, Integer, String> cdFunTable = HashBasedTable.create();
    private static final Table<String, Integer, Integer> memRegionTable = HashBasedTable.create();
    private static final Map<Integer, CdMemRegion> memRegionMap = new HashMap<>();
    private static final int LOW_ENTRY, HIGH_ENTRY;

    public static final boolean enabled;

    static class CdMemRegion {
        public String name;
        public int startInclusive, endInclusive;

        @Override
        public String toString() {
            return name + " [" + th(startInclusive) + "," + th(endInclusive) + "]";
        }
    }

    static {
        Map<String, Integer> cdBiosFunMap = new HashMap<>();
        Map<String, Integer> cdBootFunMap = new HashMap<>();
        Map<String, Integer> cdBuramFunMap = new HashMap<>();
        var m = cdBiosFunMap;
        m.put("MSCSTOP", 0x0002);
        m.put("MSCPAUSEON", 0x0003);
        m.put("MSCPAUSEOFF", 0x0004);
        m.put("MSCSCANFF", 0x0005);
        m.put("MSCSCANFR", 0x0006);
        m.put("MSCSCANOFF", 0x0007);
        m.put("ROMPAUSEON", 0x0008);
        m.put("ROMPAUSEOFF", 0x0009);
        m.put("DRVOPEN", 0x000A);
        m.put("DRVINIT", 0x0010);
        m.put("MSCPLAY", 0x0011);
        m.put("MSCPLAY1", 0x0012);
        m.put("MSCPLAYR", 0x0013);
        m.put("MSCPLAYT", 0x0014);
        m.put("MSCSEEK", 0x0015);
        m.put("MSCSEEKT", 0x0016);
        m.put("ROMREAD", 0x0017);
        m.put("ROMSEEK", 0x0018);
        m.put("MSCSEEK1", 0x0019);
        m.put("TESTENTRY", 0x001E);
        m.put("TESTENTRYLOOP", 0x001F);
        m.put("ROMREADN", 0x0020);
        m.put("ROMREADE", 0x0021);
        m.put("CDBCHK", 0x0080);
        m.put("CDBSTAT", 0x0081);
        m.put("CDBTOCWRITE", 0x0082);
        m.put("CDBTOCREAD", 0x0083);
        m.put("CDBPAUSE", 0x0084);
        m.put("FDRSET", 0x0085);
        m.put("FDRCHG", 0x0086);
        m.put("CDCSTART", 0x0087);
        m.put("CDCSTARTP", 0x0088);
        m.put("CDCSTOP", 0x0089);
        m.put("CDCSTAT", 0x008A);
        m.put("CDCREAD", 0x008B);
        m.put("CDCTRN", 0x008C);
        m.put("CDCACK", 0x008D);
        m.put("SCDINIT", 0x008E);
        m.put("SCDSTART", 0x008F);
        m.put("SCDSTOP", 0x0090);
        m.put("SCDSTAT", 0x0091);
        m.put("SCDREAD", 0x0092);
        m.put("SCDPQ", 0x0093);
        m.put("SCDPQL", 0x0094);
        m.put("LEDSET", 0x0095);
        m.put("CDCSETMODE", 0x0096);
        m.put("WONDERREQ", 0x0097);
        m.put("WONDERCHK", 0x0098);
        m = cdBootFunMap;
        m.put("CBTINIT", 0x0000);
        m.put("CBTINT", 0x0001);
        m.put("CBTOPENDISC", 0x0002);
        m.put("CBTOPENSTAT", 0x0003);
        m.put("CBTCHKDISC", 0x0004);
        m.put("CBTCHKSTAT", 0x0005);
        m.put("CBTIPDISC", 0x0006);
        m.put("CBTIPSTAT", 0x0007);
        m.put("CBTSPDISC", 0x0008);
        m.put("CBTSPSTAT", 0x0009);
        m = cdBuramFunMap;
        m.put("BRMINIT", 0x0000);
        m.put("BRMSTAT", 0x0001);
        m.put("BRMSERCH", 0x0002);
        m.put("BRMREAD", 0x0003);
        m.put("BRMWRITE", 0x0004);
        m.put("BRMDEL", 0x0005);
        m.put("BRMFORMAT", 0x0006);
        m.put("BRMDIR", 0x0007);
        m.put("BRMVERIFY", 0x0008);
        var m1 = cdBiosEntryPointMap;
        m1.put("_ADRERR", 0x00005F40);
        m1.put("_BOOTSTAT", 0x00005EA0);
        m1.put("_BURAM", 0x00005F16);
        m1.put("_CDBIOS", 0x00005F22);
        m1.put("_CDBOOT", 0x00005F1C);
        m1.put("_CDSTAT", 0x00005E80);
        m1.put("_CHKERR", 0x00005F52);
        m1.put("_CODERR", 0x00005F46);
        m1.put("_DEVERR", 0x00005F4C);
        m1.put("_LEVEL1", 0x00005F76);
        m1.put("_LEVEL2", 0x00005F7C);
        m1.put("_LEVEL3(TIMER INTERRUPT)", 0x00005F82);
        m1.put("_LEVEL4", 0x00005F88);
        m1.put("_LEVEL5", 0x00005F8E);
        m1.put("_LEVEL6", 0x00005F94);
        m1.put("_LEVEL7", 0x00005F9A);
        m1.put("_NOCOD0", 0x00005F6A);
        m1.put("_NOCOD1", 0x00005F70);
        m1.put("_SETJMPTBL", 0x00005F0A);
        m1.put("_SPVERR", 0x00005F5E);
        m1.put("_TRACE", 0x00005F64);
        m1.put("_TRAP00", 0x00005FA0);
        m1.put("_TRAP01", 0x00005FA6);
        m1.put("_TRAP02", 0x00005FAC);
        m1.put("_TRAP03", 0x00005FB2);
        m1.put("_TRAP04", 0x00005FB8);
        m1.put("_TRAP05", 0x00005FBE);
        m1.put("_TRAP06", 0x00005FC4);
        m1.put("_TRAP07", 0x00005FCA);
        m1.put("_TRAP08", 0x00005FD0);
        m1.put("_TRAP09", 0x00005FD6);
        m1.put("_TRAP10", 0x00005FDC);
        m1.put("_TRAP11", 0x00005FE2);
        m1.put("_TRAP12", 0x00005FE8);
        m1.put("_TRAP13", 0x00005FEE);
        m1.put("_TRAP14", 0x00005FF4);
        m1.put("_TRAP15", 0x00005FFA);
        m1.put("_TRPERR", 0x00005F58);
        m1.put("_USERCALL0(INIT)", 0x00005F28);
        m1.put("_USERCALL1(MAIN)", 0x00005F2E);
        m1.put("_USERCALL2(VINT)", 0x00005F34);
        m1.put("_USERCALL3(NOT DEFINED)", 0x00005F3A);
        m1.put("_USERMODE", 0x00005EA6);
        m1.put("_WAITVSYNC", 0x00005F10);
        var invCdBiosFunMap = ImmutableBiMap.copyOf(cdBiosFunMap).inverse();
        var invCdBootFunMap = ImmutableBiMap.copyOf(cdBootFunMap).inverse();
        var invCdBuramFunMap = ImmutableBiMap.copyOf(cdBuramFunMap).inverse();

        addToMemRegion("cddFlags0", 0x580a, 1);
        addToMemRegion("cddFlags1", 0x580b, 1);
        addToMemRegion("cddFlags2", 0x580c, 1);
        addToMemRegion("cddFlags3", 0x580d, 1);
        addToMemRegion("cddFlags4", 0x580e, 1);
        addToMemRegion("cddFlags5", 0x580f, 1);
        addToMemRegion("cddFlags6", 0x5810, 1);
        addToMemRegion("cddFlags7", 0x5811, 1);
        addToMemRegion("requestedTrackNumber", 0x5819, 1);
        addToMemRegion("cddCommandWorkArea", 0x582a, 16); //?

        addToMemRegion("cddCommandBuffer", 0x583a, 10); //?
        addToMemRegion("cddStatusCache", 0x5844, 10); //?
        addToMemRegion("cddStatusCode", 0x584e, 2);
        addToMemRegion("cddFirstTrack", 0x5850, 1);
        addToMemRegion("cddLastTrack", 0x5851, 1);
        addToMemRegion("cddVersion", 0x5852, 2);
        addToMemRegion("cddLeadOutTime", 0x5854, 4);
        addToMemRegion("cddTocTable", 0x5858, 20); //?

        addToMemRegion("cddTocCache", 0x59E8, 4);
        addToMemRegion("cddAbsFrameTime", 0x59EC, 4);
        addToMemRegion("cddRelFrameTime", 0x59F0, 4);
        addToMemRegion("currentTrackNumber", 0x59F4, 1);
        addToMemRegion("discControlCode", 0x59F5, 1);

        addToMemRegion("cddWatchdogCounter", 0x5a02, 2);

        addToMemRegion("cdcFlags0", 0x5a06, 1);
        addToMemRegion("cdcFlags1", 0x5a07, 1);
        addToMemRegion("cdcRegisterCache", 0x5a08, 4);
        addToMemRegion("cdcStatus", 0x5a38, 2);
        addToMemRegion("cdcFrameHeader", 0x5a3a, 4);
        addToMemRegion("cdcStat0", 0x5a40, 1);
        addToMemRegion("cdcStat1", 0x5a41, 1);
        addToMemRegion("cdcStat2", 0x5a42, 1);
        addToMemRegion("cdcStat3", 0x5a43, 1);
        addToMemRegion("cdcRingBuffer", 0x5a44, 60); //??

        addToMemRegion("cdbStat", 0x5a80, 4);
        addToMemRegion("scdFlags0", 0x5a84, 2);
        addToMemRegion("scdPcktBuffer", 0x5a90, 4);
        addToMemRegion("scdPackBuffer", 0x5a94, 4);
        addToMemRegion("scdQcodBuffer", 0x5a98, 4);
        addToMemRegion("cddFaderCache", 0x5abc, 2);

        addToMemRegion("cdbCommand", 0x5ac8, 10); //?
        addToMemRegion("cdbControlStatus", 0x5ad2, 2);
        addToMemRegion("cdbResumeAddress", 0x5ad4, 4);
        addToMemRegion("cdbDelayedRoutine", 0x5ad8, 4);

        addToMemRegion("cdbCommandCache", 0x5ae0, 2);
        addToMemRegion("cdbArg1Cache", 0x5ae2, 4);
        addToMemRegion("cdbArg2Cache", 0x5ae6, 4);


        addToMemRegion("cdbSpindownDelay", 0x5b0c, 2);
        addToMemRegion("cbtFlags", 0x5b24, 1);
        addToMemRegion("cbtResumeAddress", 0x5b26, 4);
        addToMemRegion("cbtResumeData", 0x5B2A, 4);
        addToMemRegion("cbtDeferredAddress", 0x5b2e, 4);
        addToMemRegion("dataStartSector", 0x5b32, 2);
        addToMemRegion("readSectorStart", 0x5b34, 4);
        addToMemRegion("readSectorCount", 0x5b38, 4);
        addToMemRegion("readSectorLoopCount", 0x5b3c, 2);
        addToMemRegion("bootHeaderAddress", 0x5b3e, 4);
        addToMemRegion("ipDstAddress", 0x5b42, 4);
        addToMemRegion("spDstAddress", 0x5b46, 4);
        addToMemRegion("dataBufferAddress", 0x5b4a, 4);
        addToMemRegion("headerBuffer", 0x5b4e, 4);
        addToMemRegion("frameCheckValue", 0x5b52, 1);

        addToMemRegion("vBlankFlag", 0x5ea4, 1);

        addToMemRegion("discType", 0x833C + 0x42, 1);

        invCdBiosEntryPointMap = ImmutableBiMap.copyOf(cdBiosEntryPointMap).inverse();
        invCdBiosFunMap.entrySet().stream().forEach(e -> cdFunTable.put(cdBiosEntryPointMap.get(CDBIOS), e.getKey(), e.getValue()));
        invCdBootFunMap.entrySet().stream().forEach(e -> cdFunTable.put(cdBiosEntryPointMap.get(CDBOOT), e.getKey(), e.getValue()));
        invCdBuramFunMap.entrySet().stream().forEach(e -> cdFunTable.put(cdBiosEntryPointMap.get(BURAM), e.getKey(), e.getValue()));
        HIGH_ENTRY = invCdBiosEntryPointMap.keySet().stream().max(Integer::compareTo).orElseThrow();
        LOW_ENTRY = invCdBiosEntryPointMap.keySet().stream().min(Integer::compareTo).orElseThrow();
        enabled = Boolean.valueOf(System.getProperty("68k.debug", "false"));
    }

    public static String getFunctionName(int pc, int code) {
        return cdFunTable.row(pc).getOrDefault(code, NO_FUNCTION);
    }

    public static String getCdBiosEntryPointIfAny(int pc) {
        return invCdBiosEntryPointMap.getOrDefault(pc, NO_ENTRY_POINT);
    }

    public static void logCdPcInfo(int pc, MC68000 cpu) {
        if (!enabled) {
            return;
        }
        if (pc >= LOW_ENTRY && pc <= HIGH_ENTRY) {
            String res = CdBiosHelper.getCdBiosEntryPointIfAny(pc);
            if (res != CdBiosHelper.NO_ENTRY_POINT) {
                LOG.warn("calling sub_bios entry point {}({})", res, th(pc));
                Map<Integer, String> rowFunc = cdFunTable.row(pc);
                if (rowFunc != null && !rowFunc.isEmpty()) {
                    String fname = CdBiosHelper.getFunctionName(pc, cpu.getDataRegisterByte(0));
                    assert !NO_FUNCTION.equals(fname);
                    handleCdBiosCalls(fname, cpu);
                    LOG.warn("calling fn {} #{}({})", res, fname, th(pc));
                }
            }
        }
    }

    private static void handleCdBiosCalls(String fname, MC68000 cpu) {
        if ("ROMREADN".equalsIgnoreCase(fname)) {
            int memAddr = cpu.getAddrRegisterLong(0);
            int firstSector = cpu.readMemoryLong(memAddr);
            int length = cpu.readMemoryLong(memAddr + 4);
            LOG.warn("CDBIOS {}, firstSector {}, length {}", fname, firstSector, length);
        }
    }

    private static void addToMemRegion(String name, int startInclusive, int len) {
        CdMemRegion r = new CdMemRegion();
        r.name = name;
        r.startInclusive = startInclusive;
        r.endInclusive = startInclusive + len;

        for (int i = startInclusive; i < r.endInclusive; i++) {
            assert memRegionMap.get(i) == null : th(i);
            memRegionMap.put(i, r);
        }
    }

    public static void checkMemRegion(byte[] data, int address) {
        if (!enabled) {
            return;
        }
        CdMemRegion r = memRegionMap.get(address);
        if (r != null) {
            printMemRegion(data, r);
        }
    }

    private static int[] lastCdbStatHash = new int[0xA000];
    private static final StringBuilder sb = new StringBuilder();

    private static void printMemRegion(byte[] data, CdMemRegion r) {
        MemView.fillFormattedString(sb, data, r.startInclusive, r.endInclusive);
        int hc = sb.toString().hashCode();
        //print first write or when changed
        boolean print = lastCdbStatHash[r.startInclusive] == 0 || lastCdbStatHash[r.startInclusive] != hc;
        if (print) {
            LOG.info("{} update\n{}", r, sb);
            lastCdbStatHash[r.startInclusive] = hc;
        }
        sb.setLength(0);
    }
}
