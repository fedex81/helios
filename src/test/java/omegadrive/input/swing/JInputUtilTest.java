package omegadrive.input.swing;

import net.java.games.input.Component;
import org.junit.Test;
import org.junit.jupiter.api.Assertions;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

import static omegadrive.input.swing.JInputUtil.VIRTUAL_KEYS;

/**
 * Federico Berti
 * <p>
 * Copyright 2022
 */
public class JInputUtilTest {

    private static Function<Field, String> toKeyName = f -> {
        try {
            return ((Component.Identifier.Key) f.get(null)).getName();
        } catch (IllegalAccessException e) {
        }
        return "ERROR";
    };

    @Test
    public void testKeyboardMappingsLoader() {
        Assertions.assertEquals(JInputUtil.jInputAwtKeyMap.size(), JInputUtil.awtJInputKeyMap.size() + 4);
    }

    @Test
    public void testJInputFieldsSet() {
        String[] ignored = JInputUtil.ignoredJInputKeys.split(":");
        Field[] jinputFields = Component.Identifier.Key.class.getDeclaredFields();
        Set<String> allKeyNames = Arrays.stream(jinputFields).filter(f -> Modifier.isStatic(f.getModifiers())).map(toKeyName).
                collect(Collectors.toSet());
        Set<String> matched = new TreeSet<>(Arrays.stream(VIRTUAL_KEYS).map(Component.Identifier.Key::getName).collect(Collectors.toList()));
        for (var k : allKeyNames) {
            boolean ignore = Arrays.stream(ignored).anyMatch(i -> i.equalsIgnoreCase(k));
            if (ignore) {
                continue;
            }
            Assertions.assertTrue(matched.contains(k), "Missing: " + k);
        }
    }
}
