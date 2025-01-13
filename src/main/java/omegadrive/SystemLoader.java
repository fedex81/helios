/*
 * SystemLoader
 * Copyright (c) 2018-2019 Federico Berti
 * Last modified: 17/10/19 14:04
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

package omegadrive;

import omegadrive.input.KeyboardInputHelper;
import omegadrive.system.SysUtil;
import omegadrive.system.SysUtil.RomSpec;
import omegadrive.system.SystemProvider;
import omegadrive.ui.DisplayWindow;
import omegadrive.ui.PrefStore;
import omegadrive.ui.SwingWindow;
import omegadrive.util.LogHelper;
import omegadrive.util.Util;
import omegadrive.util.ZipUtil;
import org.slf4j.Logger;

import javax.swing.*;
import java.awt.*;
import java.io.FileReader;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipEntry;

import static omegadrive.input.InputProvider.bootstrap;
import static omegadrive.system.SystemProvider.RomContext.NO_ROM;
import static omegadrive.system.SystemProvider.SystemEvent.NEW_ROM;

public class SystemLoader {

    protected DisplayWindow emuFrame;

    private final static Logger LOG = LogHelper.getLogger(SystemLoader.class.getSimpleName());

    public static final SystemLoader INSTANCE = new SystemLoader();

    private static final String PROPERTIES_FILENAME = "./helios.properties";

    public static boolean showFps, headless, smdFileAsInterleaved, testMode;
    public static String biosFolder = "./res/bios";
    public static String biosNameMsx1 = "cbios_main_msx1.rom";
    public static String biosNameColeco = "bios_coleco.col";

    protected static void loadProperties() {
        try (
                FileReader reader = new FileReader(PROPERTIES_FILENAME)
        ) {
            System.getProperties().load(reader);
        } catch (Exception e) {
            LOG.error("Unable to load properties file: " + PROPERTIES_FILENAME);
        }
        System.getProperties().list(System.out);
        System.out.println("-- done listing properties --");
        showFps = Boolean.parseBoolean(java.lang.System.getProperty("helios.fps", "false"));
        headless = Boolean.parseBoolean(java.lang.System.getProperty("helios.headless", "false"));
        biosFolder = String.valueOf(java.lang.System.getProperty("bios.folder", biosFolder));
        biosNameMsx1 = String.valueOf(java.lang.System.getProperty("bios.name.msx1", biosNameMsx1));
        biosNameColeco = String.valueOf(java.lang.System.getProperty("bios.name.coleco", biosNameColeco));
        smdFileAsInterleaved = Boolean.parseBoolean(java.lang.System.getProperty("md.enable.smd.handling", "false"));
        testMode = Boolean.parseBoolean(java.lang.System.getProperty("helios.test.mode", "false"));
        PrefStore.initPrefs();
    }

    private SystemProvider systemProvider;

    private static final AtomicBoolean init = new AtomicBoolean();

    protected static boolean isHeadless() {
        GraphicsEnvironment ge =
                GraphicsEnvironment.getLocalGraphicsEnvironment();
        return ge.isHeadlessInstance() || headless;
    }

    public static void main(String[] args) {
        init();
        if (args.length > 0) {
            //linux pulseaudio can crash if we start too quickly
            Util.sleep(250);
            String filePath = args[0];
            LOG.info("Launching file at: {}", filePath);
            RomSpec romSpec = RomSpec.of(filePath);
            INSTANCE.handleNewRomFile(romSpec);
            Util.sleep(1_000); //give the game thread a chance
        }
        if (headless) {
            Util.waitForever();
        }
    }

    private SystemLoader() {
        Util.registerJmx(this);
    }

    public static SystemLoader getInstance() {
        if (init.compareAndSet(false, true)) {
            init();
        }
        return INSTANCE;
    }

     private static void init() {
         loadProperties();
         bootstrap();
         boolean isHeadless = isHeadless();
         LOG.info("Headless mode: {}", isHeadless);
         SystemLoader.setHeadless(isHeadless);
         KeyboardInputHelper.init();
         INSTANCE.createFrame(isHeadless);
         init.set(true);
     }

    // Create the frame on the event dispatching thread
    protected void createFrame(boolean isHeadless) {
        Runnable frameRunnable = () -> {
            emuFrame = isHeadless ? DisplayWindow.HEADLESS_INSTANCE : new SwingWindow(getSystemAdapter());
            emuFrame.init();
        };
        if (SwingUtilities.isEventDispatchThread()) {
            frameRunnable.run();
        } else {
            try {
                SwingUtilities.invokeAndWait(frameRunnable);
            } catch (Exception e) {
                LOG.error("Unable to create SwingUI", e);
            }
        }
    }

    private static void setHeadless(boolean headless) {
        SystemLoader.headless = headless;
    }

    public SystemProvider handleNewRomFile(RomSpec file) {
        systemProvider = createSystemProvider(file);
        if (systemProvider != null) {
            emuFrame.reloadSystem(systemProvider);
            systemProvider.handleSystemEvent(NEW_ROM, file);
        }
        return systemProvider;
    }

    SystemProvider getSystemAdapter() {
        return new SystemProvider() {

            @Override
            public void handleSystemEvent(SystemEvent event, Object parameter) {
                LOG.info("Event: {}, with parameter: {}", event, parameter);
                switch (event) {
                    case NEW_ROM:
                        handleNewRomFile((RomSpec) parameter);
                        break;
                    case CLOSE_ROM:
                        break;
                    case CLOSE_APP:
                        PrefStore.close();
                        break;
                    case PAD_SETUP_CHANGE:
                    case FORCE_PAD_TYPE:
                        UserConfigHolder.addUserConfig(event, parameter);
                        break;
                    default:
                        LOG.warn("Unable to handle event: {}, with parameter: {}", event, parameter);
                        break;
                }
            }

            @Override
            public boolean isRomRunning() {
                return false;
            }

            @Override
            public RomContext getRomContext() {
                return NO_ROM;
            }

            @Override
            public SystemType getSystemType() {
                return SystemType.NONE;
            }
        };
    }
    public static String handleCompressedFiles(Path file, String lowerCaseName) {
        if (ZipUtil.isZipArchiveByteStream(file)) {
            Optional<? extends ZipEntry> optEntry = ZipUtil.getSupportedZipEntryIfAny(file);
            if (optEntry.isEmpty()) {
                LOG.error("Unable to find a system to load: {}", file.toAbsolutePath());
                return null;
            }
            LOG.info("Valid zipEntry detected: {}", optEntry.get().getName());
            lowerCaseName = optEntry.get().getName().toLowerCase();
        } else if (ZipUtil.isGZipByteStream(file)) {
            lowerCaseName = ZipUtil.getGZipFileName(file);
            LOG.info("GZip file detected, assuming name: {}", lowerCaseName);
        }
        return lowerCaseName;
    }

    public SystemProvider createSystemProvider(RomSpec file) {
        systemProvider = SysUtil.createSystemProvider(file, emuFrame);
        return systemProvider;
    }

    public SystemProvider getSystemProvider() {
        return systemProvider;
    }

    public enum SystemType {
        NONE(""),
        MD("MD"),
        MEGACD("MCD"),
        S32X("32X"),
        MEGACD_S32X("MCD_32X"), //tower of power
        SG_1000("SG"),
        COLECO("CV"),
        MSX("MSX"),
        SMS("SMS"),
        GG("GG"),
        NES("NES"),
        GB("GB");

        private final String shortName;

        SystemType(String s) {
            this.shortName = s;
        }

        public String getShortName() {
            return shortName;
        }

        public boolean isMegaCdAttached() {
            return this == MEGACD || this == MEGACD_S32X;
        }
    }
}
