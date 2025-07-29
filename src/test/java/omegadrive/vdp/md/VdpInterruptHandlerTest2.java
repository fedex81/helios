/*
 * Copyright (c) 2018-2019 Federico Berti
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

package omegadrive.vdp.md;

import omegadrive.util.LogHelper;
import omegadrive.util.VideoMode;
import omegadrive.vdp.BaseVdpInterruptHandlerTest;
import omegadrive.vdp.MdVdpTestUtil;
import omegadrive.vdp.model.BaseVdpProvider;
import org.junit.Test;
import org.junit.jupiter.api.Assertions;
import org.slf4j.Logger;

import java.util.concurrent.atomic.AtomicInteger;

public class VdpInterruptHandlerTest2 extends BaseVdpInterruptHandlerTest {

    private static final Logger LOG = LogHelper.getLogger(VdpInterruptHandlerTest2.class.getSimpleName());

    private AtomicInteger hIntCounter = new AtomicInteger();

    /**
     * Beastball Proto, probably others
     */
    @Test
    public void testHintPendingRetriggersWhenLeftPending() {
        BaseVdpProvider vdp = MdVdpTestUtil.createBaseTestVdp();
        VdpInterruptHandler h = VdpInterruptHandler.createMdInstance(vdp);
        prepareVdp(vdp, h, VideoMode.NTSCU_H40_V30);
        int hLineCounter = 2;
        int expectedHInt = 2;
        MdVdpTestUtil.updateHCounter(vdp, hLineCounter);
        hIntCounter.set(0);
        do {
            h.increaseHCounter();
        } while (lineCount < 1 + hLineCounter * expectedHInt);
        Assertions.assertEquals(expectedHInt, hIntCounter.get());
    }

    @Override
    protected void hIntPending(VdpInterruptHandler h, boolean value) {
        if (value) {
            hIntCounter.incrementAndGet();
        }
    }
}
