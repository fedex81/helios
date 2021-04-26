package omegadrive.vdp.model;

import java.util.Collections;
import java.util.EventListener;
import java.util.List;

/**
 * Federico Berti
 * <p>
 * Copyright 2021
 */
public interface BaseVdpAdapterEventSupport {

    default List<BaseVdpProvider.VdpEventListener> getVdpEventListenerList() {
        return Collections.emptyList();
    }

    default void fireVdpEvent(BaseVdpProvider.VdpEvent event, Object value) {
        getVdpEventListenerList().forEach(l -> l.onVdpEvent(event, value));
    }

    default void fireVdpEventOnChange(BaseVdpProvider.VdpEvent event, Object prev, Object value) {
        if (prev != value) {
            getVdpEventListenerList().forEach(l -> l.onVdpEvent(event, value));
        }
    }

    default boolean addVdpEventListener(BaseVdpProvider.VdpEventListener l) {
        return getVdpEventListenerList().add(l);
    }

    default boolean removeVdpEventListener(BaseVdpProvider.VdpEventListener l) {
        return getVdpEventListenerList().remove(l);
    }

    enum VdpEvent {
        NEW_FRAME, VIDEO_MODE, REG_H_LINE_COUNTER_CHANGE, INTERRUPT, LEFT_COL_BLANK, H_LINE_UNDERFLOW,
        H_BLANK_CHANGE, V_COUNT_INC, V_BLANK_CHANGE, INTERLACE_FIELD_CHANGE, INTERLACE_MODE_CHANGE,
        VDP_VINT_PENDING
    }

    interface VdpEventListener extends EventListener {

        default void onVdpEvent(BaseVdpProvider.VdpEvent event, Object value) {
        }

        default void onRegisterChange(int reg, int value) {
        }

        default void onNewFrame() {
            onVdpEvent(BaseVdpProvider.VdpEvent.NEW_FRAME, null);
        }
    }
}
