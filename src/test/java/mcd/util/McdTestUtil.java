package mcd.util;

import omegadrive.SystemLoader;
import omegadrive.system.MediaSpecHolder;
import omegadrive.system.SystemProvider;
import omegadrive.util.RegionDetector;

import java.nio.file.Path;

import static omegadrive.system.MediaSpecHolder.NO_ROM;

/**
 * Federico Berti
 * <p>
 * Copyright 2021
 */
public class McdTestUtil {

    public static SystemProvider createTestMcdProvider() {
        return new SystemProvider() {
            private MediaSpecHolder msh;

            {
                msh = NO_ROM;
                assert msh.cartFile != null;
//                assert memoryProvider.getRomData() != null;
//                msh.cartFile.mediaInfoProvider = MdCartInfoProvider.createMdInstance(memoryProvider.getRomData());
                msh.reload();
            }

            @Override
            public RegionDetector.Region getRegion() {
                return null;
            }

            @Override
            public void handleSystemEvent(SystemEvent event, Object parameter) {

            }

            @Override
            public boolean isRomRunning() {
                return false;
            }

            @Override
            public void init() {

            }

            @Override
            public MediaSpecHolder getMediaSpec() {
                return msh;
            }

            @Override
            public Path getRomPath() {
                return null;
            }


            @Override
            public SystemLoader.SystemType getSystemType() {
                return SystemLoader.SystemType.MEGACD;
            }

            @Override
            public void reset() {

            }
        };
    }
}