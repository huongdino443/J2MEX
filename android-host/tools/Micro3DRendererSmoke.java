package com.mascotcapsule.micro3d.v3;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import javax.microedition.lcdui.Graphics;
import org.recompile.mobile.PlatformImage;

/** Confirms Micro3D geometry is rasterized and flushes through MIDP Graphics. */
public final class Micro3DRendererSmoke {
    public static void main(String[] args) {
        TestGraphics graphics = new TestGraphics(64, 64);
        Graphics3D renderer = new Graphics3D();
        FigureLayout layout = new FigureLayout();
        layout.setCenter(32, 32);
        layout.setParallelSize(64, 64);
        Effect3D effect = new Effect3D();

        int[] vertices = {
                -20, -20, 0,
                 20, -20, 0,
                  0,  20, 0
        };
        Micro3DModel.Triangle triangle = new Micro3DModel.Triangle(
                0, 1, 2, -1, 0, 0, 0xffff3344,
                0, 0, 0, 0, 0, 0);
        Figure figure = new Figure(new Micro3DModel(
                5, 0, 1, vertices, new Micro3DModel.Triangle[]{triangle}));

        renderer.bind(graphics);
        renderer.renderFigure(figure, 0, 0, layout, effect);
        check(graphics.countVisible() == 0, "renderFigure should wait for flush");
        renderer.flush();
        int visible = graphics.countVisible();
        check(visible > 500, "triangle did not cover enough pixels: " + visible);
        check(graphics.getPixel(32, 32) == 0xffff3344, "triangle center has wrong color");
        check(graphics.getPixel(2, 2) == 0, "renderer changed pixels outside geometry");
        renderer.release(graphics);
        verifyPrimitivePath(vertices, layout, effect);
        verifyPrimitiveQuadSplit(layout, effect);
        verifyPrimitiveQuadSplit(layout, effect, new int[]{
                -20, -20, 0,
                -20,  20, 0,
                 20,  20, 0,
                 20, -20, 0
        });
        verifySubtractivePrimitiveBlend(layout, effect);
        verifyPointAndLinePrimitives(layout, effect);
        verifyPointSpritePrimitive(layout, effect);
        verifyPerspectivePointSpriteSizingAndBlend();
        verifyPointSpriteBlendHonorsEffectTransparency();
        verifyAdditivePointSpritePreservesCanvasBackground();
        verifyPointSpriteTextureOrientationAndDarkTexels();
        verifyPointSpriteTextureCoordinatesWrapAsBytes();
        verifyProjectionYAxis();
        verifyFaceCulling();
        verifyTextureVOrientation();
        verifyPerspectiveConstantUDoesNotBleed();
        verifyNearAndFarClipping();
        verifyClipPlaneRounding();
        verifyParallelClipping();
        verifyFigureDepthClearsBetweenModels();
        verifyPrimitiveDepthIsSharedWithinFigure();
        verifyNormalLighting();
        verifyTransparentPass();
        verifyTextureAlphaMaterialSemantics();
        verifyFixedPointTransforms();
        verifyBindPose();
        System.out.println("Micro3DRendererSmoke: renderer checks passed ("
                + visible + " pixels)");
    }

    private static void verifyPrimitivePath(int[] vertices, FigureLayout layout, Effect3D effect) {
        TestGraphics graphics = new TestGraphics(64, 64);
        Graphics3D renderer = new Graphics3D();
        renderer.bind(graphics);
        renderer.renderPrimitives(null, 0, 0, layout, effect,
                Graphics3D.PRIMITVE_TRIANGLES | Graphics3D.PDATA_COLOR_PER_COMMAND,
                1, vertices, new int[0], new int[0], new int[]{0x3366cc});
        renderer.flush();
        check(graphics.getPixel(32, 32) == 0xff3366cc,
                "renderPrimitives did not draw its color");
        renderer.release(graphics);
    }

    private static void verifyPrimitiveQuadSplit(FigureLayout layout, Effect3D effect) {
        verifyPrimitiveQuadSplit(layout, effect, new int[]{
                -20, 20, 0,
                -20, -20, 0,
                 20, -20, 0,
                 20, 20, 0
        });
    }

    private static void verifyPrimitiveQuadSplit(FigureLayout layout, Effect3D effect,
                                                 int[] vertices) {
        TestGraphics graphics = new TestGraphics(64, 64);
        Graphics3D renderer = new Graphics3D();
        renderer.bind(graphics);
        renderer.renderPrimitives(null, 0, 0, layout, effect,
                Graphics3D.PRIMITVE_QUADS | Graphics3D.PDATA_COLOR_PER_FACE,
                1, vertices, new int[0], new int[0], new int[]{0x44aaee});
        renderer.flush();
        check(graphics.countVisible() > 1500,
                "colored quad lost a triangle because its split winding was culled: "
                        + graphics.countVisible());
        check(graphics.getPixel(20, 20) == 0xff44aaee
                        && graphics.getPixel(43, 43) == 0xff44aaee,
                "colored quad did not cover both halves");
        renderer.release(graphics);
    }

    private static void verifySubtractivePrimitiveBlend(FigureLayout layout,
                                                         Effect3D effect) {
        PlatformImage image = new PlatformImage(64, 64);
        drawSubtractiveQuad(image, layout, effect, 0xffa0b0c0);
        check(image.getCanvas().getRGB(32, 32) == 0xff506070,
                "subtractive primitive blend did not match destination-minus-source");

        PlatformImage translucentImage = new PlatformImage(64, 64);
        drawSubtractiveQuad(translucentImage, layout, effect, 0x80a0b0c0);
        check((translucentImage.getCanvas().getRGB(32, 32) >>> 24) == 0xc0,
                "subtractive primitive blend did not preserve destination alpha");
    }

    private static void drawSubtractiveQuad(PlatformImage image, FigureLayout layout,
                                            Effect3D effect, int backgroundColor) {
        int[] background = new int[64 * 64];
        java.util.Arrays.fill(background, backgroundColor);
        image.getCanvas().setRGB(0, 0, 64, 64, background, 0, 64);
        Graphics graphics = image.getGraphics();
        Graphics3D renderer = new Graphics3D();
        renderer.bind(graphics);
        renderer.renderPrimitives(null, 0, 0, layout, effect,
                Graphics3D.PRIMITVE_QUADS | Graphics3D.PDATA_COLOR_PER_FACE
                        | Graphics3D.PATTR_BLEND_SUB,
                1, new int[]{
                        -20, 20, 0,
                        -20, -20, 0,
                         20, -20, 0,
                         20, 20, 0
                }, new int[0], new int[0], new int[]{0x505050});
        renderer.flush();
        renderer.release(graphics);
    }

    private static void verifyPointAndLinePrimitives(FigureLayout layout, Effect3D effect) {
        TestGraphics points = new TestGraphics(64, 64);
        Graphics3D renderer = new Graphics3D();
        renderer.bind(points);
        renderer.renderPrimitives(null, 0, 0, layout, effect,
                Graphics3D.PRIMITVE_POINTS | Graphics3D.PDATA_COLOR_PER_COMMAND,
                1, new int[]{0, 0, 0}, new int[0], new int[0], new int[]{0x33cc88});
        renderer.flush();
        check(points.countVisible() > 0, "point primitive did not rasterize");
        renderer.release(points);

        TestGraphics lines = new TestGraphics(64, 64);
        renderer = new Graphics3D();
        renderer.bind(lines);
        renderer.renderPrimitives(null, 0, 0, layout, effect,
                Graphics3D.PRIMITVE_LINES | Graphics3D.PDATA_COLOR_PER_COMMAND,
                1, new int[]{-15, 0, 0, 15, 0, 0}, new int[0], new int[0],
                new int[]{0x33cc88});
        renderer.flush();
        check(lines.countVisible() > 20, "line primitive did not rasterize");
        renderer.release(lines);
    }

    private static void verifyPointSpritePrimitive(FigureLayout layout, Effect3D effect) {
        try {
            BufferedImage image = new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < 4; y++) {
                for (int x = 0; x < 4; x++) image.setRGB(x, y, 0x00101010);
            }
            for (int y = 1; y <= 2; y++) {
                for (int x = 1; x <= 2; x++) image.setRGB(x, y, 0xffff4422);
            }
            ByteArrayOutputStream png = new ByteArrayOutputStream();
            ImageIO.write(image, "png", png);
            Texture texture = new Texture(png.toByteArray(), true);
            TestGraphics graphics = new TestGraphics(64, 64);
            Graphics3D renderer = new Graphics3D();
            renderer.bind(graphics);
            FigureLayout scaledLayout = new FigureLayout();
            scaledLayout.setCenter(32, 32);
            scaledLayout.setParallelSize(64, 64);
            scaledLayout.setAffineTrans(new AffineTrans(
                    16384, 0, 0, 0, 0, 16384, 0, 0, 0, 0, 16384, 0));
            renderer.renderPrimitives(texture, 0, 0, scaledLayout, effect,
                    Graphics3D.PRIMITVE_POINT_SPRITES
                            | Graphics3D.PDATA_POINT_SPRITE_PARAMS_PER_CMD
                            | Graphics3D.PATTR_COLORKEY | Graphics3D.PATTR_BLEND_ADD,
                    1, new int[]{0, 0, 0}, new int[0],
                    new int[]{16, 16, 0, 0, 0, 4, 4,
                            Graphics3D.POINT_SPRITE_LOCAL_SIZE
                                    | Graphics3D.POINT_SPRITE_PERSPECTIVE},
                    new int[0]);
            renderer.flush();
            check(graphics.countVisible() > 20, "point sprite did not rasterize");
            check(graphics.countVisible() < 180,
                    "point sprite color-key did not discard its transparent background");
            check(graphics.getPixel(25, 25) == 0,
                    "point sprite black background was not transparent");
            check(graphics.getPixel(32, 32) != 0,
                    "point sprite foreground was incorrectly keyed out");
            renderer.release(graphics);
        } catch (Exception e) {
            throw new AssertionError("point sprite test failed", e);
        }
    }

    private static void verifyPerspectivePointSpriteSizingAndBlend() {
        try {
            BufferedImage image = new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < 4; y++) {
                for (int x = 0; x < 4; x++) image.setRGB(x, y, 0xff101010);
            }
            for (int y = 1; y <= 2; y++) {
                for (int x = 1; x <= 2; x++) image.setRGB(x, y, 0xff202020);
            }
            ByteArrayOutputStream png = new ByteArrayOutputStream();
            ImageIO.write(image, "png", png);
            Texture texture = new Texture(png.toByteArray(), true);

            TestGraphics graphics = new TestGraphics(64, 64);
            Graphics3D renderer = new Graphics3D();
            FigureLayout layout = new FigureLayout();
            layout.setCenter(32, 32);
            layout.setPerspective(1024, 12288, 1024);
            Effect3D effect = new Effect3D();
            int[] backgroundVertices = {
                    -4000, -4000, 7000,
                     4000, -4000, 7000,
                        0,  4000, 7000
            };
            Micro3DModel.Triangle backgroundTriangle = new Micro3DModel.Triangle(
                    0, 1, 2, -1, 0, 0, 0xff101010,
                    0, 0, 0, 0, 0, 0);
            renderer.bind(graphics);
            renderer.renderFigure(new Figure(new Micro3DModel(5, 0, 1,
                    backgroundVertices, new Micro3DModel.Triangle[]{backgroundTriangle})),
                    0, 0, layout, effect);
            renderer.renderPrimitives(texture, 0, 0, layout, effect,
                    Graphics3D.PRIMITVE_POINT_SPRITES
                            | Graphics3D.PDATA_POINT_SPRITE_PARAMS_PER_CMD
                            | Graphics3D.PATTR_COLORKEY | Graphics3D.PATTR_BLEND_ADD,
                    1, new int[]{0, 0, 5000}, new int[0],
                    new int[]{8, 8, 0, 0, 0, 4, 4,
                            Graphics3D.POINT_SPRITE_PIXEL_SIZE
                                    | Graphics3D.POINT_SPRITE_NO_PERS},
                    new int[0]);
            renderer.flush();
            int blendedPixels = 0;
            for (int y = 28; y <= 36; y++) {
                for (int x = 28; x <= 36; x++) {
                    if (graphics.getPixel(x, y) == 0xff303030) blendedPixels++;
                }
            }
            check(blendedPixels >= 8,
                    "perspective pixel-size sprite shrank or lost additive blending: "
                            + blendedPixels);
            check(graphics.getPixel(32, 32) == 0xff303030,
                    "point-sprite additive mode was not honored without Effect3D transparency");
            renderer.release(graphics);
        } catch (Exception e) {
            throw new AssertionError("perspective point-sprite test failed", e);
        }
    }

    private static void verifyPointSpriteBlendHonorsEffectTransparency() {
        try {
            check(new Effect3D().isTransparency(),
                    "Effect3D default does not match J2ME Loader transparency semantics");
            BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < 2; y++) {
                for (int x = 0; x < 2; x++) image.setRGB(x, y, 0xff202020);
            }
            ByteArrayOutputStream png = new ByteArrayOutputStream();
            ImageIO.write(image, "png", png);
            Texture texture = new Texture(png.toByteArray(), true);
            FigureLayout layout = new FigureLayout();
            layout.setCenter(32, 32);
            layout.setParallelSize(64, 64);
            int command = Graphics3D.PRIMITVE_POINT_SPRITES
                    | Graphics3D.PDATA_POINT_SPRITE_PARAMS_PER_CMD
                    | Graphics3D.PATTR_COLORKEY | Graphics3D.PATTR_BLEND_ADD;
            int[] params = {
                    8, 8, 0, 0, 0, 2, 2,
                    Graphics3D.POINT_SPRITE_LOCAL_SIZE
                            | Graphics3D.POINT_SPRITE_PERSPECTIVE
            };

            PlatformImage opaqueImage = new PlatformImage(64, 64);
            int[] opaqueBackground = new int[64 * 64];
            java.util.Arrays.fill(opaqueBackground, 0xff101010);
            opaqueImage.getCanvas().setRGB(0, 0, 64, 64, opaqueBackground, 0, 64);
            Graphics opaqueGraphics = opaqueImage.getGraphics();
            Graphics3D renderer = new Graphics3D();
            Effect3D opaqueEffect = new Effect3D();
            opaqueEffect.setTransparency(false);
            renderer.bind(opaqueGraphics);
            renderer.renderPrimitives(texture, 0, 0, layout, opaqueEffect, command,
                    1, new int[]{0, 0, 0}, new int[0], params, new int[0]);
            renderer.flush();
            check(opaqueImage.getCanvas().getRGB(32, 32) == 0xff202020,
                    "point-sprite blend flags ignored Effect3D transparency=false");
            renderer.release(opaqueGraphics);

            PlatformImage blendedImage = new PlatformImage(64, 64);
            blendedImage.getCanvas().setRGB(0, 0, 64, 64, opaqueBackground, 0, 64);
            Graphics blendedGraphics = blendedImage.getGraphics();
            renderer = new Graphics3D();
            renderer.bind(blendedGraphics);
            renderer.renderPrimitives(texture, 0, 0, layout, new Effect3D(), command,
                    1, new int[]{0, 0, 0}, new int[0], params, new int[0]);
            renderer.flush();
            check(blendedImage.getCanvas().getRGB(32, 32) == 0xff303030,
                    "point-sprite additive blending was lost when transparency is enabled");
            renderer.release(blendedGraphics);
        } catch (Exception e) {
            throw new AssertionError("point-sprite transparency semantics test failed", e);
        }
    }

    private static void verifyAdditivePointSpritePreservesCanvasBackground() {
        try {
            BufferedImage textureImage = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < 2; y++) {
                for (int x = 0; x < 2; x++) textureImage.setRGB(x, y, 0xff202020);
            }
            ByteArrayOutputStream png = new ByteArrayOutputStream();
            ImageIO.write(textureImage, "png", png);
            Texture texture = new Texture(png.toByteArray(), true);

            PlatformImage image = new PlatformImage(64, 64);
            BufferedImage canvas = image.getCanvas();
            for (int y = 0; y < 64; y++) {
                for (int x = 0; x < 64; x++) canvas.setRGB(x, y, 0xff101010);
            }
            Graphics graphics = image.getGraphics();
            Graphics3D renderer = new Graphics3D();
            FigureLayout layout = new FigureLayout();
            layout.setCenter(32, 32);
            layout.setParallelSize(64, 64);
            Effect3D effect = new Effect3D();
            renderer.bind(graphics);
            renderer.renderPrimitives(texture, 0, 0, layout, effect,
                    Graphics3D.PRIMITVE_POINT_SPRITES
                            | Graphics3D.PDATA_POINT_SPRITE_PARAMS_PER_CMD
                            | Graphics3D.PATTR_COLORKEY | Graphics3D.PATTR_BLEND_ADD,
                    1, new int[]{0, 0, 0}, new int[0],
                    new int[]{8, 8, 0, 0, 0, 2, 2,
                            Graphics3D.POINT_SPRITE_LOCAL_SIZE
                                    | Graphics3D.POINT_SPRITE_PERSPECTIVE},
                    new int[0]);
            renderer.flush();
            check(canvas.getRGB(32, 32) == 0xff303030,
                    "additive sprite did not blend against the existing MIDP canvas");
            check(canvas.getRGB(20, 20) == 0xff101010,
                    "additive sprite flush changed pixels outside the sprite");
            renderer.release(graphics);
        } catch (Exception e) {
            throw new AssertionError("canvas background blend test failed", e);
        }
    }

    private static void verifyPointSpriteTextureCoordinatesWrapAsBytes() {
        try {
            BufferedImage textureImage = new BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < 256; y++) {
                for (int x = 0; x < 256; x++) textureImage.setRGB(x, y, 0xffff0000);
            }
            for (int y = 64; y < 96; y++) {
                for (int x = 32; x < 64; x++) textureImage.setRGB(x, y, 0xff00ff00);
            }
            ByteArrayOutputStream png = new ByteArrayOutputStream();
            ImageIO.write(textureImage, "png", png);
            Texture texture = new Texture(png.toByteArray(), true);

            TestGraphics graphics = new TestGraphics(64, 64);
            Graphics3D renderer = new Graphics3D();
            FigureLayout layout = new FigureLayout();
            layout.setCenter(32, 32);
            layout.setParallelSize(64, 64);
            renderer.bind(graphics);
            renderer.renderPrimitives(texture, 0, 0, layout, new Effect3D(),
                    Graphics3D.PRIMITVE_POINT_SPRITES
                            | Graphics3D.PDATA_POINT_SPRITE_PARAMS_PER_CMD
                            | Graphics3D.PATTR_COLORKEY | Graphics3D.PATTR_BLEND_ADD,
                    1, new int[]{0, 0, 0}, new int[0],
                    new int[]{8, 8, 0, 32, 320, 64, 352,
                            Graphics3D.POINT_SPRITE_LOCAL_SIZE
                                    | Graphics3D.POINT_SPRITE_PERSPECTIVE},
                    new int[0]);
            renderer.flush();
            check(graphics.getPixel(32, 32) == 0xff00ff00,
                    "point-sprite UVs above 255 did not wrap as unsigned bytes");
            renderer.release(graphics);
        } catch (Exception e) {
            throw new AssertionError("point-sprite UV wrap test failed", e);
        }
    }

    private static void verifyPointSpriteTextureOrientationAndDarkTexels() {
        try {
            BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < 16; y++) {
                for (int x = 0; x < 16; x++) {
                    image.setRGB(x, y, y < 8 ? 0xff101010 : 0xff00ff00);
                }
            }
            ByteArrayOutputStream png = new ByteArrayOutputStream();
            ImageIO.write(image, "png", png);
            Texture texture = new Texture(png.toByteArray(), true);

            TestGraphics graphics = new TestGraphics(64, 64);
            Graphics3D renderer = new Graphics3D();
            FigureLayout layout = new FigureLayout();
            layout.setCenter(32, 32);
            layout.setParallelSize(64, 64);
            renderer.bind(graphics);
            renderer.renderPrimitives(texture, 0, 0, layout, new Effect3D(),
                    Graphics3D.PRIMITVE_POINT_SPRITES
                            | Graphics3D.PDATA_POINT_SPRITE_PARAMS_PER_CMD
                            | Graphics3D.PATTR_COLORKEY | Graphics3D.PATTR_BLEND_ADD,
                    1, new int[]{0, 0, 0}, new int[0],
                    new int[]{16, 16, 0, 0, 0, 16, 16,
                            Graphics3D.POINT_SPRITE_LOCAL_SIZE
                                    | Graphics3D.POINT_SPRITE_PERSPECTIVE},
                    new int[0]);
            renderer.flush();
            check(graphics.getPixel(32, 28) == 0xff00ff00,
                    "point-sprite top edge did not sample v1 like J2ME Loader");
            check(graphics.getPixel(32, 36) == 0xff101010,
                    "point-sprite color-key discarded a dark, nontransparent texel");
            renderer.release(graphics);
        } catch (Exception e) {
            throw new AssertionError("point-sprite orientation test failed", e);
        }
    }

    private static void verifyFixedPointTransforms() {
        AffineTrans left = new AffineTrans();
        left.setIdentity();
        left.m03 = 7;
        AffineTrans right = new AffineTrans();
        right.setIdentity();
        right.m13 = -3;
        left.mul(right);
        check(left.m00 == 4096 && left.m11 == 4096 && left.m22 == 4096,
                "fixed-point identity multiplication changed scale");
        check(left.m03 == 7 && left.m13 == -3,
                "fixed-point multiplication lost translation");

        AffineTrans rotation = new AffineTrans();
        rotation.rotationX(0);
        check(rotation.m00 == 4096 && rotation.m11 == 4096 && rotation.m22 == 4096,
                "zero-degree rotation is not the identity");

        rotation.rotationY(1024);
        Vector3D rotated = rotation.transform(new Vector3D(4096, 0, 0));
        check(Math.abs(rotated.x) <= 1 && rotated.z == -4096,
                "rotation or row-major point transform does not match Micro3D");

        AffineTrans view = new AffineTrans();
        view.lookAt(new Vector3D(0, 0, -4096),
                new Vector3D(0, 0, 4096), new Vector3D(0, 4096, 0));
        Vector3D cameraOrigin = view.transform(new Vector3D(0, 0, -4096));
        check(cameraOrigin.z == 0 && view.m23 == 4096,
                "lookAt did not include the camera translation");
    }

    private static void verifyProjectionYAxis() {
        TestGraphics graphics = new TestGraphics(64, 64);
        Graphics3D renderer = new Graphics3D();
        FigureLayout layout = new FigureLayout();
        layout.setCenter(32, 32);
        layout.setParallelSize(64, 64);
        int[] vertices = {
                 0,  12, 0,
               -12, -12, 0,
                12, -12, 0
        };
        Micro3DModel.Triangle triangle = new Micro3DModel.Triangle(
                0, 1, 2, -1, 0, 0, 0xff33cc88,
                0, 0, 0, 0, 0, 0);
        Figure figure = new Figure(new Micro3DModel(
                5, 0, 1, vertices, new Micro3DModel.Triangle[]{triangle}));
        renderer.bind(graphics);
        renderer.renderFigure(figure, 0, 0, layout, new Effect3D());
        renderer.flush();
        check(graphics.getPixel(40, 21) == 0xff33cc88,
                "positive Micro3D Y projection has the wrong vertical direction");
        check(graphics.getPixel(40, 43) == 0,
                "positive Micro3D Y was vertically inverted");
        renderer.release(graphics);

        graphics = new TestGraphics(64, 64);
        renderer = new Graphics3D();
        layout = new FigureLayout();
        layout.setCenter(32, 32);
        layout.setPerspective(1, 32767, 64, 64);
        int[] perspectiveVertices = {
                 0,  12, 4096,
               -12, -12, 4096,
                12, -12, 4096
        };
        Figure perspectiveFigure = new Figure(new Micro3DModel(
                5, 0, 1, perspectiveVertices, new Micro3DModel.Triangle[]{triangle}));
        renderer.bind(graphics);
        renderer.renderFigure(perspectiveFigure, 0, 0, layout, new Effect3D());
        renderer.flush();
        check(graphics.getPixel(40, 21) == 0xff33cc88,
                "perspective projection inverted positive Micro3D Y");
        check(graphics.getPixel(40, 43) == 0,
                "perspective projection inverted the vertical edge");
        renderer.release(graphics);
    }

    private static void verifyFaceCulling() {
        FigureLayout layout = new FigureLayout();
        layout.setCenter(32, 32);
        layout.setParallelSize(64, 64);
        Effect3D effect = new Effect3D();
        int[] frontVertices = {
                -20, -20, 0,
                 20, -20, 0,
                   0, 20, 0
        };
        Micro3DModel.Triangle oneSided = new Micro3DModel.Triangle(
                0, 1, 2, -1, 0, 32, 0xff44aaee,
                0, 0, 0, 0, 0, 0);
        TestGraphics graphics = new TestGraphics(64, 64);
        Graphics3D renderer = new Graphics3D();
        renderer.bind(graphics);
        renderer.renderFigure(new Figure(new Micro3DModel(
                5, 0, 1, frontVertices, new Micro3DModel.Triangle[]{oneSided})),
                0, 0, layout, effect);
        renderer.flush();
        check(graphics.getPixel(32, 32) == 0xff44aaee,
                "front-facing single-sided Micro3D geometry was culled");
        renderer.release(graphics);

        int[] backVertices = {
                -20, -20, 0,
                   0, 20, 0,
                 20, -20, 0
        };
        graphics = new TestGraphics(64, 64);
        renderer = new Graphics3D();
        renderer.bind(graphics);
        renderer.renderFigure(new Figure(new Micro3DModel(
                5, 0, 1, backVertices, new Micro3DModel.Triangle[]{oneSided})),
                0, 0, layout, effect);
        renderer.flush();
        check(graphics.getPixel(32, 32) == 0,
                "back-facing single-sided Micro3D geometry was not culled");
        renderer.release(graphics);

        Micro3DModel.Triangle doubleSided = new Micro3DModel.Triangle(
                0, 1, 2, -1, 0, 48, 0xff44aaee,
                0, 0, 0, 0, 0, 0);
        graphics = new TestGraphics(64, 64);
        renderer = new Graphics3D();
        renderer.bind(graphics);
        renderer.renderFigure(new Figure(new Micro3DModel(
                5, 0, 1, backVertices, new Micro3DModel.Triangle[]{doubleSided})),
                0, 0, layout, effect);
        renderer.flush();
        check(graphics.getPixel(32, 32) == 0xff44aaee,
                "MBAC double-sided geometry was incorrectly culled");
        renderer.release(graphics);
    }

    private static void verifyTextureVOrientation() {
        BufferedImage image = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, 0xffff0000);
        image.setRGB(0, 127, 0xff0000ff);
        check(Micro3DSoftwareRenderer.sample(image, 0, 0) == 0xffff0000,
                "Micro3D V=0 did not sample the texture's top row");
        check(Micro3DSoftwareRenderer.sample(image, 0, 127) == 0xff0000ff,
                "Micro3D V=127 did not sample the texture's bottom row");

        BufferedImage large = new BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB);
        large.setRGB(127, 127, 0xff55aa33);
        large.setRGB(254, 254, 0xff3366cc);
        check(Micro3DSoftwareRenderer.sample(large, 127, 127) == 0xff55aa33,
                "Micro3D 8-bit UV was incorrectly scaled to the texture size");
        check(Micro3DSoftwareRenderer.sample(large, 254, 254) == 0xff3366cc,
                "Micro3D 8-bit UV did not reach the bitmap's final texels");
    }

    private static void verifyPerspectiveConstantUDoesNotBleed() {
        try {
            BufferedImage image = new BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB);
            for (int y = 219; y <= 238; y++) {
                image.setRGB(249, y, 0xfffa200f);
                image.setRGB(250, y, 0xff0099cc);
                image.setRGB(251, y, 0xff0099cc);
                image.setRGB(252, y, 0xfffa200f);
            }
            ByteArrayOutputStream png = new ByteArrayOutputStream();
            ImageIO.write(image, "png", png);

            Micro3DModel.Triangle triangle = new Micro3DModel.Triangle(
                    0, 1, 2, 0, 0, 48, 0xffffffff,
                    250, 219, 250, 238, 250, 219);
            Figure figure = new Figure(new Micro3DModel(5, 0, 1,
                    new int[]{
                            -1000, -1000, 4000,
                             1000, -1000, 5000,
                                0,  1000, 6000
                    }, new Micro3DModel.Triangle[]{triangle}));
            figure.setTexture(new Texture(png.toByteArray(), true));

            TestGraphics graphics = new TestGraphics(64, 64);
            Graphics3D renderer = new Graphics3D();
            FigureLayout layout = new FigureLayout();
            layout.setCenter(32, 32);
            layout.setPerspective(1024, 12288, 1024);
            renderer.bind(graphics);
            renderer.renderFigure(figure, 0, 0, layout, new Effect3D());
            renderer.flush();

            int bluePixels = 0;
            int redPixels = 0;
            for (int y = 0; y < 64; y++) {
                for (int x = 0; x < 64; x++) {
                    int color = graphics.getPixel(x, y);
                    if (color == 0xff0099cc) bluePixels++;
                    if (color == 0xfffa200f) redPixels++;
                }
            }
            check(bluePixels > 0, "constant-U perspective figure did not sample its blue texel");
            check(redPixels == 0,
                    "constant U=250 drifted into the adjacent red texel: " + redPixels);
            renderer.release(graphics);
        } catch (Exception e) {
            throw new AssertionError("constant-U perspective texture test failed", e);
        }
    }

    private static void verifyNearAndFarClipping() {
        verifyClippedTriangle(new int[]{
                -2, 0, 50,
                 2, -2, 200,
                 2,  2, 200
        }, "near");
        verifyClippedTriangle(new int[]{
                -2, 0, 1200,
                 2, -2, 800,
                 2,  2, 800
        }, "far");
    }

    private static void verifyClipPlaneRounding() {
        verifyPerspectiveClippingCase(new int[]{
                0, 3, 0,
                -3, -3, 101,
                3, -3, 202
        }, 100, 1000, "near-plane intersection rounding");
        verifyPerspectiveClippingCase(new int[]{
                0, 3, 1163,
                -3, -3, 101,
                3, -3, 130
        }, 100, 1000, "far-plane intersection rounding");
        verifyPerspectiveClippingCase(new int[]{
                -20, -20, 17515,
                20, -20, 17515,
                0, 20, 17515
        }, 17514, 17515, "far-plane depth precision");
    }

    private static void verifyPerspectiveClippingCase(int[] vertices, int near, int far,
                                                       String description) {
        TestGraphics graphics = new TestGraphics(64, 64);
        Graphics3D renderer = new Graphics3D();
        FigureLayout layout = new FigureLayout();
        layout.setCenter(32, 32);
        layout.setPerspective(near, far, 26214, 26214);
        Micro3DModel.Triangle triangle = new Micro3DModel.Triangle(
                0, 1, 2, -1, 0, 16, 0xff33cc88,
                0, 0, 0, 0, 0, 0);
        renderer.bind(graphics);
        renderer.renderFigure(new Figure(new Micro3DModel(
                5, 0, 1, vertices, new Micro3DModel.Triangle[]{triangle})),
                0, 0, layout, new Effect3D());
        renderer.flush();
        check(graphics.countVisible() > 0,
                description + " discarded visible geometry");
        renderer.release(graphics);
    }

    private static void verifyClippedTriangle(int[] vertices, String plane) {
        TestGraphics graphics = new TestGraphics(64, 64);
        Graphics3D renderer = new Graphics3D();
        FigureLayout layout = new FigureLayout();
        layout.setCenter(32, 32);
        layout.setPerspective(100, 1000, 16384, 16384);
        Micro3DModel.Triangle triangle = new Micro3DModel.Triangle(
                0, 1, 2, -1, 0, 0, 0xff33cc88,
                0, 0, 0, 0, 0, 0);
        Figure figure = new Figure(new Micro3DModel(
                5, 0, 1, vertices, new Micro3DModel.Triangle[]{triangle}));
        renderer.bind(graphics);
        renderer.renderFigure(figure, 0, 0, layout, new Effect3D());
        renderer.flush();
        check(graphics.countVisible() > 0,
                "triangle crossing the " + plane + " plane was dropped instead of clipped");
        renderer.release(graphics);
    }

    private static void verifyParallelClipping() {
        verifyParallelClipTriangle(new int[]{
                -12, 0, -70000,
                 12, -12, 0,
                 12, 12, 0
        }, "near");
        verifyParallelClipTriangle(new int[]{
                -12, 0, 70000,
                 12, -12, 0,
                 12, 12, 0
        }, "far");
    }

    private static void verifyParallelClipTriangle(int[] vertices, String plane) {
        TestGraphics graphics = new TestGraphics(64, 64);
        Graphics3D renderer = new Graphics3D();
        FigureLayout layout = new FigureLayout();
        layout.setCenter(32, 32);
        layout.setParallelSize(64, 64);
        Micro3DModel.Triangle triangle = new Micro3DModel.Triangle(
                0, 1, 2, -1, 0, 0, 0xff33cc88,
                0, 0, 0, 0, 0, 0);
        renderer.bind(graphics);
        renderer.renderFigure(new Figure(new Micro3DModel(
                5, 0, 1, vertices, new Micro3DModel.Triangle[]{triangle})),
                0, 0, layout, new Effect3D());
        renderer.flush();
        check(graphics.countVisible() > 0,
                "parallel triangle crossing the " + plane + " plane was dropped");
        renderer.release(graphics);
    }

    private static void verifyTextureAlphaMaterialSemantics() {
        check(Micro3DSoftwareRenderer.applyTextureAlpha(0x00112233, 32) == 0xff112233,
                "non-color-key material did not preserve transparent texel RGB");
        check(Micro3DSoftwareRenderer.applyTextureAlpha(0x00112233, 33) == 0,
                "color-key material did not discard a transparent texel");
        check(Micro3DSoftwareRenderer.applyTextureAlpha(0x80112233, 33) == 0xff112233,
                "color-key material discarded a texel above the alpha threshold");
        check(Micro3DSoftwareRenderer.applyTextureAlpha(0xff000000, 69) == 0xff000000,
                "point-sprite color-key changed an opaque black texel");
        check(Micro3DSoftwareRenderer.applyTextureAlpha(0xff101010, 69) == 0xff101010,
                "point-sprite color-key changed an opaque dark texel");
        check(Micro3DSoftwareRenderer.applyTextureAlpha(0xff101010, 5) == 0xff101010,
                "point-sprite color-key handling changed a regular textured model");
        check(Micro3DSoftwareRenderer.applyTextureAlpha(0xff191919, 69) == 0xff191919,
                "point-sprite color-key handling changed an opaque dark shade");
    }

    private static void verifyFigureDepthClearsBetweenModels() {
        TestGraphics graphics = new TestGraphics(64, 64);
        Graphics3D renderer = new Graphics3D();
        FigureLayout layout = new FigureLayout();
        layout.setCenter(32, 32);
        layout.setParallelSize(64, 64);
        Effect3D effect = new Effect3D();
        int[] nearVertices = {
                -20, -20, 1000,
                 20, -20, 1000,
                   0,  20, 1000
        };
        int[] farVertices = {
                -20, -20, 2000,
                 20, -20, 2000,
                   0,  20, 2000
        };
        Micro3DModel.Triangle red = new Micro3DModel.Triangle(
                0, 1, 2, -1, 0, 0, 0xffff3344,
                0, 0, 0, 0, 0, 0);
        Micro3DModel.Triangle blue = new Micro3DModel.Triangle(
                0, 1, 2, -1, 0, 0, 0xff3366cc,
                0, 0, 0, 0, 0, 0);

        renderer.bind(graphics);
        renderer.drawFigure(new Figure(new Micro3DModel(
                5, 0, 1, nearVertices, new Micro3DModel.Triangle[]{red})),
                0, 0, layout, effect);
        renderer.drawFigure(new Figure(new Micro3DModel(
                5, 0, 1, farVertices, new Micro3DModel.Triangle[]{blue})),
                0, 0, layout, effect);
        check(graphics.getPixel(32, 32) == 0xff3366cc,
                "a completed drawFigure batch's depth hid the next Figure");
        renderer.release(graphics);
    }

    private static void verifyPrimitiveDepthIsSharedWithinFigure() {
        TestGraphics graphics = new TestGraphics(64, 64);
        Graphics3D renderer = new Graphics3D();
        FigureLayout layout = new FigureLayout();
        layout.setCenter(32, 32);
        layout.setParallelSize(64, 64);
        int primitive = Graphics3D.PRIMITVE_TRIANGLES
                | Graphics3D.PDATA_COLOR_PER_COMMAND;
        int[] triangleAtDepth = {
                -20, -20, 1000,
                 20, -20, 1000,
                   0,  20, 1000
        };
        int[] farTriangle = {
                -20, -20, 2000,
                 20, -20, 2000,
                   0,  20, 2000
        };
        renderer.bind(graphics);
        renderer.renderPrimitives(null, 0, 0, layout, new Effect3D(), primitive,
                1, triangleAtDepth, new int[0], new int[0], new int[]{0xff0000});
        renderer.renderPrimitives(null, 0, 0, layout, new Effect3D(), primitive,
                1, farTriangle, new int[0], new int[0], new int[]{0x0000ff});
        Figure farModel = new Figure(new Micro3DModel(
                5, 0, 1, farTriangle,
                new Micro3DModel.Triangle[]{new Micro3DModel.Triangle(
                        0, 1, 2, -1, 0, 0, 0xff00ff00,
                        0, 0, 0, 0, 0, 0)}));
        renderer.renderFigure(farModel, 0, 0, layout, new Effect3D());
        renderer.flush();
        check(graphics.getPixel(32, 32) == 0xffff0000,
                "a later queued primitive/model overwrote nearer geometry before batch flush");
        renderer.release(graphics);
    }

    private static void verifyNormalLighting() {
        TestGraphics graphics = new TestGraphics(64, 64);
        Graphics3D renderer = new Graphics3D();
        FigureLayout layout = new FigureLayout();
        layout.setCenter(32, 32);
        layout.setParallelSize(64, 64);
        int[] vertices = {
                -20, -20, 0,
                 20, -20, 0,
                   0, 20, 0
        };
        Micro3DModel.Triangle triangle = new Micro3DModel.Triangle(
                0, 1, 2, -1, 0, 32, 0xffffffff,
                0, 0, 0, 0, 0, 0);
        Micro3DModel litModel = new Micro3DModel(5, 0, 1, vertices,
                new Micro3DModel.Triangle[]{triangle}, new Micro3DModel.Bone[0],
                new int[]{0, 0, -4096, 0, 0, -4096, 0, 0, -4096});
        Effect3D effect = new Effect3D();
        Light light = new Light(new Vector3D(0, 0, 4096), 0, 0);
        light.setParallelLightIntensity(4096);
        effect.setLight(light);
        renderer.bind(graphics);
        renderer.renderFigure(new Figure(litModel), 0, 0, layout, effect);
        renderer.flush();
        check(graphics.getPixel(32, 32) == 0xffffffff,
                "Micro3D normal lighting did not illuminate a face toward the light");
        renderer.release(graphics);

        graphics = new TestGraphics(64, 64);
        renderer = new Graphics3D();
        Micro3DModel shadowedModel = new Micro3DModel(5, 0, 1, vertices,
                new Micro3DModel.Triangle[]{triangle}, new Micro3DModel.Bone[0],
                new int[]{0, 0, 4096, 0, 0, 4096, 0, 0, 4096});
        renderer.bind(graphics);
        renderer.renderFigure(new Figure(shadowedModel), 0, 0, layout, effect);
        renderer.flush();
        check(graphics.getPixel(32, 32) == 0xff000000,
                "Micro3D normal lighting incorrectly illuminated the face facing away");
        renderer.release(graphics);
    }

    private static void verifyTransparentPass() {
        TestGraphics graphics = new TestGraphics(64, 64);
        Graphics3D renderer = new Graphics3D();
        FigureLayout layout = new FigureLayout();
        layout.setCenter(32, 32);
        layout.setParallelSize(64, 64);
        int[] vertices = {
                -20, -20, -10,
                 20, -20, -10,
                   0, 20, -10
        };
        Micro3DModel.Triangle transparent = new Micro3DModel.Triangle(
                0, 1, 2, -1, 0, 2, 0xffff0000,
                0, 0, 0, 0, 0, 0);
        Figure transparentFigure = new Figure(new Micro3DModel(
                5, 0, 1, vertices, new Micro3DModel.Triangle[]{transparent}));
        int[] backgroundVertices = {
                -20, -20, 10,
                 20, -20, 10,
                   0, 20, 10
        };
        Micro3DModel.Triangle opaque = new Micro3DModel.Triangle(
                0, 1, 2, -1, 0, 0, 0xff0000ff,
                0, 0, 0, 0, 0, 0);
        Figure background = new Figure(new Micro3DModel(
                5, 0, 1, backgroundVertices, new Micro3DModel.Triangle[]{opaque}));
        Effect3D effect = new Effect3D();
        effect.setTransparency(true);
        renderer.bind(graphics);
        renderer.renderFigure(background, 0, 0, layout, effect);
        renderer.renderFigure(transparentFigure, 0, 0, layout, effect);
        renderer.flush();
        check(graphics.getPixel(32, 32) == 0xff7f007f,
                "transparent Micro3D geometry did not blend over the opaque depth pass");
        renderer.release(graphics);
    }

    private static void verifyBindPose() {
        int[] identityTranslated = {
                4096, 0, 0, 10,
                0, 4096, 0, 20,
                0, 0, 4096, 30
        };
        Micro3DModel model = new Micro3DModel(5, 0, 1,
                new int[]{1, 2, 3}, new Micro3DModel.Triangle[0],
                new Micro3DModel.Bone[]{new Micro3DModel.Bone(1, -1, identityTranslated)});
        check(model.vertices[0] == 11 && model.vertices[1] == 22 && model.vertices[2] == 33,
                "MBAC bind transform was not applied to its vertices");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static final class TestGraphics extends Graphics {
        private final int width;
        private final int height;
        private final int[] pixels;

        TestGraphics(int width, int height) {
            this.width = width;
            this.height = height;
            this.pixels = new int[width * height];
            clipWidth = width;
            clipHeight = height;
        }

        @Override
        public void drawRGB(int[] source, int offset, int scanlength,
                            int x, int y, int drawWidth, int drawHeight,
                            boolean processAlpha) {
            for (int row = 0; row < drawHeight; row++) {
                for (int col = 0; col < drawWidth; col++) {
                    int tx = x + col;
                    int ty = y + row;
                    if (tx < 0 || ty < 0 || tx >= width || ty >= height) {
                        continue;
                    }
                    pixels[ty * width + tx] = source[offset + row * scanlength + col];
                }
            }
        }

        int countVisible() {
            int count = 0;
            for (int pixel : pixels) {
                if ((pixel >>> 24) != 0) {
                    count++;
                }
            }
            return count;
        }

        int getPixel(int x, int y) {
            return pixels[y * width + x];
        }
    }
}
