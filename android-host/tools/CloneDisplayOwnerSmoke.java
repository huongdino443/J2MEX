import org.recompile.mobile.MobilePlatform;

public final class CloneDisplayOwnerSmoke {
    public static void main(String[] args) {
        Object firstMidlet = new Object();
        Object secondMidlet = new Object();
        Object firstDisplay = new Object();
        Object secondDisplay = new Object();

        MobilePlatform.resetDisplayOwners();
        MobilePlatform.registerDisplayOwner(firstDisplay, firstMidlet);
        MobilePlatform.registerDisplayOwner(secondDisplay, secondMidlet);
        require(!MobilePlatform.isDisplayOwnerActive(firstDisplay), "starts with no active child");

        MobilePlatform.activateDisplayOwner(firstMidlet);
        require(MobilePlatform.isDisplayOwnerActive(firstDisplay), "first clone starts");
        require(!MobilePlatform.isDisplayOwnerActive(secondDisplay), "second clone stays inactive");

        MobilePlatform.activateDisplayOwner(secondMidlet);
        require(!MobilePlatform.isDisplayOwnerActive(firstDisplay), "old clone loses display ownership");
        require(MobilePlatform.isDisplayOwnerActive(secondDisplay), "selected clone owns display");

        MobilePlatform.deactivateDisplayOwner(secondMidlet);
        require(!MobilePlatform.isDisplayOwnerActive(firstDisplay), "inactive clone cannot reclaim display");
        require(!MobilePlatform.isDisplayOwnerActive(secondDisplay), "paused clone releases display");

        MobilePlatform.activateDisplayOwner(firstMidlet);
        require(MobilePlatform.isDisplayOwnerActive(firstDisplay), "saved clone can be resumed");
        MobilePlatform.resetDisplayOwners();
        require(!MobilePlatform.isDisplayOwnerActive(firstDisplay), "new JAR clears old owners");

        System.out.println("CloneDisplayOwnerSmoke: 9 owner arbitration checks passed");
    }

    private static void require(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
    }
}
