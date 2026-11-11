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
        System.out.println("M3GCompositingSmoke: "+checks+" checks passed; moving meshes, current 2D background, RGB alpha, clips, target rebinding and unlit textures");
    }
    private static VertexBuffer triangle(int color) {
        VertexArray positions=new VertexArray(3,3,VertexArray.SHORT);
        positions.set(0,3,new short[]{-6,-6,-30,6,-6,-30,0,6,-30});
        VertexBuffer result=new VertexBuffer(); result.setPositions(positions,0.1f,null); result.setDefaultColor(color);
        return result;
    }
    private static int[] read(Image image) {
        int[] pixels=new int[image.getWidth()*image.getHeight()];
        image.getRGB(pixels,0,image.getWidth(),0,0,image.getWidth(),image.getHeight()); return pixels;
    }
}
