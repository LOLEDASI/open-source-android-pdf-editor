# open-source-pdf-editor

Free PDF editor for Android. Got tired of every PDF app wanting a subscription or an account just to sign something, so here's one that doesn't.

**[Download the APK](https://github.com/LOLEDASI/open-source-android-pdf-editor/releases/latest/download/open-source-pdf-editor.apk)** (Android 8+)

Open the file on your phone to install it. Android will probably ask you to allow installs from your browser first. New versions install over the old one.

## What it does

- open and read PDFs, zoom, search your recent files
- see every PDF on your phone in one list (needs "All files access", the app asks)
- draw / highlight / erase on pages
- edit text: tap a line to change it, or tap anywhere to add new text
- sign stuff: draw it, type it, or take a photo of your signature on paper
- fill in PDF forms
- reorder, rotate, duplicate, delete or extract pages
- merge, split, compress
- PDF to images and images to PDF
- scan documents with the camera, and the scans come out searchable (OCR, works offline)
- add or remove a password, watermark, flatten
- redact: actually removes the text, not just a black box on top
- works on "no editing" protected PDFs too
- saves as normal PDF annotations so they show up in other apps too

## Opening PDFs from other apps

Share a PDF, or a message with a PDF link in it, to **PDF Editor** and it saves a copy to `Downloads/PDF Editor` and opens it. Works with Finqalab notices: hit share on the notice and pick PDF Editor, it grabs the PDF link out of the message.

You can also paste a link (or the whole message) into the 🔗 button on the home screen.

## Privacy

No account, no ads, no tracking. Everything happens on your phone. The only time it touches the internet is to download a PDF from a link you opened.

## Building

Needs JDK 17 and the Android SDK.

    ./gradlew assembleDebug

## License

GPL-3.0
