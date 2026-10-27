name: Build & Release APK

on:
  push:
    branches: [ main ]
    tags:
      - 'v*'
  release:
    types: [published]
  workflow_dispatch:

permissions:
  contents: write

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'

      - name: Setup Gradle
        uses: gradle/actions/setup-gradle@v3
        with:
          gradle-version: '8.10.2'

      - name: Ensure Stable Keystore
        run: |
          mkdir -p ~/.android
          if [ ! -f ~/.android/debug.keystore ]; then
            keytool -genkeypair -alias androiddebugkey -keypass androiddebugkey -keystore ~/.android/debug.keystore -storepass androiddebugkey -dname "CN=Android Debug,O=Android,C=US" -validity 10000 -keyalg RSA -keysize 2048
          fi

      - name: Generate Wrapper if missing
        run: |
          if [ ! -f "gradlew" ]; then
            gradle wrapper --gradle-version 8.10.2
          fi
          chmod +x gradlew

      - name: Build Debug APK
        run: ./gradlew assembleDebug --stacktrace

      # 1. Reads versionName from app/build.gradle.kts and creates versioned name
      - name: Rename APK with Version
        run: |
          # Extract version (e.g. 1.7) from app/build.gradle.kts
          VERSION=$(grep -o 'versionName = "[^"]*"' app/build.gradle.kts | cut -d'"' -f2 | sed 's/^v//')
          echo "Detected version: $VERSION"
          
          # Create RetroTV-v1.7.apk
          cp app/build/outputs/apk/debug/app-debug.apk "app/build/outputs/apk/debug/RetroTV-v${VERSION}.apk"
          
          # Create RetroIPTV.apk (for in-app updater)
          cp app/build/outputs/apk/debug/app-debug.apk "app/build/outputs/apk/debug/RetroIPTV.apk"

      # 2. Uploads both APKs to GitHub Artifacts
      - name: Upload Artifact
        uses: actions/upload-artifact@v4
        with:
          name: RetroTV-APKs
          path: app/build/outputs/apk/debug/*.apk

      # 3. Attaches both files to your GitHub Release
      - name: Upload APKs to Release
        if: startsWith(github.ref, 'refs/tags/') || github.event_name == 'release'
        uses: softprops/action-gh-release@v2
        with:
          files: |
            app/build/outputs/apk/debug/RetroTV-v*.apk
            app/build/outputs/apk/debug/RetroIPTV.apk
          tag_name: ${{ github.event.release.tag_name || github.ref_name }}
        env:
          GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
