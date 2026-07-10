Question Paper Reformatter (Android)
A small Android app that takes messy, inconsistently-aligned question paper text and outputs a cleanly formatted PDF:
First 2–3 heading lines → always centered, regardless of original alignment.
School logo (optional, pick any image) → placed to the left of the heading.
Date line → left aligned. Class line → left aligned, with Marks pushed to the right of that same row.
Part-I, Part-II, Section-A, etc. → centered.
Standalone Roman numeral sub-headings (I., II., ...) → bold + black, normal (left) alignment.
Every question number sits in a fixed column so all numbers line up.
Wrapped/broken lines are merged back together automatically.
How to build the APK via GitHub
Create a new GitHub repository (or use an existing one).
Upload everything in this folder, keeping the folder structure exactly as-is (including the hidden .github/workflows/build-apk.yml file).
Push to the main branch (or click Actions → Build APK → Run workflow to trigger it manually).
Wait for the "Build APK" workflow to finish (Actions tab).
Open the finished run → scroll to Artifacts → download question-reformatter-apk. Unzip it to get app-debug.apk.
Transfer that APK to your Android phone and install it (you'll need to allow "install from unknown sources" since it isn't from the Play Store).
Notes
This builds a debug APK (auto-signed with a debug key), which is fine for installing on your own device but not for publishing to the Play Store.
No external services or paid tools are required — the whole build runs on GitHub's free Actions minutes.
If you want to tweak the formatting rules (margins, font sizes, which keywords count as "date"/"class"/"marks", etc.), the logic lives in app/src/main/java/com/qreform/app/TextFormatter.kt and PdfBuilder.kt.