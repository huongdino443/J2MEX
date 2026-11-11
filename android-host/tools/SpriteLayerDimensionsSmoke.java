import javax.microedition.lcdui.Image;
import javax.microedition.lcdui.game.Sprite;

public final class SpriteLayerDimensionsSmoke {
    private static int checks;

    public static void main(String[] args) {
        Sprite sprite = new Sprite(Image.createImage(16, 12), 8, 6);
        checkSize(sprite, 8, 6, "untransformed frame reports its dimensions");

        sprite.setTransform(Sprite.TRANS_ROT90);
        checkSize(sprite, 6, 8, "90-degree rotation swaps layer dimensions");

        sprite.setTransform(Sprite.TRANS_MIRROR);
        checkSize(sprite, 8, 6, "mirror preserves layer dimensions");

        sprite.setImage(Image.createImage(18, 20), 6, 10);
        checkSize(sprite, 6, 10, "setImage refreshes frame dimensions");

        sprite.setTransform(Sprite.TRANS_MIRROR_ROT270);
        checkSize(sprite, 10, 6, "mirrored 270-degree rotation swaps layer dimensions");

        System.out.println("SpriteLayerDimensionsSmoke: " + checks + " checks passed");
    }

    private static void checkSize(Sprite sprite, int expectedWidth, int expectedHeight,
            String description) {
        if (sprite.getWidth() != expectedWidth || sprite.getHeight() != expectedHeight) {
            throw new AssertionError(description + ": expected " + expectedWidth + "x"
                    + expectedHeight + ", got " + sprite.getWidth() + "x" + sprite.getHeight());
        }
        checks++;
    }
}
