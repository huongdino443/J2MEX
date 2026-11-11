import java.io.*;
import java.net.URL;
import java.nio.file.Files;
import javax.imageio.ImageIO;
import org.recompile.mobile.*;

/** Local-engine capture harness. Uses temporary game data, never contacts a game server itself. */
public final class GameJarCapture {
    public static void main(String[] args) {
        try { run(args); System.exit(0); }
        catch(Throwable error) { error.printStackTrace(); System.exit(1); }
    }
    private static void run(String[] args) throws Exception {
        MobilePlatform platform=new MobilePlatform(240,320); Mobile.setPlatform(platform);
        File data=Files.createTempDirectory("freej2me-game-capture-").toFile();
        platform.dataPath=data.getCanonicalPath()+File.separator;
        platform.setPainter(()->{});
        AudioBridge.configure(new File(data,"audio"),(action,id,text,extra,value,number,flags)->{});
        platform.loader=new MIDletLoader(new URL[]{new File(args[0]).toURI().toURL()});
        platform.loader.start();
        for(int i=1;i<args.length;i++) {
            String[] command=args[i].split(":",2);
            if(command[0].equals("wait")) Thread.sleep(Long.parseLong(command[1]));
            else if(command[0].equals("key")) {
                int code=Integer.parseInt(command[1]);
                platform.keyPressed(code); Thread.sleep(60); platform.keyReleased(code);
            } else if(command[0].equals("shot")) {
                int w=platform.lcdWidth,h=platform.lcdHeight; int[] pixels=new int[w*h]; platform.copyFrameTo(pixels);
                java.awt.image.BufferedImage image=new java.awt.image.BufferedImage(w,h,java.awt.image.BufferedImage.TYPE_INT_ARGB);
                image.setRGB(0,0,w,h,pixels,0,w); ImageIO.write(image,"png",new File(command[1]));
                java.util.HashSet<Integer> colors=new java.util.HashSet<Integer>(); for(int p:pixels) colors.add(p);
                System.out.println("CAPTURE "+command[1]+" "+w+"x"+h+" colors="+colors.size());
            } else throw new IllegalArgumentException("Unknown capture command");
        }
        System.out.println("GAME_CAPTURE_COMPLETE");
    }
}
