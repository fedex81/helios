package omegadrive.savestate;

import java.io.Serial;
import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Federico Berti
 * <p>
 * Copyright 2025
 */
public class MapLikeHolder implements Serializable {
    @Serial
    private static final long serialVersionUID = 1_144_206_089_806_580_950L;

    private static final Set<Class<?>> validTypes = Set.of(LinkedHashMap.class);

    public int arraysLen;
    public Object[] keys;

    public Object[] values;

    public void storeFromMap(Map map) {
        checkValidType(map);
        arraysLen = map.size();
        keys = map.keySet().toArray();
        values = map.values().toArray(new Object[0]);
        assert keys.length == arraysLen && values.length == arraysLen;
    }

    public void loadToMap(Map map) {
        checkValidType(map);
        assert map.isEmpty();
        assert keys.length == arraysLen && values.length == arraysLen;
        for (int i = 0; i < arraysLen; i++) {
            map.put(keys[i], values[i]);
        }
        assert !map.isEmpty();
    }

    private void checkValidType(Map m) {
        if (!validTypes.contains(m.getClass())) {
            throw new RuntimeException("Unable to handle this type: " + m.getClass());
        }
    }
}
