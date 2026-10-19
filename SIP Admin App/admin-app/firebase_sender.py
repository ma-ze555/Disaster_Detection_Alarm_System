import firebase_admin
from firebase_admin import credentials, messaging
import os
import json
from datetime import datetime

class FirebaseSender:
    def __init__(self):
        self.initialized = False
        self.topic = "disaster_alerts"  # all user app devices subscribe to this topic

    def initialize(self, service_account_path="serviceAccountKey.json"):
        """Initialize Firebase with service account credentials."""
        if self.initialized:
            return True
        if not os.path.exists(service_account_path):
            return False
        try:
            cred = credentials.Certificate(service_account_path)
            firebase_admin.initialize_app(cred)
            self.initialized = True
            return True
        except Exception as e:
            print(f"Firebase init error: {e}")
            return False

    def send_earthquake_alert(self, magnitude, location="Unknown", threshold=None):
        """Send high-priority earthquake alert to all users via FCM topic."""
        if not self.initialized:
            return False, "Firebase not initialized"

        try:
            message = messaging.Message(
                topic=self.topic,
                android=messaging.AndroidConfig(
                    priority="high",
                    notification=messaging.AndroidNotification(
                        title="⚠️ EARTHQUAKE ALERT",
                        body=f"Seismic activity detected! Magnitude: {magnitude:.2f}. Stay safe!",
                        channel_id="earthquake_alarm",  # user app must create this channel
                        priority="max",
                        default_vibrate_timings=False,
                        vibrate_timings_millis=[0, 500, 200, 500],
                    ),
                ),
                data={
                    "type": "EARTHQUAKE_ALERT",
                    "magnitude": str(round(magnitude, 2)),
                    "threshold": str(threshold) if threshold else "",
                    "location": location,
                    "timestamp": datetime.now().isoformat(),
                    "requires_response": "true",  # triggers the Yes/No prompt in user app
                    "response_timeout_seconds": "120",  # 2 min auto-No
                },
            )
            response = messaging.send(message)
            return True, response
        except Exception as e:
            return False, str(e)

    def send_manual_alert(self, message_text, location="Unknown"):
        """Admin manually triggers an alert."""
        if not self.initialized:
            return False, "Firebase not initialized"

        try:
            message = messaging.Message(
                topic=self.topic,
                android=messaging.AndroidConfig(
                    priority="high",
                    notification=messaging.AndroidNotification(
                        title="⚠️ EMERGENCY ALERT",
                        body=message_text,
                        channel_id="earthquake_alarm",
                        priority="max",
                    ),
                ),
                data={
                    "type": "MANUAL_ALERT",
                    "message": message_text,
                    "location": location,
                    "timestamp": datetime.now().isoformat(),
                    "requires_response": "true",
                    "response_timeout_seconds": "120",
                },
            )
            response = messaging.send(message)
            return True, response
        except Exception as e:
            return False, str(e)
