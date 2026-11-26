package com.dxbmr.lgservice;

import android.content.Context;
import android.hardware.ConsumerIrManager;
import java.util.ArrayList;
import java.util.List;

/** LG NEC infrared codes, sent through the phone's IR blaster. */
public class LgIr {
    public static final int FREQ = 38000;
    // Standard LG NEC codes (address 0x04 -> 0x20DF prefix)
    public static final int POWER = 0x20DF10EF, SETTINGS = 0x20DFC23D, OK = 0x20DF22DD,
            BACK = 0x20DF14EB, EXIT = 0x20DFDA25, HOME = 0x20DF3EC1,
            UP = 0x20DF02FD, DOWN = 0x20DF827D, LEFT = 0x20DFE01F, RIGHT = 0x20DF609F,
            IN_START = 0x20DFDF20, EZ_ADJUST = 0x20DFFF00, IN_STOP = 0x20DF5FA0;
    public static final int[] DIGITS = {0x20DF08F7, 0x20DF8877, 0x20DF48B7, 0x20DFC837,
            0x20DF28D7, 0x20DFA857, 0x20DF6897, 0x20DFE817, 0x20DF18E7, 0x20DF9867};

    private final ConsumerIrManager ir;

    public LgIr(Context c) { ir = (ConsumerIrManager) c.getSystemService(Context.CONSUMER_IR_SERVICE); }

    public boolean available() { return ir != null && ir.hasIrEmitter(); }

    private static int[] necFrame(int code) {
        List<Integer> p = new ArrayList<>();
        p.add(9000); p.add(4500);
        for (int i = 31; i >= 0; i--) {           // MSB first, matching IRremote sendNEC(0x20DF....)
            p.add(560); p.add(((code >>> i) & 1) == 1 ? 1690 : 560);
        }
        p.add(560); p.add(40000);                  // end burst + gap (~108 ms frame)
        int[] a = new int[p.size()];
        for (int i = 0; i < a.length; i++) a[i] = p.get(i);
        return a;
    }

    private static final int[] REPEAT = {9000, 2250, 560, 96000}; // NEC repeat frame, 108 ms

    public void send(int code) { ir.transmit(FREQ, necFrame(code)); }

    /** Long-press: one frame then NEC repeat frames for holdMs. Blocking; call off the UI thread. */
    public void hold(int code, int holdMs) {
        ir.transmit(FREQ, necFrame(code));
        int n = holdMs / 108;
        for (int i = 0; i < n; i++) ir.transmit(FREQ, REPEAT);
    }

    public void typeDigits(String digits) throws InterruptedException {
        for (char ch : digits.toCharArray()) {
            if (ch < '0' || ch > '9') continue;
            send(DIGITS[ch - '0']);
            Thread.sleep(450);
        }
    }
}
