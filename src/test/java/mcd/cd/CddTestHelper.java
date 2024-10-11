package mcd.cd;

import mcd.McdDeviceHelper.McdLaunchContext;
import mcd.cdd.Cdd;
import mcd.cdd.Cdd.CddRequest;
import mcd.cdd.ExtendedCueSheet;
import mcd.dict.MegaCdDict;
import omegadrive.sound.msumd.CueFileParser;
import omegadrive.util.Size;
import org.junit.jupiter.api.Assertions;

import static mcd.cdd.Cdd.CDD_REG_NUM;
import static mcd.dict.MegaCdDict.MDC_SUB_GATE_REGS_MASK;
import static mcd.dict.MegaCdDict.RegSpecMcd.*;
import static mcd.dict.MegaCdDict.getRegSpec;
import static omegadrive.util.BufferUtil.CpuDeviceAccess.SUB_M68K;
import static omegadrive.util.BufferUtil.readBuffer;

/**
 * Federico Berti
 * <p>
 * Copyright 2024
 */
public class CddTestHelper {

    private static CueFileParser.MsfHolder msfHolder = new CueFileParser.MsfHolder();

    //at 75hz
    static int cddWaitLimit = 1000;

    public static void insertDiscEnableHock(McdLaunchContext lc, ExtendedCueSheet extendedCueSheet) {
        lc.cdd.tryInsert(extendedCueSheet);
        //hock enable
        lc.cdd.write(MCD_CDD_CONTROL, MCD_CDD_CONTROL.addr, 4, Size.BYTE);
    }

    public static void cddRequest(McdLaunchContext lc, CddRequest request) {
        lc.cdd.write(MCD_CDD_COMM5, MCD_CDD_COMM5.addr, Cdd.CddCommand.Request.ordinal(), Size.BYTE);
        lc.cdd.write(MCD_CDD_COMM6, MCD_CDD_COMM6.addr + 1, request.ordinal(), Size.BYTE);
        setCommandChecksum(lc);
    }

    public static void cddRequest(McdLaunchContext lc, Cdd.CddCommand command, int lba) {
        CueFileParser.toMSF(lba, msfHolder);
        lc.cdd.write(MCD_CDD_COMM5, MCD_CDD_COMM5.addr, command.ordinal(), Size.BYTE);
        int[] vals = {msfHolder.minute / 10, msfHolder.minute % 10,
                msfHolder.second / 10, msfHolder.second % 10, msfHolder.frame / 10, msfHolder.frame % 10};
        int baseAddr = MCD_CDD_COMM6.addr;
        for (int i = 0; i < 6; i++) {
            MegaCdDict.RegSpecMcd regSpec = getRegSpec(SUB_M68K, baseAddr + i);
            lc.cdd.write(regSpec, baseAddr + i, vals[i], Size.BYTE);
        }
        setCommandChecksum(lc);
    }

    public static void setCommandChecksum(McdLaunchContext lc) {
        int checksum = 0;
        for (int i = 0; i < CDD_REG_NUM - 1; i++) {
            checksum +=
                    readBuffer(lc.memoryContext.commonGateRegsBuf, (MCD_CDD_COMM5.addr + i) & MDC_SUB_GATE_REGS_MASK,
                            Size.BYTE);
        }
        checksum = ~checksum;
        lc.cdd.write(MCD_CDD_COMM9, MCD_CDD_COMM9.addr + 1, checksum & 0xF, Size.BYTE);
    }

    public static void clearCmdReg(McdLaunchContext lc) {
        int baseAddr = MCD_CDD_COMM5.addr;
        for (int i = 0; i < 9; i++) {
            MegaCdDict.RegSpecMcd regSpec = getRegSpec(SUB_M68K, baseAddr + i);
            lc.cdd.write(regSpec, baseAddr + i, 0, Size.BYTE);
        }
    }


    public static void waitCddStatus(McdLaunchContext lc, Cdd.CddStatus status) {
        int cnt = 0;
        Cdd.CddStatus lastStatus = null;
        do {
            lc.cdd.step(1);
            int res = readBuffer(lc.memoryContext.commonGateRegsBuf, (MCD_CDD_COMM0.addr) & MDC_SUB_GATE_REGS_MASK,
                    Size.BYTE);
            lastStatus = Cdd.statusVals[res];
            cnt++;
        } while (lastStatus != status && cnt < cddWaitLimit);
        Assertions.assertTrue(cnt < cddWaitLimit, "act: " + lastStatus + " vs exp: " + status);
    }

    public static String setMsfGetTestString(int lba, int[] input) {
        int[] vals = input.clone();
        CueFileParser.toMSF(lba, msfHolder);
        vals[2] = msfHolder.minute / 10;
        vals[3] = msfHolder.minute % 10;
        vals[4] = msfHolder.second / 10;
        vals[5] = msfHolder.second % 10;
        vals[6] = msfHolder.frame / 10;
        vals[7] = msfHolder.frame % 10;
        vals[9] = Cdd.getCddChecksum(vals);
        return CddLogDecoder.toTestString(vals);
    }
}
