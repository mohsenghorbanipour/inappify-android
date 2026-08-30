# Native sample application

The `app` module is a minimal host for testing the public Android SDK API. It
supports:

- configuring Inappify for Cafe Bazaar;
- refreshing and rendering customer information;
- refreshing and rendering all offerings and packages;
- starting a Bazaar purchase from a returned product;
- reconciling interrupted Bazaar purchases with `syncPurchases()`.

## Local configuration

Keep application keys and signing credentials out of source control. Copy the
required entries from `sample-config.properties.example` into the existing
root `local.properties` file. Preserve its `sdk.dir` entry.

The keys may also be pasted into the sample UI at runtime. Build-time values
only prefill those fields; neither value is written to the operation log.

For a production-like Bazaar payment test, the following values must belong to
the same application registration:

1. `inappify.sample.applicationId`
2. `inappify.sample.apiKey`
3. `inappify.sample.bazaarPublicKey`
4. the signing certificate, when Bazaar validates it
5. the products returned by the Inappify offering

Prefer registering a dedicated test application in Inappify and Cafe Bazaar.
If the configured application ID and signing certificate match an application
already installed on the device, Android treats the sample as an update to that
application. Use a dedicated registration, uninstall the existing application,
or provide a compatible `inappify.sample.versionCode`; otherwise installation
can fail with `INSTALL_FAILED_VERSION_DOWNGRADE`.

## Run

Open the repository root, not the `sdk` subdirectory, in Android Studio. Set
the Gradle JDK to 17, run **Sync Project with Gradle Files**, select the `app`
configuration, and run it on a physical Android device with Cafe Bazaar
installed and a test account signed in.

The same debug APK can be produced with:

```shell
./gradlew :app:assembleDebug
```

In the application:

1. enter or verify the Inappify and Bazaar public keys;
2. optionally enter the existing Inappify app-user identifier;
3. tap **Configure**;
4. refresh **Customer Info** and **Offerings**;
5. use the purchase button generated under the desired server product;
6. use **بازیابی خریدها (Sync Purchases)** after an interrupted verification.

The screen intentionally omits raw customer identifiers, store references,
receipts, purchase tokens, and payment URLs.

Open the repository root rather than the `sdk` directory so Android Studio can
load the shared version catalog and repository configuration.
