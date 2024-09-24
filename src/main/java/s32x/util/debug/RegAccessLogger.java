package s32x.util.debug;

import omegadrive.util.LogHelper;
import omegadrive.util.MdRuntimeData;
import omegadrive.util.Size;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static omegadrive.util.Util.th;
import static s32x.dict.S32xDict.RegSpecS32x.*;

/**
 * Federico Berti
 * <p>
 * Copyright 2022
 */
public class RegAccessLogger {
    private static final Logger LOG = LogHelper.getLogger(RegAccessLogger.class.getSimpleName());

    private static final boolean ENABLE = false;
    private static final boolean ENABLE_UNIQ = false;

    private static final Map<String, Integer> log = new HashMap<>();
    private static final Map<String, Set<String>> logUniq = new HashMap<>();
    private static final Set<String> ignoredUniq = new HashSet<>();


    public static void regAccess(String regSpec, int address, int val, Size size, boolean read) {
        if (ENABLE_UNIQ && !read) regAccessUniqueWrite(regSpec, address, val, size);
        if (!ENABLE) {
            return;
        }
        if (regSpec.startsWith("FRT_") || regSpec.startsWith("FBCR")) {
            return;
        }
        String s = MdRuntimeData.getAccessTypeExt() + "," + (read ? "R," : "W,") + regSpec + "," + th(address) + "," + size;
        Integer v = log.get(s);
        if (v == null || v != val) {
            LOG.info("{},{}", s, th(val));
            log.put(s, val);
        }
    }

    public static void regAccessUniqueWrite(String regSpec, int address, int val, Size size) {
        if (!ENABLE_UNIQ) {
            return;
        }
        if (regSpec.startsWith("COMM") || regSpec.startsWith("AF") || regSpec.startsWith(MD_FIFO_REG.name()) ||
                PWM_LCH_PW.name().equals(regSpec) || PWM_RCH_PW.name().equals(regSpec) || PWM_MONO.name().equals(regSpec) ||
                regSpec.startsWith("DIV_")) {
            if (ignoredUniq.add(regSpec)) {
                LOG.info("Ignoring: {}", regSpec);
            }
            return;
        }
        Set<String> set = logUniq.computeIfAbsent(regSpec, k -> new HashSet<>());
        String str = th(address) + "," + th(val) + "," + size;
        if (set.add(str)) {
            System.out.println(regSpec + ":" + str);
//            LOG.info(regSpec + ":" + str);
        }
    }
}
