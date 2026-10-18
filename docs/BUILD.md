# 本地构建说明

## 环境
- JDK 17（如 Temurin 17）
- Android SDK：platform `android-35`、build-tools 34.0.0（AGP 8.5.2 会自动下载缺失组件）
- 首次构建需要联网下载 Gradle 8.9 与依赖（Google Maven / Maven Central）

## 步骤
```bash
cd app-src
echo "sdk.dir=/path/to/Android/sdk" > local.properties
./gradlew :app:testDebugUnitTest      # 单元测试（27 项）
./gradlew :app:assembleDebug          # 调试包：app/build/outputs/apk/debug/
./gradlew :app:assembleRelease        # 发布包（需签名配置，见下）
```

## 签名
release 签名从项目外的 `../signing/keystore.properties` 读取（也可用环境变量 `GEEKRADAR_SIGNING_PROPS` 指定路径）：
```
storeFile=geekradar-release.p12
storePassword=…
keyAlias=geekradar
keyPassword=…
```
钥匙与密码不在源码包内。没有该文件时 `assembleRelease` 生成未签名包；自行生成钥匙：
`keytool -genkeypair -storetype PKCS12 -keystore geekradar-release.p12 -alias geekradar -keyalg RSA -keysize 3072 -validity 10000`
注意：以后更新应用必须使用同一把钥匙，否则手机会拒绝覆盖安装。

## 开发测试用的 mock 服务
`tools/mock_server.py` 模拟 DeepSeek 接口与测试网页（Key 为 `sk-mock-good`）：
`python3 tools/mock_server.py 8787`，模拟器内 Base URL 填 `http://10.0.2.2:8787`。
