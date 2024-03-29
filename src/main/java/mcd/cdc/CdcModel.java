package mcd.cdc;

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
    int NUM_CDC_REG_MASK = 31;

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
        int read, write; //n3
        int empty = 1; //n1
        int enable;     //SOUTEN, n1
        int active;     //STEN, n1
        int busy;       //STBSY, n1
        int wait;       //STWAI, n1

        public void reset() {
            active = busy = enable = 0;
            wait = 1;
        }
    }

    class CdcDecoder {
        int enable;  //DECEN, n1
        int mode;    //MODE, n1
        int form;    //FORM, n1
        int valid;   //!VALST, n1

        public void reset() {
            enable = form = mode = 0;
        }
    }

    //cdc-transfer.cpp
    interface CdcTransferAction {
        default void dma() {
            throw new RuntimeException();
        }

        default int read() {
            throw new RuntimeException();
        } //n16

        default void start() {
            throw new RuntimeException();
        }

        default void complete() {
            throw new RuntimeException();
        }

        default void stop() {
            LogHelper.logWarnOnce(LOG, "Not supported, CDC transfer stop");
        }
    }

    class CdcTransfer implements CdcTransferAction {
        int destination; //n3
        int address; //n19

        int source; //n16
        int target; //n16
        int pointer; //n16
        int length; //n12

        int enable;     //DOUTEN, n1
        int active;     //DTEN, n1
        int busy;       //DTBSY, n1
        int wait;       //DTWAI, n1
        int ready;      //DSR, n1
        int completed;  //EDT, n1

        public void reset() {
            enable = active = busy = 0;
            wait = 1;
//            stop(); //TODO
        }
    }

    class McdIrq {
        public int enable;     //n1
        public int pending;     //n1
    }

    class CdcIrq extends McdIrq {
        public McdIrq decoder;   //DECEIN + DECI
        public McdIrq transfer;  //DTEIEN + DTEI
        public McdIrq command;   //CMDIEN + CMDI

        public CdcIrq() {
            decoder = new McdIrq();
            transfer = new McdIrq();
            command = new McdIrq();
        }

        void reset() {
            decoder.pending = transfer.pending = command.pending = 0;
            decoder.enable = transfer.enable = command.enable = 0;
        }
    }

    class CdcControl {
        //all n1
        int head;               //SHDREN: 0 = read header, 1 = read subheader
        int mode;               //MODE
        int form;               //FORM
        int commandBreak;       //CMDBK
        int modeByteCheck;      //MBCKRQ
        int erasureRequest;     //ERAMRQ
        int writeRequest;       //WRRQ
        int pCodeCorrection;    //PRQ
        int qCodeCorrection;    //QRQ
        int autoCorrection;     //AUTOQ
        int errorCorrection;    //E01RQ
        int edcCorrection;      //EDCRQ
        int correctionWrite;    //COWREN
        int descramble;         //DSCREN
        int syncDetection;      //SYDEN
        int syncInterrupt;      //SYIEN
        int erasureCorrection;  //ERAMSL
        int statusTrigger;      //STENTRG
        int statusControl;      //STENCTL

        public void reset() {
            commandBreak = 1;
            pCodeCorrection = qCodeCorrection = writeRequest = erasureRequest = autoCorrection =
                    errorCorrection = edcCorrection = 0;
            head = modeByteCheck = form = mode = correctionWrite = descramble = syncDetection = syncInterrupt = 0;
            statusTrigger = statusControl = erasureCorrection = 0;
        }
    }

    class CdcContext {
        public int address, stopwatch;
        public byte[] ram;
        public CdcStatus status;
        public CdcCommand command;
        public CdcDecoder decoder;
        public CdcTransfer transfer;
        public CdcIrq irq;
        public CdcControl control;


        public CdcContext() {
            status = new CdcStatus();
            command = new CdcCommand();
            decoder = new CdcDecoder();
            transfer = new CdcTransfer();
            irq = new CdcIrq();
            control = new CdcControl();
        }
    }

    enum CdcAddress {
        COMIN_SBOUT, IFCTRL, DBCL, DBCH,
        NONE4, NONE5, NONE6, NONE7,
        WAL, WAH, PTL, PTH,
        NONE12, NONE13, NONE14, RESET
    }

    CdcAddress[] cdcAddrVals = CdcAddress.values();
}
