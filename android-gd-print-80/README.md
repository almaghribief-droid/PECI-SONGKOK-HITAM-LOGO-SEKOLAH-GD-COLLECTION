# GD PRINT

Android app for receiving PDF/JPG/PNG documents through Android Share, previewing them, and sending raster ESC/POS output to compatible thermal printers.

## Implemented in this branch
- Android Share / Open Document intake for PDF, JPG and PNG.
- On-screen preview before printing.
- Bluetooth Classic SPP, USB OTG bulk OUT, and Wi-Fi RAW TCP connections.
- Paper width presets: 58 mm, 80 mm, 112 mm, and Custom.
- Portrait and Landscape orientation.
- Raster image printing using ESC/POS `GS v 0`.

## Build
Open this directory as an Android Gradle project in Android Studio, or run:

```bash
gradle assembleDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`.

A GitHub Actions workflow builds this app when files in `android-gd-print-80/` change.

## Important test notes
- PDF input currently renders and prints the first page only.
- Bluetooth requires a paired Classic SPP printer.
- USB OTG requires a printer exposing a compatible USB bulk OUT endpoint.
- Wi-Fi printing uses a raw TCP socket; many network ESC/POS printers listen on port 9100, but this is device-dependent.
- Paper widths are requested raster widths, not automatic printer capability detection. Actual printable width and dot density vary by model. 112 mm may require a wide-format printer.
- RPP02N variants must be tested physically. A successful data transfer does not guarantee correct paper feed, darkness, scaling, or cutter behavior.
- Some marketplace apps do not expose a Share action for their receipts. In that case, save/download the document first and open it in GD PRINT.
- Start with a short test print before printing long documents.
