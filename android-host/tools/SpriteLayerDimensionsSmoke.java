import java.awt.image.BufferedImage;
import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Image;
import javax.microedition.lcdui.game.LayerManager;
import javax.microedition.lcdui.game.Sprite;
import javax.microedition.lcdui.game.TiledLayer;
import org.recompile.mobile.Mobile;
import org.recompile.mobile.MobilePlatform;
import org.recompile.mobile.PlatformImage;

public final class SpriteLayerDimensionsSmoke {
    private static int checks;

    public static void main(String[] args) {
        Sprite sprite = new Sprite(Image.createImage(16, 12), 8, 6);
        if (!sprite.isVisible())
            throw new AssertionError("MIDP layers must be visible by default");
        checks++;
        checkSize(sprite, 8, 6, "untransformed frame reports its dimensions");

        sprite.setTransform(Sprite.TRANS_ROT90);
        checkSize(sprite, 6, 8, "90-degree rotation swaps layer dimensions");

        sprite.setTransform(Sprite.TRANS_MIRROR);
        checkSize(sprite, 8, 6, "mirror preserves layer dimensions");

        sprite.setImage(Image.createImage(18, 20), 6, 10);
        checkSize(sprite, 6, 10, "setImage refreshes frame dimensions");

        sprite.setTransform(Sprite.TRANS_MIRROR_ROT270);
        checkSize(sprite, 10, 6, "mirrored 270-degree rotation swaps layer dimensions");

        checkLayerManagerPaintsLayersDirectly();
        checkLayerManagerInsertMovesExistingLayer();
        checkLayerManagerReinsertChangesDepth();
        System.out.println("SpriteLayerDimensionsSmoke: " + checks + " checks passed");
    }

    private static void checkLayerManagerPaintsLayersDirectly() {
        MobilePlatform platform = new MobilePlatform(64, 48);
        Mobile.setPlatform(platform);

        Image target = Image.createImage(64, 48);
        Graphics targetGraphics = target.getGraphics();
        targetGraphics.setColor(0x000000);
        targetGraphics.fillRect(0, 0, 64, 48);

        Image tileSet = Image.createImage(4, 2);
        Graphics tileGraphics = tileSet.getGraphics();
        tileGraphics.setColor(0x00FF00);
        tileGraphics.fillRect(0, 0, 2, 2);
        tileGraphics.setColor(0x0000FF);
        tileGraphics.fillRect(2, 0, 2, 2);

        TiledLayer tiledLayer = new TiledLayer(2, 1, tileSet, 2, 2);
        tiledLayer.setCell(0, 0, 1);
        tiledLayer.setCell(1, 0, 2);
        tiledLayer.setPosition(10, 7);
        if (tiledLayer.getWidth() != 4 || tiledLayer.getHeight() != 2)
            throw new AssertionError("TiledLayer must report its cell-grid dimensions");
        if (!tiledLayer.isVisible())
            throw new AssertionError("new TiledLayer must be visible without an explicit override");
        checks++;

        Image spriteImage = Image.createImage(4, 4);
        Graphics spriteGraphics = spriteImage.getGraphics();
        spriteGraphics.setColor(0xFF0000);
        spriteGraphics.fillRect(0, 0, 4, 4);
        Sprite sprite = new Sprite(spriteImage);
        sprite.setPosition(10, 7);
        if (!sprite.collidesWith(tiledLayer, false))
            throw new AssertionError("Sprite must collide with occupied tiled cells");
        checks++;
        sprite.setVisible(true);

        Sprite movingSprite = new Sprite(spriteImage);
        movingSprite.setPosition(12, 9);
        movingSprite.setVisible(true);

        Sprite clippedSprite = new Sprite(spriteImage);
        clippedSprite.setPosition(18, 7);
        clippedSprite.setVisible(true);

        LayerManager manager = new LayerManager();
        manager.setViewWindow(10, 7, 8, 8);
        manager.append(tiledLayer);
        manager.append(sprite);
        manager.append(movingSprite);
        manager.append(clippedSprite);
        manager.paint(targetGraphics, 4, 5);

        BufferedImage rendered = ((PlatformImage) target).getCanvas();
        requireColor(rendered.getRGB(4, 5), 0x00FF00,
                "static tile index 1 selects the first tile and honors the view offset");
        requireColor(rendered.getRGB(6, 5), 0x0000FF,
                "static tile index 2 selects the second tile");
        requireColor(rendered.getRGB(6, 7), 0xFF0000,
                "moving Sprite paints at its screen position without leaving the viewport");
        requireColor(rendered.getRGB(12, 5), 0x000000,
                "view-window clipping prevents layers from drawing outside the viewport");
        if (targetGraphics.getTranslateX() != 0 || targetGraphics.getTranslateY() != 0)
            throw new AssertionError("LayerManager must restore the caller's graphics translation");
        if (targetGraphics.getClipX() != 0 || targetGraphics.getClipY() != 0
                || targetGraphics.getClipWidth() != 64 || targetGraphics.getClipHeight() != 48)
            throw new AssertionError("LayerManager must restore the caller's graphics clip");
        checks += 6;

        TiledLayer animatedLayer = new TiledLayer(1, 1, tileSet, 2, 2);
        int animatedTile = animatedLayer.createAnimatedTile(2);
        animatedLayer.setCell(0, 0, animatedTile);
        animatedLayer.setPosition(20, 7);
        animatedLayer.setAnimatedTile(animatedTile, 1);
        animatedLayer.paint(targetGraphics);
        requireColor(rendered.getRGB(20, 7), 0x00FF00,
                "changing an animated tile redraws its current static tile");
        animatedLayer.setCell(0, 0, 0);
        Image clearedTarget = Image.createImage(4, 4);
        Graphics clearedGraphics = clearedTarget.getGraphics();
        clearedGraphics.setColor(0x000000);
        clearedGraphics.fillRect(0, 0, 4, 4);
        animatedLayer.paint(clearedGraphics);
        requireColor(((PlatformImage) clearedTarget).getCanvas().getRGB(0, 0), 0x000000,
                "empty tile index 0 clears the old tile instead of leaving a trail");
        checks += 2;

        TiledLayer wallLayer = new TiledLayer(3, 2, tileSet, 2, 2);
        wallLayer.setCell(1, 0, 1);
        Sprite projectile = new Sprite(spriteImage);
        projectile.setPosition(2, 0);
        if (!projectile.collidesWith(wallLayer, false))
            throw new AssertionError("Sprite overlap with a wall cell must register");
        checks++;
        wallLayer.setCell(1, 0, 0);
        if (projectile.collidesWith(wallLayer, false))
            throw new AssertionError("empty TiledLayer cells must not collide");
        checks++;
        wallLayer.setCell(1, 0, 1);
        projectile.setPosition(6, 0);
        if (projectile.collidesWith(wallLayer, false))
            throw new AssertionError("edge-touching sprite must not collide with the map");
        checks++;
        projectile.setPosition(2, 0);
        projectile.setVisible(false);
        if (projectile.collidesWith(wallLayer, false))
            throw new AssertionError("invisible sprites must not collide with tiled cells");
        checks++;
    }

    private static void checkLayerManagerInsertMovesExistingLayer() {
        LayerManager manager = new LayerManager();
        Image image = Image.createImage(2, 2);
        Sprite first = new Sprite(image);
        Sprite second = new Sprite(image);
        manager.append(first);
        manager.append(second);
        manager.insert(first, 1);
        if (manager.getSize() != 2 || manager.getLayerAt(0) != second
                || manager.getLayerAt(1) != first)
            throw new AssertionError("insert(existing layer) must move it, not duplicate it");
        manager.append(second);
        if (manager.getSize() != 2 || manager.getLayerAt(1) != second)
            throw new AssertionError("append(existing layer) must move it to the back");
        checks += 2;
    }

    private static void checkLayerManagerReinsertChangesDepth() {
        Image target = Image.createImage(64, 48);
        Graphics graphics = target.getGraphics();
        graphics.setColor(0x000000);
        graphics.fillRect(0, 0, 64, 48);

        Image markerImage = Image.createImage(1, 1);
        Image flagImage = Image.createImage(20, 20);
        Graphics flagGraphics = flagImage.getGraphics();
        flagGraphics.setColor(0xFFFF00);
        flagGraphics.fillRect(0, 0, 20, 20);
        flagGraphics.setColor(0x000000);
        for (int x = -20; x < 40; x += 6)
            flagGraphics.fillRect(x, 0, 2, 20);

        Image tankImage = Image.createImage(20, 20);
        Graphics tankGraphics = tankImage.getGraphics();
        tankGraphics.setColor(0xFF0000);
        tankGraphics.fillRect(0, 0, 20, 20);

        LayerManager manager = new LayerManager();
        manager.setViewWindow(0, 0, 64, 48);
        TiledLayer ground = new TiledLayer(1, 1, markerImage, 1, 1);
        manager.append(ground);
        for (int priority = 1; priority <= 8; priority++)
            manager.insert(new PriorityMarker(priority, markerImage), 0);

        Sprite groundObject = new Sprite(flagImage);
        groundObject.setPosition(16, 16);
        // The game adds this object in its base constructor, then inserts the
        // same Sprite again at its declared priority. Reinsert must move it.
        manager.insert(groundObject, 0);
        manager.insert(groundObject, findPriority(manager, 4));

        Sprite tank = new Sprite(tankImage);
        tank.setPosition(16, 16);
        manager.insert(tank, findPriority(manager, 5));
        manager.paint(graphics, 0, 0);

        BufferedImage rendered = ((PlatformImage) target).getCanvas();
        requireColor(rendered.getRGB(24, 24), 0xFF0000,
                "a reinserted ground layer must use its new depth beneath the tank");
        checks++;
    }

    private static int findPriority(LayerManager manager, int priority) {
        for (int i = manager.getSize() - 1; i >= 0; i--) {
            if (manager.getLayerAt(i) instanceof PriorityMarker
                    && ((PriorityMarker) manager.getLayerAt(i)).priority == priority)
                return i;
        }
        throw new AssertionError("layer-priority marker is missing");
    }

    private static final class PriorityMarker extends Sprite {
        final int priority;

        PriorityMarker(int priority, Image image) {
            super(image);
            this.priority = priority;
            setVisible(false);
        }
    }

    private static void requireColor(int actual, int expectedRgb, String message) {
        if ((actual & 0x00FFFFFF) != expectedRgb) {
            throw new AssertionError(message + ": expected 0x"
                    + Integer.toHexString(expectedRgb) + ", got 0x"
                    + Integer.toHexString(actual & 0x00FFFFFF));
        }
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
