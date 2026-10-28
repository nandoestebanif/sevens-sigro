# Claves que necesita el Info.plist del proyecto de Xcode

Al crear el proyecto `iosApp` en Xcode (ver `android/LEEME.md`), agrega estas claves a su `Info.plist`:

```xml
<!-- Compose Multiplatform lo exige en iOS -->
<key>CADisableMinimumFrameDurationOnPhone</key>
<true/>

<!-- Solo desarrollo: permite http hacia la API local. El ambiente de pruebas va por https. -->
<key>NSAppTransportSecurity</key>
<dict>
    <key>NSAllowsLocalNetworking</key>
    <true/>
</dict>
```
