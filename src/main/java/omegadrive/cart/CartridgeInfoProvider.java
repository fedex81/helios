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
import omegadrive.memory.MemoryProvider;
import omegadrive.system.SysUtil;
import omegadrive.system.SystemProvider.RomContext;
import omegadrive.util.LogHelper;
import omegadrive.util.RegionDetector;
import omegadrive.util.Util;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.util.Optional;

public class CartridgeInfoProvider {

    private static final Logger LOG = LogHelper.getLogger(CartridgeInfoProvider.class.getSimpleName());

    public static final boolean AUTOFIX_CHECKSUM = false;

    protected IMemoryProvider memoryProvider = MemoryProvider.NO_MEMORY;

    protected RomContext romContext = RomContext.NO_ROM;
    private long checksum;
    private long computedChecksum;
    private String sha1;
    private String crc32;

    protected String romName;

    public static CartridgeInfoProvider createInstance(IMemoryProvider memoryProvider, Path rom) {
        CartridgeInfoProvider provider = new CartridgeInfoProvider();
        provider.memoryProvider = memoryProvider;
        provider.romContext = new RomContext(SysUtil.RomSpec.of(rom));
        provider.init();
        return provider;
    }

    public String getRomName() {
        return romName;
    }

    public RomContext getRomContext() {
        return romContext;
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
        romName = Optional.ofNullable(romContext.romSpec.file).map(p -> p.getFileName().toString()).orElse("norom.bin");
        this.initChecksum();
    }

    protected void initChecksum() {
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

}
