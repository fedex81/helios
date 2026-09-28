/*
 * DisplayWindow
 * Copyright (c) 2018-2019 Federico Berti
 * Last modified: 14/10/19 15:26
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

package omegadrive.ui;

import omegadrive.system.MediaSpecHolder;
import omegadrive.system.SystemProvider;
import omegadrive.util.FileUtil;
import omegadrive.util.RegionDetector;
import omegadrive.util.VideoMode;

import java.awt.event.KeyListener;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public interface DisplayWindow extends RegionDetector.RegionOverrideSupplier {

    String APP_NAME = "Helios";
    String VERSION = FileUtil.loadVersionFromManifest();
    String FRAME_TITLE_HEAD = APP_NAME + " " + VERSION;

    int SHOW_INFO_FRAMES_DELAY = 120; //~2sec


    class DisplayContext {

        protected final static DecimalFormat audioDelayFormat = new DecimalFormat("0.0");
        public final static String LABEL_KEY = "label";
        public final static String FPS_KEY = "fps";
        public final static String WAIT_NS_KEY = "waitNs";
        public final static String MCD_LED_KEY = "mcdLedState";
        public final static String AUDIO_DELAY_KEY = "audioDelay";
        public final static String MAX_AUDIO_DELAY_KEY = "maxAudioDelay";

        public int[] data;
        public VideoMode videoMode;

        Map<String, Object> values = new HashMap<>();

        public Object get(String key) {
            return values.get(key);
        }

        public void put(String key, Object value) {
            values.put(key, value);
        }

        public void copyValuesFrom(DisplayContext other) {
            values.clear();
            values.putAll(other.values);
        }
    }


    DisplayWindow HEADLESS_INSTANCE = new DisplayWindow() {
        @Override
        public void addKeyListener(KeyListener keyAdapter) {

        }

        @Override
        public void setRomData(MediaSpecHolder rom) {

        }

        @Override
        public void init() {

        }

        @Override
        public void renderScreenLinear(DisplayContext displayContext) {

        }

        @Override
        public void resetScreen() {

        }

        @Override
        public void setFullScreen(boolean value) {

        }

        @Override
        public String getRegionOverride() {
            return null;
        }

        @Override
        public void reloadSystem(SystemProvider systemProvider) {

        }
    };

    void setRomData(MediaSpecHolder rom);

    void init();

    void renderScreenLinear(DisplayContext displayContext);

    void resetScreen();

    void setFullScreen(boolean value);

    String getRegionOverride();

    void reloadSystem(SystemProvider systemProvider);

    void addKeyListener(KeyListener keyAdapter);

    default void close() {
        //DO NOTHING
    }

    default void reloadControllers(Collection<String> list) {
        //DO NOTHING
    }

    default void showInfo(String info) {
        //DO NOTHING
    }

    default String getAboutString() {
        int year = LocalDate.now().getYear();
        String yrString = year == 2018 ? "2018" : "2018-" + year;
        String res = FRAME_TITLE_HEAD + "\nA Java-based multi-system emulator.";
        res += "\n\nCopyright " + yrString + ", Federico Berti";
        res += "\n\nSee CREDITS.TXT for more information";
        res += "\n\nReleased under GPL v.3.0 license.";
        return res;
    }


}
