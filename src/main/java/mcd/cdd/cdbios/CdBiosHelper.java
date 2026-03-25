package mcd.cdd.cdbios;

import com.google.common.base.Enums;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.Table;
import m68k.cpu.Cpu;
import mcd.cdd.Cdd;
import omegadrive.util.BufferUtil;
import omegadrive.util.BufferUtil.CpuDeviceAccess;
import omegadrive.util.HexUtil;
import omegadrive.util.LogHelper;
import omegadrive.util.Size;
import org.slf4j.Logger;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static m68k.cpu.Cpu.C_FLAG;
import static m68k.cpu.Cpu.PC_MASK;
import static mcd.cdd.cdbios.CdBiosModel.*;
import static mcd.cdd.cdbios.CdBiosModel.CdBiosEntryPoint.*;
import static mcd.cdd.cdbios.CdBiosModel.CdBiosFunction.*;
import static mcd.cdd.cdbios.CdBiosModel.CdBootFunction.*;
import static mcd.cdd.cdbios.CdBiosModel.CdBuramFunction.BRMINIT;
import static mcd.cdd.cdbios.CdBiosModel.OtherBiosFun.BOOTSTAT;
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

    public static final String RETURN_POINT_SUFFIX = "_return";

    public static final NameCode NO_ENTRY_POINT = invalidInstance("NONE");
    public static final NameCode NO_FUNCTION = invalidInstance("ERROR");
    private static final Map<NameCode, Integer> cdBiosEntryPointMap = new HashMap<>();
    private static final Map<Integer, NameCode> invCdBiosEntryPointMap;
    private static final Table<Integer, Integer, NameCode> cdFunTable = HashBasedTable.create();
    private static final Map<Integer, CdMemRegion> subMemRegionMap = new HashMap<>();
    private static final Map<Integer, CdMemRegion> mainMemRegionMap = new HashMap<>();
    private static final int LOW_ENTRY, HIGH_ENTRY;
    private static final String[] noInputParameters = {CBTINIT.name(), CBTINT.name(), CDBSTAT.name(),
            CBTCHKSTAT.name(), CDBCHK.name(), CDCSTAT.name(), CDCREAD.name(), CDCACK.name(), SCDSTOP.name(),
            MSCSTOP.name(), CDCSTOP.name()};

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

        Map<NameCode, Integer> cdBiosFunMap = new HashMap<>();
        Map<NameCode, Integer> cdBootFunMap = new HashMap<>();
        Map<NameCode, Integer> cdBuramFunMap = new HashMap<>();
        for (var fun : CdBiosFunction.values()) {
            cdBiosFunMap.put(fun, fun.code);
        }
        for (var fun : CdBootFunction.values()) {
            cdBootFunMap.put(fun, fun.code);
        }
        for (var fun : CdBuramFunction.values()) {
            cdBuramFunMap.put(fun, fun.code);
        }
        for (var fun : CdBiosEntryPoint.values()) {
            cdBiosEntryPointMap.put(fun, fun.getCode());
        }
//        addBiosUs200WRegions();
        var invCdBiosFunMap = ImmutableBiMap.copyOf(cdBiosFunMap).inverse();
        var invCdBootFunMap = ImmutableBiMap.copyOf(cdBootFunMap).inverse();
        var invCdBuramFunMap = ImmutableBiMap.copyOf(cdBuramFunMap).inverse();
        invCdBiosEntryPointMap = ImmutableBiMap.copyOf(cdBiosEntryPointMap).inverse();
        invCdBiosFunMap.entrySet().stream().forEach(e -> cdFunTable.put(cdBiosEntryPointMap.get(_CDBIOS), e.getKey(), e.getValue()));
        invCdBootFunMap.entrySet().stream().forEach(e -> cdFunTable.put(cdBiosEntryPointMap.get(_CDBOOT), e.getKey(), e.getValue()));
        invCdBuramFunMap.entrySet().stream().forEach(e -> cdFunTable.put(cdBiosEntryPointMap.get(_BURAM), e.getKey(), e.getValue()));
        HIGH_ENTRY = invCdBiosEntryPointMap.keySet().stream().max(Integer::compareTo).orElseThrow();
        LOW_ENTRY = invCdBiosEntryPointMap.keySet().stream().min(Integer::compareTo).orElseThrow();

        var set = subMemRegionMap.values().stream().map(cdr -> cdr.name).
                filter(n -> n.endsWith(RETURN_POINT_SUFFIX)).
                map(n -> n.replace(RETURN_POINT_SUFFIX, "")).collect(Collectors.toSet());
        Arrays.sort(noInputParameters);
    }

    public static NameCode getFunctionName(int pc, int code) {
        return cdFunTable.row(pc).getOrDefault(code, NO_FUNCTION);
    }

    public static NameCode getCdBiosEntryPointIfAny(int pc) {
        return invCdBiosEntryPointMap.getOrDefault(pc, NO_ENTRY_POINT);
    }

    private static Cpu secCpu; //TODO hack

    public static void logCdPcInfo(int pc, Cpu m68k) {
        if (!enabled) {
            return;
        }
        secCpu = m68k;
        if (pc >= LOW_ENTRY && pc <= HIGH_ENTRY) {
            NameCode res = CdBiosHelper.getCdBiosEntryPointIfAny(pc);
            if (res != CdBiosHelper.NO_ENTRY_POINT) {
                LOG.warn("calling sub_bios entry point {}({})", res, th(pc));
                Map<Integer, NameCode> rowFunc = cdFunTable.row(pc);
                if (rowFunc != null && !rowFunc.isEmpty()) {
                    NameCode fname = CdBiosHelper.getFunctionName(pc, m68k.getDataRegisterByte(0));
                    assert !NO_FUNCTION.equals(fname);
                    LOG.warn("calling fn {} #{}({})", res, fname, th(pc));
                    checkInParameters(fname, m68k);
                }
            }
        }
    }

    private static void checkInParameters(NameCode nc, Cpu cpu) {
        if (DRVINIT == nc) {
            int memAddr = cpu.getAddrRegisterLong(0);
            subMemRegionMap.remove(memAddr);
            if (subMemRegionMap.get(memAddr) == null) {
                addToSubMemRegion(getReturnString(nc), memAddr, 2);
            }
            LOG.warn("CDBIOS {}, ramPointer {}", nc, th(memAddr));
        } else if (CBTCHKDISC == nc) {
            LOG.warn("CDBIOS {}, ramPointer {}", nc, th(cpu.getAddrRegisterLong(0)));
        } else if (ROMREADN == nc || ROMREAD == nc || ROMREADE == nc || ROMSEEK == nc) {
            int memAddr = cpu.getAddrRegisterLong(0);
            LOG.warn("CDBIOS {}, ramPointer {}", nc, th(memAddr));
        } else if (CDBTOCREAD == nc) {
            int tocnum = cpu.getDataRegisterWord(1);
            LOG.warn("CDBIOS {}, songNumber(hex) {}", nc, th(tocnum));
        } else if (CDCTRN == nc) {
            int destDataBuffer = cpu.getAddrRegisterLong(0); //0x8420
            int destHeaderBuffer = cpu.getAddrRegisterLong(1); //0x5b4e
            LOG.warn("CDBIOS {}, sectorBufferPointer: {}, " +
                    "headerBufferPointer: {}", nc, th(destDataBuffer), th(destHeaderBuffer));
        } else if (WONDERCHK == nc || WONDERREQ == nc) {
            LOG.warn("CDBIOS {}, TODO", nc);
        } else if (BRMINIT == nc) {
            int memAddr0 = cpu.getAddrRegisterLong(0);
            int memAddr1 = cpu.getAddrRegisterLong(1);
            LOG.warn("BURAM {}, ramPointer: {}, " +
                    "displayStringBufferPointer: {}", nc, th(memAddr0), th(memAddr1));
        } else if (SCDINIT == nc) {
            int memAddr0 = cpu.getAddrRegisterLong(0);
            if (subMemRegionMap.get(memAddr0) == null) {
                addToSubMemRegion(getReturnString(nc), memAddr0, 0x750);
            }
            LOG.warn("CDBIOS {}, ramPointer: {}", nc, th(memAddr0));
        } else if (SCDSTART == nc) {
            int memAddr0 = cpu.getDataRegisterWord(1);
            var mode = SubcodeProcessingMode.values()[memAddr0 & 3];
            LOG.warn("CDBIOS {}, subcodeProcessingMode: {}({})", nc, mode, th(memAddr0));
        } else if (SCDREAD == nc) {
            int memAddr0 = cpu.getAddrRegisterLong(0);
            if (subMemRegionMap.get(memAddr0) == null) {
                addToSubMemRegion(getReturnString(nc), memAddr0, 24);
            }
            LOG.warn("CDBIOS {}, ramPointer: {}", nc, th(memAddr0));
        } else if (MSCPLAY == nc) {
            int memAddr0 = cpu.getAddrRegisterLong(0);
            int tno = cpu.readMemoryWord(memAddr0);
            LOG.warn("CDBIOS {}, tableAddr: {}, trackNumber: {}", nc, th(memAddr0), tno);
        } else if (CDCSTARTP == nc) {
            int val = cpu.getDataRegisterWord(1);
            String mode = val == 0xE ? "CD+G" : "TODO";
            LOG.warn("CDBIOS {}, value: {}, cdcMode: {}", nc, th(val), mode);
        } else {
            if (Arrays.binarySearch(noInputParameters, nc.name()) < 0) {
                LogHelper.logWarnOnce(LOG, "Not handled: {}", nc);
            }
        }
    }

    enum SubcodeProcessingMode {
        NO_CHANNEL, R_W, PQ, ALL;
    }

    public static void handleCdBiosCalls(NameCode nc, CdMemRegion region, Cpu cpu) {
        int memAddr = region.startInclusive;
        if (ROMREADN == nc) { //TODO find return point
            int firstSector = cpu.readMemoryLong(memAddr);
            int length = cpu.readMemoryLong(memAddr + 4);
            LOG.warn("CDBIOS {}, firstSector {}, length {}", region.name, firstSector, length);
        } else if (CDBSTAT == nc) {
            int biosStatus = cpu.readMemoryWord(memAddr);
            int bs1 = (biosStatus >> 8) & 0xFF;
            int bs0 = biosStatus & 0xFF;
            var bsb1 = BiosStatusByte1.getBiosStatus(bs1);
            var bsb0 = BiosStatusByte0.getBiosStatus(bsb1, bs0);
            int ledStatus = cpu.readMemoryWord(memAddr + 2);
            int cddStatus1 = cpu.readMemoryLong(memAddr + 4);
            int absTime = cpu.readMemoryLong(memAddr + 8);
            int relTime = cpu.readMemoryLong(memAddr + 12);
            int firstSong = cpu.readMemoryByte(memAddr + 16);
            int lastSong = cpu.readMemoryByte(memAddr + 17);
            int driveVersion = cpu.readMemoryByte(memAddr + 18);
            int flags = cpu.readMemoryByte(memAddr + 19);
            int leadOutMsf = cpu.readMemoryLong(memAddr + 20);
            int bp0 = (cddStatus1 >>> 24) & 0xFF;
            int bp1 = (cddStatus1 >>> 16) & 0xFF;
            int bp2 = (cddStatus1 >>> 8) & 0xFF;
            int bp3 = cddStatus1 & 0xFF;
            LOG.warn("CDBIOS {}, biosStatus: {}({}) {}({}), ledStatus {}, statusCode {}, reportCode {}, " +
                            "discControlCode {}, songNumber {}, absTimeMsf {}, relTimeMsf {}, firstTrack {}, " +
                            "lastTrack {}, flags: {}, leadOutMsf {}", region.name,
                    bsb1, th(bs1), bsb0, th(bs0), th(ledStatus), Cdd.statusVals[bp0], th(bp1), th(bp2), th(bp3),
                    th(absTime), th(relTime), firstSong, lastSong, flags, leadOutMsf);
        } else if (CBTCHKDISC == nc) { //TODO find return point
            boolean cc = !cpu.isFlagSet(C_FLAG); //carryClear = true -> OK, !cc(cs) -> BUSY
            LOG.warn("CDBIOS {}, canBoot {}", nc, cc);
        } else if (BOOTSTAT == nc) {
            int type = cpu.readMemoryWord(memAddr);
            BootstatEnum be = type == -1 ? BootstatEnum.CD_NOTREADY : null;
            if (type >= 0) {
                be = type < BootstatEnum.vals.length ? BootstatEnum.vals[type] : null;
            }
            LOG.warn("CDBIOS {}, cdType {}({})", region.name, be, th(type));
        } else if (DRVINIT == nc) {
            int firstTrack = cpu.readMemoryByte(memAddr);
            int lastTrack = cpu.readMemoryByte(memAddr + 1);
            boolean autoPlay = (firstTrack & 0x80) == 0x80;
            LOG.warn("CDBIOS {}, firstTrack {}, lastTrack {}, autoPlay {}", nc, firstTrack, lastTrack, autoPlay);
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
            assert map.get(i) == null : name + "," + map.get(i) + "," + th(i);
            map.put(i, r);
        }
    }

    public static void checkMainMemRegion(byte[] data, int address, Size size) {
        checkMemRegion(M68K, data, address & PC_MASK, size);
    }

    public static void checkSubMemRegion(byte[] data, int address, Size size) {
        checkMemRegion(SUB_M68K, data, address, size);
    }

    public static void checkMemRegion(CpuDeviceAccess cpu, byte[] data, int address, Size size) {
        if (!enabled) {
            return;
        }
        switch (size) {
            case WORD -> {
                checkMemRegionByte(cpu, data, address);
                checkMemRegionByte(cpu, data, address + 1);
            }
            case LONG -> {
                checkMemRegionByte(cpu, data, address);
                checkMemRegionByte(cpu, data, address + 1);
                checkMemRegionByte(cpu, data, address + 2);
                checkMemRegionByte(cpu, data, address + 3);
            }
            case BYTE -> checkMemRegionByte(cpu, data, address);
        }
    }

    public static void checkMemRegionByte(CpuDeviceAccess cpu, byte[] data, int address) {
        if (!enabled) {
            return;
        }
        assert cpu == M68K || cpu == SUB_M68K;
        var map = cpu == M68K ? mainMemRegionMap : subMemRegionMap;
        CdMemRegion r = map.get(address);
        if (r != null) {
            boolean change = printMemRegion(cpu, data, r);
        }
    }

    private static final int MAX_AREA_SIZE = 0x10_000;
    private static final int MAX_AREA_MASK = MAX_AREA_SIZE - 1;
    private static final int[][] memAreaHash = new int[2][MAX_AREA_SIZE];
    private static final StringBuilder sb = new StringBuilder();

    private static boolean printMemRegion(CpuDeviceAccess cpu, byte[] data, CdMemRegion r) {
        int startIncl = r.startInclusive & MAX_AREA_MASK;
        int endIncl = r.endInclusive & MAX_AREA_MASK;
        HexUtil.fillFormattedString(sb, data, startIncl, endIncl);
        int hc = BufferUtil.hashCode(data, startIncl, endIncl);
        int[] areaHash = memAreaHash[cpu == M68K ? 0 : 1];
        //print first write or when changed
        boolean hasChanged = areaHash[startIncl] == 0 || areaHash[startIncl] != hc;
        boolean noPrint = r.name.equalsIgnoreCase("cdcDataBuffer");
        if (hasChanged) {
            if (!noPrint) {
                LOG.info("{} {} update\n{}", cpu, r, sb);
            }
            Optional<NameCode> optNc = getNameCodeIfAny(r.name);
            areaHash[startIncl] = hc;
            optNc.ifPresent(nameCode -> handleCdBiosCalls(nameCode, r, secCpu));
        }
        sb.setLength(0);
        return hasChanged;
    }

    public static Optional<NameCode> getNameCodeIfAny(String retStr) {
        if (!retStr.contains(RETURN_POINT_SUFFIX)) {
            return Optional.empty();
        }
        String tk = retStr.replace(RETURN_POINT_SUFFIX, "");
        return Stream.of(CdBiosFunction.class, CdBootFunction.class, CdBuramFunction.class, OtherBiosFun.class)
                .map(clazz -> {
                    // Cast to Class<Enum> to satisfy Enums.getIfPresent's <T extends Enum<T>>
                    @SuppressWarnings({"unchecked", "rawtypes"})
                    com.google.common.base.Optional<NameCode> opt = Enums.getIfPresent((Class) clazz, tk);
                    return opt.toJavaUtil();
                })
                .flatMap(Optional::stream)
                .findFirst();
    }

    static void addBiosUs200WRegions() {
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
        addToSubMemRegion("ROMREADN_return", 0x5b34, 4); //readSectorStart
        addToSubMemRegion("readSectorCount", 0x5b38, 4);
        addToSubMemRegion("readSectorLoopCount", 0x5b3c, 2);
        addToSubMemRegion("bootHeaderAddress", 0x5b3e, 4);
        addToSubMemRegion("ipDstAddress", 0x5b42, 4);
        addToSubMemRegion("spDstAddress", 0x5b46, 4);
        addToSubMemRegion("dataBufferAddress", 0x5b4a, 4);
        addToSubMemRegion("headerBuffer", 0x5b4e, 4);
        addToSubMemRegion("frameCheckValue", 0x5b52, 1);

        addToSubMemRegion(getReturnString(CDBSTAT), 0x5e80, 20);
        addToSubMemRegion(getReturnString(BOOTSTAT), 0x00005EA0, 2);
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

        //two regions [0x8420, 0x8420 + 0x920] and [0x8c20, 0x8c20 + 0x920] -> [0x8420, 0x9540]
        addToSubMemRegion(getReturnString(CDCTRN), 0x8420, 0x1120);
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
    }

    private static String getReturnString(NameCode nc) {
        return nc + RETURN_POINT_SUFFIX;
    }
}
