package s32x.event;

import org.junit.jupiter.api.Test;

import static s32x.dict.S32xDict.S32xRegType;
import static s32x.event.PollSysEventManager.SysEvent;
import static s32x.event.PollSysEventManager.SysEvent.INT;
import static s32x.event.PollSysEventManager.SysEvent.START_POLLING;
import static s32x.sh2.drc.Ow2DrcOptimizer.PollType;

/**
 * Federico Berti
 * <p>
 * Copyright 2022
 */
public class SysEventManagerTest {

    @Test
    public void testSysEventVsPollTypeVsS32xRegType() {
        for (SysEvent e : SysEvent.values()) {
            if (e == START_POLLING || e == INT || e.name().startsWith("SH2")) {
                continue;
            }
            System.out.println(PollType.valueOf(e.name()));
        }

        for (S32xRegType s : S32xRegType.values()) {
            System.out.println(PollType.valueOf(s.name()));
            System.out.println(SysEvent.valueOf(s.name()));
        }
    }

}
