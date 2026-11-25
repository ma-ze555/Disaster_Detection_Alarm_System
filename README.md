# 🚨 Disaster Detection Alarm System (DDAS)

A real-time earthquake detection and emergency alert system built as a multi-disciplinary major project combining **IoT hardware**, **desktop software**, and **Android mobile apps**.

---

## 📌 Project Overview

DDAS detects seismic activity using a hardware seismometer (ESP32 + MPU6050), sends alerts to citizens via a mobile app, and dispatches emergency medical teams when citizens report being unsafe.

```
[MPU6050 Sensor] → [ESP32] → [Firebase] → [Admin App]
                                         → [Citizen App] → YES (Safety Tips)
                                                         → NO  (Medical Dispatch)
                                                               → [Medical App + Location]
```

---

## 🏗️ System Architecture

| Component | Technology | Purpose |
|---|---|---|
| **Seismometer** | ESP32 + MPU6050 | Detects vibration/seismic activity |
| **Admin App** | Python + CustomTkinter | Monitors sensor, sends FCM alerts |
| **Citizen App** | Android (Kotlin) | Receives alarm, responds Yes/No |
| **Medical App** | Android (Kotlin) | Gets dispatched when citizen needs help |
| **Cloud** | Firebase (RTDB + FCM) | Real-time data sync + push notifications |

---

## 📁 Repository Structure

```
Disaster_Detection_Alarm_System/
├── admin-app/                  # Python desktop admin panel
│   ├── main.py                 # Entry point
│   ├── firebase_sender.py      # FCM + Firebase integration
│   ├── serial_reader.py        # Arduino/ESP32 serial reader
│   ├── ui/dashboard.py         # CustomTkinter UI
│   └── arduino/seismometer.ino # ESP32 + MPU6050 sketch
├── app/                        # Android app (Citizen + Medical)
│   └── src/main/java/com/ddas/app/
│       ├── citizen/            # Citizen role screens
│       ├── medical/            # Medical role screens
│       ├── auth/               # Login + Register
│       ├── common/             # Role select, Splash
│       └── services/           # FCM background service
└── README.md
```

---

## 🔧 Hardware Components

| Component | Quantity | Purpose |
|---|---|---|
| ESP32 DevKit V1 | 1 | Wi-Fi microcontroller |
| MPU6050 (GY-521) | 1 | 6-axis accelerometer + gyroscope |
| Active Buzzer | 1 | Local alarm |
| LED (Red, 5mm) | 1 | Pre-alert indicator |
| 220Ω Resistor | 1 | LED current limiter |
| Breadboard | 1 | Circuit assembly |
| Jumper Wires | 10+ | Connections |

### Wiring

| MPU6050 | ESP32 |
|---|---|
| VCC | 3V3 |
| GND | GND |
| SCL | G22 |
| SDA | G21 |

| Component | ESP32 |
|---|---|
| LED (+) → 220Ω | G4 |
| Buzzer (+) | G5 |

---

## 🖥️ Admin App Setup

### Requirements
- Python 3.8+
- pip

### Installation
```bash
cd admin-app
pip install -r requirements.txt
```

### Firebase Setup
1. Go to [Firebase Console](https://console.firebase.google.com)
2. Project Settings → Service Accounts → Generate new private key
3. Save as `serviceAccountKey.json` in `admin-app/` folder

### Run
```bash
python main.py
```

### Features
- Live seismic activity graph
- Auto-alert when vibration crosses threshold
- Manual alert with custom message
- Simulate Earthquake (demo mode)
- Alert history log
- Firebase FCM integration

---

## 📱 Android App Setup

### Requirements
- Android Studio (Ladybug 2024.2.1+)
- Android API 26+
- Firebase project with Realtime Database enabled

### Setup
1. Clone this repo
2. Open `app/` folder in Android Studio
3. Add `google-services.json` to `app/` folder
4. Sync Gradle → Run

### Features

**Citizen Role:**
- Receives FCM earthquake alarm (even when app is closed)
- Full screen alarm with countdown timer
- YES → Safety precautions screen
- NO → GPS location captured + medical dispatch triggered
- "Drop, Cover, Hold On" guidance

**Medical Role:**
- Receives dispatch alarm when citizen says NO
- Views all victims needing help
- Opens victim location in Google Maps
- Mark incident as resolved

**Common:**
- Firebase Auth (Email/Password)
- Role selection (Citizen / Medical)
- Switch role anytime

---

## 🔥 Firebase Structure

```
Firebase Realtime Database
├── sensor_data/
│   ├── vibration_magnitude: float
│   ├── alert_status: boolean
│   ├── device: string
│   └── timestamp: long
├── users/
│   └── {uid}/
│       ├── name, email, role, fcmToken
├── escalations/
│   └── {uid}/
│       ├── status: "pending" | "resolved"
│       ├── magnitude, timestamp
│       ├── latitude, longitude
└── alert_responses/
    └── {uid}/
        ├── safe: boolean
        └── timestamp
```

---

## 🌿 Branches

| Branch | Purpose |
|---|---|
| `main` | Stable production code |
| `mobile-app` | Android app (Citizen + Medical) |
| `admin-app` | Python admin desktop app |
| `hardware` | ESP32 Arduino sketches |
| `user-app` | Citizen app development |
| `emergency-app` | Emergency/Medical app development |
| `dev` | General development & testing |

---

## ⚠️ Known Limitations

1. Requires internet (Wi-Fi or mobile data) for alerts
2. FCM delivery may be delayed on aggressive battery optimization phones (iQOO, Xiaomi, Realme)
3. MPU6050 measures acceleration, not actual Richter scale magnitude
4. Single sensor — not a city-wide detection network
5. Firebase Spark (free) plan limits on simultaneous connections

---

## 🔮 Future Scope

- GSM/SIM module for SMS fallback when internet is unavailable
- Multiple sensor nodes across locations
- Richter scale calibration
- Web dashboard for alert history
- Government telecom integration for WEA alerts

---

## 👥 Team

| Role | Contribution |
|---|---|
| CS/ISE | Software, Android App, IoT Integration |
| Civil Engineering | Structural vibration analysis, sensor placement |
| Mechanical Engineering | Hardware assembly, sensor calibration |

---

## 📄 License

This project is developed for academic purposes as part of a major project.
