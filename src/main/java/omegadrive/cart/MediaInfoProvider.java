/*
 * CartridgeInfoProvider
 * Copyright (c) 2018-2019 Federico Berti
 * Last modified: 17/05/19 11:58
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

package omegadrive.cart;

import omegadrive.memory.IMemoryProvider;
import omegadrive.util.LogHelper;
import omegadrive.util.RegionDetector;
import omegadrive.util.Util;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.util.Optional;

public class MediaInfoProvider {

    private static final Logger LOG = LogHelper.getLogger(MediaInfoProvider.class.getSimpleName());

    public static final boolean AUTOFIX_CHECKSUM = false;
    private long checksum;
    private long computedChecksum;
    private String sha1;
    private String crc32;

    private Optional<IMemoryProvider> memoryProviderOptional = Optional.empty();

    public String romName;

    public int romSize;

    public static MediaInfoProvider createInstance(IMemoryProvider memoryProvider, Path rom) {
        MediaInfoProvider provider = new MediaInfoProvider();
        provider.memoryProviderOptional = Optional.of(memoryProvider);
        provider.romName = rom.getFileName().toString();
        provider.init();
        return provider;
    }

    public String getRomName() {
        return romName;
    }

    public String getRegion() {
        return RegionDetector.Region.USA.name();
    }

    public String getSha1() {
        return sha1;
    }

    public String getCrc32() {
        return crc32;
    }

    public boolean hasCorrectChecksum() {
        return checksum == computedChecksum;
    }

    public int getChecksumStartAddress(){
        return 0;
    }

    protected void init() {
        this.initChecksum();
        if (memoryProviderOptional.isPresent()) {
            romSize = memoryProviderOptional.get().getRomSize();
        }
    }

    protected void initChecksum() {
        if (memoryProviderOptional.isEmpty()) {
            return;
        }
        IMemoryProvider memoryProvider = memoryProviderOptional.get();
        this.checksum = memoryProvider.readRomByte(getChecksumStartAddress());
        this.computedChecksum = Util.computeChecksum(memoryProvider);
        this.sha1 = Util.computeSha1Sum(memoryProvider);
        this.crc32 = Util.computeCrc32(memoryProvider);

        //defaults to false
        if (AUTOFIX_CHECKSUM && checksum != computedChecksum) {
            LOG.info("Auto-fix checksum from: {} to: {}", checksum, computedChecksum);
            memoryProvider.setChecksumRomValue(computedChecksum);
        }
    }

    @Override
    public String toString() {
        return "ROM sha1: " + sha1 + " - ROM CRC32: " + crc32;
    }

    public int getRomSize() {
        assert romSize > 0;
        return romSize;
    }
}
