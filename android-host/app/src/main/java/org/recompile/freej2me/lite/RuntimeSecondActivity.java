package org.recompile.freej2me.lite;

/** Runtime entry point for the second isolated HotSpot process. */
public class RuntimeSecondActivity extends RuntimeActivity {
    @Override protected int defaultSessionSlot() { return 1; }
}
