package omegadrive;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import omegadrive.input.InputProvider.PlayerNumber;
import omegadrive.system.SystemProvider.SystemEvent;
import omegadrive.util.LogHelper;
import org.slf4j.Logger;

/**
 * Federico Berti
 * <p>
 * Copyright 2022
 */
public class UserConfigHolder {

    private static final Logger LOG = LogHelper.getLogger(UserConfigHolder.class.getSimpleName());
    public static final Table<PlayerNumber, SystemEvent, Object> userEventObjectMap = HashBasedTable.create();

    public static void addUserConfig(SystemEvent event, Object parameter) {
        switch (event) {
            case PAD_SETUP_CHANGE -> {
                String[] s1 = parameter.toString().split(":");
                PlayerNumber pn = PlayerNumber.valueOf(s1[0]);
                Object prev = userEventObjectMap.put(pn, event, s1[1]);
                if (prev != null) {
                    LOG.info("{} {} replace: {} -> {}", event, pn, prev, s1[1]);
                }
            }
        }
    }
}