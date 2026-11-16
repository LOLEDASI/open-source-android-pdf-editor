# PDF Editor for Android

A free, open-source PDF editor for Android. No account, no subscription, no ads, and your files never leave your phone.

Built with Kotlin and Jetpack Compose.

## What it does

- **Open and read PDFs** from any app or file manager ("Open with" works too), with smooth scrolling, pinch-to-zoom and double-tap zoom.
- **Recent files** with page thumbnails, search, stars and sorting.
- **Annotate** with a pen, highlighter and eraser. Choose colour, thickness and opacity, and undo or redo every change.
- **Sign** documents by drawing, typing, or taking a photo of your signature on paper (the paper is removed automatically). Save signatures and initials for reuse, then move and resize them on the page.
- **Saves real PDF annotations.** Ink is stored as standard Ink annotations and signatures as Stamp annotations, so they show up in other PDF apps and stay editable here. If a file can't be changed in place, the app saves a copy instead.

Coming next: page organizer (reorder, rotate, delete, extract), text boxes and shapes, form filling, merge/split/compress, scan to PDF.

## Privacy

Everything happens on the device. The app has no internet permission and sends nothing anywhere.

## Building

Requirements: JDK 17 and the Android SDK (API 36).

```bash
./gradlew assembleDebug
```

The APK ends up in `app/build/outputs/apk/debug/`. Install it on a device with:

```bash
./gradlew installDebug
```

### Tests

Unit tests run on your computer:

```bash
./gradlew testDebugUnitTest
```

UI tests (zoom, annotate-and-save, sign-and-save) need a connected device or emulator:

```bash
./gradlew connectedDebugAndroidTest
```

## Project layout

```
app/src/main/java/com/mobile/pdfeditor/
├── annot/       Annotation model, undo/redo, signature placement rules
├── data/        Recent files and saved signatures
├── pdf/         Page rendering (Android PdfRenderer) and reading/writing annotations (PdfBox-Android)
├── signature/   Turning drawn, typed and photographed signatures into images
└── ui/          Compose screens: home, viewer, signature sheet, tools, theme
```

## Known limitations

- Pages are drawn with Android's built-in `PdfRenderer`, which doesn't render annotations stored in the file. This app draws its own ink and signatures on top, but annotations made by other apps (for example their highlights or notes) aren't shown yet.
- Signatures are visual signatures, not cryptographic (certificate-based) digital signatures.
- Password-protected PDFs can't be opened yet.

## Contributing

Issues and pull requests are welcome. Please run the unit tests before opening a PR.

## License

[GNU General Public License v3.0](LICENSE). You can use, study, change and share this app. If you distribute a modified version, it must stay open source under the same license.

Uses [PdfBox-Android](https://github.com/TomRoush/PdfBox-Android) (Apache License 2.0).
