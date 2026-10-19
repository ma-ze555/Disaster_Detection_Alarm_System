import serial
import serial.tools.list_ports
import threading
import time

class SerialReader:
    def __init__(self, on_data_callback, on_error_callback=None):
        self.on_data = on_data_callback
        self.on_error = on_error_callback
        self.serial_conn = None
        self.running = False
        self.thread = None

    def get_available_ports(self):
        ports = serial.tools.list_ports.comports()
        return [port.device for port in ports]

    def connect(self, port, baud_rate=9600):
        try:
            self.serial_conn = serial.Serial(port, baud_rate, timeout=1)
            self.running = True
            self.thread = threading.Thread(target=self._read_loop, daemon=True)
            self.thread.start()
            return True
        except Exception as e:
            if self.on_error:
                self.on_error(str(e))
            return False

    def disconnect(self):
        self.running = False
        if self.serial_conn and self.serial_conn.is_open:
            self.serial_conn.close()

    def is_connected(self):
        return self.serial_conn is not None and self.serial_conn.is_open

    def _read_loop(self):
        while self.running:
            try:
                if self.serial_conn and self.serial_conn.in_waiting > 0:
                    line = self.serial_conn.readline().decode('utf-8').strip()
                    if line:
                        try:
                            value = float(line)
                            self.on_data(value)
                        except ValueError:
                            pass  # skip non-numeric lines
                time.sleep(0.05)
            except Exception as e:
                self.running = False
                if self.on_error:
                    self.on_error(str(e))
                break
