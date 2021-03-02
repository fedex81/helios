/*
 * VdpFifo
 * Copyright (c) 2018-2019 Federico Berti
 * Last modified: 17/10/19 11:37
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

import omegadrive.vdp.model.GenesisVdpProvider;
import omegadrive.vdp.model.IVdpFifo;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.message.ParameterizedMessage;

import java.util.Arrays;
import java.util.stream.IntStream;

public class VdpFifo implements IVdpFifo {

    public static final boolean logEnable = false;

    public static final int FIFO_SIZE = 4;
    public static final boolean printToSysOut = false;
    private int popPointer;
    private int pushPointer;
    private int fifoSize;

    public VdpFifo() {
        IntStream.range(0, FIFO_SIZE).forEach(i -> fifo[i] = new VdpFifoEntry());
    }

    private final static Logger LOG = LogManager.getLogger(VdpFifo.class.getSimpleName());
    private final VdpFifoEntry[] fifo = new VdpFifoEntry[FIFO_SIZE];

    @Override
    public void push(GenesisVdpProvider.VramMode vdpRamMode, int addressReg, int data) {
        if (isFull()) {
            LOG.info("FIFO full");
            return;
        }
        VdpFifoEntry entry = fifo[pushPointer];
        entry.data = data;
        entry.addressRegister = addressReg;
        entry.vdpRamMode = vdpRamMode;
        entry.firstByteWritten = false;
        pushPointer = (pushPointer + 1) % FIFO_SIZE;
        fifoSize++;
        logState(entry, "push");
    }

    @Override
    public VdpFifoEntry pop() {
        if (isEmpty()) {
            LOG.info("FIFO empty");
            return null;
        }
        VdpFifoEntry entry = fifo[popPointer];
        popPointer = (popPointer + 1) % FIFO_SIZE;
        fifoSize--;
        logState(entry, "pop");
        return entry;
    }

    private void logState(VdpFifoEntry entry, String type) {
        if (logEnable) {
            ParameterizedMessage pm = new ParameterizedMessage(
                    "Fifo {}: {}, address: {}, data: {}, push: {}, pop: {}, size: {}\nstate: {}", type,
                    entry.vdpRamMode,
                    Integer.toHexString(entry.addressRegister), Integer.toHexString(entry.data),
                    pushPointer, popPointer, fifoSize, Arrays.toString(fifo));
            String str = pm.getFormattedMessage();
            LOG.info(str);
            if (printToSysOut) {
                System.out.println(str);
            }

        }
    }

    public VdpFifoEntry peek() {
        return fifo[popPointer];
    }

    @Override
    public boolean isEmpty() {
        return fifoSize == 0;
    }

    @Override
    public boolean isFull() {
        return fifoSize >= FIFO_SIZE;
    }
}
