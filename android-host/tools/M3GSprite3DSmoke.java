import javax.microedition.lcdui.Graphics;
import javax.microedition.m3g.Appearance;
import javax.microedition.m3g.Camera;
import javax.microedition.m3g.Graphics3D;
import javax.microedition.m3g.Group;
import javax.microedition.m3g.Image2D;
import javax.microedition.m3g.Sprite3D;
import javax.microedition.m3g.Transform;
import javax.microedition.m3g.World;

public final class M3GSprite3DSmoke {
    private static final class CaptureGraphics extends Graphics {
        final int width,height;
        final int[] pixels;
        CaptureGraphics(int width,int height) {
            this.width=width; this.height=height; pixels=new int[width*height];
        }
        @Override public int getClipX() { return 0; }
        @Override public int getClipY() { return 0; }
        @Override public int getClipWidth() { return width; }
        @Override public int getClipHeight() { return height; }
        @Override public void drawRGB(int[] rgbData,int offset,int scanlength,int x,int y,
                int width,int height,boolean processAlpha) {
            for(int row=0;row<height;row++)
                System.arraycopy(rgbData,offset+row*scanlength,pixels,(y+row)*this.width+x,width);
        }
    }
    private static void require(boolean condition,String message) {
        if(!condition) throw new AssertionError(message);
    }
    private static CaptureGraphics render(Sprite3D sprite,Camera camera,boolean throughWorld) {
        return render(sprite,camera,throughWorld,null);
    }
    private static CaptureGraphics render(Sprite3D sprite,Camera camera,boolean throughWorld,Transform rootTransform) {
        CaptureGraphics target=new CaptureGraphics(64,64);
        Graphics3D graphics=new Graphics3D();
        graphics.bindTarget(target,true,0);
        graphics.setViewport(0,0,64,64);
        graphics.setCamera(camera,new Transform());
        if(throughWorld) {
            World world=new World();
            world.setActiveCamera(camera);
            world.addChild(sprite);
            graphics.render(world);
        } else {
            graphics.render(sprite,rootTransform);
        }
        graphics.releaseTarget();
        return target;
    }
    private static int draw(Sprite3D sprite,Camera camera,boolean throughWorld) {
        return render(sprite,camera,throughWorld).pixels[32*64+32];
    }
    private static int draw(Sprite3D sprite,Camera camera,Transform rootTransform) {
        return render(sprite,camera,false,rootTransform).pixels[32*64+32];
    }
    private static int countColor(int[] pixels,int color) {
        int count=0;
        for(int pixel:pixels) if((pixel&0xffffff)==color) count++;
        return count;
    }
    private static double centroidX(int[] pixels,int color) {
        long sum=0,count=0;
        for(int i=0;i<pixels.length;i++) if((pixels[i]&0xffffff)==color) {
            sum+=i%64;
            count++;
        }
        return count==0?Double.NaN:sum/(double)count;
    }
    public static void main(String[] args) {
        Camera camera=new Camera();
        camera.setPerspective(90,1,0.1f,100);
        byte[] rgba=new byte[4*4*4];
        for(int y=0;y<4;y++) for(int x=0;x<4;x++) {
            int p=(y*4+x)*4;
            if(x>=2&&y<2) {
                rgba[p]=(byte)255; rgba[p+1]=0; rgba[p+2]=0; rgba[p+3]=(byte)255;
            } else {
                rgba[p]=0; rgba[p+1]=0; rgba[p+2]=(byte)255; rgba[p+3]=(byte)255;
            }
        }
        Image2D atlas=new Image2D(Image2D.RGBA,4,4,rgba);
        Appearance appearance=new Appearance();
        Sprite3D cropped=new Sprite3D(true,atlas,appearance);
        cropped.setCrop(2,0,2,2);
        cropped.setTranslation(0,0,-4);
        CaptureGraphics target=render(cropped,camera,true);
        require((target.pixels[32*64+32]&0xffffff)==0xff0000,"World render omitted or mis-cropped Sprite3D");
        require(countColor(target.pixels,0x0000ff)==0,"Sprite3D crop sampled pixels outside its atlas rectangle");
        int nearCount=countColor(target.pixels,0xff0000);
        require(nearCount>=50&&nearCount<=80,
                "Scaled Sprite3D must occupy one world unit, not crop-pixel dimensions");
        cropped.setTranslation(0,0,-8);
        int farCount=countColor(render(cropped,camera,true).pixels,0xff0000);
        require(farCount>=8&&farCount<=24&&farCount<nearCount,
                "Scaled Sprite3D size did not shrink with perspective distance");

        Sprite3D fixedSize=new Sprite3D(false,new Image2D(Image2D.RGBA,2,2,new byte[]{
                (byte)255,0,0,(byte)255, (byte)255,0,0,(byte)255,
                (byte)255,0,0,(byte)255, (byte)255,0,0,(byte)255}),appearance);
        Transform fixedSizeTransform=new Transform();
        fixedSizeTransform.postTranslate(0,0,-20);
        int center=draw(fixedSize,camera,fixedSizeTransform);
        require((center&0xffffff)==0xff0000,"Direct Sprite3D render path omitted the sprite");

        Sprite3D cameraTarget=new Sprite3D(true,new Image2D(Image2D.RGBA,2,2,new byte[]{
                (byte)255,0,0,(byte)255, (byte)255,0,0,(byte)255,
                (byte)255,0,0,(byte)255, (byte)255,0,0,(byte)255}),appearance);
        cameraTarget.setTranslation(0,0,-4);
        Camera movingCamera=new Camera();
        movingCamera.setPerspective(90,1,0.1f,100);
        double centeredX=centroidX(render(cameraTarget,movingCamera,true).pixels,0xff0000);
        movingCamera.setTranslation(1,0,0);
        double shiftedX=centroidX(render(cameraTarget,movingCamera,true).pixels,0xff0000);
        require(!Double.isNaN(centeredX)&&!Double.isNaN(shiftedX)&&shiftedX<centeredX-4,
                "World render did not apply an updated active-camera translation: centered="
                        +centeredX+", shifted="+shiftedX);
        System.out.println("M3GSprite3DSmoke: crop, one-unit world sizing, perspective distance and fixed-pixel sizing passed");
    }
}
