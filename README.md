# BChat 🚀
> **Offline Peer-to-Peer (P2P) Mesh Chat, High-Speed File Sharing & End-to-End Encryption for Android**

BChat is a modern, privacy-first Android application designed for offline communication and file transfers without cellular data, active internet, or centralized servers.

---

## 🌟 Key Features

- ⚡ **Tri-Transport Offline Engine**:
  - **Nearby Share (Google Play Services Nearby Connections API)**: Automatic high-bandwidth Wi-Fi Direct, Hotspot, and BLE channel negotiation (up to 40+ MB/s).
  - **Wi-Fi Hotspot & Local Sockets (TCP/UDP + NSD)**: Native TCP socket server (port 8888) with mDNS zero-configuration network discovery.
  - **Bluetooth Classic (RFCOMM)**: Zero-infrastructure fallback working anywhere even with Wi-Fi disabled.
- 🔒 **End-to-End Encryption (E2EE)**:
  - **ECDH** (NIST P-256 / `secp256r1`) Elliptic Curve Diffie-Hellman Key Exchange.
  - **AES-256-GCM** Authenticated encryption with unique 12-byte IVs for each message.
  - In-person **Safety Number (6-digit fingerprint)** & QR code visual verification.
- 💬 **Rich Offline Messaging**:
  - 1-on-1 direct encrypted chat with delivery receipts (Sending, Sent, Delivered, Read).
  - Group broadcast mesh channel (`📢 Mesh Broadcast`).
- 📁 **Turbo File Sharing**:
  - Transfer Photos, Videos, Audio, Documents, and Android APKs with real-time transfer progress, speed meter (MB/s), and organized download library.
- 📷 **QR Code Instant Connect**:
  - CameraX + ML Kit barcode scanner for 1-second pairing without manual searching.
- 🎨 **Modern Jetpack Compose UI**:
  - Material 3 Neon & Slate dark theme, animated radar scanner, and intuitive navigation.

---

## 🏗️ Architecture & Tech Stack

- **UI Framework**: Jetpack Compose + Material 3
- **Language**: Kotlin 2.0+ with Coroutines & StateFlow
- **Architecture**: MVVM + Clean Architecture + Repository Pattern
- **Persistence**: Native SQLite Database (`BChatDatabase`) with reactive flows
- **P2P Transports**:
  - `com.google.android.gms:play-services-nearby`
  - Java `ServerSocket` + Android `NsdManager`
  - Android `BluetoothAdapter` + `BluetoothServerSocket`
- **Cryptography**: `java.security` & `javax.crypto` (`ECDH` + `AES-256-GCM`)
- **QR & Vision**: `com.google.zxing:core` + CameraX + ML Kit Barcode Scanning

---

## 📱 Getting Started

### Prerequisites
- Android Studio Ladybug / Iguana or later
- Android SDK 34 (API 26+ supported)
- JDK 17 / 21 / 25

### Build and Run
```bash
# Clone the repository
git clone https://github.com/cyberlog69/BChat.git
cd BChat

# Build Debug APK
./gradlew assembleDebug

# Install via ADB
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## 📄 License

MIT License. Free for open-source and personal use.
