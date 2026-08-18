"""
Live Performance & Activity Profiler for EPUB Reader
Monitors Android app lifecycle, user interactions, render latency (Jank/FPS),
thread CPU breakdown, memory allocations, and temperature in real time.
"""

import os
import sys
import time
import subprocess
import re
import json
import threading
from datetime import datetime

# Configure UTF-8 for Windows console
if sys.platform.startswith('win'):
    try:
        sys.stdout.reconfigure(encoding='utf-8')
        sys.stderr.reconfigure(encoding='utf-8')
    except: pass

ADB_PATH = r"C:\Users\SorakadoAo\AppData\Local\Android\Sdk\platform-tools\adb.exe"
PACKAGE_NAME = "com.example.epubreader"
REPORT_PATH = os.path.join(os.path.dirname(__file__), "perf_live_report.md")

class LivePerfAnalyzer:
    def __init__(self):
        self.running = True
        self.pid = None
        self.lock = threading.Lock()
        self.events_log = []
        self.current_metrics = {
            "fps": 60.0,
            "jank_percent": 0.0,
            "cpu_percent": 0.0,
            "top_threads": [],
            "ram_pss_mb": 0,
            "native_heap_mb": 0,
            "temp_c": 0.0,
            "p50_frame_ms": 16,
            "p90_frame_ms": 16,
            "p95_frame_ms": 16,
            "current_screen": "Unknown",
            "last_action": "App Launch"
        }

    def run_cmd(self, args):
        try:
            res = subprocess.run([ADB_PATH] + args, capture_output=True, text=True, timeout=5, encoding='utf-8', errors='ignore')
            return res.stdout
        except Exception as e:
            return ""

    def get_pid(self):
        out = self.run_cmd(["shell", f"pidof {PACKAGE_NAME}"])
        pids = out.strip().split()
        if pids:
            return pids[0]
        return None

    def monitor_logcat(self):
        # Clear logcat first
        subprocess.run([ADB_PATH, "logcat", "-c"], capture_output=True)
        cmd = [
            ADB_PATH, "logcat", "-v", "time",
            "EPUB_PERF:V", "MpvPlayerManager:W", "MpvPlayerManager:E",
            "DanmakuCanvas:D", "ActivityTaskManager:I", "*:S"
        ]
        proc = subprocess.Popen(cmd, stdout=subprocess.PIPE, stderr=subprocess.DEVNULL, text=True, encoding='utf-8', errors='ignore')
        
        for line in iter(proc.stdout.readline, ''):
            if not self.running:
                break
            line = line.strip()
            if not line:
                continue
            
            timestamp = datetime.now().strftime("%H:%M:%S.%f")[:-3]
            
            # Identify action tags
            if "EPUB_PERF" in line or "Displayed com.example.epubreader" in line:
                with self.lock:
                    msg = line.split("):", 1)[-1] if "):" in line else line
                    self.events_log.append(f"[{timestamp}] [ACT] {msg.strip()}")
                    if len(self.events_log) > 50:
                        self.events_log.pop(0)
                    self.current_metrics["last_action"] = msg.strip()
            elif "MpvPlayerManager" in line:
                with self.lock:
                    self.events_log.append(f"[{timestamp}] [MPV] {line}")
                    if len(self.events_log) > 50:
                        self.events_log.pop(0)

    def monitor_gfxinfo(self):
        while self.running:
            if not self.pid:
                self.pid = self.get_pid()
                if not self.pid:
                    time.sleep(1)
                    continue

            out = self.run_cmd(["shell", "dumpsys", "gfxinfo", PACKAGE_NAME, "framestats"])
            lines = out.splitlines()
            total_frames = 0
            janky_frames = 0
            p50 = 0
            p90 = 0
            p95 = 0
            
            for l in lines:
                if "Total frames rendered:" in l:
                    try:
                        total_frames = int(l.split(":")[-1].strip())
                    except: pass
                elif "Janky frames:" in l:
                    try:
                        janky_str = l.split(":")[-1].strip().split()[0]
                        janky_frames = int(janky_str)
                    except: pass
                elif "50th percentile:" in l:
                    try:
                        p50 = int(l.split(":")[-1].replace("ms", "").strip())
                    except: pass
                elif "90th percentile:" in l:
                    try:
                        p90 = int(l.split(":")[-1].replace("ms", "").strip())
                    except: pass
                elif "95th percentile:" in l:
                    try:
                        p95 = int(l.split(":")[-1].replace("ms", "").strip())
                    except: pass

            jank_rate = (janky_frames / max(1, total_frames)) * 100.0 if total_frames > 0 else 0.0

            with self.lock:
                self.current_metrics["jank_percent"] = jank_rate
                self.current_metrics["p50_frame_ms"] = p50
                self.current_metrics["p90_frame_ms"] = p90
                self.current_metrics["p95_frame_ms"] = p95
                if p90 > 32:
                    ts = datetime.now().strftime("%H:%M:%S")
                    warning = f"[{ts}] [WARN] 渲染卡顿: 90分位帧耗时 {p90}ms (掉帧率 {jank_rate:.1f}%)"
                    if not self.events_log or self.events_log[-1] != warning:
                        self.events_log.append(warning)

            time.sleep(1.0)

    def monitor_cpu_and_mem(self):
        while self.running:
            if not self.pid:
                self.pid = self.get_pid()
                if not self.pid:
                    time.sleep(1)
                    continue

            # 1. Thread breakdown via top
            top_out = self.run_cmd(["shell", "top", "-b", "-n", "1", "-H", "-p", str(self.pid)])
            threads = []
            total_cpu = 0.0
            for line in top_out.splitlines():
                parts = line.strip().split()
                if len(parts) >= 9 and parts[0] != "PID" and parts[0].isdigit():
                    try:
                        tid = parts[0]
                        cpu = float(parts[8])
                        name = parts[-1]
                        if cpu > 0.5:
                            threads.append({"tid": tid, "name": name, "cpu": cpu})
                            total_cpu += cpu
                    except: pass

            threads.sort(key=lambda x: x["cpu"], reverse=True)

            # 2. Battery temp
            bat_out = self.run_cmd(["shell", "dumpsys", "battery"])
            temp = 0.0
            for l in bat_out.splitlines():
                if "temperature:" in l:
                    try:
                        temp = int(l.split(":")[-1].strip()) / 10.0
                    except: pass

            # 3. Memory PSS
            mem_out = self.run_cmd(["shell", "dumpsys", "meminfo", PACKAGE_NAME])
            total_pss = 0
            native_heap = 0
            for l in mem_out.splitlines():
                if "TOTAL" in l and "TOTAL PSS" not in l:
                    parts = l.strip().split()
                    if len(parts) >= 2 and parts[1].isdigit():
                        total_pss = int(parts[1]) // 1024
                elif "Native Heap" in l:
                    parts = l.strip().split()
                    if len(parts) >= 3 and parts[2].isdigit():
                        native_heap = int(parts[2]) // 1024

            with self.lock:
                self.current_metrics["cpu_percent"] = total_cpu
                self.current_metrics["top_threads"] = threads[:5]
                self.current_metrics["temp_c"] = temp
                self.current_metrics["ram_pss_mb"] = total_pss
                self.current_metrics["native_heap_mb"] = native_heap

            self.write_report()
            time.sleep(1.0)

    def write_report(self):
        with self.lock:
            m = self.current_metrics
            threads_md = ""
            for t in m.get("top_threads", []):
                threads_md += f"- **线程 `{t['name']}` (TID {t['tid']})**: CPU `{t['cpu']:.1f}%`\n"
            if not threads_md:
                threads_md = "- 所有后台线程轻载运行中 (< 0.5% CPU)\n"

            recent_events = "\n".join(self.events_log[-12:]) if self.events_log else "_暂无异常事件_"

            report_content = f"""# 📊 EPUB Reader 实时性能与能耗诊断报告

> **更新时间**: `{datetime.now().strftime('%Y-%m-%d %H:%M:%S')}` | **目标进程 PID**: `{self.pid or '未检测到'}`

---

## ⚡ 核心实时性能看板 (Live Performance Metrics)

| 监控维度 | 当前数值 | 状态评级 | 说明 |
| :--- | :--- | :--- | :--- |
| **应用总 CPU 占用** | **`{m['cpu_percent']:.1f}%`** | {'🟢 优' if m['cpu_percent'] < 20 else '🟡 中' if m['cpu_percent'] < 50 else '🔴 负荷高'} | 主进程各线程实时 CPU 合计 |
| **掉帧率 (Jank Rate)** | **`{m['jank_percent']:.1f}%`** | {'🟢 流畅' if m['jank_percent'] < 10 else '🟡 轻微掉帧' if m['jank_percent'] < 25 else '🔴 卡顿'} | 超过 VSYNC 周期 (16.6ms/8.3ms) 比例 |
| **90分位帧耗时 (P90)** | **`{m.get('p90_frame_ms', 0)} ms`** | {'🟢 < 20ms' if m.get('p90_frame_ms', 0) < 20 else '🟡 20-35ms' if m.get('p90_frame_ms', 0) <= 35 else '🔴 > 35ms 卡顿'} | 90% 帧在此时间内完成绘制 |
| **应用实际 PSS 内存** | **`{m['ram_pss_mb']} MB`** | 🟢 正常 | 包含 Java 堆、Native 堆及系统共享物理内存 |
| **电池 / 芯片温度** | **`{m['temp_c']:.1f} °C`** | {'🟢 凉爽' if m['temp_c'] < 38 else '🟡 微温' if m['temp_c'] < 42 else '🔴 发热明显'} | 实时电池与 SoC 传感器温度 |

---

## 🧵 高负载线程剖析 (Top Active Threads)
{threads_md}

---

## 📝 实时操作与异常活动追踪流水线 (Live Activity Stream)
```text
{recent_events}
```
"""
            try:
                with open(REPORT_PATH, "w", encoding="utf-8") as f:
                    f.write(report_content)
            except: pass

    def start(self):
        print(f"[*] Started EPUB Reader Live Performance Profiler for: {PACKAGE_NAME}")
        t1 = threading.Thread(target=self.monitor_logcat, daemon=True)
        t2 = threading.Thread(target=self.monitor_gfxinfo, daemon=True)
        t3 = threading.Thread(target=self.monitor_cpu_and_mem, daemon=True)
        t1.start()
        t2.start()
        t3.start()

        try:
            while self.running:
                time.sleep(1)
        except KeyboardInterrupt:
            self.running = False
            print("[*] Profiler stopped.")

if __name__ == "__main__":
    analyzer = LivePerfAnalyzer()
    analyzer.start()
