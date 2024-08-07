package mcd.cdc;

import com.google.common.base.MoreObjects;
import omegadrive.util.LogHelper;
import org.slf4j.Logger;

/**
 * Federico Berti
 * <p>
 * Copyright 2024
 */
public interface CdcModel {

    Logger LOG = LogHelper.getLogger(Cdc.class.getSimpleName());

    //some Cdc models have 16, others 32; mcd_verificator expects 32
    int NUM_CDC_REG = 32;
    int NUM_CDC_REG_MASK = NUM_CDC_REG - 1;

    int NUM_CDC_MCD_REG = 16;
    int NUM_CDC_MCD_REG_MASK = NUM_CDC_MCD_REG - 1;

    class CdcCommand {
        public byte[] fifo = new byte[8];  //COMIN
        public int read; //n3
        public int write; //n3
        public int empty = 1; //n1
    }

    class CdcStatus {
        public byte[] fifo = new byte[8];    //SBOUT
        public int read, write; //n3
        public int empty = 1; //n1
        public int enable;     //SOUTEN, n1
        public int active;     //STEN, n1
        public int busy;       //STBSY, n1
        public int wait;       //STWAI, n1

        public void reset() {
            active = busy = enable = 0;
            wait = 1;
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                    .add("enable_SOUTEN", enable)
                    .add("active_STEN", active)
                    .add("busy_STBSY", busy)
                    .add("wait_STWAI", wait)
                    .toString();
        }
    }

    class CdcDecoder {
        public int enable;  //DECEN, n1
        public int mode;    //MODE, n1
        public int form;    //FORM, n1
        public int valid;   //!VALST, n1

        public void reset() {
            enable = form = mode = 0;
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                    .add("enable_DECEN", enable)
                    .add("mode", mode)
                    .add("form", form)
                    .add("valid_!VALST", valid)
                    .toString();
        }
    }

    //cdc-transfer.cpp
    interface CdcTransferAction {
        void start();

        void stop();

        void dma();

        int read(); //n16

        void complete();
    }

    enum CdcTransferDestination {
        NONE_0, NONE_1, MAIN_READ_2, SUB_READ_3, DMA_PCM_4, DMA_PROGRAM_5, NONE_6, DMA_SUB_WRAM_7;

        public static CdcTransferDestination[] vals = CdcTransferDestination.values();
        private boolean dmaDestination;
        private boolean valid;

        CdcTransferDestination() {
            dmaDestination = name().startsWith("DMA");
            valid = !name().startsWith("NONE");
        }

        public boolean isDma() {
            return dmaDestination;
        }

        public boolean isValid() {
            return valid;
        }
    }

    class CdcTransfer {
        public CdcTransferDestination destination; //n3
        public int address; //n19

        public int source; //n16
        public int target; //n16
        public int pointer; //n16
        public int length; //n12

        public int enable;     //DOUTEN, n1
        public int active;     //DTEN, n1
        public int busy;       //DTBSY, n1
        public int wait;       //DTWAI, n1
        public int ready;      //DSR, n1
        public int completed;  //EDT, n1


        public void reset() {
            enable = active = busy = 0;
            wait = 1;
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                    .add("destination", destination)
                    .add("address", address)
                    .add("source", source)
                    .add("target", target)
                    .add("pointer", pointer)
                    .add("length", length)
                    .add("enable_DOUTEN", enable)
                    .add("active_DTEN", active)
                    .add("busy_DTBSY", busy)
                    .add("wait_DTWAI", wait)
                    .add("ready_DSR", ready)
                    .add("completed_EDT", completed)
                    .toString();
        }
    }

    class McdIrq {
        public int enable;     //n1
        public int pending;     //n1 DECI decoder, DTEI transfer

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                    .add("enable", enable)
                    .add("pending", pending)
                    .toString();
        }
    }

    class CdcIrq extends McdIrq {
        public McdIrq decoder;   //DECEIN + DECI
        public McdIrq transfer;  //DTEIEN + DTEI
//        public McdIrq command;   //CMDIEN + CMDI, unused

        public CdcIrq() {
            decoder = new McdIrq();
            transfer = new McdIrq();
        }

        public void reset() {
            decoder.pending = transfer.pending = 0;
            decoder.enable = transfer.enable = 0;
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                    .add("decoder_DECEIN+DECI", decoder)
                    .add("transfer_DTEIEN+DTEI", transfer)
                    .toString();
        }
    }

    class CdcHeader {
        public int minute; //n8
        public int second;//n8
        public int frame; //n8
        public int mode;//n8

        public void reset() {
            minute = second = frame = mode = 0;
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                    .add("minute", minute)
                    .add("second", second)
                    .add("frame", frame)
                    .add("mode", mode)
                    .toString();
        }
    }

    class CdcControl {
        //all n1
        public int head;               //SHDREN: 0 = read header, 1 = read subheader
        public int mode;               //MODE
        public int form;               //FORM
        public int commandBreak;       //CMDBK
        public int modeByteCheck;      //MBCKRQ
        public int erasureRequest;     //ERAMRQ
        public int writeRequest;       //WRRQ
        public int pCodeCorrection;    //PRQ
        public int qCodeCorrection;    //QRQ
        public int autoCorrection;     //AUTOQ
        public int errorCorrection;    //E01RQ
        public int edcCorrection;      //EDCRQ
        public int correctionWrite;    //COWREN
        public int descramble;         //DSCREN
        public int syncDetection;      //SYDEN
        public int syncInterrupt;      //SYIEN
        public int erasureCorrection;  //ERAMSL
        public int statusTrigger;      //STENTRG
        public int statusControl;      //STENCTL

        public void reset() {
            commandBreak = 1;
            pCodeCorrection = qCodeCorrection = writeRequest = erasureRequest = autoCorrection =
                    errorCorrection = edcCorrection = 0;
            head = modeByteCheck = form = mode = correctionWrite = descramble = syncDetection = syncInterrupt = 0;
            statusTrigger = statusControl = erasureCorrection = 0;
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                    .add("head_SHDREN", head)
                    .add("mode", mode)
                    .add("form", form)
                    .add("writeRequest_WRRQ", writeRequest)
                    .toString();
        }
    }

    class CdcContext {
        public int address, stopwatch;
        public byte[] ram;
        public CdcStatus status;
        public CdcDecoder decoder;
        public CdcTransfer transfer;
        public CdcIrq irq;
        public CdcControl control;
        public CdcHeader header;


        public CdcContext() {
            status = new CdcStatus();
            decoder = new CdcDecoder();
            transfer = new CdcTransfer();
            irq = new CdcIrq();
            control = new CdcControl();
            header = new CdcHeader();
        }

        @Override
        public String toString() {
            return status + "\n" +
                    transfer + "\n" +
                    decoder + "\n" +
                    irq + "\n" +
                    control + "\n" +
                    header;

        }
    }

    enum CdcAddressRead {
        COMIN, IFSTAT, DBCL, DBCH, HEAD0, HEAD1, HEAD2, HEAD3, PTL, PTH, WAL, WAH, STAT0, STAT1, STAT2, STAT3;
    }

    enum CdcAddressWrite {
        SBOUT, IFCTRL, DBCL, DBCH, DACL, DACH, DTRG, DTACK, WAL, WAH, CTRL0, CTRL1, PTL, PTH, CTRL2, RESET;
    }

    CdcAddressRead[] cdcAddrReadVals = CdcAddressRead.values();
    CdcAddressWrite[] cdcAddrWriteVals = CdcAddressWrite.values();
}
