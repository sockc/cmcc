package io.github.sockc.cmcbypass;

import android.content.Context;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XC_MethodHook.MethodHookParam;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class MainHook implements IXposedHookLoadPackage {

    private static final String TARGET_PACKAGE = "com.samsung.android.mdecservice";

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!TARGET_PACKAGE.equals(lpparam.packageName)) {
            return;
        }

        XposedBridge.log("[CMCBypass] Loaded in " + lpparam.packageName);

        hookChinaSimPresentCheck(lpparam);
        hookMccFallback(lpparam);
    }

    /**
     * Main bypass for Samsung MDEC / Call & text on other devices.
     * Target:
     *   com.samsung.android.cmcsettings.utils.Utils.isChinaSimPresentInGlobalPD(Context)
     */
    private void hookChinaSimPresentCheck(final XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            XposedHelpers.findAndHookMethod(
                    "com.samsung.android.cmcsettings.utils.Utils",
                    lpparam.classLoader,
                    "isChinaSimPresentInGlobalPD",
                    Context.class,
                    XC_MethodReplacement.returnConstant(false)
            );
            XposedBridge.log("[CMCBypass] OK: isChinaSimPresentInGlobalPD(Context) -> false");
        } catch (Throwable t) {
            XposedBridge.log("[CMCBypass] FAIL: isChinaSimPresentInGlobalPD hook: " + t);
        }
    }

    /**
     * Fallback only inside com.samsung.android.mdecservice.
     * If Samsung checks MCC directly elsewhere, make MDEC see non-China MCC.
     * This does NOT globally change system SIM / VoLTE / phone behavior.
     */
    private void hookMccFallback(final XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            XposedHelpers.findAndHookMethod(
                    "com.samsung.android.mdeccommon.utils.SimUtils",
                    lpparam.classLoader,
                    "getMcc",
                    Context.class,
                    int.class,
                    new XC_MethodReplacement() {
                        @Override
                        protected Object replaceHookedMethod(MethodHookParam param) {
                            XposedBridge.log("[CMCBypass] SimUtils.getMcc(Context,int) -> 234");
                            return "234";
                        }
                    }
            );
            XposedBridge.log("[CMCBypass] OK: SimUtils.getMcc(Context,int) -> 234");
        } catch (Throwable t) {
            XposedBridge.log("[CMCBypass] INFO: SimUtils.getMcc fallback not hooked: " + t);
        }
    }
}
