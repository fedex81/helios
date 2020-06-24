package omegadrive.util;

/**
 * Federico Berti
 * <p>
 * Copyright 2020
 */
public interface JmxLauncherProxy {
    String exportToJMX(Object object, String name);

    JmxLauncherProxy launch(String name);
}
