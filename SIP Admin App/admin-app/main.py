import sys
import os

# ── Firebase init (optional — app works without it) ───────────────────────────
from firebase_sender import FirebaseSender
from serial_reader import SerialReader
from ui.dashboard import Dashboard

def main():
    firebase = FirebaseSender()
    fb_ok = firebase.initialize("serviceAccountKey.json")
    if not fb_ok:
        print("[WARNING] serviceAccountKey.json not found. Firebase alerts disabled.")
        print("          Place your Firebase service account key as 'serviceAccountKey.json'")
        print("          in the admin-app folder to enable FCM alerts.\n")

    dashboard = Dashboard(
        serial_reader=SerialReader(
            on_data_callback=lambda v: dashboard.on_serial_data(v),
            on_error_callback=lambda e: dashboard.on_serial_error(e),
        ),
        firebase_sender=firebase,
    )

    dashboard.mainloop()

if __name__ == "__main__":
    main()
