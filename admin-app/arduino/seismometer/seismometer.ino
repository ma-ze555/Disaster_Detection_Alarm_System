/*
  DDAS - Disaster Detection Alarm System
  ESP32 + MPU6050 Seismometer Sketch

  Components:
    - ESP32 DevKit V1
    - MPU6050 (GY-521) via I2C
    - LED on GPIO 4
    - Active Buzzer on GPIO 5

  Wiring:
    MPU6050 VCC  → 3V3
    MPU6050 GND  → GND
    MPU6050 SCL  → G22
    MPU6050 SDA  → G21
    LED +        → 220Ω → G4
    LED -        → GND
    Buzzer +     → G5
    Buzzer -     → GND
*/

#include <WiFi.h>
#include <Wire.h>
#include <Adafruit_MPU6050.h>
#include <Adafruit_Sensor.h>
#include <Firebase_ESP_Client.h>
#include "addons/TokenHelper.h"
#include "addons/RTDBHelper.h"

// ── Wi-Fi Credentials ────────────────────────────────────────────────────────
#define WIFI_SSID     "Helping the poor is my passion..."
#define WIFI_PASSWORD "maze555hehe"

// ── Firebase Credentials ─────────────────────────────────────────────────────
#define API_KEY       "AIzaSyAGs7zpOpDoRF3qaFN-9AptMxu7YaxQgGE"
#define DATABASE_URL  "https://disaster-detection-alarm-default-rtdb.firebaseio.com"

// ── Hardware Pins ─────────────────────────────────────────────────────────────
#define LED_PIN       4
#define BUZZER_PIN    5
#define BUZZER_CH     0   // LEDC channel for ESP32 buzzer

// ── Thresholds & Timing ───────────────────────────────────────────────────────
#define VIBRATION_THRESHOLD  12.0   // m/s² — above this triggers alert
#define ALERT_COOLDOWN_MS    30000  // 30 seconds between alerts
#define SEND_INTERVAL_MS     2000   // send data to Firebase every 2 seconds

// ── Firebase Objects ──────────────────────────────────────────────────────────
FirebaseData   fbdo;
FirebaseAuth   auth;
FirebaseConfig config;

// ── Sensor ───────────────────────────────────────────────────────────────────
Adafruit_MPU6050 mpu;

// ── State ────────────────────────────────────────────────────────────────────
unsigned long lastSendTime  = 0;
unsigned long lastAlertTime = 0;
bool firebaseReady          = false;

// ── Buzzer helpers (ESP32 uses LEDC, not tone()) ──────────────────────────────
void buzzerOn() {
  ledcSetup(BUZZER_CH, 1000, 8);
  ledcAttachPin(BUZZER_PIN, BUZZER_CH);
  ledcWrite(BUZZER_CH, 128);
}

void buzzerOff() {
  ledcWrite(BUZZER_CH, 0);
  ledcDetachPin(BUZZER_PIN);
  digitalWrite(BUZZER_PIN, LOW);
}

// ── Setup ────────────────────────────────────────────────────────────────────
void setup() {
  Serial.begin(115200);
  pinMode(LED_PIN,    OUTPUT);
  pinMode(BUZZER_PIN, OUTPUT);
  digitalWrite(LED_PIN,    LOW);
  digitalWrite(BUZZER_PIN, LOW);

  // — Wi-Fi —
  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
  Serial.print("Connecting to Wi-Fi");
  int tries = 0;
  while (WiFi.status() != WL_CONNECTED && tries < 30) {
    delay(500);
    Serial.print(".");
    tries++;
  }
  if (WiFi.status() == WL_CONNECTED) {
    Serial.println("\n✅ Wi-Fi Connected! IP: " + WiFi.localIP().toString());
  } else {
    Serial.println("\n❌ Wi-Fi failed — running without cloud.");
  }

  // — MPU6050 —
  Wire.begin(21, 22);
  if (!mpu.begin()) {
    Serial.println("❌ MPU6050 not found! Check wiring.");
    while (1) delay(10);
  }
  mpu.setAccelerometerRange(MPU6050_RANGE_8_G);
  mpu.setGyroRange(MPU6050_RANGE_500_DEG);
  mpu.setFilterBandwidth(MPU6050_BAND_21_HZ);
  Serial.println("✅ MPU6050 Ready!");

  // — Firebase —
  if (WiFi.status() == WL_CONNECTED) {
    config.api_key           = API_KEY;
    config.database_url      = DATABASE_URL;
    config.token_status_callback = tokenStatusCallback;

    // Anonymous sign-in (no email/password needed)
    auth.user.email    = "";
    auth.user.password = "";

    Firebase.begin(&config, &auth);
    Firebase.reconnectWiFi(true);
    firebaseReady = true;
    Serial.println("✅ Firebase Initialized!");
  }
}

// ── Main Loop ────────────────────────────────────────────────────────────────
void loop() {
  sensors_event_t a, g, temp;
  mpu.getEvent(&a, &g, &temp);

  // Calculate total acceleration magnitude
  float magnitude = sqrt(
    a.acceleration.x * a.acceleration.x +
    a.acceleration.y * a.acceleration.y +
    a.acceleration.z * a.acceleration.z
  );

  Serial.print("Magnitude: ");
  Serial.print(magnitude);
  Serial.println(" m/s²");

  unsigned long now     = millis();
  bool          isAlert = (magnitude > VIBRATION_THRESHOLD);

  // — Local alert: LED + Buzzer —
  if (isAlert && (now - lastAlertTime > ALERT_COOLDOWN_MS)) {
    digitalWrite(LED_PIN, HIGH);
    buzzerOn();
    Serial.println("⚠️  EARTHQUAKE DETECTED! Triggering alert...");
    lastAlertTime = now;
  } else if (!isAlert) {
    digitalWrite(LED_PIN, LOW);
    buzzerOff();
  }

  // — Send data to Firebase every 2 seconds —
  if (firebaseReady && Firebase.ready() && (now - lastSendTime > SEND_INTERVAL_MS)) {
    lastSendTime = now;

    FirebaseJson json;
    json.set("vibration_magnitude", magnitude);
    json.set("alert_status",        isAlert);
    json.set("device",              "ESP32_DDAS");
    json.set("timestamp",           (int)now);

    if (Firebase.RTDB.setJSON(&fbdo, "/sensor_data", &json)) {
      Serial.println("✅ Firebase updated!");
    } else {
      Serial.print("❌ Firebase error: ");
      Serial.println(fbdo.errorReason());
    }
  }

  delay(100);
}
