import java.io.*;
import java.lang.reflect.*;
import java.net.URL;
import java.nio.file.Files;
import java.util.Arrays;
import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Image;
import javax.microedition.m3g.*;
import org.recompile.mobile.*;

/** Regression for 2D backgrounds composed with moving 3D objects across bindings. */
public final class M3GCompositingSmoke {
    private static int checks;
    private static void check(boolean value,String message) {
        if(!value) throw new AssertionError(message); checks++;
    }
    public static void main(String[] args) throws Exception {
        Graphics3D renderer=new Graphics3D();
        Camera camera=new Camera(); camera.setPerspective(60,1,0.1f,100);
        Appearance appearance=new Appearance();
        PolygonMode polygon=new PolygonMode(); polygon.setCulling(PolygonMode.CULL_NONE);
        appearance.setPolygonMode(polygon);
        Background background=new Background();
        background.setColorClearEnable(false); background.setDepthClearEnable(true);
        TriangleStripArray indices=new TriangleStripArray(new int[]{0,1,2},new int[]{3});
        Image target=Image.createImage(64,64);
        for(int frame=0;frame<20;frame++) {
            int base=(frame%2==0)?0x204080:0x80a020;
            Graphics graphics=target.getGraphics(); graphics.setColor(base); graphics.fillRect(0,0,64,64);
            renderer.bindTarget(graphics); renderer.clear(background); renderer.setCamera(camera,new Transform());
            Transform position=new Transform(); position.postTranslate(frame%2==0?-0.8f:0.8f,0,0);
            renderer.render(triangle(0xffff0000),indices,appearance,position); renderer.releaseTarget();
            int[] pixels=read(target);
            int oldX=frame%2==0?47:17,newX=frame%2==0?17:47;
            if((pixels[32*64+oldX]&0xffffff)!=base||(pixels[32*64+newX]&0xffffff)!=0xff0000) {
                int minX=64,maxX=-1,minY=64,maxY=-1;
                for(int y=0;y<64;y++) for(int x=0;x<64;x++) if((pixels[y*64+x]&0xffffff)==0xff0000) {
                    minX=Math.min(minX,x);maxX=Math.max(maxX,x);minY=Math.min(minY,y);maxY=Math.max(maxY,y);
                }
                System.out.println("FRAME_DIAG "+frame+" old="+Integer.toHexString(pixels[32*64+oldX])+" new="+Integer.toHexString(pixels[32*64+newX])+" base="+Integer.toHexString(base)+" redBounds="+minX+","+minY+"-"+maxX+","+maxY);
                java.awt.image.BufferedImage diagnostic=new java.awt.image.BufferedImage(64,64,java.awt.image.BufferedImage.TYPE_INT_ARGB);
                diagnostic.setRGB(0,0,64,64,pixels,0,64); javax.imageio.ImageIO.write(diagnostic,"png",new File("/tmp/m3g-frame-"+frame+".png"));
            }
            check((pixels[32*64+oldX]&0xffffff)==base,"A previous-frame object or background survived binding "+frame);
            check((pixels[32*64+newX]&0xffffff)==0xff0000,"Moving mesh missing in frame "+frame);
        }
        check(renderer.isDepthBufferEnabled(),"bindTarget(target) did not enable depth");
        // A different target and clip must not inherit the old viewport or pixels.
        Image second=Image.createImage(80,80); Graphics g=second.getGraphics();
        g.setColor(0x112233); g.fillRect(0,0,80,80); g.translate(7,9); g.setClip(3,4,30,25);
        renderer.bindTarget(g);
        check(renderer.getViewportX()==3&&renderer.getViewportY()==4
            &&renderer.getViewportWidth()==30&&renderer.getViewportHeight()==25,"Viewport was not reset for a new target");
        renderer.clear(background); renderer.releaseTarget();
        boolean untouched=true; for(int p:read(second)) untouched&=(p&0xffffff)==0x112233;
        check(untouched,"Translated/clipped target was damaged without drawing");
        // Color-clear alpha is ignored for a MIDP RGB Graphics target.
        renderer.bindTarget(g); Background black=new Background(); black.setColor(0);
        renderer.clear(black); renderer.releaseTarget();
        int[] clipped=read(second);
        check((clipped[13*80+10]&0xffffff)==0,"Zero-alpha color clear did not clear RGB");
        boolean outsideClipIntact=true;
        for(int y=0;y<80;y++) for(int x=0;x<80;x++)
            if(x<10||x>=40||y<13||y>=38) outsideClipIntact&=(clipped[y*80+x]&0xffffff)==0x112233;
        check(outsideClipIntact,"Color clear escaped the target clip");
        // Alpha must be blended once, not once into a stale buffer and again on release.
        Image blend=Image.createImage(64,64); g=blend.getGraphics(); g.setColor(0x0000ff); g.fillRect(0,0,64,64);
        CompositingMode mode=new CompositingMode(); mode.setBlending(CompositingMode.ALPHA);
        appearance.setCompositingMode(mode);
        renderer.bindTarget(g); renderer.clear(background); renderer.setCamera(camera,new Transform());
        renderer.render(triangle(0x80ff0000),indices,appearance,null); renderer.releaseTarget();
        int center=read(blend)[32*64+32];
        check(Math.abs(((center>>>16)&255)-128)<=1&&Math.abs((center&255)-127)<=1,"Alpha blended against the wrong background or twice");

        // J2ME Loader uses an inclusive alpha test and relaxes a threshold of
        // 1.0 slightly for GLES precision. NFS sets this threshold on ALPHA
        // appearances; opaque texels must not disappear at the boundary.
        for(int sourceAlpha:new int[]{255,254}) {
            Image thresholdTarget=Image.createImage(64,64); g=thresholdTarget.getGraphics();
            g.setColor(0); g.fillRect(0,0,64,64);
            Image2D thresholdTexel=new Image2D(Image2D.RGBA,1,1,
                    new byte[]{(byte)255,0,0,(byte)sourceAlpha});
            Texture2D thresholdTexture=new Texture2D(thresholdTexel);
            thresholdTexture.setBlending(Texture2D.FUNC_REPLACE);
            Appearance thresholdAppearance=new Appearance();
            thresholdAppearance.setPolygonMode(polygon);
            thresholdAppearance.setTexture(0,thresholdTexture);
            CompositingMode thresholdMode=new CompositingMode();
            thresholdMode.setBlending(CompositingMode.ALPHA);
            thresholdMode.setAlphaThreshold(1f);
            thresholdAppearance.setCompositingMode(thresholdMode);
            renderer.bindTarget(g); renderer.clear(background); renderer.setCamera(camera,new Transform());
            renderer.render(triangleAt(0xffffffff,-20),indices,thresholdAppearance,null);
            renderer.releaseTarget();
            int thresholdPixel=read(thresholdTarget)[32*64+32];
            check(((thresholdPixel>>>16)&255)>=sourceAlpha-1,
                    String.format("Alpha %d at threshold 1.0 was discarded: #%08x",
                            sourceAlpha,thresholdPixel));
        }

        // With a zero alpha threshold, fully transparent texels must not
        // update depth and hide geometry drawn behind the textured polygon.
        Image transparentDepth=Image.createImage(64,64); g=transparentDepth.getGraphics();
        g.setColor(0); g.fillRect(0,0,64,64);
        Image2D transparentTexel=new Image2D(Image2D.RGBA,1,1,new byte[]{(byte)255,0,0,0});
        Texture2D transparentTexture=new Texture2D(transparentTexel);
        transparentTexture.setBlending(Texture2D.FUNC_REPLACE);
        Appearance transparentAppearance=new Appearance();
        transparentAppearance.setPolygonMode(polygon);
        transparentAppearance.setTexture(0,transparentTexture);
        Appearance greenAppearance=new Appearance();
        greenAppearance.setPolygonMode(polygon);
        renderer.bindTarget(g); renderer.clear(background); renderer.setCamera(camera,new Transform());
        renderer.render(triangleAt(0xffffffff,-5),indices,transparentAppearance,null);
        renderer.render(triangleAt(0xff00ff00,-30),indices,greenAppearance,null);
        renderer.releaseTarget();
        int throughTransparent=read(transparentDepth)[32*64+32];
        check((throughTransparent&0xffffff)==0x00ff00,
                String.format("A fully transparent texel occluded farther geometry: #%08x",throughTransparent));

        // A binary alpha mask is a cutout, not translucent blending. Opaque
        // cable texels must write depth so a later, farther mesh cannot erase
        // them, while fully transparent texels remain discarded.
        Image cutoutDepth=Image.createImage(64,64); g=cutoutDepth.getGraphics();
        g.setColor(0); g.fillRect(0,0,64,64);
        Image2D cutoutImage=new Image2D(Image2D.RGBA,2,1,
                new byte[]{(byte)255,0,0,(byte)255, 0,(byte)255,0,0});
        Texture2D cutoutTexture=new Texture2D(cutoutImage);
        cutoutTexture.setBlending(Texture2D.FUNC_REPLACE);
        cutoutTexture.setFiltering(Texture2D.FILTER_BASE_LEVEL,Texture2D.FILTER_NEAREST);
        cutoutTexture.setWrapping(Texture2D.WRAP_CLAMP,Texture2D.WRAP_CLAMP);
        VertexBuffer cutoutVertices=triangleAt(0xffffffff,-20);
        VertexArray cutoutCoords=new VertexArray(3,2,VertexArray.BYTE);
        cutoutCoords.set(0,3,new byte[]{1,2,1,2,1,2});
        cutoutVertices.setTexCoords(0,cutoutCoords,0.25f,null);
        Appearance cutoutAppearance=new Appearance();
        cutoutAppearance.setPolygonMode(polygon);
        CompositingMode cutoutBlend=new CompositingMode();
        cutoutBlend.setBlending(CompositingMode.ALPHA);
        cutoutAppearance.setCompositingMode(cutoutBlend);
        cutoutAppearance.setTexture(0,cutoutTexture);
        check(requiresDeferredAlphaPass(cutoutAppearance,cutoutVertices),
                "Binary-alpha cutout did not retain its alpha-pass ordering");
        Mesh cutoutMesh=new Mesh(cutoutVertices,indices,cutoutAppearance);
        Mesh fartherMesh=new Mesh(triangleAt(0xff00ff00,-30),indices,greenAppearance);
        renderer.bindTarget(g); renderer.clear(background); renderer.setCamera(camera,new Transform());
        renderer.render(cutoutMesh,new Transform());
        renderer.render(fartherMesh,new Transform());
        renderer.releaseTarget();
        int cutoutCenter=read(cutoutDepth)[32*64+32];
        check((cutoutCenter&0xffffff)==0xff0000,
                String.format("Binary-alpha opaque texel lost its depth: #%08x",cutoutCenter));

        // Battle Boats uses ALPHA for its water surface and ALPHA_ADD for
        // the red boundary/effect layer. Water retains its configured depth
        // writes; additive effects render after regular scene geometry and
        // do not write depth.
        int waterDepth=renderDepthOrder(renderer,camera,indices,polygon,
                CompositingMode.ALPHA,0x80ff0000);
        check(Math.abs(((waterDepth>>>16)&255)-128)<=1
                        &&((waterDepth>>>8)&255)<=1&&(waterDepth&255)<=1,
                String.format("Partial-alpha water did not retain depth: #%08x",waterDepth));
        int additiveDepth=renderDepthOrder(renderer,camera,indices,polygon,
                CompositingMode.ALPHA_ADD,0xffff0000);
        check((additiveDepth&0xffffff)==0x00ff00,
                String.format("Additive effect wrote depth and hid later geometry: #%08x",additiveDepth));

        // Direct M3G draws with partial alpha and depth writes disabled must
        // render after the opaque queue, while still respecting nearer depth.
        Image directWater=Image.createImage(64,64); g=directWater.getGraphics();
        g.setColor(0x0000ff); g.fillRect(0,0,64,64);
        Appearance directWaterAppearance=new Appearance(); directWaterAppearance.setPolygonMode(polygon);
        CompositingMode directWaterMode=new CompositingMode();
        directWaterMode.setBlending(CompositingMode.ALPHA);
        directWaterMode.setDepthWriteEnable(false);
        directWaterAppearance.setCompositingMode(directWaterMode);
        Material directWaterMaterial=new Material();
        directWaterMaterial.setColor(Material.DIFFUSE,0x80ff0000);
        directWaterAppearance.setMaterial(directWaterMaterial);
        Appearance opaqueGreen=new Appearance(); opaqueGreen.setPolygonMode(polygon);
        CompositingMode directReplace=new CompositingMode(); directReplace.setBlending(CompositingMode.REPLACE);
        opaqueGreen.setCompositingMode(directReplace);
        Material greenMaterial=new Material(); greenMaterial.setColor(Material.DIFFUSE,0xff00ff00);
        opaqueGreen.setMaterial(greenMaterial);
        renderer.bindTarget(g); renderer.clear(background); renderer.setCamera(camera,new Transform());
        renderer.render(triangleAt(0xffffffff,-20),indices,directWaterAppearance,null);
        renderer.render(triangleAt(0xffffffff,-30),indices,opaqueGreen,null);
        renderer.releaseTarget();
        int directWaterCenter=read(directWater)[32*64+32];
        check(Math.abs(((directWaterCenter>>>16)&255)-128)<=1
                        &&Math.abs(((directWaterCenter>>>8)&255)-127)<=1
                        &&(directWaterCenter&255)<=1,
                String.format("Direct alpha water failed to blend over later opaque geometry: #%08x",
                        directWaterCenter));

        Image directWaterOcclusion=Image.createImage(64,64); g=directWaterOcclusion.getGraphics();
        g.setColor(0x0000ff); g.fillRect(0,0,64,64);
        Material yellowMaterial=new Material(); yellowMaterial.setColor(Material.DIFFUSE,0xffffff00);
        Appearance opaqueYellow=new Appearance(); opaqueYellow.setPolygonMode(polygon);
        opaqueYellow.setCompositingMode(directReplace); opaqueYellow.setMaterial(yellowMaterial);
        renderer.bindTarget(g); renderer.clear(background); renderer.setCamera(camera,new Transform());
        renderer.render(triangleAt(0xffffffff,-20),indices,directWaterAppearance,null);
        renderer.render(triangleAt(0xffffffff,-10),indices,opaqueYellow,null);
        renderer.releaseTarget();
        check((read(directWaterOcclusion)[32*64+32]&0xffffff)==0xffff00,
                "Deferred direct water covered nearer opaque geometry");

        int fogFirst=renderFogBoat(renderer,camera,indices,polygon,true);
        int boatFirst=renderFogBoat(renderer,camera,indices,polygon,false);
        check((fogFirst&0xffffff)==0xffff00,
                String.format("Additive effect did not overlay later geometry: #%08x",fogFirst));
        check((boatFirst&0xffffff)==0xffff00,
                String.format("Additive effect result depended on scene insertion order: #%08x",boatFirst));
        int waterFirst=renderWaterReflection(renderer,camera,indices,polygon,true);
        int reflectionFirst=renderWaterReflection(renderer,camera,indices,polygon,false);
        check(Math.abs(((waterFirst>>>16)&255)-128)<=1
                        &&Math.abs((waterFirst&255)-127)<=1,
                String.format("Translucent water did not reveal the reflection: #%08x",waterFirst));
        check(Math.abs(((reflectionFirst>>>16)&255)-128)<=1
                        &&Math.abs((reflectionFirst&255)-127)<=1,
                String.format("Water reflection depended on scene insertion order: #%08x",reflectionFirst));
        // Some binary models serialize transparent defaultColor because their
        // material supplies the visible color. If the game removes that
        // material, an unlit texture still needs an opaque modulation color.
        Image unlit=Image.createImage(64,64); g=unlit.getGraphics(); g.setColor(0); g.fillRect(0,0,64,64);
        Image2D whiteImage=new Image2D(Image2D.RGB,1,1,new byte[]{(byte)255,(byte)255,(byte)255});
        Texture2D whiteTexture=new Texture2D(whiteImage); whiteTexture.setBlending(Texture2D.FUNC_MODULATE);
        PolygonMode unlitPolygon=new PolygonMode(); unlitPolygon.setCulling(PolygonMode.CULL_NONE);
        Appearance unlitAppearance=new Appearance(); unlitAppearance.setPolygonMode(unlitPolygon);
        unlitAppearance.setTexture(0,whiteTexture);
        renderer.bindTarget(g); renderer.clear(background); renderer.setCamera(camera,new Transform());
        renderer.render(triangle(0x00ffffff),indices,unlitAppearance,null); renderer.releaseTarget();
        int unlitCenter=read(unlit)[32*64+32];
        check((unlitCenter&0xffffff)==0xffffff,
                String.format("No-material textured mesh lost its opaque texture: #%08x",unlitCenter));
        System.out.println("M3GCompositingSmoke: "+checks+" checks passed; per-pixel alpha depth, mesh overlap, clips and unlit textures");
    }
    private static VertexBuffer triangle(int color) {
        return triangleAt(color,-30);
    }
    private static VertexBuffer triangleAt(int color,float z) {
        VertexArray positions=new VertexArray(3,3,VertexArray.SHORT);
        positions.set(0,3,new short[]{-6,-6,(short)z,6,-6,(short)z,0,6,(short)z});
        VertexBuffer result=new VertexBuffer(); result.setPositions(positions,0.1f,null); result.setDefaultColor(color);
        return result;
    }
    private static int renderFogBoat(Graphics3D renderer,Camera camera,TriangleStripArray indices,
            PolygonMode polygon,boolean fogFirst) {
        Image target=Image.createImage(64,64);
        Graphics graphics=target.getGraphics(); graphics.setColor(0x000000); graphics.fillRect(0,0,64,64);
        Background background=new Background();
        background.setColorClearEnable(false); background.setDepthClearEnable(true);
        World world=new World(); world.setBackground(background); world.setActiveCamera(camera);
        world.addChild(camera);

        Appearance fogAppearance=new Appearance(); fogAppearance.setPolygonMode(polygon);
        CompositingMode additive=new CompositingMode(); additive.setBlending(CompositingMode.ALPHA_ADD);
        fogAppearance.setCompositingMode(additive);
        Material red=new Material(); red.setColor(Material.DIFFUSE,0xffff0000);
        fogAppearance.setMaterial(red);
        Mesh fog=new Mesh(triangleAt(0xffffffff,-20),indices,fogAppearance);

        Appearance boatAppearance=new Appearance(); boatAppearance.setPolygonMode(polygon);
        CompositingMode replace=new CompositingMode(); replace.setBlending(CompositingMode.REPLACE);
        boatAppearance.setCompositingMode(replace);
        Material green=new Material(); green.setColor(Material.DIFFUSE,0xff00ff00);
        boatAppearance.setMaterial(green);
        Mesh boat=new Mesh(triangleAt(0xffffffff,-30),indices,boatAppearance);
        if(fogFirst) { world.addChild(fog); world.addChild(boat); }
        else { world.addChild(boat); world.addChild(fog); }

        renderer.bindTarget(graphics); renderer.render(world); renderer.releaseTarget();
        return read(target)[32*64+32];
    }
    private static int renderWaterReflection(Graphics3D renderer,Camera camera,
            TriangleStripArray indices,PolygonMode polygon,boolean waterFirst) {
        Image target=Image.createImage(64,64);
        Graphics graphics=target.getGraphics(); graphics.setColor(0); graphics.fillRect(0,0,64,64);
        Background background=new Background();
        World world=new World(); world.setBackground(background); world.setActiveCamera(camera);
        world.addChild(camera);

        Appearance waterAppearance=new Appearance(); waterAppearance.setPolygonMode(polygon);
        CompositingMode alpha=new CompositingMode(); alpha.setBlending(CompositingMode.ALPHA);
        waterAppearance.setCompositingMode(alpha);
        Material water=new Material(); water.setColor(Material.DIFFUSE,0x80ff0000);
        waterAppearance.setMaterial(water);
        Mesh surface=new Mesh(triangleAt(0xffffffff,-20),indices,waterAppearance);

        Appearance reflectionAppearance=new Appearance(); reflectionAppearance.setPolygonMode(polygon);
        CompositingMode replace=new CompositingMode(); replace.setBlending(CompositingMode.REPLACE);
        reflectionAppearance.setCompositingMode(replace);
        Material reflection=new Material(); reflection.setColor(Material.DIFFUSE,0xff0000ff);
        reflectionAppearance.setMaterial(reflection);
        Mesh reflectedObject=new Mesh(triangleAt(0xffffffff,-30),indices,reflectionAppearance);

        if(waterFirst) { world.addChild(surface); world.addChild(reflectedObject); }
        else { world.addChild(reflectedObject); world.addChild(surface); }
        renderer.bindTarget(graphics); renderer.render(world); renderer.releaseTarget();
        return read(target)[32*64+32];
    }
    private static int renderDepthOrder(Graphics3D renderer,Camera camera,TriangleStripArray indices,
            PolygonMode polygon,int blending,int frontColor) {
        Image target=Image.createImage(64,64);
        Graphics graphics=target.getGraphics(); graphics.setColor(0); graphics.fillRect(0,0,64,64);
        Appearance frontAppearance=new Appearance(); frontAppearance.setPolygonMode(polygon);
        CompositingMode frontMode=new CompositingMode(); frontMode.setBlending(blending);
        frontAppearance.setCompositingMode(frontMode);
        Material frontMaterial=new Material(); frontMaterial.setColor(Material.DIFFUSE,frontColor);
        frontAppearance.setMaterial(frontMaterial);
        Appearance rearAppearance=new Appearance(); rearAppearance.setPolygonMode(polygon);
        CompositingMode rearMode=new CompositingMode(); rearMode.setBlending(CompositingMode.REPLACE);
        rearAppearance.setCompositingMode(rearMode);
        Material rearMaterial=new Material(); rearMaterial.setColor(Material.DIFFUSE,0xff00ff00);
        rearAppearance.setMaterial(rearMaterial);
        renderer.bindTarget(graphics); renderer.clear(new Background()); renderer.setCamera(camera,new Transform());
        renderer.render(triangleAt(0xffffffff,-20),indices,frontAppearance,null);
        renderer.render(triangleAt(0xffffffff,-30),indices,rearAppearance,null);
        renderer.releaseTarget();
        return read(target)[32*64+32];
    }
    private static int[] read(Image image) {
        int[] pixels=new int[image.getWidth()*image.getHeight()];
        image.getRGB(pixels,0,image.getWidth(),0,0,image.getWidth(),image.getHeight()); return pixels;
    }
    private static boolean requiresDeferredAlphaPass(Appearance appearance,VertexBuffer vertices)
            throws Exception {
        Method method=Graphics3D.class.getDeclaredMethod("requiresDeferredAlphaPass",
                Appearance.class,VertexBuffer.class,Image2D.class);
        method.setAccessible(true);
        return ((Boolean)method.invoke(new Graphics3D(),appearance,vertices,null)).booleanValue();
    }
}
