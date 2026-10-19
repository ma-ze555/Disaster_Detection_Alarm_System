/*
  Disaster Detection Alarm System
  Arduino Nano — Seismometer sketch

  Reads analog vibration sensor on A0 and sends the value
  over Serial at 9600 baud. The admin app reads this value.

  Wiring:
    VCC  → 5V
    GND  → GND
    AOUT → A0
*/

const int SENSOR_PIN = A0;
const int SAMPLE_DELAY = 50; // ms between readings (~20 samples/sec)

void setup() {
  Serial.begin(9600);
}

void loop() {
  int value = analogRead(SENSOR_PIN); // 0–1023
  Serial.println(value);
  delay(SAMPLE_DELAY);
}
