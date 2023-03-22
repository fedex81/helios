package s32x.util.debug;

import omegadrive.util.LogHelper;
import omegadrive.util.Size;
import org.slf4j.Logger;
import s32x.dict.S32xDict;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import static omegadrive.util.Util.th;
import static s32x.bus.Sh2Bus.SH2_MEM_ACCESS_STATS;

/**
 * Federico Berti
 * <p>
 * Copyright 2023
 */
public class MemAccessStats {

    private static final Logger LOG = LogHelper.getLogger(MemAccessStats.class.getSimpleName());

    public static final MemAccessStats NO_STATS = new MemAccessStats();
    //printCnt needs to be a multiple of 2
    static final long printCnt = 0x1FF_FFFF + 1, printCntMask = printCnt - 1;

    long[][] readHits = SH2_MEM_ACCESS_STATS ? new long[3][0x100] : null;
    long[][] writeHits = SH2_MEM_ACCESS_STATS ? new long[3][0x100] : null;

    long cnt = 0, readCnt;

    static final Function<Map<String, Double>, String> mapToStr = m ->
            m.entrySet().stream().sorted(Map.Entry.<String, Double>comparingByValue().reversed()).
                    map(Objects::toString).collect(Collectors.joining("\n"));

    public void addMemHit(boolean read, int address, Size size) {
        long[][] ref = read ? readHits : writeHits;
        int idx = size.ordinal();
        ref[size.ordinal()][(address >>> S32xDict.SH2_PC_AREA_SHIFT) & 0xFF]++;
        readCnt += read ? 1 : 0;
        if ((++cnt & printCntMask) == 0) {
            Map<String, Double> rm = new HashMap<>(), wm = new HashMap<>();
            long writeCnt = cnt - readCnt;
            for (int i = 0; i < 3; i++) {
                for (int j = 0; j < ref[i].length; j++) {
                    if (readHits[i][j] > 0) {
                        addStatString("read, ", readHits[i][j], i, j, rm);
                    }
                    if (writeHits[i][j] > 0) {
                        addStatString("write, ", writeHits[i][j], i, j, wm);
                    }
                }
            }
            LOG.info(th(cnt) + "\n" + mapToStr.apply(rm));
            LOG.info(th(cnt) + "\n" + mapToStr.apply(wm));
        }
    }

    private void addStatString(String head, long cnt, int i, int j, Map<String, Double> map) {
        if (cnt > 0) {
            String s = head + Size.values()[i] + "," + th(j) + "," + cnt;
            double readPerc = 100d * cnt / readCnt;
            double totPerc = 100d * cnt / cnt;
            map.put(s, readPerc);
        }
    }
}