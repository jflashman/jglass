# Third-party notices

The iPhone companion diagnostic uses Apple system frameworks only and adds no
third-party package or application code.

The Android build resolves third-party libraries transitively through the
Android Gradle Plugin. Security version floors for those host-side tools are
documented in `SECURITY.md`; their binaries are downloaded into the developer or
CI Gradle cache and are not copied into the repository or packaged in the Glass
APK.

Repository links in planning documents are references and do not indicate that
their code has been incorporated.

## Kartik Gupta J.A.R.V.I.S loader screen

- Upstream: https://codepen.io/Kartik-Gupta-the-typescripter/pen/PorbjBV
- Revision: CodePen does not expose a Git commit for this Pen. HTML and CSS were
  retrieved from the public editor on 2026-08-18 and canonically joined as
  `HTML + "\n---CSS---\n" + CSS`.
- Retrieved editor export SHA-256:
  `22c31ae98eb1b50f6ef2f9e78d7ff3a6cef5e2cdd818db6aabfeab2d7b0d5f6b`
- License: MIT; CodePen applies MIT to public Pens. Preserved text:
  `firmware/xe24/branding/LICENSE-KARTIK-GUPTA-LOADER.txt`
- Copyright: Copyright (c) 2023 Kartik Gupta
- Used in: `apps/glass/src/main/java/com/jglass/diagnostic/StartupLoaderView.java`
  and `firmware/xe24/branding/BootAnimationGenerator.java`
- Material used: concentric 344-coordinate SVG ring proportions, cyan/yellow
  palette, segmented arcs, and 7/4/3/2-second alternating rotations.
- Modifications: reimplemented with dependency-free Android `Canvas` and Java2D;
  simplified SVG masks into stroked arcs, added explicit counter-rotation,
  scaled for 640×360, removed hover behavior, and added original JGlass text.
- Verification date: 2026-08-18

## Open-Meteo weather data

- Upstream: https://open-meteo.com/
- License: CC-BY-4.0, https://open-meteo.com/en/license
- Used in: runtime weather summaries from `services/bridge/`
- Material used: current/daily forecast data only; no source or assets copied
- Attribution displayed on Glass: `Weather data by Open-Meteo.com`
- Modifications: temperatures are requested in Fahrenheit and values are rounded
  into a compact spoken/visual summary.
- Verification date: 2026-08-18

## Gradle Wrapper 8.13

- Upstream: https://github.com/gradle/gradle
- Commit: `073314332697ba45c16c0a0ce1891fa6794179ff` (`v8.13.0`)
- License: Apache-2.0, https://github.com/gradle/gradle/blob/v8.13.0/LICENSE
- Copyright: Gradle, Inc. and Gradle contributors
- Used in: `gradlew`, `gradlew.bat`, and `gradle/wrapper/`
- Material used: generated wrapper launch scripts and wrapper JAR
- Modifications: distribution URL and official SHA-256 checksum are pinned for Gradle 8.13
- Wrapper JAR SHA-256: `81a82aaea5abcc8ff68b3dfcb58b3c3c429378efd98e7433460610fecd7ae45f`
- Verification date: 2026-08-17
