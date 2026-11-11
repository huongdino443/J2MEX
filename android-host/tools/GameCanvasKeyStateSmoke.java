import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.game.GameCanvas;
import org.recompile.mobile.Mobile;
import org.recompile.mobile.MobilePlatform;

/** Confirms key-state polling preserves held directions and short taps. */
public final class GameCanvasKeyStateSmoke {
    private static int checks;
    private static void check(boolean value,String message) {
        if(!value) throw new AssertionError(message);
        checks++;
    }
    private static final class Probe extends GameCanvas {
        Probe() { super(false); }
    }
    public static void main(String[] args) {
        MobilePlatform platform=new MobilePlatform(16,16);
        Mobile.setPlatform(platform);
        Probe game=new Probe();

        platform.keyPressed(Mobile.NOKIA_UP);
        check(game.getKeyStates()==GameCanvas.UP_PRESSED,"Nokia up key was not reported");
        check(game.getKeyStates()==GameCanvas.UP_PRESSED,"Held up key disappeared after one poll");
        check(game.getKeyStates()==GameCanvas.UP_PRESSED,"Held up key disappeared after repeated polls");
        platform.keyReleased(Mobile.NOKIA_UP);
        check(game.getKeyStates()==0,"Released up key remained held");

        platform.keyPressed(Mobile.NOKIA_LEFT);
        platform.keyReleased(Mobile.NOKIA_LEFT);
        check(game.getKeyStates()==GameCanvas.LEFT_PRESSED,"A quick left tap was lost before polling");
        check(game.getKeyStates()==0,"A quick left tap repeated after its pending event was consumed");

        platform.keyPressed(Mobile.NOKIA_LEFT);
        platform.keyPressed(Mobile.KEY_NUM4);
        check(game.getKeyStates()==GameCanvas.LEFT_PRESSED,"Equivalent left controls were not combined");
        platform.keyReleased(Mobile.NOKIA_LEFT);
        check(game.getKeyStates()==GameCanvas.LEFT_PRESSED,"Releasing one alias cleared the other held key");
        platform.keyReleased(Mobile.KEY_NUM4);
        check(game.getKeyStates()==0,"Left direction remained after all aliases were released");

        platform.keyPressed(Canvas.RIGHT);
        check(game.getKeyStates()==GameCanvas.RIGHT_PRESSED,"Canvas.RIGHT action was not reported");
        platform.keyReleased(Canvas.RIGHT);
        check(game.getKeyStates()==0,"Canvas.RIGHT remained held after release");
        platform.keyPressed(Mobile.NOKIA_FIRE);
        check(game.getKeyStates()==GameCanvas.FIRE_PRESSED,"Nokia fire key was not reported");
        platform.keyReleased(Mobile.NOKIA_FIRE);
        check(game.getKeyStates()==0,"Nokia fire key remained held after release");
        System.out.println("GameCanvasKeyStateSmoke: "+checks+" checks passed; held, tap, alias and action keys");
    }
}
