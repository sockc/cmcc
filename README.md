# CMC Bypass v4

LSPosed 模块，用于测试绕过三星 MDEC / CMC 的以下限制：

- 中国 SIM 检测
- VPN 检测
- 伪装 Wi-Fi / same_wifi_network
- 尝试强制 callforking / call_activation / active_services 为可用

## 构建

上传到 GitHub 仓库根目录后，进入 Actions 运行 `Build APK`。

构建产物：`CMCBypass-v4-debug-apk` → `app-debug.apk`

## LSPosed 作用域

建议先勾选：

- `com.samsung.android.mdecservice`
- `com.google.android.gms`
- `com.samsung.android.galaxycontinuity`

如果设备上存在，也可以勾选：

- `com.samsung.android.mcfserver`
- `com.samsung.android.mcfds`

查询命令：

```bash
su -c 'pm list packages | grep -iE "mcf|continuity|nearby|mdec"'
```

## 验证日志

```bash
su -c 'logcat -d -v time' | grep -iE 'CMCBypass-v4|same_wifi|vpn_state|NetworkCapabilities|callfork|pcscf|mdec'
```
