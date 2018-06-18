package omegadrive.util;

//import com.jmxwebtools.JmxLauncher;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * ${FILE}
 * <p>
 * Federico Berti
 * <p>
 * Copyright 2018
 * <p>
 * TODO remove dependency from com.jmxwebtools
 */
public class JmxBridge {

    private static Logger LOG = LogManager.getLogger(JmxBridge.class.getSimpleName());

    private static boolean JMX_SUPPORT_FLAG;
//    private static JmxLauncher jmxLauncher;

    static {
        try {
            Class.forName("com.jmxwebtools.JmxLauncher");
//            jmxLauncher = JmxLauncher.getInstance().launch("GenesisEmu");
            JMX_SUPPORT_FLAG = true;
        } catch (Exception e) {
            LOG.info("JMX not supported");
            JMX_SUPPORT_FLAG = false;
        }
    }

    public static void registerJmx(Object object) {
        if (JMX_SUPPORT_FLAG) {
            String name = object.getClass().getSimpleName();
//            ObjectName objName = jmxLauncher.getExporter().exportToJMX(object, name);
//            if (objName != null) {
//                LOG.info("JMX exporting: " + objName);
//            } else {
//                LOG.warn("JMX exporting failed: " + name);
//            }
        }
    }
}
