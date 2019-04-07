/*
 * SystemProvider
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

package omegadrive.system;

import omegadrive.Device;
import omegadrive.SystemLoader;
import omegadrive.util.RegionDetector;

import java.nio.file.Path;

public interface SystemProvider extends Device {

    RegionDetector.Region getRegion();

    default long getRegionCode() {
        return getRegion().getVersionCode();
    }

    void renderScreen(int[][] screenData);

    void handleNewRom(Path file);

    void handleCloseRom();

    void handleCloseApp();

    void handleLoadState(Path file);

    boolean isRomRunning();

    boolean isSoundWorking();

    void toggleMute();

    void toggleSoundRecord();

    void setFullScreen(boolean value);

    void setPlayers(int i);

    void setDebug(boolean value);

    String getRomName();

    void handleSaveState(Path file);

    void handlePause();

    SystemLoader.SystemType getSystemType();
}
