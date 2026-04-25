# CMC Bypass VPN LSPosed Module

最小 LSPosed 模块，用于绕过三星 `com.samsung.android.mdecservice` 的两类限制：

1. 中国 SIM + 非中国销售型号限制。
2. CMC 检测到 Android VPN/TUN 后限制跨设备通话。

## 编译方式

1. 新建或打开你的 GitHub 仓库。
2. 把本项目所有文件上传/覆盖到仓库根目录。
3. 打开仓库 `Actions`。
4. 运行 `Build APK`。
5. 下载 artifact：`CMCBypass-debug-apk`，里面就是 `app-debug.apk`。

## 使用方式

1. 安装新生成的 `app-debug.apk`，覆盖旧版。
2. 打开 LSPosed。
3. 启用 `CMC Bypass VPN`。
4. 作用域只勾选：`com.samsung.android.mdecservice`。
5. 重启手机/平板。
6. 重新打开「设置 → 已连接的设备 → 在其他设备上接打电话和收发短信」。

## 重要

如果 VPN 开在平板上，平板也需要 root + LSPosed 并安装本模块；否则平板自己的 MDEC 仍会把 `is_vpn:true` 上报给三星/主手机。

## 验证日志

```bash
su -c 'logcat -d -v time' | grep -iE 'CMCBypass|vpn_state|is_vpn|mdec|callfork'
```

正常应看到：

```text
[CMCBypass] Loaded in com.samsung.android.mdecservice / v2-vpn
[CMCBypass] OK: NetworkCapabilities.hasTransport(VPN) -> false
[CMCBypass] OK: JSONObject.getBoolean VPN keys -> false
```
