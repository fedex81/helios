package omegadrive;

import omegadrive.memory.MemoryProvider;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * ${FILE}
 * <p>
 * Federico Berti
 * <p>
 * Copyright 2018
 */
public class GameQuirks {

    private static Logger LOG = LogManager.getLogger(GameQuirks.class.getSimpleName());

    public static boolean isSsf2Mapper(MemoryProvider memory) {
        int[] ssf2Title = new int[]{
                0x53, 0x55, 0x50, 0x45, 0x52, 0x20, 0x53, 0x54, 0x52, 0x45, 0x45, 0x54, 0x20, 0x46, 0x49, 0x47,
                0x48, 0x54, 0x45, 0x52, 0x32, 0x20, 0x54, 0x68, 0x65, 0x20, 0x4E, 0x65, 0x77, 0x20, 0x43, 0x68,
                0x61, 0x6C, 0x6C, 0x65, 0x6E, 0x67, 0x65, 0x72, 0x73, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20
        };

        int[] titanOverdrive2Title = new int[]{
                0x4F, 0x56, 0x45, 0x52, 0x44, 0x52, 0x49, 0x56, 0x45, 0x20, 0x32, 0x20, 0x20, 0x20, 0x20, 0x20,
        };
        boolean isSsf2Mapper = true;
        int offset = 0x150;
        for (int i = offset; i < offset + ssf2Title.length; i++) {
            if (memory.readCartridgeByte(i) != ssf2Title[i - offset]) {
                isSsf2Mapper = false;
            }
        }

        if (!isSsf2Mapper) {
            isSsf2Mapper = true;
            for (int i = offset; i < offset + titanOverdrive2Title.length; i++) {
                if (memory.readCartridgeByte(i) != titanOverdrive2Title[i - offset]) {
                    isSsf2Mapper = false;
                }
            }
        }
        if (isSsf2Mapper) {
            LOG.info("SSF2 Mapper!");
        }
        return isSsf2Mapper;
    }
}
