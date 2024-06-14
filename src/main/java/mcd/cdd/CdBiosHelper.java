package mcd.cdd;

import com.google.common.collect.ImmutableBiMap;
import omegadrive.util.LogHelper;
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
    public static final int CDBIOS_PC = 0x5f22;

    public static final String NO_ENTRY_POINT = "NONE";
    private static final Map<String, Integer> cdBiosFunMap = new HashMap<>();
    private static final Map<String, Integer> cdBiosEntryPointMap = new HashMap<>();
    private static final Map<Integer, String> invCdBiosFunMap;
    private static final Map<Integer, String> invCdBiosEntryPointMap;

    private static final int LOW_ENTRY, HIGH_ENTRY;

    static {
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
//        m.put("CBTINIT",0x0000);
//        m.put("CBTINT",0x0001);
//        m.put("CBTOPENDISC",0x0002);
//        m.put("CBTOPENSTAT",0x0003);
//        m.put("CBTCHKDISC",0x0004);
//        m.put("CBTCHKSTAT",0x0005);
//        m.put("CBTIPDISC",0x0006);
//        m.put("CBTIPSTAT",0x0007);
//        m.put("CBTSPDISC",0x0008);
//        m.put("CBTSPSTAT",0x0009);
//        m.put("BRMINIT",0x0000);
//        m.put("BRMSTAT",0x0001);
//        m.put("BRMSERCH",0x0002);
//        m.put("BRMREAD",0x0003);
//        m.put("BRMWRITE",0x0004);
//        m.put("BRMDEL",0x0005);
//        m.put("BRMFORMAT",0x0006);
//        m.put("BRMDIR",0x0007);
//        m.put("BRMVERIFY",0x0008);
        invCdBiosFunMap = ImmutableBiMap.copyOf(cdBiosFunMap).inverse();
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

        invCdBiosEntryPointMap = ImmutableBiMap.copyOf(cdBiosEntryPointMap).inverse();
        HIGH_ENTRY = invCdBiosEntryPointMap.keySet().stream().max(Integer::compareTo).orElseThrow();
        LOW_ENTRY = invCdBiosEntryPointMap.keySet().stream().min(Integer::compareTo).orElseThrow();
    }

    public static String getFunctionName(int code) {
        return invCdBiosFunMap.getOrDefault(code, "ERROR");
    }

    public static String getCdBiosEntryPointIfAny(int pc) {
        return invCdBiosEntryPointMap.getOrDefault(pc, NO_ENTRY_POINT);
    }

    public static void logCdPcInfo(int pc, int d0) {
        if (pc == CdBiosHelper.CDBIOS_PC) {
            LogHelper.logWarnOnceForce(LOG, "calling CDBIOS #{}({})", CdBiosHelper.getFunctionName(d0), th(pc));
//            LOG.info("calling CDBIOS #{}({})", CdBiosHelper.getFunctionName(d0), th(pc));
        } else if (pc >= LOW_ENTRY && pc <= HIGH_ENTRY) {
            String res = CdBiosHelper.getCdBiosEntryPointIfAny(pc);
            if (res != CdBiosHelper.NO_ENTRY_POINT) {
//                LOG.info("calling cdbios entry point {}({})", res, th(pc));
                LogHelper.logWarnOnceForce(LOG, "calling cdbios entry point {}({})", res, th(pc));
            }
        }
    }
}
