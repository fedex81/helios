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
import omegadrive.joypad.GenesisJoypad;
import omegadrive.joypad.JoypadProvider.JoypadType;
import omegadrive.system.Genesis;
import omegadrive.system.Sms;
import omegadrive.system.SystemProvider;
import omegadrive.system.Z80BaseSystem;
import omegadrive.system.gb.Gb;
import omegadrive.system.nes.Nes;
import omegadrive.ui.DisplayWindow;
import omegadrive.ui.PrefStore;
import omegadrive.ui.SwingWindow;
import omegadrive.util.RegionDetector;
import omegadrive.util.Util;
import omegadrive.util.ZipUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import ru.krlvm.swingdpi.SwingDPI;

import javax.swing.*;
import java.awt.*;
import java.io.FileReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;

import static omegadrive.input.InputProvider.PlayerNumber;
import static omegadrive.input.InputProvider.bootstrap;
import static omegadrive.system.SystemProvider.SystemEvent.NEW_ROM;

public class SystemLoader {

    protected DisplayWindow emuFrame;

    private final static Logger LOG = LogManager.getLogger(SystemLoader.class.getSimpleName());

    public static final SystemLoader INSTANCE = new SystemLoader();

    private static final String PROPERTIES_FILENAME = "./helios.properties";

    public static final String SMD_INTERLEAVED_EXT = ".smd";

    public static String[] mdBinaryTypes = {".md", ".bin", SMD_INTERLEAVED_EXT};
    public static String[] sgBinaryTypes = {".sg", ".sc"};
    public static String[] cvBinaryTypes = {".col"};
    public static String[] msxBinaryTypes = {".rom"};
    public static String[] smsBinaryTypes = {".sms"};
    public static String[] ggBinaryTypes = {".gg"};
    public static String[] nesBinaryTypes = {".nes"};
    public static String[] gbBinaryTypes = {".gb"};
    public static String[] compressedBinaryTypes = {".gz", ".zip"};

    public static String[] binaryTypes = Stream.of(
            mdBinaryTypes, sgBinaryTypes, cvBinaryTypes, msxBinaryTypes, smsBinaryTypes, ggBinaryTypes, nesBinaryTypes,
            gbBinaryTypes,
            compressedBinaryTypes
    ).flatMap(Stream::of).toArray(String[]::new);

    public static boolean debugPerf = false;
    public static boolean showFps = false;
    public static boolean headless = false;
    public static boolean smdFileAsInterleaved = false;
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
        debugPerf = Boolean.parseBoolean(java.lang.System.getProperty("helios.debug", "false"));
        showFps = Boolean.parseBoolean(java.lang.System.getProperty("helios.fps", "false"));
        headless = Boolean.parseBoolean(java.lang.System.getProperty("helios.headless", "false"));
        biosFolder = String.valueOf(java.lang.System.getProperty("bios.folder", biosFolder));
        biosNameMsx1 = String.valueOf(java.lang.System.getProperty("bios.name.msx1", biosNameMsx1));
        biosNameColeco = String.valueOf(java.lang.System.getProperty("bios.name.coleco", biosNameColeco));
        smdFileAsInterleaved = Boolean.parseBoolean(java.lang.System.getProperty("md.enable.smd.handling", "false"));
        PrefStore.initPrefs();
    }

    private SystemProvider systemProvider;

    private static AtomicBoolean init = new AtomicBoolean();

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
            INSTANCE.handleNewRomFile(Paths.get(filePath));
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
         initLookAndFeel();
         INSTANCE.createFrame(isHeadless);
         init.set(true);
     }

    private static void initLookAndFeel() {
        try {
            String lf = UIManager.getSystemLookAndFeelClassName();
            UIManager.setLookAndFeel(lf);
            if (lf.equals("com.sun.java.swing.plaf.windows.WindowsLookAndFeel")) {
                SwingDPI.excludeDefaults(
                        "RadioButtonMenuItem.font",
                        "CheckBoxMenuItem.font",
                        "MenuBar.font",
                        "PopupMenu.font",
                        "MenuItem.font",
                        "Menu.font"
                );
            }
        } catch (Exception e) {
            LOG.error("Failed to set SwingUI native Look and Feel", e);
        }
        UIDefaults defaults = UIManager.getLookAndFeelDefaults();
        defaults.put("TextArea.font", ((Font) defaults.get("TextArea.font")).deriveFont(13 * SwingDPI.getScaleFactor()));
        SwingDPI.applyScalingAutomatically();
        LOG.info("SwingUI DPI Scale factor: {}", SwingDPI.getScaleFactor());
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

    public SystemProvider handleNewRomFile(Path file) {
        systemProvider = createSystemProvider(file, debugPerf);
        if (systemProvider != null) {
            emuFrame.reloadSystem(systemProvider);
            systemProvider.handleSystemEvent(NEW_ROM, file);
        }
        return systemProvider;
    }

    SystemProvider getSystemAdapter(){
        return new SystemProvider() {
            @Override
            public RegionDetector.Region getRegion() {
                return null;
            }

            @Override
            public void handleSystemEvent(SystemEvent event, Object parameter) {
                LOG.info("Event: {}, with parameter: {}", event, Objects.toString(parameter));
                switch (event) {
                    case NEW_ROM:
                        handleNewRomFile((Path) parameter);
                        break;
                    case CLOSE_ROM:
                    case CLOSE_APP:
                        //do nothing
                        break;
                    case PAD_SETUP_CHANGE:
                        String[] s1 = parameter.toString().split(":");
                        PlayerNumber pn = PlayerNumber.valueOf(s1[0]);
                        JoypadType jt = JoypadType.valueOf(s1[1]);
                        GenesisJoypad.P1_DEFAULT_TYPE = pn == PlayerNumber.P1 ? jt : GenesisJoypad.P1_DEFAULT_TYPE;
                        GenesisJoypad.P2_DEFAULT_TYPE = pn == PlayerNumber.P2 ? jt : GenesisJoypad.P2_DEFAULT_TYPE;
                        break;
                    default:
                        LOG.warn("Unable to handle event: {}, with parameter: {}", event, Objects.toString(parameter));
                        break;
                }
            }

            @Override
            public boolean isRomRunning() {
                return false;
            }

            @Override
            public Path getRomPath() {
                return null;
            }

            @Override
            public SystemType getSystemType() {
                return SystemType.NONE;
            }
        };
    }

    public SystemProvider createSystemProvider(Path file) {
        return createSystemProvider(file, false);
    }

    private static String handleCompressedFiles(Path file, String lowerCaseName) {
        if (ZipUtil.isZipArchiveByteStream(file)) {
            Optional<? extends ZipEntry> optEntry = ZipUtil.getSupportedZipEntryIfAny(file);
            if (!optEntry.isPresent()) {
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

    public SystemProvider createSystemProvider(Path file, boolean debugPerf) {
        String lowerCaseName = handleCompressedFiles(file, file.toString().toLowerCase());
        if (lowerCaseName == null) {
            LOG.error("Unable to load file: " + file != null ? file.toAbsolutePath() : "null");
            return null;
        }
        boolean isGen = Arrays.stream(mdBinaryTypes).anyMatch(lowerCaseName::endsWith);
        boolean isSg = Arrays.stream(sgBinaryTypes).anyMatch(lowerCaseName::endsWith);
        boolean isCv = Arrays.stream(cvBinaryTypes).anyMatch(lowerCaseName::endsWith);
        boolean isMsx = Arrays.stream(msxBinaryTypes).anyMatch(lowerCaseName::endsWith);
        boolean isSms = Arrays.stream(smsBinaryTypes).anyMatch(lowerCaseName::endsWith);
        boolean isGg = Arrays.stream(ggBinaryTypes).anyMatch(lowerCaseName::endsWith);
        boolean isNes = Arrays.stream(nesBinaryTypes).anyMatch(lowerCaseName::endsWith);
        boolean isGb = Arrays.stream(gbBinaryTypes).anyMatch(lowerCaseName::endsWith);
        if (isGen) {
            systemProvider = Genesis.createNewInstance(emuFrame, debugPerf);
        } else if (isSg) {
            systemProvider = Z80BaseSystem.createNewInstance(SystemType.SG_1000, emuFrame);
        } else if (isCv) {
            systemProvider = Z80BaseSystem.createNewInstance(SystemType.COLECO, emuFrame);
        } else if (isMsx) {
            systemProvider = Z80BaseSystem.createNewInstance(SystemType.MSX, emuFrame);
        } else if (isSms) {
            systemProvider = Sms.createNewInstance(SystemType.SMS, emuFrame, debugPerf);
        } else if (isGg) {
            systemProvider = Sms.createNewInstance(SystemType.GG, emuFrame, debugPerf);
        } else if (isNes) {
            systemProvider = Nes.createNewInstance(SystemType.NES, emuFrame);
        } else if (isGb) {
            systemProvider = Gb.createNewInstance(SystemType.GB, emuFrame);
        }
        if (systemProvider == null) {
            LOG.error("Unable to find a system to load: {}", file.toAbsolutePath());
        }
        return systemProvider;
    }

    public SystemProvider getSystemProvider() {
        return systemProvider;
    }

    public enum SystemType {
        NONE(""),
        GENESIS("MD"),
        SG_1000("SG"),
        COLECO("CV"),
        MSX("MSX"),
        SMS("SMS"),
        GG("GG"),
        NES("NES"),
        GB("GB");

        private String shortName;

        SystemType(String s) {
            this.shortName = s;
        }

        public String getShortName() {
            return shortName;
        }
    }
}
