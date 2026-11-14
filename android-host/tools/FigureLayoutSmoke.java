package com.mascotcapsule.micro3d.v3;

public final class FigureLayoutSmoke {
    private static int checks;

    public static void main(String[] args) {
        FigureLayout layout = new FigureLayout();
        layout.setCenter(120, 160);
        assertIdentity(layout.getAffineTrans(), "default constructor");
        check(layout.getScaleX() == 512 && layout.getScaleY() == 512,
                "default scale does not match the Micro3D default");
        check(layout.getCenterX() == 120 && layout.getCenterY() == 160,
                "setting the center changed the default transform");

        assertIdentity(new FigureLayout(null, 2, 3, 4, 5).getAffineTrans(),
                "constructor with null transform");
        layout.setAffineTrans((AffineTrans)null);
        assertIdentity(layout.getAffineTrans(), "setAffineTrans(null)");

        System.out.println("FigureLayoutSmoke: " + checks + " checks passed");
    }

    private static void assertIdentity(AffineTrans transform, String context) {
        check(transform != null, context + " returned no transform");
        check(transform.m00 == 4096 && transform.m11 == 4096 && transform.m22 == 4096,
                context + " did not return a fixed-point identity transform");
        check(transform.m01 == 0 && transform.m02 == 0 && transform.m03 == 0
                        && transform.m10 == 0 && transform.m12 == 0 && transform.m13 == 0
                        && transform.m20 == 0 && transform.m21 == 0 && transform.m23 == 0,
                context + " returned a non-identity transform");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }
}
