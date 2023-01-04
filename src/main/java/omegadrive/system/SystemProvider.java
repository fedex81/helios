/*
 * SystemProvider
 * Copyright (c) 2018-2019 Federico Berti
 * Last modified: 13/10/19 15:41
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

package omegadrive.system;

import omegadrive.Device;
import omegadrive.SystemLoader;
import omegadrive.cart.CartridgeInfoProvider;
import omegadrive.cart.MdCartInfoProvider;
import omegadrive.util.RegionDetector;

import java.nio.file.Path;
import java.util.StringJoiner;

public interface SystemProvider extends Device {

    class RomContext {
        public RegionDetector.Region region;
        public Path romPath;
        public CartridgeInfoProvider cartridgeInfoProvider;

        public static final RomContext NO_ROM;

        static {
            NO_ROM = new RomContext();
            NO_ROM.region = RegionDetector.Region.USA;
            NO_ROM.romPath = Path.of("NO_PATH");
            NO_ROM.cartridgeInfoProvider = new MdCartInfoProvider();
        }

        @Override
        public String toString() {
            return new StringJoiner(", ", RomContext.class.getSimpleName() + "[", "]")
                    .add("region=" + region)
                    .add("romPath=" + romPath)
                    .add("\ncartridgeInfoProvider=" + cartridgeInfoProvider)
                    .toString();
        }
    }

    void handleSystemEvent(SystemEvent event, Object parameter);

    /**
     * STATE
     **/

    boolean isRomRunning();

    RomContext getRomContext();

    SystemLoader.SystemType getSystemType();

    default RegionDetector.Region getRegion() {
        return getRomContext().region;
    }

    default int getRegionCode() {
        return getRegion().getVersionCode();
    }

    default Path getRomPath() {
        return getRomContext().romPath;
    }

    enum SystemEvent {
        NONE,
        NEW_ROM,
        CLOSE_ROM,
        CLOSE_APP,
        RESET,
        LOAD_STATE,
        SAVE_STATE,
        QUICK_SAVE,
        QUICK_LOAD,
        TOGGLE_PAUSE,
        SOUND_ENABLED,
        TOGGLE_FULL_SCREEN,
        TOGGLE_THROTTLE,
        CONTROLLER_CHANGE,
        SHOW_FPS,
        TOGGLE_SOUND_RECORD,
        SOFT_RESET,
        PAD_SETUP_CHANGE,
        FORCE_PAD_TYPE
    }

    interface NewFrameListener {
        void newFrame();
    }

    interface SystemClock {
        int getCycleCounter();

        long getFrameCounter();
    }

    SystemClock NO_CLOCK = new SystemClock() {
        @Override
        public int getCycleCounter() {
            return 0;
        }

        @Override
        public long getFrameCounter() {
            return 0;
        }
    };
}
