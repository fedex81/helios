package omegadrive.input.swing;

import net.java.games.input.*;
import omegadrive.joypad.JoypadProvider;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.swing.*;
import java.awt.event.KeyEvent;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;

/**
 * Federico Berti
 * <p>
 * Copyright 2022
 */
public class JInputUtil {

    private static final Logger LOG = LogManager.getLogger(JInputUtil.class.getSimpleName());

    private final static String manualAwtJInputMap = "PAGEUP=VK_PAGE_UP,PAGEDOWN=VK_PAGE_DOWN,LBRACKET=VK_OPEN_BRACKET," +
            "RBRACKET=VK_CLOSE_BRACKET,CAPITAL=VK_CAPS_LOCK,LCONTROL=VK_CONTROL,RCONTROL=VK_CONTROL,LSHIFT=VK_SHIFT,RSHIFT=VK_SHIFT," +
            "LALT=VK_ALT,RALT=VK_ALT,LWIN=VK_WINDOWS,RWIN=VK_WINDOWS,APOSTROPHE=VK_QUOTEDBL,RETURN=VK_ENTER,SCROLL=VK_SCROLL_LOCK," +
            "NUMLOCK=VK_NUM_LOCK,BACKSLASH=VK_BACK_SLASH";

    protected final static String ignoredJInputKeys = "SysRq:Unlabeled:Unknown:Back:Num Enter:Ax:Num ,:Sleep:~:Power:Num =:Noconvert:Apps:Underline:Void:Yen";

    /**
     * Maps JInput Key.<name> to Awt KeyEvent.keyCode
     * See awtJInputMappingGenerator to recreate the mappings.
     */
    private final static String awtJInputMap = manualAwtJInputMap + ",ADD=VK_ADD, DOWN=VK_DOWN, DECIMAL=VK_DECIMAL, MINUS=VK_MINUS, NUMPAD1=VK_NUMPAD1, CONVERT=VK_CONVERT, NUMPAD2=VK_NUMPAD2, NUMPAD3=VK_NUMPAD3, NUMPAD4=VK_NUMPAD4, SEMICOLON=VK_SEMICOLON, NUMPAD0=VK_NUMPAD0, NUMPAD9=VK_NUMPAD9, UP=VK_UP, NUMPAD5=VK_NUMPAD5, NUMPAD6=VK_NUMPAD6, NUMPAD7=VK_NUMPAD7, NUMPAD8=VK_NUMPAD8, LEFT=VK_LEFT, STOP=VK_STOP, KANA=VK_KANA, F1=VK_F1, F2=VK_F2, F3=VK_F3, F4=VK_F4, F5=VK_F5, F6=VK_F6, _0=VK_0, F7=VK_F7, _1=VK_1, F8=VK_F8, _2=VK_2, F9=VK_F9, AT=VK_AT, _3=VK_3, _4=VK_4, _5=VK_5, _6=VK_6, PERIOD=VK_PERIOD, _7=VK_7, _8=VK_8, _9=VK_9, MULTIPLY=VK_MULTIPLY, END=VK_END, INSERT=VK_INSERT, A=VK_A, B=VK_B, C=VK_C, D=VK_D, E=VK_E, F=VK_F, G=VK_G, H=VK_H, KANJI=VK_KANJI, I=VK_I, J=VK_J, K=VK_K, L=VK_L, M=VK_M, SUBTRACT=VK_SUBTRACT, N=VK_N, O=VK_O, DIVIDE=VK_DIVIDE, P=VK_P, SPACE=VK_SPACE, PAUSE=VK_PAUSE, Q=VK_Q, R=VK_R, DELETE=VK_DELETE, S=VK_S, T=VK_T, U=VK_U, V=VK_V, W=VK_W, X=VK_X, Y=VK_Y, RIGHT=VK_RIGHT, Z=VK_Z, COMMA=VK_COMMA, CIRCUMFLEX=VK_CIRCUMFLEX, EQUALS=VK_EQUALS, F10=VK_F10, F12=VK_F12, F11=VK_F11, SLASH=VK_SLASH, F14=VK_F14, F13=VK_F13, COLON=VK_COLON, F15=VK_F15, ESCAPE=VK_ESCAPE, TAB=VK_TAB, HOME=VK_HOME";

    public final static Component.Identifier.Key[] VIRTUAL_KEYS;
    public final static Map<Component.Identifier.Key, Integer> jInputAwtKeyMap;
    public final static Map<Integer, Component.Identifier.Key> awtJInputKeyMap;

    static {
        jInputAwtKeyMap = new HashMap<>();
        awtJInputKeyMap = new HashMap<>();
        VIRTUAL_KEYS = parseMappings();
    }

    private static Component.Identifier.Key[] parseMappings() {
        List<Component.Identifier.Key> l = new ArrayList<>();
        String[] tk = awtJInputMap.split(",");
        for (String s : tk) {
            String[] intks = s.split("=");
            String jInputName = intks[0].trim();
            String awtName = intks[1].trim();
            try {
                Field jf = Component.Identifier.Key.class.getDeclaredField(jInputName);
                Field af = KeyEvent.class.getDeclaredField(awtName);
                Integer awtCode = (Integer) af.get(null);
                Component.Identifier.Key key = (Component.Identifier.Key) jf.get(null);
                awtJInputKeyMap.put(awtCode, key);
                jInputAwtKeyMap.put(key, awtCode);
                l.add(key);
            } catch (Exception e) {
                LOG.error("Unable to map: {}", Arrays.toString(intks));
            }
        }
        return l.toArray(Component.Identifier.Key[]::new);
    }


    public static void main(String[] args) throws Exception {
        awtJInputMappingGenerator();
        parseMappings();
    }

    public static final Controller NO_CONTROLLER = new AbstractController("NONE",
            new Component[0], new Controller[0], new Rumbler[0]) {
        @Override
        protected boolean getNextDeviceEvent(Event event) {
            return false;
        }
    };

    public static final Controller VIRTUAL_KEYBOARD = new AbstractController("Keyboard",
            new Component[0], new Controller[0], new Rumbler[0]) {
        @Override
        protected boolean getNextDeviceEvent(Event event) {
            return false;
        }
    };

    public static class JInputId {
        String name;
        Class<? extends Component.Identifier> type;

        public JInputId(String name, Class<? extends Component.Identifier> type) {
            this.name = name;
            this.type = type;
        }

        public JInputId(Component.Identifier cmp) {
            this(cmp.getName(), cmp.getClass());
        }

        @Override
        public String toString() {
            return GamepadSetupView.toString(name, type);
        }
    }

    public static class ActiveControllerCtx {
        public Controller c;
        public String[] joyNames;
        public JLabel[] padLabels, consolePadLabels;
        public JComboBox<String>[] padSelectedBox;
        public JoypadProvider.JoypadButton[] jb;
        public Map<String, Boolean> invertedMap;

        public ActiveControllerCtx(Controller c) {
            this.c = c;
            joyNames = Arrays.stream(c.getComponents()).map(cm ->
                    cm.getIdentifier().getClass().getSimpleName() + ": " + cm.getName()).toArray(String[]::new);
            jb = Arrays.stream(JoypadProvider.JoypadButton.values()).filter(jb -> !jb.name().startsWith("K")).
                    toArray(JoypadProvider.JoypadButton[]::new);
            invertedMap = new HashMap<>();
        }
    }

    private static void awtJInputMappingGenerator() {
        Field[] jinputFields = Component.Identifier.Key.class.getDeclaredFields();
        Map<String, String> m = new HashMap<>();
        for (Field f : jinputFields) {
            if (Modifier.isStatic(f.getModifiers())) {
                String awtName = "VK_" + f.getName();
                if (f.getName().startsWith("_")) {
                    awtName = "VK" + f.getName();
                }
                awtName = awtName.toUpperCase();
                try {
                    Field awt = KeyEvent.class.getDeclaredField(awtName);
                    m.put(f.getName(), awt.getName());
                } catch (Exception e) {
                    System.out.println("Unable to map: " + f.getName() + ", awt expected field: " + awtName);
                }
            }
        }
        System.out.println(m);
    }
}
