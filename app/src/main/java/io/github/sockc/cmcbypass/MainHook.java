package io.github.sockc.cmcbypass;

import android.content.Context;
import android.net.NetworkCapabilities;

import org.json.JSONArray;
import org.json.JSONObject;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodHook.MethodHookParam;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class MainHook implements IXposedHookLoadPackage {

    private static final String TARGET_PACKAGE = "com.samsung.android.mdecservice";
    private static final int TRANSPORT_VPN = 4;
    private static final int TYPE_VPN = 17;

    private static final Set<String> BOOL_FALSE_KEYS = new HashSet<String>();
    private static final Set<String> EMPTY_STRING_KEYS = new HashSet<String>();

    static {
        BOOL_FALSE_KEYS.add("is_vpn");
        BOOL_FALSE_KEYS.add("used_vpn");
        BOOL_FALSE_KEYS.add("KEY_USED_VPN");
        BOOL_FALSE_KEYS.add("KEY_DEVICE_DATA_IS_VPN");

        EMPTY_STRING_KEYS.add("vpn_application");
        EMPTY_STRING_KEYS.add("vpnApplication");
        EMPTY_STRING_KEYS.add("country_code");
        EMPTY_STRING_KEYS.add("iso3_country_code");
        EMPTY_STRING_KEYS.add("restricted_country");
        EMPTY_STRING_KEYS.add("detected_transports");
        EMPTY_STRING_KEYS.add("binded_network");
    }

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!TARGET_PACKAGE.equals(lpparam.packageName)) {
            return;
        }

        XposedBridge.log("[CMCBypass] Loaded in " + lpparam.packageName + " / v2-vpn");

        hookChinaSimPresentCheck(lpparam);
        hookMccFallback(lpparam);
        hookAndroidVpnDetection();
        hookSamsungVpnUtils(lpparam);
        hookJsonVpnFlags();
    }

    /**
     * Main China-SIM bypass for Samsung MDEC / Call & text on other devices.
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

    /**
     * Hide Android system VPN/TUN transport from MDEC only.
     * Real network traffic still uses your VPN; MDEC just will not see TRANSPORT_VPN.
     */
    private void hookAndroidVpnDetection() {
        try {
            XposedHelpers.findAndHookMethod(
                    NetworkCapabilities.class,
                    "hasTransport",
                    int.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            if (param.args != null && param.args.length > 0
                                    && param.args[0] instanceof Integer
                                    && ((Integer) param.args[0]) == TRANSPORT_VPN) {
                                param.setResult(false);
                            }
                        }
                    }
            );
            XposedBridge.log("[CMCBypass] OK: NetworkCapabilities.hasTransport(VPN) -> false");
        } catch (Throwable t) {
            XposedBridge.log("[CMCBypass] INFO: NetworkCapabilities.hasTransport hook failed: " + t);
        }

        try {
            XposedHelpers.findAndHookMethod(
                    NetworkCapabilities.class,
                    "getTransportTypes",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            Object result = param.getResult();
                            if (!(result instanceof int[])) return;
                            int[] src = (int[]) result;
                            int count = 0;
                            for (int v : src) if (v != TRANSPORT_VPN) count++;
                            if (count == src.length) return;
                            int[] dst = new int[count];
                            int i = 0;
                            for (int v : src) if (v != TRANSPORT_VPN) dst[i++] = v;
                            param.setResult(dst);
                        }
                    }
            );
            XposedBridge.log("[CMCBypass] OK: NetworkCapabilities.getTransportTypes remove VPN");
        } catch (Throwable t) {
            XposedBridge.log("[CMCBypass] INFO: NetworkCapabilities.getTransportTypes hook failed: " + t);
        }

        try {
            XposedHelpers.findAndHookMethod(
                    "android.net.NetworkInfo",
                    null,
                    "getType",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            Object result = param.getResult();
                            if (result instanceof Integer && ((Integer) result) == TYPE_VPN) {
                                param.setResult(1); // ConnectivityManager.TYPE_WIFI
                            }
                        }
                    }
            );
            XposedBridge.log("[CMCBypass] OK: NetworkInfo.getType VPN -> WIFI");
        } catch (Throwable t) {
            XposedBridge.log("[CMCBypass] INFO: NetworkInfo.getType hook failed: " + t);
        }

        try {
            XposedHelpers.findAndHookMethod(
                    "android.net.NetworkInfo",
                    null,
                    "getTypeName",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            Object result = param.getResult();
                            if (result instanceof String && "VPN".equalsIgnoreCase((String) result)) {
                                param.setResult("WIFI");
                            }
                        }
                    }
            );
            XposedBridge.log("[CMCBypass] OK: NetworkInfo.getTypeName VPN -> WIFI");
        } catch (Throwable t) {
            XposedBridge.log("[CMCBypass] INFO: NetworkInfo.getTypeName hook failed: " + t);
        }
    }

    /**
     * Samsung MDEC has its own VPN utilities. Sanitize all obvious VPN-related outputs.
     */
    private void hookSamsungVpnUtils(final XC_LoadPackage.LoadPackageParam lpparam) {
        hookAllMethodsByName(lpparam, "com.samsung.android.mdeccommon.utils.ConnectivityUtils",
                new String[]{"isUsedVpn", "getVpnApplication", "getVpnApplications", "getVpnState", "makeJsonForVpnState"});
        hookAllMethodsByName(lpparam, "com.samsung.android.mdeccommon.utils.ConnectivityManagerHelper",
                new String[]{"isUsedVpn", "getVpnApplication", "getVpnApplications", "getVpnState", "makeJsonForVpnState"});
    }

    private void hookAllMethodsByName(final XC_LoadPackage.LoadPackageParam lpparam, String className, String[] methodNames) {
        try {
            Class<?> cls = XposedHelpers.findClass(className, lpparam.classLoader);
            Method[] methods = cls.getDeclaredMethods();
            for (final Method m : methods) {
                for (String name : methodNames) {
                    if (!name.equals(m.getName())) continue;
                    XposedBridge.hookMethod(m, new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            sanitizeReturnByMethodName(param, m.getName(), m.getReturnType());
                        }
                    });
                    XposedBridge.log("[CMCBypass] OK: hooked " + m.getDeclaringClass().getName() + "." + m.getName());
                }
            }
        } catch (Throwable t) {
            XposedBridge.log("[CMCBypass] INFO: " + className + " VPN util hook failed: " + t);
        }
    }

    private void sanitizeReturnByMethodName(MethodHookParam param, String methodName, Class<?> returnType) {
        try {
            if (returnType == Boolean.TYPE || returnType == Boolean.class || methodName.toLowerCase().contains("isusedvpn")) {
                param.setResult(false);
                return;
            }
            if (returnType == Integer.TYPE || returnType == Integer.class) {
                param.setResult(0);
                return;
            }
            if (returnType == String.class) {
                if (methodName.toLowerCase().contains("vpnapplication")) {
                    param.setResult("");
                } else {
                    Object r = param.getResult();
                    if (r instanceof String) {
                        param.setResult(sanitizeVpnString((String) r));
                    }
                }
                return;
            }
            if (JSONObject.class.isAssignableFrom(returnType)) {
                JSONObject obj = (JSONObject) param.getResult();
                if (obj == null) obj = new JSONObject();
                sanitizeJsonObject(obj);
                param.setResult(obj);
                return;
            }
            if (JSONArray.class.isAssignableFrom(returnType)) {
                param.setResult(new JSONArray());
                return;
            }
            if (java.util.Collection.class.isAssignableFrom(returnType)) {
                param.setResult(new ArrayList<Object>());
            }
        } catch (Throwable t) {
            XposedBridge.log("[CMCBypass] INFO: sanitizeReturn failed for " + methodName + ": " + t);
        }
    }

    /**
     * Many remote SD/tablet states are JSON strings. Make all VPN policy reads return safe values.
     */
    private void hookJsonVpnFlags() {
        try {
            XposedHelpers.findAndHookMethod(JSONObject.class, "getBoolean", String.class, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam param) {
                    if (isFalseKey(param.args[0])) param.setResult(false);
                }
            });
            XposedBridge.log("[CMCBypass] OK: JSONObject.getBoolean VPN keys -> false");
        } catch (Throwable t) { XposedBridge.log("[CMCBypass] INFO: JSONObject.getBoolean hook failed: " + t); }

        try {
            XposedHelpers.findAndHookMethod(JSONObject.class, "optBoolean", String.class, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam param) {
                    if (isFalseKey(param.args[0])) param.setResult(false);
                }
            });
            XposedBridge.log("[CMCBypass] OK: JSONObject.optBoolean(String) VPN keys -> false");
        } catch (Throwable t) { XposedBridge.log("[CMCBypass] INFO: JSONObject.optBoolean(String) hook failed: " + t); }

        try {
            XposedHelpers.findAndHookMethod(JSONObject.class, "optBoolean", String.class, boolean.class, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam param) {
                    if (isFalseKey(param.args[0])) param.setResult(false);
                }
            });
            XposedBridge.log("[CMCBypass] OK: JSONObject.optBoolean(String,boolean) VPN keys -> false");
        } catch (Throwable t) { XposedBridge.log("[CMCBypass] INFO: JSONObject.optBoolean(String,boolean) hook failed: " + t); }

        try {
            XposedHelpers.findAndHookMethod(JSONObject.class, "getString", String.class, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam param) {
                    String replacement = replacementStringForKey(param.args[0]);
                    if (replacement != null) param.setResult(replacement);
                }
            });
            XposedBridge.log("[CMCBypass] OK: JSONObject.getString VPN keys sanitized");
        } catch (Throwable t) { XposedBridge.log("[CMCBypass] INFO: JSONObject.getString hook failed: " + t); }

        try {
            XposedHelpers.findAndHookMethod(JSONObject.class, "optString", String.class, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam param) {
                    String replacement = replacementStringForKey(param.args[0]);
                    if (replacement != null) param.setResult(replacement);
                }
            });
            XposedBridge.log("[CMCBypass] OK: JSONObject.optString(String) VPN keys sanitized");
        } catch (Throwable t) { XposedBridge.log("[CMCBypass] INFO: JSONObject.optString(String) hook failed: " + t); }

        try {
            XposedHelpers.findAndHookMethod(JSONObject.class, "optString", String.class, String.class, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam param) {
                    String replacement = replacementStringForKey(param.args[0]);
                    if (replacement != null) param.setResult(replacement);
                }
            });
            XposedBridge.log("[CMCBypass] OK: JSONObject.optString(String,String) VPN keys sanitized");
        } catch (Throwable t) { XposedBridge.log("[CMCBypass] INFO: JSONObject.optString(String,String) hook failed: " + t); }

        try {
            XposedHelpers.findAndHookMethod(JSONObject.class, "put", String.class, Object.class, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam param) {
                    String key = String.valueOf(param.args[0]);
                    if (BOOL_FALSE_KEYS.contains(key)) {
                        param.args[1] = false;
                    } else if ("vpn_application".equals(key) || "vpnApplication".equals(key)) {
                        param.args[1] = "";
                    } else if ("restricted_countries".equals(key) || "restrictedCountries".equals(key)) {
                        param.args[1] = "[]";
                    } else if ("restricted_country_checked_status".equals(key)) {
                        param.args[1] = 0;
                    } else if ("detected_transports".equals(key) || "binded_network".equals(key)) {
                        param.args[1] = "NONE";
                    }
                }
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    Object r = param.getResult();
                    if (r instanceof JSONObject) sanitizeJsonObject((JSONObject) r);
                }
            });
            XposedBridge.log("[CMCBypass] OK: JSONObject.put VPN keys sanitized");
        } catch (Throwable t) { XposedBridge.log("[CMCBypass] INFO: JSONObject.put hook failed: " + t); }
    }

    private boolean isFalseKey(Object keyObj) {
        if (keyObj == null) return false;
        return BOOL_FALSE_KEYS.contains(String.valueOf(keyObj));
    }

    private String replacementStringForKey(Object keyObj) {
        if (keyObj == null) return null;
        String key = String.valueOf(keyObj);
        if ("restricted_countries".equals(key) || "restrictedCountries".equals(key)) return "[]";
        if ("restricted_country_checked_status".equals(key)) return "0";
        if ("detected_transports".equals(key) || "binded_network".equals(key)) return "NONE";
        if (EMPTY_STRING_KEYS.contains(key)) return "";
        return null;
    }

    private String sanitizeVpnString(String s) {
        if (s == null) return null;
        return s
                .replace("\"is_vpn\":true", "\"is_vpn\":false")
                .replace("\"is_vpn\": true", "\"is_vpn\": false")
                .replace("\"used_vpn\":true", "\"used_vpn\":false")
                .replace("\"used_vpn\": true", "\"used_vpn\": false")
                .replace("[CHN, PHL]", "[]")
                .replace("CHN", "")
                .replace("PHL", "");
    }

    private void sanitizeJsonObject(JSONObject obj) {
        if (obj == null) return;
        tryPut(obj, "is_vpn", false);
        tryPut(obj, "used_vpn", false);
        tryPut(obj, "vpn_application", "");
        tryPut(obj, "vpnApplication", "");
        tryPut(obj, "restricted_countries", "[]");
        tryPut(obj, "restrictedCountries", "[]");
        tryPut(obj, "restricted_country", "");
        tryPut(obj, "restricted_country_checked_status", 0);
        tryPut(obj, "restricted_country_status", 0);
        tryPut(obj, "country_code", "");
        tryPut(obj, "iso3_country_code", "");
        tryPut(obj, "detected_transports", "NONE");
        tryPut(obj, "binded_network", "NONE");

        try {
            JSONArray names = obj.names();
            if (names == null) return;
            for (int i = 0; i < names.length(); i++) {
                String key = names.optString(i, null);
                if (key == null) continue;
                Object value = obj.opt(key);
                if (value instanceof JSONObject) sanitizeJsonObject((JSONObject) value);
                if (value instanceof JSONArray) sanitizeJsonArray((JSONArray) value);
            }
        } catch (Throwable ignored) {}
    }

    private void sanitizeJsonArray(JSONArray array) {
        if (array == null) return;
        for (int i = 0; i < array.length(); i++) {
            Object value = array.opt(i);
            if (value instanceof JSONObject) sanitizeJsonObject((JSONObject) value);
            if (value instanceof JSONArray) sanitizeJsonArray((JSONArray) value);
        }
    }

    private void tryPut(JSONObject obj, String key, Object value) {
        try { if (obj.has(key)) obj.put(key, value); } catch (Throwable ignored) {}
    }
}
