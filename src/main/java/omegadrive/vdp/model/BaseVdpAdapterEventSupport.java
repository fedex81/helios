package omegadrive.vdp.model;

import java.util.Collections;
import java.util.Comparator;
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
        final List<VdpEventListener> l = getVdpEventListenerList();
        for (int i = 0; i < l.size(); i++) {
            l.get(i).onVdpEvent(event, value);
        }
    }

    default void fireVdpEventOnChange(BaseVdpProvider.VdpEvent event, Object prev, Object value) {
        if (prev != value) {
            fireVdpEvent(event, value);
        }
    }

    default boolean addVdpEventListener(BaseVdpProvider.VdpEventListener l) {
        List<BaseVdpProvider.VdpEventListener> l1 = getVdpEventListenerList();
        boolean res = l1.add(l);
        //NOTE: make sure the baseSystem's is the last listener to be called
        l1.sort(Comparator.comparingInt(VdpEventListener::order));
        return res;
    }

    default boolean removeVdpEventListener(BaseVdpProvider.VdpEventListener l) {
        return getVdpEventListenerList().remove(l);
    }

    enum VdpEvent {
        NEW_FRAME, VIDEO_MODE, REG_H_LINE_COUNTER_CHANGE, INTERRUPT, LEFT_COL_BLANK, H_LINE_UNDERFLOW,
        H_BLANK_CHANGE, V_COUNT_INC, V_BLANK_CHANGE, INTERLACE_FIELD_CHANGE, INTERLACE_MODE_CHANGE,
        VDP_VINT_PENDING, VDP_ACTIVE_DISPLAY_CHANGE, VDP_HINT_PENDING, VDP_IE0_VINT, VDP_IE1_HINT, VDP_IE2_EXT_INT,
    }

    interface VdpEventListener extends EventListener {

        default int order() {
            return 0;
        }

        default void onVdpEvent(BaseVdpProvider.VdpEvent event, Object value) {
        }

        default void onRegisterChange(int reg, int value) {
        }

        default void onNewFrame() {
            onVdpEvent(BaseVdpProvider.VdpEvent.NEW_FRAME, null);
        }
    }
}
