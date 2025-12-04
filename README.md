# Build & Run Instructions for Mobile-AI-Assistant

Step-by-step guide to build and run the project on an Arm-based Android device.

---

## 📥 Quick Install (Pre-built APK)

Don't want to build from source? Download the pre-built APK directly:

[![Download APK](https://img.shields.io/badge/Download-APK%20v0.0.1-green?style=for-the-badge&logo=android)](https://github.com/GGUFloader/Mobile-AI-Assistant/releases/download/0.0.1/mobile.ai.assistant.beta.v.0.0.1.apk)

**Direct Download Link:**
```
https://github.com/GGUFloader/Mobile-AI-Assistant/releases/download/0.0.1/mobile.ai.assistant.beta.v.0.0.1.apk
```

### ⚠️ Before Installing APK

**Disable Google Play Protect temporarily:**

1. Open **Settings** on your Android device
2. Go to **Google** → **Security** (or search for "Play Protect")
3. Tap **Google Play Protect**
4. Tap the **Settings gear icon** (top right)
5. Turn OFF **"Scan apps with Play Protect"**
6. Install the APK
7. (Optional) Re-enable Play Protect after installation

> **Why?** The APK is not signed with a Play Store certificate, so Play Protect may block installation. The app is open-source and safe — you can verify the code yourself.

After installing, skip to [Step 6: Download a GGUF Model](#step-6-download-a-gguf-model) to get started!

---

## 🛠️ Build from Source

If you prefer to build the app yourself, follow the steps below.

## Prerequisites

Before you begin, ensure you have the following installed:

### Required Software
| Software | Version | Download |
|----------|---------|----------|
| Android Studio | Hedgehog (2023.1.1) or newer | [Download](https://developer.android.com/studio) |
| Android NDK | 25.0 or newer | Via Android Studio SDK Manager |
| CMake | 3.22.1+ | Via Android Studio SDK Manager |
| Git | Any recent version | [Download](https://git-scm.com/) |

### Hardware Requirements
- **Development Machine**: Windows, macOS, or Linux with 8GB+ RAM
- **Target Device**: Arm64 Android device (arm64-v8a architecture)
  - Android 8.0 (API 26) or higher
  - Minimum 2GB RAM (4GB+ recommended)
  - ~500MB free storage for app + model

---

## Step 1: Clone the Repository

Open a terminal and run:

```bash
git clone https://github.com/GGUFloader/Mobile-AI-Assistant.git
cd Mobile-AI-Assistant
```

---

## Step 2: Install Android SDK Components

1. Open Android Studio
2. Go to **Tools → SDK Manager**
3. In the **SDK Tools** tab, ensure these are installed:
   - ✅ Android SDK Build-Tools
   - ✅ NDK (Side by side) - version 25.0+
   - ✅ CMake - version 3.22.1+
4. Click **Apply** to install any missing components

---

## Step 3: Open Project in Android Studio

1. Launch Android Studio
2. Select **File → Open**
3. Navigate to the `Mobile-AI-Assistant` folder and click **OK**
4. Wait for Gradle sync to complete (this may take a few minutes on first run)
   - **Note:** Gradle automatically downloads llama.cpp during sync — no manual cloning required!

If you see any errors about missing SDK components, Android Studio will prompt you to install them.

---

## Step 4: Build the Project

### Option A: Using Android Studio (Recommended)

1. Connect your Arm64 Android device via USB
2. Enable **USB Debugging** on your device:
   - Go to Settings → About Phone → Tap "Build Number" 7 times
   - Go to Settings → Developer Options → Enable USB Debugging
3. Select your device from the device dropdown in Android Studio
4. Click the **Run** button (green play icon) or press `Shift+F10`

### Option B: Using Command Line

```bash
# On Windows
gradlew.bat assembleRelease

# On macOS/Linux
./gradlew assembleRelease
```

The APK will be generated at:
```
app/build/outputs/apk/release/app-release.apk
```

---

## Step 5: Install the APK

### If built via Android Studio:
The app is automatically installed when you click Run.

### If built via command line:

1. Connect your device via USB with USB Debugging enabled
2. Run:
```bash
adb install app/build/outputs/apk/release/app-release.apk
```

### Manual Installation:
1. Copy the APK to your device
2. Open a file manager on your device
3. Navigate to the APK and tap to install
4. You may need to enable "Install from Unknown Sources"

---

## Step 6: Download a GGUF Model

Choose a model based on your device's RAM:

| Model | Size | RAM Needed | Best For | Download |
|-------|------|------------|----------|----------|
| **Dhi Qwen2.5 0.5B Q4** ⭐ | ~400MB | 512MB | Testing, budget phones | [Download](https://huggingface.co/mathanmithun/DhiGrowth_FAQ_RAG/resolve/main/models/Dhi_Qwen2P5_0_5B_Q4_K_M.gguf) |
| TinyLlama 1.1B Q4 | 637MB | 1GB | General chat | [Download](https://huggingface.co/TheBloke/TinyLlama-1.1B-Chat-v1.0-GGUF/resolve/main/tinyllama-1.1b-chat-v1.0.Q4_K_M.gguf) |
| Phi-2 Q4 | 1.6GB | 2GB | Reasoning tasks | [HuggingFace](https://huggingface.co/TheBloke/phi-2-GGUF) |
| Gemma 2B Q4 | 1.4GB | 2GB | Balanced performance | [HuggingFace](https://huggingface.co/google/gemma-2b-it-GGUF) |

**Download the recommended model for testing:**
```bash
# Download directly (if you have wget)
wget https://huggingface.co/mathanmithun/DhiGrowth_FAQ_RAG/resolve/main/models/Dhi_Qwen2P5_0_5B_Q4_K_M.gguf

# Or use curl
curl -L -o Dhi_Qwen2P5_0_5B_Q4_K_M.gguf https://huggingface.co/mathanmithun/DhiGrowth_FAQ_RAG/resolve/main/models/Dhi_Qwen2P5_0_5B_Q4_K_M.gguf
```

---

## Step 7: Transfer Model to Device

```bash
# Transfer the model to your device's Download folder
adb push Dhi_Qwen2P5_0_5B_Q4_K_M.gguf /sdcard/Download/
```

**Alternative**: Download the model directly on your phone using a browser, or transfer via cloud storage/file sharing.

---

## Step 8: Run the App

1. **Open the app** on your device (look for "LocalChatbot")

2. **Grant permissions** when prompted:
   - Storage access (to load models)
   - Overlay permission (for floating assistant - optional)

3. **Load a model**:
   - Tap the **folder icon** in the top bar
   - Navigate to `/Download/` folder
   - Select your downloaded `.gguf` model file
   - Wait for the model to load (progress bar will show)

4. **Start chatting!**
   - Type a message in the input field
   - Tap the send button
   - Watch the AI response stream in real-time

---

## Step 9: Try Advanced Features

### Floating Assistant
1. Toggle **"Floating Button"** switch in the main screen
2. Grant overlay permission when prompted
3. A floating bubble appears — tap it from any app to chat with AI
4. Drag the bubble to reposition, drag to top to dismiss

### Text Selection AI
1. Select any text in any app (browser, notes, etc.)
2. Tap **"Ask AI"** from the context menu
3. View AI analysis in a popup
4. Copy the response to clipboard

### Resource Monitoring
- Tap the **stats icon** to see real-time CPU and memory usage
- Useful for checking if your device can handle larger models

---

## Troubleshooting

### Build Errors

**"NDK not found"**
- Open SDK Manager → SDK Tools → Install NDK (Side by side)

**"CMake not found"**
- Open SDK Manager → SDK Tools → Install CMake 3.22.1+

**Gradle sync failed**
- Try: File → Invalidate Caches → Invalidate and Restart

### Runtime Issues

**App crashes when loading model**
- Model may be too large for your device's RAM
- Try a smaller model (Dhi Qwen 0.5B recommended)

**"Model not found" error**
- Ensure the model file is in a readable location (`/sdcard/Download/`)
- Check that storage permission is granted

**Slow inference**
- This is normal for larger models on budget devices
- Try a smaller quantized model (Q4 instead of Q8)
- Close other apps to free up RAM

**Floating button not appearing**
- Go to Settings → Apps → LocalChatbot → Display over other apps → Allow

---

## Verification Checklist

After completing all steps, verify:

- [ ] App installs and opens without crashing
- [ ] Model loads successfully (progress bar completes)
- [ ] Chat responses generate with streaming text
- [ ] Floating assistant appears when enabled
- [ ] Text selection "Ask AI" works in other apps
- [ ] Resource monitor shows CPU/memory stats

---

## Need Help?

- **GitHub Issues**: [Report a bug](https://github.com/GGUFloader/Mobile-AI-Assistant/issues)
- **Model Compatibility**: Most GGUF models from HuggingFace should work

---

<p align="center">
  Built for <strong>Arm AI Developer Challenge 2025</strong>
</p>
