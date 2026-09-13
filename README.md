# GDialer Tweak

An LSPosed module for Google Phone with two independently configurable features:

- **Keep the current app after answering** — routes the notification's Answer action through Google Phone's receiver, then blocks its automatic in-call activity launch. Google Phone still opens when answering on the lock screen or launcher, and when you intentionally tap the ongoing-call notification.
- **Enable call recording** — enables Google Phone's own built-in call-recording eligibility path. This does not remove or silence Google's recording disclosure.

The companion app also provides a carrier-free **test incoming call** through an Android Telecom test calling account.
Its settings screen uses Material Design 3, Android dynamic color, and edge-to-edge system-bar insets.

## Tested target

- Xiaomi 14 (`houji`)
- HyperOS 2 / Android 15 (API 35)
- Google Phone `236.0.969488611-pixel` (`versionCode 20031818`)
- LSPosed

The in-call behavior and recording implementation hook R8-obfuscated Google Phone classes verified for the version above. A Google Phone update can change those names; check the LSPosed log for `GDialerTweak` messages after updating.

The recommended LSPosed scope includes both **Google Phone (`com.google.android.dialer`)** and **Xiaomi InCallUI (`com.android.incallui`)**. HyperOS can bind and launch its stock in-call UI even while Google Phone holds the default dialer role; the second scope lets the module block that Xiaomi activity launch. A lifecycle hook returns it to the background as a fallback.

## Install and configure

1. Build and install the debug APK:

   ```powershell
   .\gradlew.bat :app:assembleDebug
   adb install -r .\app\build\outputs\apk\debug\app-debug.apk
   ```

2. Enable GDialer Tweak in LSPosed and select both recommended scopes: Google Phone and Xiaomi InCallUI.
3. Open GDialer Tweak and choose each feature independently.
4. Open the configuration screen once from LSPosed, then reboot after enabling the module or changing its LSPosed scope. For development, force-stopping both scoped packages is sufficient.

The installed debug APK is signed with the standard Android debug key.

## Test incoming call

1. In GDialer Tweak, tap **Enable test calling account**.
2. Enable **GDialer Tweak test calls** on Android's calling-account screen.
3. Return to the module and tap **Simulate incoming call**.

The test call is created locally with `ConnectionService` and does not contact a mobile carrier. Answering it changes the simulated connection to active; hanging up destroys it.

## Behavior and limitations

- Notification Answer uses Google Phone's receiver-only path and does not create `InCallActivity`. Automatic post-answer launches are blocked as a fallback.
- Tapping the ongoing-call notification is explicitly marked as an intentional UI launch and clears suppression.
- If HyperOS subsequently tries to launch its own `com.android.incallui/.InCallActivity`, that launch is blocked while Google Phone is the default dialer.
- Full-screen lock-screen and launcher answer flows remain visible.
- If Android cannot determine the foreground task, the module fails open and leaves Google Phone visible.
- Call recording availability still depends on Google Phone containing the recorder implementation and on the device audio path working. The module only changes eligibility decisions.
- Recording calls may be regulated or prohibited depending on jurisdiction. The user is responsible for consent and compliance.

## Diagnostics

Filter the LSPosed log for `GDialerTweak`. On the tested Google Phone build, successful startup includes:

```text
receiver-only notification answer hook installed
notification content marker hook installed
internal InCallActivity launch blocker installed
Xiaomi InCallActivity suppressor installed
Xiaomi InCallActivity launch blocker installed
recording gate hooked: kxt.a
recording gate hooked: kwz.a
loaded in Google Phone
```

If either recording gate is reported as unavailable after a Google Phone update, the recording hook needs a new version mapping.
