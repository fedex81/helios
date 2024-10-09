package mcd.cdd;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.Table;
import m68k.cpu.Cpu;
import omegadrive.util.BufferUtil.CpuDeviceAccess;
import omegadrive.util.LogHelper;
import omegadrive.vdp.util.MemView;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;

import static m68k.cpu.Cpu.PC_MASK;
import static omegadrive.util.BufferUtil.CpuDeviceAccess.M68K;
import static omegadrive.util.BufferUtil.CpuDeviceAccess.SUB_M68K;
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
    private static final Map<Integer, CdMemRegion> subMemRegionMap = new HashMap<>();
    private static final Map<Integer, CdMemRegion> mainMemRegionMap = new HashMap<>();
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
        enabled = Boolean.valueOf(System.getProperty("68k.debug", "false"));
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

        /* SUB */

        addToSubMemRegion("cddFlags0", 0x580a, 1);
        addToSubMemRegion("cddFlags1", 0x580b, 1);
        addToSubMemRegion("cddFlags2", 0x580c, 1);
        addToSubMemRegion("cddFlags3", 0x580d, 1);
        addToSubMemRegion("cddFlags4", 0x580e, 1);
        addToSubMemRegion("cddFlags5", 0x580f, 1);
        addToSubMemRegion("cddFlags6", 0x5810, 1);
        addToSubMemRegion("cddFlags7", 0x5811, 1);
        addToSubMemRegion("requestedTrackNumber", 0x5819, 1);
        addToSubMemRegion("cddCommandWorkArea", 0x582a, 16); //?

        addToSubMemRegion("cddCommandBuffer", 0x583a, 10); //?
        addToSubMemRegion("cddStatusCache", 0x5844, 10); //?
        addToSubMemRegion("cddStatusCode", 0x584e, 2);
        addToSubMemRegion("cddFirstTrack", 0x5850, 1);
        addToSubMemRegion("cddLastTrack", 0x5851, 1);
        addToSubMemRegion("cddVersion", 0x5852, 2);
        addToSubMemRegion("cddLeadOutTime", 0x5854, 4);
        addToSubMemRegion("cddTocTable", 0x5858, 80); //?

        addToSubMemRegion("cddTocCache", 0x59E8, 4);
        addToSubMemRegion("cddAbsFrameTime", 0x59EC, 4);
        addToSubMemRegion("cddRelFrameTime", 0x59F0, 4);
        addToSubMemRegion("currentTrackNumber", 0x59F4, 1);
        addToSubMemRegion("discControlCode", 0x59F5, 1);

        addToSubMemRegion("cddWatchdogCounter", 0x5a02, 2);

        addToSubMemRegion("cdcFlags0", 0x5a06, 1);
        addToSubMemRegion("cdcFlags1", 0x5a07, 1);
        addToSubMemRegion("cdcRegisterCache", 0x5a08, 4);
        addToSubMemRegion("cdcStatus", 0x5a38, 2);
        addToSubMemRegion("cdcFrameHeader", 0x5a3a, 4);
        addToSubMemRegion("cdcStat0", 0x5a40, 1);
        addToSubMemRegion("cdcStat1", 0x5a41, 1);
        addToSubMemRegion("cdcStat2", 0x5a42, 1);
        addToSubMemRegion("cdcStat3", 0x5a43, 1);
        addToSubMemRegion("cdcRingBuffer", 0x5a44, 60); //??

        addToSubMemRegion("cdbStat", 0x5a80, 4);
        addToSubMemRegion("scdFlags0", 0x5a84, 2);
        addToSubMemRegion("scdPcktBuffer", 0x5a90, 4);
        addToSubMemRegion("scdPackBuffer", 0x5a94, 4);
        addToSubMemRegion("scdQcodBuffer", 0x5a98, 4);
        addToSubMemRegion("cddFaderCache", 0x5abc, 2);

        addToSubMemRegion("cdbCommand", 0x5ac8, 10); //?
        addToSubMemRegion("cdbControlStatus", 0x5ad2, 2);
        addToSubMemRegion("cdbResumeAddress", 0x5ad4, 4);
        addToSubMemRegion("cdbDelayedRoutine", 0x5ad8, 4);

        addToSubMemRegion("cdbCommandCache", 0x5ae0, 2);
        addToSubMemRegion("cdbArg1Cache", 0x5ae2, 4);
        addToSubMemRegion("cdbArg2Cache", 0x5ae6, 4);


        addToSubMemRegion("cdbSpindownDelay", 0x5b0c, 2);
        addToSubMemRegion("cbtFlags", 0x5b24, 1);
        addToSubMemRegion("cbtResumeAddress", 0x5b26, 4);
        addToSubMemRegion("cbtResumeData", 0x5B2A, 4);
        addToSubMemRegion("cbtDeferredAddress", 0x5b2e, 4);
        addToSubMemRegion("dataStartSector", 0x5b32, 2);
        addToSubMemRegion("readSectorStart", 0x5b34, 4);
        addToSubMemRegion("readSectorCount", 0x5b38, 4);
        addToSubMemRegion("readSectorLoopCount", 0x5b3c, 2);
        addToSubMemRegion("bootHeaderAddress", 0x5b3e, 4);
        addToSubMemRegion("ipDstAddress", 0x5b42, 4);
        addToSubMemRegion("spDstAddress", 0x5b46, 4);
        addToSubMemRegion("dataBufferAddress", 0x5b4a, 4);
        addToSubMemRegion("headerBuffer", 0x5b4e, 4);
        addToSubMemRegion("frameCheckValue", 0x5b52, 1);

        addToSubMemRegion("vBlankFlag", 0x5ea4, 1);

        addToSubMemRegion("vblankFlag", 0x833C + 0x3, 1);
        addToSubMemRegion("mainCommDataCache", 0x833C + 0xE, 0x10);
        addToSubMemRegion("subCommDataBuffer", 0x833C + 0x1E, 0x10);
        addToSubMemRegion("discType", 0x833C + 0x42, 1);
        addToSubMemRegion("biosStatus.currentStatus", 0x833C + 0x44, 2);
        addToSubMemRegion("biosStatus.previousStatus", 0x833C + 0x46, 2);
        addToSubMemRegion("biosStatus.absFrameTime", 0x833C + 0x48, 4);
        addToSubMemRegion("biosStatus.relFrameTime", 0x833C + 0x4c, 4);
        addToSubMemRegion("biosStatus.trackNumber", 0x833C + 0x50, 1);
        addToSubMemRegion("biosStatus.flag", 0x833C + 0x51, 1);
        addToSubMemRegion("biosStatus.firstTrack", 0x833C + 0x52, 1);
        addToSubMemRegion("biosStatus.lastTrack", 0x833C + 0x53, 1);
        addToSubMemRegion("biosStatus.leadOutTime", 0x833C + 0x54, 4);
        addToSubMemRegion("biosStatus.cddStatusCode", 0x833C + 0x58, 1);

        addToSubMemRegion("cdcHeaderBuffer", 0x833C + 0x135C, 4);
        addToSubMemRegion("cdcDataBuffer", 0x833C + 0x1360, 0x920); //TODO Check len
        addToSubMemRegion("cdcDataBuffer??", 0x833C + 0x1c80, 0x10);

        addToSubMemRegion("oldGfxCompleteHandler", 0x833C + 0x1c98, 4);
        addToSubMemRegion("gfxCompleteFlag", 0x833C + 0x1c9e, 1);
        addToSubMemRegion("gfxPrevCompleteFlag", 0x833C + 0x1c9f, 1);
        addToSubMemRegion("gfxAllOpsComplete", 0x833C + 0x1ca2, 2);
        //size??
//        addToSubMemRegion("gfxObject0", 0x833C + 0x1caa, 1);
//        addToSubMemRegion("gfxObject1", 0x833C + 0x1d4a, 1);
//        addToSubMemRegion("gfxVectorTable0", 0x833C + 0x1c98, 1);
//        addToSubMemRegion("gfxVectorTable1", 0x833C + 0x1c98, 1);

        /* MAIN */

//        addToMainMemRegion("securityBlockAnimations", 0xFFC000, 0x1400);
//        addToMainMemRegion("decompressionBufferArea", 0xFFE000, 0x1700);
//        addToMainMemRegion("nemesisDecompressionBuffer ", 0xFFF700, 0x200);
//        addToMainMemRegion("spriteDataBuffer ", 0xFFF900, 0x280);
//        addToMainMemRegion("paletteDataBuffer ", 0xFFFB80, 0x80);
        addToMainMemRegion("jmpToUserVBlankHandler ", 0xFFFDA8, 6);
        addToMainMemRegion("vdpRegisterCache ", 0xFFFDB4, 38);
        addToMainMemRegion("mainCpuCommFlagCache ", 0xFFFDDE, 1);
        addToMainMemRegion("subCpuCommFlagCache ", 0xFFFDDf, 1);
        addToMainMemRegion("mainCpuCommRegsCache ", 0xFFFDE0, 16);
        addToMainMemRegion("subCpuCommRegsCache ", 0xFFFDF0, 16);
        addToMainMemRegion("p1ButtonsHeld ", 0xFFFE20, 1);
        addToMainMemRegion("p1ButtonsTapped ", 0xFFFE21, 1);
        addToMainMemRegion("p2ButtonsHeld ", 0xFFFE22, 1);
        addToMainMemRegion("p2ButtonsTapped ", 0xFFFE23, 1);
        addToMainMemRegion("p1DirectionHoldTimer", 0xFFFE24, 1);
        addToMainMemRegion("p1DirectionHoldTimer", 0xFFFE25, 1);
        /**
         * (gets cleared at the end of a V-BLANK handler)
         *      Bit 0 - Enables sprite updates for handlers that handles them.
         *      Bit 1 - Enable user defined V-BLANK handler.
         */
        addToMainMemRegion("vBlankFlagsTemp", 0xFFFE26, 1);
        addToMainMemRegion("userVBlankCounter", 0xFFFE27, 1);
        //(overrides bit 1 of 0xFFFE26).
        addToMainMemRegion("disableVdpUpdatesAndUserVBlank", 0xFFFE28, 1);
        addToMainMemRegion("paletteUpdateFlag", 0xFFFE29, 1); //(must use bit 0).
        addToMainMemRegion("randomRngSeed", 0xFFFE2A, 2);
        addToMainMemRegion("baseTileIdOfFont", 0xFFFE2c, 2);
        addToMainMemRegion("strideVdpPlane", 0xFFFE2e, 2); //(plane width * 2).
        addToMainMemRegion("pointerObjSpriteRamBuffer", 0xFFFE30, 4);
        addToMainMemRegion("pointerObjIndexTable", 0xFFFE34, 4);
        addToMainMemRegion("objSpriteLinkValue", 0xFFFE38, 2);
        addToMainMemRegion("biosStatus", 0xFFFE3A, 2);
        addToMainMemRegion("absoluteDiscTime", 0xFFFE3C, 2);
        addToMainMemRegion("relativeDiscTime", 0xFFFE3E, 2);
        addToMainMemRegion("endDiscTime", 0xFFFE40, 3);
        addToMainMemRegion("currentDiscTrack", 0xFFFE43, 1);
        addToMainMemRegion("firstDiscTrack", 0xFFFE44, 1);
        addToMainMemRegion("lastDiscTrack", 0xFFFE45, 1);
        addToMainMemRegion("paletteFadeInOffset", 0xFFFE46, 1);
        addToMainMemRegion("paletteFadeInLen", 0xFFFE47, 1);
        addToMainMemRegion("paletteFadeInIntensity", 0xFFFE48, 2);
        addToMainMemRegion("paletteFadeInData", 0xFFFE4A, 4);
        //(should be a longword, but a bug messes with this).
        addToMainMemRegion("msfTimeSubtractionMinuend", 0xFFFE4E, 2);
        addToMainMemRegion("msfTimeSubtractionSubtrahend", 0xFFFE50, 2);

        invCdBiosEntryPointMap = ImmutableBiMap.copyOf(cdBiosEntryPointMap).inverse();
        invCdBiosFunMap.entrySet().stream().forEach(e -> cdFunTable.put(cdBiosEntryPointMap.get(CDBIOS), e.getKey(), e.getValue()));
        invCdBootFunMap.entrySet().stream().forEach(e -> cdFunTable.put(cdBiosEntryPointMap.get(CDBOOT), e.getKey(), e.getValue()));
        invCdBuramFunMap.entrySet().stream().forEach(e -> cdFunTable.put(cdBiosEntryPointMap.get(BURAM), e.getKey(), e.getValue()));
        HIGH_ENTRY = invCdBiosEntryPointMap.keySet().stream().max(Integer::compareTo).orElseThrow();
        LOW_ENTRY = invCdBiosEntryPointMap.keySet().stream().min(Integer::compareTo).orElseThrow();
    }

    public static String getFunctionName(int pc, int code) {
        return cdFunTable.row(pc).getOrDefault(code, NO_FUNCTION);
    }

    public static String getCdBiosEntryPointIfAny(int pc) {
        return invCdBiosEntryPointMap.getOrDefault(pc, NO_ENTRY_POINT);
    }

    public static void logCdPcInfo(int pc, Cpu m68k) {
        if (!enabled) {
            return;
        }
        if (pc >= LOW_ENTRY && pc <= HIGH_ENTRY) {
            String res = CdBiosHelper.getCdBiosEntryPointIfAny(pc);
            if (res != CdBiosHelper.NO_ENTRY_POINT) {
                LOG.warn("calling sub_bios entry point {}({})", res, th(pc));
                Map<Integer, String> rowFunc = cdFunTable.row(pc);
                if (rowFunc != null && !rowFunc.isEmpty()) {
                    String fname = CdBiosHelper.getFunctionName(pc, m68k.getDataRegisterByte(0));
                    assert !NO_FUNCTION.equals(fname);
                    handleCdBiosCalls(fname, m68k);
                    LOG.warn("calling fn {} #{}({})", res, fname, th(pc));
                }
            }
        }
    }

    private static void handleCdBiosCalls(String fname, Cpu cpu) {
        if ("ROMREADN".equalsIgnoreCase(fname)) {
            int memAddr = cpu.getAddrRegisterLong(0);
            int firstSector = cpu.readMemoryLong(memAddr);
            int length = cpu.readMemoryLong(memAddr + 4);
            LOG.warn("CDBIOS {}, firstSector {}, length {}", fname, firstSector, length);
        }
    }

    private static void addToSubMemRegion(String name, int startInclusive, int len) {
        addToMemRegion(subMemRegionMap, name, startInclusive, len);
    }

    private static void addToMainMemRegion(String name, int startInclusive, int len) {
        addToMemRegion(mainMemRegionMap, name, startInclusive, len);
    }

    private static void addToMemRegion(Map<Integer, CdMemRegion> map, String name, int startInclusive, int len) {
        CdMemRegion r = new CdMemRegion();
        r.name = name;
        r.startInclusive = startInclusive;
        r.endInclusive = startInclusive + len;

        for (int i = startInclusive; i < r.endInclusive; i++) {
            assert map.get(i) == null : name + "," + th(i);
            map.put(i, r);
        }
    }

    public static void checkMainMemRegion(byte[] data, int address) {
        checkMemRegion(M68K, data, address & PC_MASK);
    }

    public static void checkSubMemRegion(byte[] data, int address) {
        checkMemRegion(SUB_M68K, data, address);
    }

    public static void checkMemRegion(CpuDeviceAccess cpu, byte[] data, int address) {
        if (!enabled) {
            return;
        }
        assert cpu == M68K || cpu == SUB_M68K;
        var map = cpu == M68K ? mainMemRegionMap : subMemRegionMap;
        CdMemRegion r = map.get(address);
        if (r != null) {
            boolean change = printMemRegion(cpu, data, r);
//            if("biosStatus".equals(r.name) && change){
//                System.out.println("here");
//            }
        }

    }

    private static final int MAX_AREA_SIZE = 0x10_000;
    private static final int MAX_AREA_MASK = MAX_AREA_SIZE - 1;
    private static final int[][] memAreaHash = new int[2][MAX_AREA_SIZE];
    private static final StringBuilder sb = new StringBuilder();

    private static boolean printMemRegion(CpuDeviceAccess cpu, byte[] data, CdMemRegion r) {
        int startIncl = r.startInclusive & MAX_AREA_MASK;
        int endIncl = r.endInclusive & MAX_AREA_MASK;
        MemView.fillFormattedString(sb, data, startIncl, endIncl);
        int hc = sb.toString().hashCode();
        int[] areaHash = memAreaHash[cpu == M68K ? 0 : 1];
        //print first write or when changed
        boolean hasChanged = areaHash[startIncl] == 0 || areaHash[startIncl] != hc;
        if (hasChanged) {
            LOG.info("{} {} update\n{}", cpu, r, sb);
            areaHash[startIncl] = hc;
        }
        sb.setLength(0);
        return hasChanged;
    }
}
