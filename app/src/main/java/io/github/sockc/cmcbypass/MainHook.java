package io.github.sockc.cmcbypass;

import android.content.ContentValues;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;

import org.json.JSONArray;
import org.json.JSONObject;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class MainHook implements IXposedHookLoadPackage {

    private static final String TAG = "[CMCBypass-v4] ";

    private static final String PKG_MDEC = "com.samsung.android.mdecservice";
    private static final String PKG_GMS = "com.google.android.gms";
    private static final String PKG_GALAXY_CONTINUITY = "com.samsung.android.galaxycontinuity";

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!isTargetPackage(lpparam.packageName)) {
            return;
        }

        log("Loaded in " + lpparam.packageName);

        hookChinaSimCheck(lpparam);
        hookMccCheck(lpparam);
        hookVpnAndWifiNetwork(lpparam);
        hookWifiInfo(lpparam);
        hookJsonPolicyAndCallforking(lpparam);
        hookContentValues(lpparam);

        log("All hooks installed for " + lpparam.packageName);
    }

    private boolean isTargetPackage(String packageName) {
        if (PKG_MDEC.equals(packageName)) return true;
        if (PKG_GMS.equals(packageName)) return true;
        if (PKG_GALAXY_CONTINUITY.equals(packageName)) return true;

        if (packageName != null && packageName.toLowerCase().contains("mcf")) return true;
        if (packageName != null && packageName.toLowerCase().contains("continuity")) return true;
        if (packageName != null && packageName.toLowerCase().contains("nearby")) return true;

        return false;
    }

    private void hookChinaSimCheck(final XC_LoadPackage.LoadPackageParam lpparam) {
        if (!PKG_MDEC.equals(lpparam.packageName)) {
            return;
        }

        try {
            XposedHelpers.findAndHookMethod(
                    "com.samsung.android.cmcsettings.utils.Utils",
                    lpparam.classLoader,
                    "isChinaSimPresentInGlobalPD",
                    Context.class,
                    XC_MethodReplacement.returnConstant(false)
            );
            log("OK: Utils.isChinaSimPresentInGlobalPD(Context) -> false");
        } catch (Throwable t) {
            log("SKIP/FAIL: isChinaSimPresentInGlobalPD: " + t);
        }
    }

    private void hookMccCheck(final XC_LoadPackage.LoadPackageParam lpparam) {
        if (!PKG_MDEC.equals(lpparam.packageName)) {
            return;
        }

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
                            log("SimUtils.getMcc(Context,int) -> 234");
                            return "234";
                        }
                    }
            );
            log("OK: SimUtils.getMcc(Context,int) -> 234");
        } catch (Throwable t) {
            log("SKIP/FAIL: SimUtils.getMcc: " + t);
        }
    }

    private void hookVpnAndWifiNetwork(final XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            XposedHelpers.findAndHookMethod(
                    NetworkCapabilities.class,
                    "hasTransport",
                    int.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            int transport = (Integer) param.args[0];

                            if (transport == NetworkCapabilities.TRANSPORT_VPN) {
                                param.setResult(false);
                                log("NetworkCapabilities.hasTransport(VPN) -> false");
                                return;
                            }

                            if (transport == NetworkCapabilities.TRANSPORT_WIFI) {
                                param.setResult(true);
                                log("NetworkCapabilities.hasTransport(WIFI) -> true");
                            }
                        }
                    }
            );
            log("OK: NetworkCapabilities.hasTransport spoofed");
        } catch (Throwable t) {
            log("SKIP/FAIL: NetworkCapabilities.hasTransport: " + t);
        }

        try {
            XposedHelpers.findAndHookMethod(
                    NetworkCapabilities.class,
                    "toString",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            Object result = param.getResult();
                            if (result instanceof String) {
                                String s = (String) result;
                                s = s.replace("TRANSPORT_VPN", "TRANSPORT_WIFI");
                                s = s.replace(" VPN", " WIFI");
                                s = s.replace("|VPN", "|WIFI");
                                s = s.replace(" VPN ", " WIFI ");
                                param.setResult(s);
                            }
                        }
                    }
            );
            log("OK: NetworkCapabilities.toString filtered");
        } catch (Throwable t) {
            log("SKIP/FAIL: NetworkCapabilities.toString: " + t);
        }

        tryHookNetworkInfo("getType", ConnectivityManager.TYPE_WIFI);
        tryHookNetworkInfo("getTypeName", "WIFI");
        tryHookNetworkInfo("isConnected", true);
        tryHookNetworkInfo("isConnectedOrConnecting", true);
        tryHookNetworkInfo("isAvailable", true);
        tryHookNetworkInfo("getState", NetworkInfo.State.CONNECTED);
        tryHookNetworkInfo("getDetailedState", NetworkInfo.DetailedState.CONNECTED);
    }

    private void tryHookNetworkInfo(String method, Object result) {
        try {
            XposedHelpers.findAndHookMethod(
                    NetworkInfo.class,
                    method,
                    XC_MethodReplacement.returnConstant(result)
            );
            log("OK: NetworkInfo." + method + " -> " + result);
        } catch (Throwable t) {
            log("SKIP/FAIL: NetworkInfo." + method + ": " + t);
        }
    }

    private void hookWifiInfo(final XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            XposedHelpers.findAndHookMethod(
                    WifiManager.class,
                    "isWifiEnabled",
                    XC_MethodReplacement.returnConstant(true)
            );
            log("OK: WifiManager.isWifiEnabled -> true");
        } catch (Throwable t) {
            log("SKIP/FAIL: WifiManager.isWifiEnabled: " + t);
        }

        try {
            XposedHelpers.findAndHookMethod(
                    WifiManager.class,
                    "getWifiState",
                    XC_MethodReplacement.returnConstant(WifiManager.WIFI_STATE_ENABLED)
            );
            log("OK: WifiManager.getWifiState -> WIFI_STATE_ENABLED");
        } catch (Throwable t) {
            log("SKIP/FAIL: WifiManager.getWifiState: " + t);
        }

        tryHookWifiInfo("getSSID", "\"CMC-Home\"");
        tryHookWifiInfo("getBSSID", "02:11:22:33:44:55");
        tryHookWifiInfo("getNetworkId", 1);
        tryHookWifiInfo("getSupplicantState", SupplicantState.COMPLETED);
    }

    private void tryHookWifiInfo(String method, Object result) {
        try {
            XposedHelpers.findAndHookMethod(
                    WifiInfo.class,
                    method,
                    XC_MethodReplacement.returnConstant(result)
            );
            log("OK: WifiInfo." + method + " -> " + result);
        } catch (Throwable t) {
            log("SKIP/FAIL: WifiInfo." + method + ": " + t);
        }
    }

    private void hookJsonPolicyAndCallforking(final XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            XposedHelpers.findAndHookMethod(
                    JSONObject.class,
                    "getBoolean",
                    String.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            String key = key(param);
                            Object fake = fakeBooleanForKey(key);
                            if (fake != null) {
                                param.setResult(fake);
                                log("JSONObject.getBoolean(" + key + ") -> " + fake);
                            }
                        }
                    }
            );
            log("OK: JSONObject.getBoolean hooked");
        } catch (Throwable t) {
            log("SKIP/FAIL: JSONObject.getBoolean: " + t);
        }

        try {
            XposedHelpers.findAndHookMethod(
                    JSONObject.class,
                    "optBoolean",
                    String.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            String key = key(param);
                            Object fake = fakeBooleanForKey(key);
                            if (fake != null) {
                                param.setResult(fake);
                                log("JSONObject.optBoolean(" + key + ") -> " + fake);
                            }
                        }
                    }
            );
            log("OK: JSONObject.optBoolean(String) hooked");
        } catch (Throwable t) {
            log("SKIP/FAIL: JSONObject.optBoolean(String): " + t);
        }

        try {
            XposedHelpers.findAndHookMethod(
                    JSONObject.class,
                    "optBoolean",
                    String.class,
                    boolean.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            String key = key(param);
                            Object fake = fakeBooleanForKey(key);
                            if (fake != null) {
                                param.setResult(fake);
                                log("JSONObject.optBoolean(" + key + ",default) -> " + fake);
                            }
                        }
                    }
            );
            log("OK: JSONObject.optBoolean(String,boolean) hooked");
        } catch (Throwable t) {
            log("SKIP/FAIL: JSONObject.optBoolean(String,boolean): " + t);
        }

        try {
            XposedHelpers.findAndHookMethod(
                    JSONObject.class,
                    "getJSONObject",
                    String.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            String key = key(param);
                            JSONObject fake = fakeObjectForKey(key);
                            if (fake != null) {
                                param.setResult(fake);
                                log("JSONObject.getJSONObject(" + key + ") -> fake");
                            }
                        }
                    }
            );
            log("OK: JSONObject.getJSONObject hooked");
        } catch (Throwable t) {
            log("SKIP/FAIL: JSONObject.getJSONObject: " + t);
        }

        try {
            XposedHelpers.findAndHookMethod(
                    JSONObject.class,
                    "optJSONObject",
                    String.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            String key = key(param);
                            JSONObject fake = fakeObjectForKey(key);
                            if (fake != null) {
                                param.setResult(fake);
                                log("JSONObject.optJSONObject(" + key + ") -> fake");
                            }
                        }
                    }
            );
            log("OK: JSONObject.optJSONObject hooked");
        } catch (Throwable t) {
            log("SKIP/FAIL: JSONObject.optJSONObject: " + t);
        }

        try {
            XposedHelpers.findAndHookMethod(
                    JSONObject.class,
                    "getJSONArray",
                    String.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            String key = key(param);
                            JSONArray fake = fakeArrayForKey(key);
                            if (fake != null) {
                                param.setResult(fake);
                                log("JSONObject.getJSONArray(" + key + ") -> fake");
                            }
                        }
                    }
            );
            log("OK: JSONObject.getJSONArray hooked");
        } catch (Throwable t) {
            log("SKIP/FAIL: JSONObject.getJSONArray: " + t);
        }

        try {
            XposedHelpers.findAndHookMethod(
                    JSONObject.class,
                    "optJSONArray",
                    String.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            String key = key(param);
                            JSONArray fake = fakeArrayForKey(key);
                            if (fake != null) {
                                param.setResult(fake);
                                log("JSONObject.optJSONArray(" + key + ") -> fake");
                            }
                        }
                    }
            );
            log("OK: JSONObject.optJSONArray hooked");
        } catch (Throwable t) {
            log("SKIP/FAIL: JSONObject.optJSONArray: " + t);
        }

        try {
            XposedHelpers.findAndHookMethod(
                    JSONObject.class,
                    "getInt",
                    String.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            String key = key(param);
                            Integer fake = fakeIntForKey(key);
                            if (fake != null) {
                                param.setResult(fake);
                                log("JSONObject.getInt(" + key + ") -> " + fake);
                            }
                        }
                    }
            );
            log("OK: JSONObject.getInt hooked");
        } catch (Throwable t) {
            log("SKIP/FAIL: JSONObject.getInt: " + t);
        }

        try {
            XposedHelpers.findAndHookMethod(
                    JSONObject.class,
                    "optInt",
                    String.class,
                    int.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            String key = key(param);
                            Integer fake = fakeIntForKey(key);
                            if (fake != null) {
                                param.setResult(fake);
                                log("JSONObject.optInt(" + key + ",default) -> " + fake);
                            }
                        }
                    }
            );
            log("OK: JSONObject.optInt(String,int) hooked");
        } catch (Throwable t) {
            log("SKIP/FAIL: JSONObject.optInt(String,int): " + t);
        }

        try {
            XposedHelpers.findAndHookMethod(
                    JSONObject.class,
                    "optInt",
                    String.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            String key = key(param);
                            Integer fake = fakeIntForKey(key);
                            if (fake != null) {
                                param.setResult(fake);
                                log("JSONObject.optInt(" + key + ") -> " + fake);
                            }
                        }
                    }
            );
            log("OK: JSONObject.optInt(String) hooked");
        } catch (Throwable t) {
            log("SKIP/FAIL: JSONObject.optInt(String): " + t);
        }

        try {
            XposedHelpers.findAndHookMethod(
                    JSONObject.class,
                    "getString",
                    String.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            String key = key(param);
                            String fake = fakeStringForKey(key);
                            if (fake != null) {
                                param.setResult(fake);
                                log("JSONObject.getString(" + key + ") -> " + fake);
                            }
                        }
                    }
            );
            log("OK: JSONObject.getString hooked");
        } catch (Throwable t) {
            log("SKIP/FAIL: JSONObject.getString: " + t);
        }

        try {
            XposedHelpers.findAndHookMethod(
                    JSONObject.class,
                    "optString",
                    String.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            String key = key(param);
                            String fake = fakeStringForKey(key);
                            if (fake != null) {
                                param.setResult(fake);
                                log("JSONObject.optString(" + key + ") -> " + fake);
                            }
                        }
                    }
            );
            log("OK: JSONObject.optString(String) hooked");
        } catch (Throwable t) {
            log("SKIP/FAIL: JSONObject.optString(String): " + t);
        }

        try {
            XposedHelpers.findAndHookMethod(
                    JSONObject.class,
                    "optString",
                    String.class,
                    String.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            String key = key(param);
                            String fake = fakeStringForKey(key);
                            if (fake != null) {
                                param.setResult(fake);
                                log("JSONObject.optString(" + key + ",default) -> " + fake);
                            }
                        }
                    }
            );
            log("OK: JSONObject.optString(String,String) hooked");
        } catch (Throwable t) {
            log("SKIP/FAIL: JSONObject.optString(String,String): " + t);
        }

        try {
            XposedHelpers.findAndHookMethod(
                    JSONObject.class,
                    "get",
                    String.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            String key = key(param);
                            Object fake = fakeGenericForKey(key);
                            if (fake != null) {
                                param.setResult(fake);
                                log("JSONObject.get(" + key + ") -> fake");
                            }
                        }
                    }
            );
            log("OK: JSONObject.get hooked");
        } catch (Throwable t) {
            log("SKIP/FAIL: JSONObject.get: " + t);
        }

        try {
            XposedHelpers.findAndHookMethod(
                    JSONObject.class,
                    "opt",
                    String.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            String key = key(param);
                            Object fake = fakeGenericForKey(key);
                            if (fake != null) {
                                param.setResult(fake);
                                log("JSONObject.opt(" + key + ") -> fake");
                            }
                        }
                    }
            );
            log("OK: JSONObject.opt hooked");
        } catch (Throwable t) {
            log("SKIP/FAIL: JSONObject.opt: " + t);
        }

        try {
            XposedHelpers.findAndHookMethod(
                    JSONObject.class,
                    "toString",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            Object result = param.getResult();
                            if (result instanceof String) {
                                param.setResult(sanitizeJsonString((String) result));
                            }
                        }
                    }
            );
            log("OK: JSONObject.toString sanitized");
        } catch (Throwable t) {
            log("SKIP/FAIL: JSONObject.toString: " + t);
        }
    }

    private void hookContentValues(final XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            XposedHelpers.findAndHookMethod(
                    ContentValues.class,
                    "put",
                    String.class,
                    String.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            String key = key(param);
                            if (isActiveServicesKey(key)) {
                                param.args[1] = fakeActiveServices().toString();
                                log("ContentValues.put(" + key + ", String) -> force callforking");
                            }
                        }
                    }
            );
            log("OK: ContentValues.put(String,String) hooked");
        } catch (Throwable t) {
            log("SKIP/FAIL: ContentValues.put(String,String): " + t);
        }

        try {
            XposedHelpers.findAndHookMethod(
                    ContentValues.class,
                    "put",
                    String.class,
                    Boolean.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            String key = key(param);
                            if ("is_vpn".equalsIgnoreCase(key)) {
                                param.args[1] = Boolean.FALSE;
                                log("ContentValues.put(is_vpn, Boolean) -> false");
                            }
                        }
                    }
            );
            log("OK: ContentValues.put(String,Boolean) hooked");
        } catch (Throwable t) {
            log("SKIP/FAIL: ContentValues.put(String,Boolean): " + t);
        }

        try {
            XposedHelpers.findAndHookMethod(
                    ContentValues.class,
                    "put",
                    String.class,
                    Integer.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            String key = key(param);
                            Integer fake = fakeIntForKey(key);
                            if (fake != null) {
                                param.args[1] = fake;
                                log("ContentValues.put(" + key + ", Integer) -> " + fake);
                            }
                        }
                    }
            );
            log("OK: ContentValues.put(String,Integer) hooked");
        } catch (Throwable t) {
            log("SKIP/FAIL: ContentValues.put(String,Integer): " + t);
        }
    }

    private String key(XC_MethodHook.MethodHookParam param) {
        if (param == null || param.args == null || param.args.length == 0 || param.args[0] == null) {
            return "";
        }
        return String.valueOf(param.args[0]);
    }

    private Object fakeBooleanForKey(String key) {
        if (key == null) return null;

        if ("is_vpn".equalsIgnoreCase(key)) return false;
        if ("supported".equalsIgnoreCase(key)) return true;
        if ("value".equalsIgnoreCase(key)) return true;
        if ("activation".equalsIgnoreCase(key)) return true;
        if ("call_activation".equalsIgnoreCase(key)) return true;
        if ("message_activation".equalsIgnoreCase(key)) return true;
        if ("is_wifi".equalsIgnoreCase(key)) return true;
        if ("same_wifi_network".equalsIgnoreCase(key)) return true;

        return null;
    }

    private JSONObject fakeObjectForKey(String key) throws Throwable {
        if (key == null) return null;

        if ("vpn_state".equalsIgnoreCase(key)) return fakeVpnState();
        if ("same_wifi_network".equalsIgnoreCase(key)) return fakeSameWifiPolicy();
        if ("cmc_network_info".equalsIgnoreCase(key)) return fakeCmcNetworkInfo();
        if ("call_forking".equalsIgnoreCase(key)) return fakeSupportedFeature();
        if ("message_sync".equalsIgnoreCase(key)) return fakeSupportedFeature();
        if ("call_log_sync".equalsIgnoreCase(key)) return fakeSupportedFeature();
        if ("cmc_activation_info".equalsIgnoreCase(key)) return fakeCmcActivationInfo();

        return null;
    }

    private JSONArray fakeArrayForKey(String key) {
        if (key == null) return null;

        if ("restricted_countries".equalsIgnoreCase(key)) return new JSONArray();
        if (isActiveServicesKey(key)) return fakeActiveServices();

        return null;
    }

    private Integer fakeIntForKey(String key) {
        if (key == null) return null;

        if ("activation".equalsIgnoreCase(key)) return 1;
        if ("call_activation".equalsIgnoreCase(key)) return 1;
        if ("message_activation".equalsIgnoreCase(key)) return 1;
        if ("selected_network_type".equalsIgnoreCase(key)) return 0;
        if ("restricted_country_checked_status".equalsIgnoreCase(key)) return 0;
        if ("access_type".equalsIgnoreCase(key)) return 0;

        return null;
    }

    private String fakeStringForKey(String key) {
        if (key == null) return null;

        if ("restricted_countries".equalsIgnoreCase(key)) return "[]";
        if ("detected_transports".equalsIgnoreCase(key)) return "WIFI";
        if ("binded_network".equalsIgnoreCase(key)) return "WIFI";
        if ("vpn_application".equalsIgnoreCase(key)) return "[]";
        if ("country_code".equalsIgnoreCase(key)) return "";
        if ("iso3_country_code".equalsIgnoreCase(key)) return "";
        if ("network_type".equalsIgnoreCase(key)) return "WIFI";
        if (isActiveServicesKey(key)) return fakeActiveServices().toString();

        return null;
    }

    private Object fakeGenericForKey(String key) throws Throwable {
        Object bool = fakeBooleanForKey(key);
        if (bool != null) return bool;

        JSONObject obj = fakeObjectForKey(key);
        if (obj != null) return obj;

        JSONArray arr = fakeArrayForKey(key);
        if (arr != null) return arr;

        Integer integer = fakeIntForKey(key);
        if (integer != null) return integer;

        String str = fakeStringForKey(key);
        if (str != null) return str;

        return null;
    }

    private boolean isActiveServicesKey(String key) {
        if (key == null) return false;
        return "active_services".equalsIgnoreCase(key)
                || "activeServices".equalsIgnoreCase(key)
                || "COL_ACTIVE_SERVICES".equalsIgnoreCase(key)
                || "active_services_list".equalsIgnoreCase(key);
    }

    private JSONObject fakeVpnState() throws Throwable {
        JSONObject obj = new JSONObject();
        obj.put("binded_network", "WIFI");
        obj.put("country_code", "");
        obj.put("detected_transports", "WIFI");
        obj.put("is_vpn", false);
        obj.put("iso3_country_code", "");
        obj.put("restricted_countries", new JSONArray());
        obj.put("restricted_country_checked_status", 0);
        obj.put("vpn_application", new JSONArray());
        return obj;
    }

    private JSONObject fakeSameWifiPolicy() throws Throwable {
        JSONObject obj = new JSONObject();
        obj.put("access_type", 0);
        obj.put("value", true);
        obj.put("supported", true);
        return obj;
    }

    private JSONObject fakeCmcNetworkInfo() throws Throwable {
        JSONObject obj = new JSONObject();
        obj.put("access_type", 0);
        obj.put("selected_network_type", 0);
        obj.put("network_type", "WIFI");
        obj.put("is_wifi", true);
        obj.put("is_vpn", false);
        return obj;
    }

    private JSONObject fakeSupportedFeature() throws Throwable {
        JSONObject obj = new JSONObject();
        obj.put("access_type", 0);
        obj.put("supported", true);
        obj.put("value", true);
        return obj;
    }

    private JSONObject fakeCmcActivationInfo() throws Throwable {
        JSONObject obj = new JSONObject();
        obj.put("access_type", 0);
        obj.put("activation", 1);
        obj.put("call_activation", 1);
        obj.put("message_activation", 1);
        return obj;
    }

    private JSONArray fakeActiveServices() {
        JSONArray arr = new JSONArray();
        arr.put("message");
        arr.put("msglogv2");
        arr.put("callforking");
        arr.put("calllog");
        arr.put("calllogv2");
        return arr;
    }

    private String sanitizeJsonString(String s) {
        if (s == null) return null;

        String out = s;
        out = out.replace("\"is_vpn\":true", "\"is_vpn\":false");
        out = out.replace("\"detected_transports\":\"VPN\"", "\"detected_transports\":\"WIFI\"");
        out = out.replace("\"binded_network\":\"VPN\"", "\"binded_network\":\"WIFI\"");
        out = out.replace("\"restricted_countries\":\"[CHN, PHL]\"", "\"restricted_countries\":\"[]\"");
        out = out.replace("\"restricted_countries\":[\"CHN\",\"PHL\"]", "\"restricted_countries\":[]");
        out = out.replace("\"restricted_country_checked_status\":-1", "\"restricted_country_checked_status\":0");
        out = out.replace("\"call_activation\":0", "\"call_activation\":1");
        out = out.replace("\"message_activation\":0", "\"message_activation\":1");
        out = out.replace("\"activation\":0", "\"activation\":1");

        return out;
    }

    private void log(String msg) {
        XposedBridge.log(TAG + msg);
    }
}
