import java.util.Arrays;
import org.recompile.mobile.PlatformGraphics;
import org.recompile.mobile.PlatformImage;

/** Checks drawRGB clipping, source bounds and alpha without allocating intermediate images. */
public final class RgbBlitSmoke {
    private static int checks;
    private static void check(boolean value,String message) {
        if(!value) throw new AssertionError(message); checks++;
    }
    public static void main(String[] args) {
        PlatformImage image=new PlatformImage(8,8);
        PlatformGraphics graphics=image.getGraphics();
        graphics.setColor(0x0000ff); graphics.fillRect(0,0,8,8);
        int[] source=new int[64];
        for(int i=0;i<64;i++) source[i]=0xff000000|((i&255)<<16);
        int[] original=source.clone();
        graphics.translate(1,1); graphics.setClip(1,1,2,2);
        graphics.drawRGB(source,0,8,0,0,8,8,false);
        int[] actual=new int[64]; image.getCanvas().getRGB(0,0,8,8,actual,0,8);
        check(Arrays.equals(source,original),"drawRGB modified the caller's array");
        check(actual[2*8+2]==source[9]&&actual[2*8+3]==source[10]
                &&actual[3*8+2]==source[17]&&actual[3*8+3]==source[18],
                "Translated/clipped RGB rows were not copied from the correct source coordinates");
        check(actual[1*8+2]==0xff0000ff&&actual[2*8+1]==0xff0000ff,
                "RGB blit wrote outside its translated clip");
        graphics.reset(); graphics.setColor(0x0000ff); graphics.fillRect(0,0,8,8);
        graphics.drawRGB(new int[]{0x80ff0000,0xff00ff00},0,2,3,4,1,1,true);
        image.getCanvas().getRGB(3,4,1,1,actual,0,1);
        int pixel=actual[0];
        check(((pixel>>>16)&255)==128&&((pixel>>>8)&255)==0&&(pixel&255)==127,
                "Alpha drawRGB did not blend once over the destination");
        try { graphics.drawRGB(source,63,2,0,0,2,2,false); throw new AssertionError("Bad source range accepted"); }
        catch(ArrayIndexOutOfBoundsException expected) { checks++; }
        System.out.println("RgbBlitSmoke: "+checks+" checks passed; clipped RGB, immutability, alpha and bounds");
    }
}
