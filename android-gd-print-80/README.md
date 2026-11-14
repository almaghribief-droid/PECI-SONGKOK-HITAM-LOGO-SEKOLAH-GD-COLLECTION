# GD PRINT 80

Android Bluetooth Classic ESC/POS receipt printer app, initial prototype.

## Current prototype
- Lists Bluetooth devices already paired in Android settings.
- Connects using the Bluetooth Serial Port Profile (SPP) UUID.
- Sends ESC/POS test-print bytes.
- Prints a simple 80 mm sales receipt.

## Build
Open this folder as an Android Gradle project in Android Studio, or run:

```bash
gradle assembleDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`.

A GitHub Actions workflow is included at `.github/workflows/build-apk.yml`.

## Important limitations
This is an initial prototype, not yet field-tested. Pair the printer in Android Bluetooth settings first. RPP02N variants may differ in Bluetooth profile, paper width, code page, and cutter support. The receipt currently uses a basic ESC/POS text layout; shipment-label formats from Shopee or POS Indonesia are not implemented yet. Test with a small amount of paper before routine use.
