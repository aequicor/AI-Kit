# ADR-001 — PDF Rendering Library Choices

**Status:** Accepted  
**Date:** 2026-05-12  
**Milestone:** M1 — PDF Viewer Scaffold

## Context

The app renders PDF files on Android, Desktop (JVM), iOS, and Web (JS/wasmJs). Each target has different available APIs and constraints. The `PdfRenderer` class is an `expect`/`actual` in `shared`, returning a platform-agnostic `PdfPageImage` (ARGB_8888 byte buffer). Conversion to `ImageBitmap` for Compose UI happens in `composeApp`.

## Decision

| Platform | Library | Integration |
|----------|---------|-------------|
| Android | `android.graphics.pdf.PdfRenderer` (API 21+) | Built-in; `ParcelFileDescriptor` + `Bitmap.ARGB_8888` |
| JVM Desktop | Apache PDFBox 3.x (`org.apache.pdfbox:pdfbox:3.0.3`) | `Loader.loadPDF` + `PDFRenderer.renderImageWithDPI` → `BufferedImage` |
| iOS | PDFKit (`platform.PDFKit.*`) | System framework via K/N cinterop; `UIGraphicsBeginImageContextWithOptions` + `CGDataProviderCopyData` |
| JS/wasmJs | PDF.js (stub for M1) | `NotImplementedError` — deferred to M1-web patch |

## Rationale

### Android — `android.graphics.pdf.PdfRenderer`
- Zero extra dependencies; no APK size impact.
- API 21+ covers 100 % of the declared `minSdk`.
- Renders text with system-level quality (hardware-accelerated on modern devices).

### JVM Desktop — Apache PDFBox 3.x
- Pure-Java, no native `.so` or JNI; runs on all desktop OSes without additional setup.
- Apache 2.0 license — compatible with this project.
- `renderImageWithDPI` gives sharp text at arbitrary DPI; DPI derived from requested pixel size.
- PDFBox 3.x drops the deprecated `PDDocument.load()` in favour of `Loader.loadPDF()`.

### iOS — PDFKit
- System framework available since iOS 11; no extra dependency, no App Store review risk.
- Accessed via `platform.PDFKit` in Kotlin/Native without a custom `.def` file.
- Renders via `page.drawWithBox` into a `UIGraphicsImageContext`; pixel data extracted with `CGDataProviderCopyData`. Byte order is BGRA premultiplied (standard CGImage format).

### JS/wasmJs — PDF.js (deferred)
- PDF.js is the only viable option for browser rendering.
- Requires JS interop scaffolding (`dynamic` calls or typed bindings) and a bundler step to include the worker script.
- Deferred to M1-web to keep M1 scope manageable. Current stub throws `NotImplementedError` so compilation succeeds on all targets.

## Rejected Alternatives

| Option | Platform | Reason rejected |
|--------|----------|-----------------|
| Pdfium (via NDK) | Android | Requires native `.so` redistribution; APK size +10–20 MB; revisit if render quality is insufficient |
| PDF.js via JCEF | Desktop | JCEF adds ~100 MB runtime; overkill when PDFBox suffices |
| iText / PdfBox | iOS | Not available for Kotlin/Native targets; PDFKit is the only viable option |
| Skia PDF parser | All | Not exposed in Compose Multiplatform's public API surface |

## Pixel Format Notes

`PdfPageImage.pixels` byte layout differs per platform by necessity:

- **Android / JVM:** Big-endian packed ARGB ints — `[A, R, G, B]` per pixel — produced by `ByteBuffer.putInt(int)` from `Bitmap.getPixels()` / `BufferedImage.getRGB()`.
- **iOS:** BGRA premultiplied bytes — `[B, G, R, A]` per pixel — produced by `CGDataProviderCopyData` from a `UIGraphicsBeginImageContextWithOptions` context (standard Core Graphics default).

Conversion to `ImageBitmap` in `composeApp` is handled by `PdfPageImage.toImageBitmap()` (`expect`/`actual`) which accounts for these differences per platform.

## Consequences

- `shared` remains free of any Compose UI dependency.
- The `PdfRenderer` contract (`openDocument` / `renderPage` / `closeDocument`) is stable for M2+.
- PDF.js integration will require additional work in M1-web; the stub keeps the build green in the meantime.
- If Android render quality is insufficient, Pdfium can replace the Android actual without touching other platforms.
