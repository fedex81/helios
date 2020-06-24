/*
 * JmxBridge
 * Copyright (c) 2018-2019 Federico Berti
 * Last modified: 07/04/19 16:01
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package omegadrive.util;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Method;

public class JmxBridge {

    private static Logger LOG = LogManager.getLogger(JmxBridge.class.getSimpleName());

    private static boolean JMX_SUPPORT_FLAG;
    private static JmxLauncherProxy proxy;

    static {
        try {
            Class<JmxLauncherProxy> cl = (Class<JmxLauncherProxy>) Class.forName("com.jmxwebtools.JmxLauncher");
            Method factoryMethod = cl.getDeclaredMethod("getInstance"); //static method
            proxy = (JmxLauncherProxy) factoryMethod.invoke(null, (Object[]) null);
            proxy.launch("Helios");
            JMX_SUPPORT_FLAG = true;
        } catch (Exception e) {
            LOG.info("JMX not supported");
            JMX_SUPPORT_FLAG = false;
        }
    }

    public static void registerJmx(Object object) {
        if (JMX_SUPPORT_FLAG) {
            try {
                String name = object.getClass().getSimpleName();
                String objName = proxy.exportToJMX(object, name);
                if (objName != null) {
                    LOG.info("JMX exporting: " + objName);
                } else {
                    LOG.warn("JMX exporting failed: " + name);
                }
            } catch (Exception e) {
                LOG.warn("Unable to export: " + object.getClass().getSimpleName());
            }
        }
    }
}
