# GDialer Tweak

An LSPosed module for Google Phone with two independently configurable features:

- **Keep the current app after answering** — when an incoming call is answered from its notification, Google Phone's in-call activity is moved behind the app you were using. Google Phone still opens when the answer happens on the lock screen or while the launcher is foreground.
- **Enable call recording** — enables Google Phone's own built-in call-recording eligibility path. This does not remove or silence Google's recording disclosure.

The companion app also provides a carrier-free **test incoming call** through an Android Telecom test calling account.

## Tested target

- Xiaomi 14 (`houji`)
- HyperOS 2 / Android 15 (API 35)
- Google Phone `236.0.969488611-pixel` (`versionCode 20031818`)
- LSPosed

The in-call behavior uses stable Google Phone component names. The recording implementation additionally hooks R8-obfuscated eligibility classes verified for the version above. A Google Phone update can change those names; check the LSPosed log for `GDialerTweak` messages after updating.

Xiaomi's stock `com.android.incallui` is intentionally not hooked. The recommended LSPosed scope is **Google Phone (`com.google.android.dialer`) only**.

## Install and configure

1. Build and install the debug APK:

   ```powershell
   .\gradlew.bat :app:assembleDebug
   adb install -r .\app\build\outputs\apk\debug\app-debug.apk
   ```

2. Enable GDialer Tweak in LSPosed and select Google Phone as its scope.
3. Open GDialer Tweak and choose each feature independently.
4. Open the configuration screen once from LSPosed, then force-stop Google Phone or reboot after enabling the module or changing its LSPosed scope.

The installed debug APK is signed with the standard Android debug key.

## Test incoming call

1. In GDialer Tweak, tap **Enable test calling account**.
2. Enable **GDialer Tweak test calls** on Android's calling-account screen.
3. Return to the module and tap **Simulate incoming call**.

The test call is created locally with `ConnectionService` and does not contact a mobile carrier. Answering it changes the simulated connection to active; hanging up destroys it.

## Behavior and limitations

- Only notification answer actions are marked for backgrounding. Full-screen answer UI actions do not take this path.
- If Android cannot determine the foreground task, the module fails open and leaves Google Phone visible.
- Call recording availability still depends on Google Phone containing the recorder implementation and on the device audio path working. The module only changes eligibility decisions.
- Recording calls may be regulated or prohibited depending on jurisdiction. The user is responsible for consent and compliance.

## Diagnostics

Filter the LSPosed log for `GDialerTweak`. On the tested Google Phone build, successful startup includes:

```text
recording gate hooked: kxt.a
recording gate hooked: kwz.a
loaded in Google Phone
```

If either recording gate is reported as unavailable after a Google Phone update, the recording hook needs a new version mapping.
