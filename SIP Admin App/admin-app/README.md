# DDAS Admin App

Desktop admin panel for the **Disaster Detection Alarm System**.

## Setup

### 1. Install Python dependencies
```bash
pip install -r requirements.txt
```

### 2. Firebase setup
1. Go to [Firebase Console](https://console.firebase.google.com/) → create a project
2. Project Settings → Service Accounts → Generate new private key
3. Save the downloaded file as `serviceAccountKey.json` in this folder
4. The app works without it, but FCM alerts will be disabled

### 3. Upload Arduino sketch
- Open `arduino/seismometer.ino` in Arduino IDE
- Select board: **Arduino Nano**
- Upload to the board

### 4. Run the app
```bash
python main.py
```

## Usage
1. Select the correct COM port and baud rate (9600)
2. Click **Connect**
3. Set the threshold slider — any reading above this triggers an alert
4. Live graph shows real-time seismic data
5. Use **Manual Alert** to send an alert anytime

## Alert flow
```
Arduino Sensor → Admin App → FCM → User App (alarm rings)
                                        ↓ (if "No" or timeout)
                                   Emergency App (location + dispatch)
```

## Notes
- Alert cooldown is 30 seconds to prevent spam
- `serviceAccountKey.json` is gitignored — never commit it
