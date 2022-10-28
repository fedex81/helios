package omegadrive.ui.flatlaf;

import com.formdev.flatlaf.FlatDarculaLaf;
import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatIntelliJLaf;
import com.formdev.flatlaf.FlatLightLaf;
import com.google.common.collect.ImmutableSortedMap;
import omegadrive.util.LogHelper;
import org.slf4j.Logger;

import javax.swing.*;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Map;
import java.util.TreeMap;

/**
 * Federico Berti
 * <p>
 * Copyright 2022
 */
public class FlatLafHelper {

    private static final Logger LOG = LogHelper.getLogger(FlatLafHelper.class.getSimpleName());

    //put FlatLaf entries first
    private static final Comparator<String> lfNameComparator = (k1, k2) -> {
        if (!k1.startsWith("Flat")) k1 = "Z" + k1;
        if (!k2.startsWith("Flat")) k2 = "Z" + k2;
        return k1.compareTo(k2);
    };

    public static Map<String, String> lafMap;

    static {
        initFlatLaf();
    }

    private static void initFlatLaf() {
        try {
            FlatLightLaf.installLafInfo();
            FlatDarkLaf.installLafInfo();
            FlatIntelliJLaf.installLafInfo();
            FlatDarculaLaf.installLafInfo();
        } catch (Exception e) {
            e.printStackTrace();
        }
        UIManager.LookAndFeelInfo[] lafs = UIManager.getInstalledLookAndFeels();
        Map<String, String> map = new TreeMap<>();
        Arrays.stream(lafs).forEach(l -> map.put(l.getName(), l.getClassName()));
        lafMap = ImmutableSortedMap.copyOf(map, lfNameComparator);
        LOG.info("LookAndFeel detected: {}", lafMap);
    }

    public static void handleLafChange(String name) {
        assert lafMap.containsKey(name);
        try {
            UIManager.setLookAndFeel(lafMap.get(name));
            com.formdev.flatlaf.FlatLaf.updateUI();
            LOG.info("Set look and feel: {}", name);
        } catch (Exception e) {
            LOG.error("Unable to set look and feel: {}, {}", name, e.getMessage());
        }
    }
}
