import json
import os
from datetime import datetime

LOG_FILE = "alert_history.json"

def load_logs():
    if not os.path.exists(LOG_FILE):
        return []
    with open(LOG_FILE, "r") as f:
        try:
            return json.load(f)
        except:
            return []

def save_log(entry: dict):
    logs = load_logs()
    logs.insert(0, entry)
    logs = logs[:200]  # keep last 200 entries
    with open(LOG_FILE, "w") as f:
        json.dump(logs, f, indent=2)

def add_alert_log(alert_type, magnitude, threshold, status):
    entry = {
        "timestamp": datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
        "type": alert_type,
        "magnitude": round(magnitude, 2),
        "threshold": threshold,
        "status": status,
    }
    save_log(entry)
    return entry
