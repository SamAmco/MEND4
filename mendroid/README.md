# MEND Android
You will need to read [the project README](../README.md) and have created a `config.xml` file using the setup process described in the [commandline application README](../desktop-mend/README.md) to use this app. You can build the app yourself or get the apk from the [releases page](https://github.com/SamAmco/MEND4/releases)

## Building a signed release APK

The app targets Android 17 (API 37) and supports Android 12 (API 31) and newer.
Use JDK 25 and install Android SDK Platform 37 through Android Studio's SDK Manager.
Set `sdk.dir` in the root `local.properties` to your Android SDK directory if it is
not already configured. Use the included Gradle wrapper for the commands below.

The `release` variant uses package name `co.samco.mendroid`, is not debuggable,
and is signed using your own keystore. The current version is **5.7.0**
(`versionCode` **50700**). Cloud backups are disabled, and the extraction rules
exclude all app data from both cloud backups and device transfers.

1. Keep your JKS file outside the repository. Find its key alias if needed:

   ```sh
   keytool -list -keystore /absolute/path/to/your-key.jks
   ```

2. Add these entries to the existing root `local.properties`, preserving `sdk.dir`
   and any other settings:

   ```properties
   MENDROID_STORE_FILE=/absolute/path/to/your-key.jks
   MENDROID_STORE_PASSWORD=your-keystore-password
   MENDROID_KEY_ALIAS=your-key-alias
   MENDROID_KEY_PASSWORD=your-key-password
   ```

   The key password may be the same as the keystore password. This file is ignored
   by version control; restrict access with `chmod 600 local.properties`.
   Values use Java properties syntax, so backslashes must be escaped.
   Alternatively, supply the same names as environment variables or Gradle
   properties (for example in `~/.gradle/gradle.properties`). Gradle properties
   take precedence over environment variables, then `local.properties`.

3. From the repository root, build and check the release:

   ```sh
   ./gradlew :mendroid:assembleRelease :mendroid:lintRelease :mendroid:testReleaseUnitTest
   ```

   The signed APK is written to:

   ```text
   mendroid/build/outputs/apk/release/mendroid-release.apk
   ```

   All four signing settings must be provided to produce a signed release.
   Without them, Gradle produces an unsigned APK (`mendroid-release-unsigned.apk`).
   To produce a signed Android App Bundle instead, run `./gradlew :mendroid:bundleRelease`.

4. Install the APK over ADB:

   ```sh
   adb install -r mendroid/build/outputs/apk/release/mendroid-release.apk
   ```

The normal debug build uses `co.samco.mendroid.debug`, so release installs alongside
it with separate app data. Import your `config.xml` and select your encrypted-data
folder in the release app. Keep the debug app until you have checked decryption in
the release app. Update your existing debug installation too, or remove it after
migrating, so its installed copy also stops participating in backups. Updating an
existing installation of `co.samco.mendroid` requires
the same signing key as that installation; keep using and securely retain your JKS.

In the unlocked notification, tap the body to open MEND, or use the **Lock MEND**
action to lock immediately.

Before relying on a new build, check unlocking, log encryption/decryption, opening
decrypted attachments, photo/video capture, audio recording/playback, notification
lock actions, and system back navigation on your device. Build and lint checks do
not exercise those device interactions.

## Development checks

```sh
./gradlew :mendroid:assembleDebug :mendroid:lintDebug :mendroid:testDebugUnitTest --warning-mode all
```

Kotlin and lint warnings fail the Android build. The app uses Material 3 throughout,
with no lint suppressions or dependency downgrades for the migration.

KSP 2.3.12 currently emits a Gradle 9.8 deprecation warning for its internal use of
`Configuration.setVisible`. This comes from the latest KSP plugin, rather than app code.
 
<p align="center">
	<img src="./screenshots/1.png" width="200px" />
	<img src="./screenshots/2.png" width="200px" />
	<img src="./screenshots/3.png" width="200px" />
	<img src="./screenshots/4.png" width="200px" />
	<img src="./screenshots/5.png" width="200px" />
	<img src="./screenshots/6.png" width="200px" />
	<img src="./screenshots/7.png" width="200px" />
</p>
