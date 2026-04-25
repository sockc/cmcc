# CMC Bypass LSPosed Module

最小 LSPosed 模块，用于绕过三星 `com.samsung.android.mdecservice` 对“中国 SIM + 非中国销售型号”的本地限制检测。

## 最简单编译方式

1. 新建 GitHub 仓库。
2. 把本项目所有文件上传到仓库根目录。
3. 打开仓库 `Actions`。
4. 运行 `Build APK`。
5. 下载 artifact：`CMCBypass-debug-apk`，里面就是 `app-debug.apk`。

## 手机端使用

1. 安装 `app-debug.apk`。
2. 打开 LSPosed。
3. 启用 `CMC Bypass`。
4. 作用域只勾选：`com.samsung.android.mdecservice`。
5. 重启手机。
6. 打开「设置 → 已连接的设备 → 在其他设备上接打电话和收发短信」。

## 验证日志

```bash
su -c 'logcat -d -v time' | grep -iE 'CMCBypass|mdec|ChinaSim|isChinaSimPresent'
```

正常应看到：

```text
[CMCBypass] Loaded in com.samsung.android.mdecservice
[CMCBypass] OK: isChinaSimPresentInGlobalPD(Context) -> false
```
