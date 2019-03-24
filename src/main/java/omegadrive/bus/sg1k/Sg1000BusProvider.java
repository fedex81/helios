package omegadrive.bus.sg1k;

import omegadrive.bus.BaseBusProvider;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * ${FILE}
 * <p>
 * Federico Berti
 * <p>
 * Copyright 2018
 */
public interface Sg1000BusProvider extends BaseBusProvider {

    Logger LOG = LogManager.getLogger(Sg1000BusProvider.class.getSimpleName());

    static Sg1000BusProvider createBus() {
        return new Sg1000Bus();
    }

    void handleVdpInterruptsZ80();
}
