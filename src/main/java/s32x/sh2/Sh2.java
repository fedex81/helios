package s32x.sh2;

import omegadrive.Device;
import omegadrive.util.LogHelper;
import org.slf4j.Logger;

/**
 * Federico Berti
 * <p>
 * Copyright 2022
 */
public interface Sh2 extends Device {

    Logger LOG = LogHelper.getLogger(Sh2.class.getSimpleName());

    int posT = 0;
    int posS = 1;
    int posQ = 8;
    int posM = 9;

    int flagT = 1 << posT;
    int flagS = 1 << posS;
    int flagIMASK = 0x000000f0;
    int flagQ = 1 << posQ;
    int flagM = 1 << posM;

    int SR_MASK = 0x3F3;

    int ILLEGAL_INST_VN = 4; //vector number
    int ILLEGAL_SLOT_INST_VN = 6; //vector number

    void reset(Sh2Context context);

    void run(Sh2Context masterCtx);

    void setCtx(Sh2Context ctx);

    default void printDebugMaybe(int opcode) {
        //NO-OP
    }
}