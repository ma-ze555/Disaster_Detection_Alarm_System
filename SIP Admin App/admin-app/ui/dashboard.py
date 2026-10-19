import customtkinter as ctk
import tkinter as tk
from tkinter import messagebox
from matplotlib.figure import Figure
from matplotlib.backends.backend_tkagg import FigureCanvasTkAgg
from collections import deque
import threading
import time
from datetime import datetime

# ── Colours ──────────────────────────────────────────────────────────────────
BG        = "#0a0a0a"
PANEL     = "#141414"
CARD      = "#1a1a1a"
RED       = "#e63946"
RED_DARK  = "#9b1a23"
RED_HOVER = "#ff4d5a"
TEXT      = "#f0f0f0"
SUBTEXT   = "#888888"
BORDER    = "#2a2a2a"
GREEN     = "#2ecc71"
YELLOW    = "#f39c12"

MAX_POINTS = 100  # number of data points shown on graph

class Dashboard(ctk.CTk):
    def __init__(self, serial_reader, firebase_sender):
        super().__init__()
        self.serial_reader = serial_reader
        self.firebase_sender = firebase_sender

        self.data_buffer = deque([0.0] * MAX_POINTS, maxlen=MAX_POINTS)
        self.threshold = tk.DoubleVar(value=500.0)
        self.alert_cooldown = False
        self.alert_logs = []
        self.connected = False
        self.current_value = 0.0

        self._setup_window()
        self._build_ui()
        self._start_graph_update()

    # ── Window setup ─────────────────────────────────────────────────────────
    def _setup_window(self):
        self.title("Disaster Detection Alarm System — Admin")
        self.geometry("1200x750")
        self.minsize(1000, 650)
        self.configure(fg_color=BG)
        ctk.set_appearance_mode("dark")

    # ── UI Layout ─────────────────────────────────────────────────────────────
    def _build_ui(self):
        self.grid_columnconfigure(1, weight=1)
        self.grid_rowconfigure(0, weight=1)

        self._build_sidebar()
        self._build_main()

    def _build_sidebar(self):
        sidebar = ctk.CTkFrame(self, fg_color=PANEL, width=260, corner_radius=0,
                               border_width=1, border_color=BORDER)
        sidebar.grid(row=0, column=0, sticky="nsew")
        sidebar.grid_propagate(False)
        sidebar.grid_rowconfigure(8, weight=1)

        # Logo / Title
        title_frame = ctk.CTkFrame(sidebar, fg_color="transparent")
        title_frame.grid(row=0, column=0, padx=20, pady=(24, 8), sticky="ew")

        ctk.CTkLabel(title_frame, text="⚠", font=("Arial", 32), text_color=RED).pack()
        ctk.CTkLabel(title_frame, text="DDAS Admin", font=("Arial", 16, "bold"),
                     text_color=TEXT).pack()
        ctk.CTkLabel(title_frame, text="Disaster Detection Alarm System",
                     font=("Arial", 9), text_color=SUBTEXT, wraplength=200).pack()

        _divider(sidebar, row=1)

        # Connection section
        ctk.CTkLabel(sidebar, text="SERIAL CONNECTION", font=("Arial", 10, "bold"),
                     text_color=SUBTEXT).grid(row=2, column=0, padx=20, pady=(12, 4), sticky="w")

        self.port_var = tk.StringVar(value="Select Port")
        self.port_menu = ctk.CTkOptionMenu(sidebar, variable=self.port_var,
                                           values=["Refresh..."],
                                           fg_color=CARD, button_color=RED,
                                           button_hover_color=RED_HOVER,
                                           text_color=TEXT, width=220)
        self.port_menu.grid(row=3, column=0, padx=20, pady=4)

        self.baud_var = tk.StringVar(value="9600")
        ctk.CTkOptionMenu(sidebar, variable=self.baud_var,
                          values=["9600", "115200", "57600", "38400"],
                          fg_color=CARD, button_color=RED,
                          button_hover_color=RED_HOVER,
                          text_color=TEXT, width=220).grid(row=4, column=0, padx=20, pady=4)

        self.connect_btn = ctk.CTkButton(sidebar, text="Connect",
                                         fg_color=RED, hover_color=RED_HOVER,
                                         text_color=TEXT, width=220,
                                         command=self._toggle_connection)
        self.connect_btn.grid(row=5, column=0, padx=20, pady=(4, 12))

        self.conn_status = ctk.CTkLabel(sidebar, text="● Disconnected",
                                        font=("Arial", 11), text_color=RED)
        self.conn_status.grid(row=6, column=0, padx=20, pady=0, sticky="w")

        _divider(sidebar, row=7)

        # Threshold section
        ctk.CTkLabel(sidebar, text="ALERT THRESHOLD", font=("Arial", 10, "bold"),
                     text_color=SUBTEXT).grid(row=8, column=0, padx=20, pady=(12, 4), sticky="w")

        self.threshold_label = ctk.CTkLabel(sidebar,
                                             text=f"Value: {self.threshold.get():.0f}",
                                             font=("Arial", 12), text_color=TEXT)
        self.threshold_label.grid(row=9, column=0, padx=20, pady=0, sticky="w")

        threshold_slider = ctk.CTkSlider(sidebar, from_=0, to=1023,
                                          variable=self.threshold,
                                          progress_color=RED, button_color=RED,
                                          button_hover_color=RED_HOVER, width=220,
                                          command=self._on_threshold_change)
        threshold_slider.grid(row=10, column=0, padx=20, pady=4)

        self.threshold_entry = ctk.CTkEntry(sidebar, textvariable=self.threshold,
                                             fg_color=CARD, border_color=BORDER,
                                             text_color=TEXT, width=220)
        self.threshold_entry.grid(row=11, column=0, padx=20, pady=(0, 4))
        self.threshold_entry.bind("<Return>", self._on_threshold_entry)

        _divider(sidebar, row=12)

        # Firebase status
        ctk.CTkLabel(sidebar, text="FIREBASE", font=("Arial", 10, "bold"),
                     text_color=SUBTEXT).grid(row=13, column=0, padx=20, pady=(12, 4), sticky="w")

        fb_ok = self.firebase_sender.initialized
        self.fb_status = ctk.CTkLabel(sidebar,
                                       text="● Connected" if fb_ok else "● Not configured",
                                       font=("Arial", 11),
                                       text_color=GREEN if fb_ok else YELLOW)
        self.fb_status.grid(row=14, column=0, padx=20, pady=(0, 20), sticky="w")

        # Refresh ports on startup
        self._refresh_ports()
        sidebar.grid_columnconfigure(0, weight=1)

    def _build_main(self):
        main = ctk.CTkFrame(self, fg_color=BG, corner_radius=0)
        main.grid(row=0, column=1, sticky="nsew", padx=0, pady=0)
        main.grid_columnconfigure(0, weight=1)
        main.grid_rowconfigure(1, weight=1)

        # Top stats bar
        stats = ctk.CTkFrame(main, fg_color=PANEL, height=90, corner_radius=0,
                              border_width=1, border_color=BORDER)
        stats.grid(row=0, column=0, sticky="ew")
        stats.grid_propagate(False)
        stats.grid_columnconfigure((0, 1, 2, 3), weight=1)

        self.stat_current = _stat_card(stats, "CURRENT VALUE", "0.00", col=0)
        self.stat_peak    = _stat_card(stats, "PEAK VALUE",    "0.00", col=1)
        self.stat_thresh  = _stat_card(stats, "THRESHOLD",     f"{self.threshold.get():.0f}", col=2)
        self.stat_alerts  = _stat_card(stats, "ALERTS SENT",   "0",    col=3)
        self.alert_count  = 0
        self.peak_value   = 0.0

        # Content area
        content = ctk.CTkFrame(main, fg_color=BG, corner_radius=0)
        content.grid(row=1, column=0, sticky="nsew", padx=16, pady=16)
        content.grid_columnconfigure(0, weight=1)
        content.grid_rowconfigure(0, weight=3)
        content.grid_rowconfigure(1, weight=2)

        # Graph card
        graph_card = ctk.CTkFrame(content, fg_color=CARD, corner_radius=8,
                                   border_width=1, border_color=BORDER)
        graph_card.grid(row=0, column=0, sticky="nsew", pady=(0, 12))
        graph_card.grid_columnconfigure(0, weight=1)
        graph_card.grid_rowconfigure(1, weight=1)

        graph_header = ctk.CTkFrame(graph_card, fg_color="transparent")
        graph_header.grid(row=0, column=0, sticky="ew", padx=16, pady=(12, 0))
        ctk.CTkLabel(graph_header, text="Live Seismic Activity",
                     font=("Arial", 13, "bold"), text_color=TEXT).pack(side="left")
        self.graph_status = ctk.CTkLabel(graph_header, text="● Waiting for data",
                                          font=("Arial", 11), text_color=SUBTEXT)
        self.graph_status.pack(side="right")

        self._build_graph(graph_card)

        # Bottom row: manual alert + log
        bottom = ctk.CTkFrame(content, fg_color="transparent")
        bottom.grid(row=1, column=0, sticky="nsew")
        bottom.grid_columnconfigure(0, weight=1)
        bottom.grid_columnconfigure(1, weight=2)

        self._build_manual_alert(bottom)
        self._build_log(bottom)

    def _build_graph(self, parent):
        fig = Figure(figsize=(5, 2.5), dpi=100, facecolor=CARD)
        self.ax = fig.add_subplot(111)
        self.ax.set_facecolor("#0f0f0f")
        self.ax.tick_params(colors=SUBTEXT, labelsize=8)
        for spine in self.ax.spines.values():
            spine.set_edgecolor(BORDER)
        self.ax.set_ylim(0, 1023)
        self.ax.set_xlim(0, MAX_POINTS)
        self.ax.set_ylabel("Amplitude", color=SUBTEXT, fontsize=9)
        self.ax.grid(True, color=BORDER, linewidth=0.5, alpha=0.5)

        self.line, = self.ax.plot(list(self.data_buffer), color=RED, linewidth=1.5)
        self.thresh_line = self.ax.axhline(y=self.threshold.get(),
                                            color=YELLOW, linewidth=1,
                                            linestyle="--", alpha=0.8)

        fig.tight_layout(pad=1.5)
        canvas = FigureCanvasTkAgg(fig, master=parent)
        canvas.get_tk_widget().grid(row=1, column=0, sticky="nsew", padx=8, pady=8)
        self.canvas = canvas

    def _build_manual_alert(self, parent):
        card = ctk.CTkFrame(parent, fg_color=CARD, corner_radius=8,
                             border_width=1, border_color=BORDER)
        card.grid(row=0, column=0, sticky="nsew", padx=(0, 8))
        card.grid_columnconfigure(0, weight=1)

        ctk.CTkLabel(card, text="Manual Alert", font=("Arial", 13, "bold"),
                     text_color=TEXT).grid(row=0, column=0, padx=16, pady=(12, 4), sticky="w")
        ctk.CTkLabel(card, text="Send an alert to all users manually.",
                     font=("Arial", 10), text_color=SUBTEXT).grid(row=1, column=0, padx=16, sticky="w")

        self.manual_msg = ctk.CTkEntry(card, placeholder_text="Alert message...",
                                        fg_color=BG, border_color=BORDER,
                                        text_color=TEXT)
        self.manual_msg.grid(row=2, column=0, padx=16, pady=8, sticky="ew")

        ctk.CTkButton(card, text="🔔  Send Manual Alert",
                      fg_color=RED_DARK, hover_color=RED,
                      text_color=TEXT, height=38,
                      command=self._send_manual_alert).grid(row=3, column=0, padx=16, pady=(0, 8), sticky="ew")

        # Divider
        ctk.CTkFrame(card, fg_color=BORDER, height=1, corner_radius=0).grid(
            row=4, column=0, sticky="ew", padx=12, pady=4)

        ctk.CTkLabel(card, text="DEMO / SIMULATION", font=("Arial", 10, "bold"),
                     text_color=SUBTEXT).grid(row=5, column=0, padx=16, pady=(4, 4), sticky="w")

        ctk.CTkButton(card, text="⚡  Simulate Earthquake",
                      fg_color="#1a1a1a", hover_color="#2a2a2a",
                      border_width=1, border_color=RED,
                      text_color=RED, height=38,
                      command=self._simulate_earthquake).grid(row=6, column=0, padx=16, pady=(0, 16), sticky="ew")

    def _build_log(self, parent):
        card = ctk.CTkFrame(parent, fg_color=CARD, corner_radius=8,
                             border_width=1, border_color=BORDER)
        card.grid(row=0, column=1, sticky="nsew")
        card.grid_columnconfigure(0, weight=1)
        card.grid_rowconfigure(1, weight=1)

        ctk.CTkLabel(card, text="Alert History", font=("Arial", 13, "bold"),
                     text_color=TEXT).grid(row=0, column=0, padx=16, pady=(12, 4), sticky="w")

        self.log_box = ctk.CTkTextbox(card, fg_color=BG, text_color=TEXT,
                                       font=("Courier", 10), state="disabled",
                                       border_width=0)
        self.log_box.grid(row=1, column=0, padx=8, pady=(0, 8), sticky="nsew")

    # ── Actions ───────────────────────────────────────────────────────────────
    def _refresh_ports(self):
        ports = self.serial_reader.get_available_ports()
        if ports:
            self.port_menu.configure(values=ports)
            self.port_var.set(ports[0])
        else:
            self.port_menu.configure(values=["No ports found"])
            self.port_var.set("No ports found")

    def _toggle_connection(self):
        if self.connected:
            self.serial_reader.disconnect()
            self.connected = False
            self.connect_btn.configure(text="Connect", fg_color=RED)
            self.conn_status.configure(text="● Disconnected", text_color=RED)
            self.graph_status.configure(text="● Disconnected", text_color=RED)
        else:
            port = self.port_var.get()
            baud = int(self.baud_var.get())
            success = self.serial_reader.connect(port, baud)
            if success:
                self.connected = True
                self.connect_btn.configure(text="Disconnect", fg_color="#333333")
                self.conn_status.configure(text="● Connected", text_color=GREEN)
                self.graph_status.configure(text="● Live", text_color=GREEN)
            else:
                messagebox.showerror("Connection Error",
                                     f"Could not connect to {port}.\nCheck the port and try again.")

    def _on_threshold_change(self, val):
        self.threshold_label.configure(text=f"Value: {float(val):.0f}")
        self.stat_thresh.configure(text=f"{float(val):.0f}")
        self.thresh_line.set_ydata([float(val), float(val)])
        self.canvas.draw_idle()

    def _on_threshold_entry(self, event=None):
        try:
            val = float(self.threshold.get())
            val = max(0, min(1023, val))
            self.threshold.set(val)
            self._on_threshold_change(val)
        except:
            pass

    def _send_manual_alert(self):
        msg = self.manual_msg.get().strip()
        if not msg:
            msg = "Emergency alert issued by admin."
        ok, resp = self.firebase_sender.send_manual_alert(msg)
        status = "Sent" if ok else f"Failed: {resp}"
        self._add_log(f"[MANUAL] {msg} — {status}")
        if not ok:
            messagebox.showerror("Alert Error", f"Failed to send alert:\n{resp}")

    def _simulate_earthquake(self):
        """Simulate a seismic spike for demo purposes — no Arduino needed."""
        import random
        import math

        self._add_log("[DEMO] Simulating earthquake spike...")

        # Build a spike wave: ramp up, peak, ramp down over ~3 seconds
        steps = 60
        peak = random.uniform(750, 1023)

        def feed_spike(i=0):
            if i >= steps:
                self._add_log("[DEMO] Simulation complete.")
                return
            # sine-based spike shape
            progress = i / steps
            value = peak * math.sin(math.pi * progress) + random.uniform(-20, 20)
            value = max(0, min(1023, value))
            self.on_serial_data(value)
            self.after(50, lambda: feed_spike(i + 1))

        feed_spike()

    def on_serial_data(self, value):
        """Called from serial reader thread."""
        self.current_value = value
        self.data_buffer.append(value)

        if value > self.peak_value:
            self.peak_value = value

        # Threshold check with cooldown
        if value >= self.threshold.get() and not self.alert_cooldown:
            self._trigger_alert(value)

    def _trigger_alert(self, value):
        self.alert_cooldown = True
        self.alert_count += 1
        self.stat_alerts.configure(text=str(self.alert_count))

        ok, resp = self.firebase_sender.send_earthquake_alert(
            magnitude=value, threshold=self.threshold.get()
        )
        status = "Sent" if ok else f"Failed: {resp}"
        self._add_log(f"[AUTO] Magnitude {value:.2f} crossed threshold {self.threshold.get():.0f} — {status}")

        # 30-second cooldown to avoid alert spam
        threading.Timer(30.0, self._reset_cooldown).start()

    def _reset_cooldown(self):
        self.alert_cooldown = False

    def _add_log(self, message):
        timestamp = datetime.now().strftime("%H:%M:%S")
        entry = f"[{timestamp}] {message}\n"
        self.log_box.configure(state="normal")
        self.log_box.insert("0.0", entry)
        self.log_box.configure(state="disabled")

    def on_serial_error(self, error):
        self.connected = False
        self.connect_btn.configure(text="Connect", fg_color=RED)
        self.conn_status.configure(text="● Error", text_color=YELLOW)
        self.graph_status.configure(text="● Error", text_color=YELLOW)
        self._add_log(f"[ERROR] Serial error: {error}")

    # ── Graph update loop ─────────────────────────────────────────────────────
    def _start_graph_update(self):
        self._update_graph()

    def _update_graph(self):
        self.line.set_ydata(list(self.data_buffer))
        self.stat_current.configure(text=f"{self.current_value:.2f}")
        self.stat_peak.configure(text=f"{self.peak_value:.2f}")
        self.canvas.draw_idle()
        self.after(100, self._update_graph)  # update every 100ms


# ── Helpers ───────────────────────────────────────────────────────────────────
def _divider(parent, row):
    ctk.CTkFrame(parent, fg_color=BORDER, height=1, corner_radius=0).grid(
        row=row, column=0, sticky="ew", padx=12, pady=4)

def _stat_card(parent, label, value, col):
    frame = ctk.CTkFrame(parent, fg_color="transparent")
    frame.grid(row=0, column=col, padx=16, pady=12, sticky="nsew")
    ctk.CTkLabel(frame, text=label, font=("Arial", 9), text_color=SUBTEXT).pack(anchor="w")
    val_label = ctk.CTkLabel(frame, text=value, font=("Arial", 22, "bold"), text_color=TEXT)
    val_label.pack(anchor="w")
    return val_label
