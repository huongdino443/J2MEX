package org.recompile.freej2me.lite;

public final class RecentsTaskPolicySmoke {
    public static void main(String[] args) {
        int visible = 42;
        if (RecentsTaskPolicy.shouldExcludeTask(visible, visible))
            throw new AssertionError("The current game task must remain visible");
        if (!RecentsTaskPolicy.shouldExcludeTask(7, visible)
                || !RecentsTaskPolicy.shouldExcludeTask(99, visible))
            throw new AssertionError("Every other J2MEX task must stay hidden");
        if (!RecentsTaskPolicy.shouldExcludeTask(0, -1))
            throw new AssertionError("No task should be visible without an active task");
        System.out.println("RecentsTaskPolicySmoke: one selected task remains visible");
    }
}
