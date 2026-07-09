Place Windows VLC runtime files in this directory before building distribution.

Required files and folders:
- libvlc.dll
- libvlccore.dll
- plugins/ (directory from VLC installation)

Then run:
gradlew :desktop:windowsShadowDist
