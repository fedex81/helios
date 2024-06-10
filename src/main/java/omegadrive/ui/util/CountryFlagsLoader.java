package omegadrive.ui.util;

import omegadrive.util.LogHelper;
import omegadrive.util.RegionDetector.Region;
import org.slf4j.Logger;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.EnumMap;
import java.util.Map;

/**
 * Federico Berti
 * <p>
 * Copyright 2024
 */
public class CountryFlagsLoader {

    private static final Logger LOG = LogHelper.getLogger(CountryFlagsLoader.class.getSimpleName());
    private static final Map<Region, String> regFileMap = Map.of(
            Region.EUROPE, "eu_25px.png",
            Region.USA, "us_25px.png",
            Region.JAPAN, "jp_25px.png"
    );
    private static final Map<Region, Icon> map = new EnumMap<>(Region.class);
    private static final Path base = Paths.get(".", "res", "icon", "flags");

    static {
        try {
            for (var e : regFileMap.entrySet()) {
                Path p = Paths.get(base.toAbsolutePath().toString(), e.getValue());
                map.put(e.getKey(), new ImageIcon(ImageIO.read(p.toFile())));
            }
            LOG.info("Loaded country flags from folder: {}, files: {}", base, regFileMap);
        } catch (IOException e) {
            LOG.error("Unable to load country flags from folder: {}, files: {}", base, regFileMap);
            LOG.error("Error", e);
        }
    }

    public static Icon getRegionIcon(Region region) {
        return map.get(region);
    }
}
