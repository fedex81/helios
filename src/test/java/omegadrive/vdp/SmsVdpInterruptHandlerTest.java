/*
 * SmsVdpInterruptHandlerTest
 * Copyright (c) 2018-2019 Federico Berti
 * Last modified: 07/04/19 16:02
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

package omegadrive.vdp;

import omegadrive.util.VideoMode;
import omegadrive.vdp.gen.VdpInterruptHandler;
import omegadrive.vdp.model.BaseVdpProvider;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Ignore;
import org.junit.Test;

@Ignore
public class SmsVdpInterruptHandlerTest extends BaseVdpInterruptHandlerTest {

    private static final Logger LOG = LogManager.getLogger(SmsVdpInterruptHandlerTest.class.getSimpleName());

    /**
     * According to http://www.smspower.org/Development/SMSOfficialDocs
     * <p>
     * R10 Value	Interrupt Requests at these HBLANK times
     * $C0-$FF	None
     * $00	1,2,3,4,5,...191
     * $01	2,4,6,8,10,..190
     * $02	3,6,9,12,....189
     * $03	4,8,12,16,...188
     * (etc)	(etc)
     */
    @Test
    public void testSmsHLinesCounter() {
        testSmsHLinesCounterInternal(0);
        testSmsHLinesCounterInternal(1);
        testSmsHLinesCounterInternal(2);
        testSmsHLinesCounterInternal(3);
        testSmsHLinesCounterInternal(0xBE);
//        //TODO shouldn't 0xBF this generate one hint?
        testSmsHLinesCounterInternal(0xBF);
        testSmsHLinesCounterInternal(0xC0);
        testSmsHLinesCounterInternal(0xFF);
    }

    @Test
    @Ignore("TODO fix")
    public void testHLinesCounter() {
        int hLinePassed = 0;
        BaseVdpProvider vdp = MdVdpTestUtil.createBaseTestVdp();
        VdpInterruptHandler h = VdpInterruptHandlerHelper.createSmsInstance(vdp);
        MdVdpTestUtil.updateHCounter(vdp, hLinePassed);
        hLinesCounterBasic2(vdp, h, VideoMode.NTSCJ_H32_V24);
    }

    private void testSmsHLinesCounterInternal(final int lineCounter) {
        VideoMode vm = VideoMode.NTSCJ_H32_V24;
        boolean[] exp = new boolean[0xFF];
        int height = vm.getDimension().height;
        BaseVdpProvider vdp = MdVdpTestUtil.createBaseTestVdp();

        VdpInterruptHandler h = VdpInterruptHandlerHelper.createSmsInstance(vdp);
        MdVdpTestUtil.updateHCounter(vdp, lineCounter);
        for (int i = lineCounter; i < exp.length; i++) {
            exp[i] = (i < height - 1) && ((i + 1) % (lineCounter + 1)) == 0;
        }
//        System.out.println(lineCounter + ":\n" +Arrays.toString( exp));
//        before();
        hLinesCounterBasic(vdp, h, vm, exp);
    }
}
