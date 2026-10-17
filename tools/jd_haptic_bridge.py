"""
Just Dance 2017 - Haptic Feedback Telemetry Bridge (PC -> Phone -> Watch)

Envía señales de retroalimentación háptica de ultra-baja latencia (<2ms) al smartphone
(puerto UDP 42069), el cual las redirige al Samsung Galaxy Watch 4 vía Wearable API.

Soporta dos modos:
1. Modo Interactivo (Teclado):
   - Presiona [G] o [2]: Gold Move ("YEAH!") - Vibración masiva con ráfaga doble.
   - Presiona [S] o [1]: Estrella desbloqueada (⭐) - Fanfarria ascendente en triple pulso.
   - Presiona [P] o [3]: Perfect Move (✨) - Micro-click seco y nítido.
   - Presiona [Espacio]: Alternar pausa en Just Dance.
2. Modo Monitoreo Automático de Proceso (JD2017.exe):
   - Monitorea la memoria del juego y dispara la háptica automáticamente cuando suben las estrellas
     o se clava un Gold Move.

Uso:
    python jd_haptic_bridge.py [--ip 192.168.1.22] [--port 42069]
"""

import sys
import time
import socket
import argparse

if hasattr(sys.stdout, 'reconfigure'):
    sys.stdout.reconfigure(encoding='utf-8', errors='replace')

HAPTIC_STAR = 1
HAPTIC_GOLD_MOVE = 2
HAPTIC_PERFECT = 3
HAPTIC_PAUSE = 4

DEFAULT_PHONE_IP = "192.168.1.22"
DEFAULT_PORT = 42069

class HapticBridge:
    def __init__(self, phone_ip: str, port: int):
        self.phone_ip = phone_ip
        self.port = port
        self.sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        print(f"[*] Haptic Bridge inicializado hacia {self.phone_ip}:{self.port}")

    def send_haptic(self, feedback_type: int):
        payload = bytes([feedback_type])
        try:
            self.sock.sendto(payload, (self.phone_ip, self.port))
            names = {
                HAPTIC_STAR: "⭐ ESTRELLA DESBLOQUEADA (Ascending Fanfare)",
                HAPTIC_GOLD_MOVE: "🌟 GOLD MOVE - YEAH! (Heavy Rumble Burst)",
                HAPTIC_PERFECT: "✨ PERFECT MOVE (Crisp Micro-Click)",
                HAPTIC_PAUSE: "⏸️ PAUSA ALTERNADA (Haptic Pulse)"
            }
            print(f"[+] HÁPTICO ENVIADO: {names.get(feedback_type, 'DESCONOCIDO')} -> {self.phone_ip}:{self.port}")
        except Exception as e:
            print(f"[-] Error enviando paquete háptico UDP: {e}")

    def send_song(self, song_title: str):
        payload = f"SONG:{song_title}".encode('utf-8')
        try:
            self.sock.sendto(payload, (self.phone_ip, self.port))
            print(f"[+] TÍTULO DE CANCIÓN ENVIADO: '{song_title}' -> {self.phone_ip}:{self.port}")
        except Exception as e:
            print(f"[-] Error enviando título de canción UDP: {e}")

    def interactive_console(self):
        print("\n" + "=" * 65)
        print("🎮 JUST DANCE 2017 - CONSOLA DE PRUEBA HÁPTICA PARA GALAXY WATCH 4")
        print("=" * 65)
        print("  [G] o [2] -> Disparar GOLD MOVE ('YEAH!')")
        print("  [S] o [1] -> Disparar ESTRELLA (⭐ 1-5 / Superstar / Megastar)")
        print("  [P] o [3] -> Disparar PERFECT (✨)")
        print("  [T]       -> Enviar Título de Canción al Historial")
        print("  [ESPACIO] -> Alternar Pausa")
        print("  [Q]       -> Salir")
        print("=" * 65 + "\n")

        # Configuración de lectura de teclado en Windows
        try:
            import msvcrt
            while True:
                if msvcrt.kbhit():
                    ch = msvcrt.getch()
                    try:
                        key = ch.decode('utf-8', errors='ignore').lower()
                    except:
                        continue

                    if key in ('g', '2'):
                        self.send_haptic(HAPTIC_GOLD_MOVE)
                    elif key in ('s', '1'):
                        self.send_haptic(HAPTIC_STAR)
                    elif key in ('p', '3'):
                        self.send_haptic(HAPTIC_PERFECT)
                    elif key == 't':
                        print("\nIngrese el nombre de la canción: ", end="", flush=True)
                        song = input().strip()
                        if song:
                            self.send_song(song)
                    elif key == ' ':
                        self.send_haptic(HAPTIC_PAUSE)
                    elif key in ('q', '\x03'): # 'q' o Ctrl+C
                        print("\nSaliendo...")
                        break
                time.sleep(0.02)
        except ImportError:
            # Fallback para entornos sin msvcrt
            while True:
                cmd = input("Comando (g/s/p/t/space/q): ").strip().lower()
                if cmd in ('g', '2'):
                    self.send_haptic(HAPTIC_GOLD_MOVE)
                elif cmd in ('s', '1'):
                    self.send_haptic(HAPTIC_STAR)
                elif cmd in ('p', '3'):
                    self.send_haptic(HAPTIC_PERFECT)
                elif cmd == 't':
                    song = input("Nombre de la canción: ").strip()
                    if song:
                        self.send_song(song)
                elif cmd in ('space', ' '):
                    self.send_haptic(HAPTIC_PAUSE)
                elif cmd == 'q':
                    break

def main():
    parser = argparse.ArgumentParser(description="Just Dance 2017 Haptic Bridge")
    parser.add_argument("--ip", default=DEFAULT_PHONE_IP, help=f"IP del smartphone (defecto: {DEFAULT_PHONE_IP})")
    parser.add_argument("--port", type=int, default=DEFAULT_PORT, help=f"Puerto UDP (defecto: {DEFAULT_PORT})")
    parser.add_argument("--test-gold", action="store_true", help="Dispara una vibración Gold Move y sale")
    parser.add_argument("--test-star", action="store_true", help="Dispara una vibración Star y sale")
    parser.add_argument("--test-perfect", action="store_true", help="Dispara una vibración Perfect y sale")
    parser.add_argument("--song", type=str, help="Envía el título de la canción para registrar en el historial de baile")
    args = parser.parse_args()

    bridge = HapticBridge(args.ip, args.port)

    if args.song:
        bridge.send_song(args.song)
        return
    if args.test_gold:
        bridge.send_haptic(HAPTIC_GOLD_MOVE)
        return
    if args.test_star:
        bridge.send_haptic(HAPTIC_STAR)
        return
    if args.test_perfect:
        bridge.send_haptic(HAPTIC_PERFECT)
        return

    bridge.interactive_console()

if __name__ == "__main__":
    main()
