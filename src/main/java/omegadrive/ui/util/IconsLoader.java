package omegadrive.ui.util;

import omegadrive.util.LogHelper;
import omegadrive.util.RegionDetector.Region;
import org.slf4j.Logger;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;

/**
 * Federico Berti
 * <p>
 * Copyright 2024
 */
public class IconsLoader {

    private static final Logger LOG = LogHelper.getLogger(IconsLoader.class.getSimpleName());
    private static final Map<Region, String> regFileMap = Map.of(
            Region.EUROPE, "eu_25px.png",
            Region.USA, "us_25px.png",
            Region.JAPAN, "jp_25px.png"
    );

    private static final String ledFileHead = "mcd-leds_";
    private static final String[] ledFileMap = {
            ledFileHead + "00" + ".png",
            ledFileHead + "01" + ".png",
            ledFileHead + "10" + ".png",
            ledFileHead + "11" + ".png"
    };


    private static final Map<Region, Icon> flagIconMap = new EnumMap<>(Region.class);

    private static final Icon[] ledIconMap = new Icon[ledFileMap.length];
    private static final Path baseLeds = Paths.get(".", "res", "icon", "mcd_leds");
    private static final Path baseFlags = Paths.get(".", "res", "icon", "flags");

    static {
        try {
            for (var e : regFileMap.entrySet()) {
                Path p = Paths.get(baseFlags.toAbsolutePath().toString(), e.getValue());
                flagIconMap.put(e.getKey(), new ImageIcon(ImageIO.read(p.toFile())));
            }
            LOG.info("Loaded country flags from folder: {}, files: {}", baseFlags, regFileMap);
        } catch (IOException e) {
            LOG.error("Unable to load country flags from folder: {}, files: {}", baseFlags, regFileMap);
            LOG.error("Error", e);
        }
        try {
            for (int i = 0; i < ledFileMap.length; i++) {
                Path p = Paths.get(baseLeds.toAbsolutePath().toString(), ledFileMap[i]);
                ledIconMap[i] = new ImageIcon(ImageIO.read(p.toFile()));
            }
            LOG.info("Loaded megacd led icons from folder: {}, files: {}", baseLeds, Arrays.toString(ledFileMap));
        } catch (IOException e) {
            LOG.error("Unable to load megacd led icons from folder: {}, files: {}", baseLeds, regFileMap);
            LOG.error("Error", e);
        }
    }

    public static Icon getRegionIcon(Region region) {
        return flagIconMap.get(region);
    }

    public static Icon getLedIcon(int state) {
        assert state >= 0 && state < ledIconMap.length;
        return ledIconMap[state];
    }
}
