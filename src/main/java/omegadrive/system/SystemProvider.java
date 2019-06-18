/*
 * SystemProvider
 * Copyright (c) 2018-2019 Federico Berti
 * Last modified: 18/06/19 17:15
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
import omegadrive.util.RegionDetector;

import java.util.EventListener;

public interface SystemProvider extends Device {

    void handleSystemEvent(SystemEvent event, Object parameter);

    boolean addFrameListener(VdpFrameListener l);


    void renderScreen(int[][] screenData);

    boolean removeFrameListener(VdpFrameListener l);

    /**
     * STATE
     **/

    boolean isRomRunning();

    RegionDetector.Region getRegion();

    default long getRegionCode() {
        return getRegion().getVersionCode();
    }

    boolean isSoundWorking();

    String getRomName();

    SystemLoader.SystemType getSystemType();

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
        TOGGLE_MUTE,
        TOGGLE_FULL_SCREEN,
        SET_PLAYERS_1,
        SET_PLAYERS_2,
        TOGGLE_DEBUG_LOGGING,
        SET_DEBUG_UI,
        TOGGLE_SOUND_RECORD
    }

    interface VdpFrameListener extends EventListener {
        void onNewFrame();
    }
}
