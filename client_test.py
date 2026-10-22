import requests
import sys
import time

# Configuración por defecto
# Cambia esta IP por la que muestre la app en la pantalla del celular
IP_CELULAR = "192.168.1.50"
PUERTO = 8080

def test_servidor(ip=IP_CELULAR, puerto=PUERTO):
    base_url = f"http://{ip}:{puerto}"
    print(f"[*] Verificando conexion con el celular en {base_url} ...")
    try:
        resp = requests.get(f"{base_url}/status", timeout=5)
        print(f"[+] Servidor respondiendo correctamente (HTTP {resp.status_code})")
        print(f"    Estado: {resp.text}")
        return True
    except requests.exceptions.RequestException as e:
        print(f"[-] No se pudo conectar al celular: {e}")
        print("    Verifique que el celular y el PC esten en la misma red Wi-Fi y el servidor este iniciado en la app.")
        return False

def disparar_llamadas(ip=IP_CELULAR, puerto=PUERTO, numero=None):
    base_url = f"http://{ip}:{puerto}"
    
    if numero:
        url = f"{base_url}/llamar?numero={numero}"
        print(f"[*] Enviando peticion de llamada para: {numero}")
    else:
        # Llama a los 3 numeros preestablecidos en la app
        url = f"{base_url}/llamar"
        print(f"[*] Enviando peticion para ejecutar las 3 llamadas preestablecidas...")

    try:
        response = requests.get(url, timeout=10)
        print(f"[+] Respuesta recibida (HTTP {response.status_code}):")
        print(response.json() if response.headers.get("Content-Type") == "application/json" else response.text)
    except requests.exceptions.RequestException as e:
        print(f"[-] Error enviando peticion: {e}")

if __name__ == "__main__":
    print("=" * 60)
    print("  CLIENTE DE CONTROL - LLAMADAS AUTOMATICAS ANDROID")
    print("=" * 60)

    # Permitir pasar IP y numero por linea de comandos
    # Ejemplo: python client_test.py 192.168.1.45 +123456789
    target_ip = sys.argv[1] if len(sys.argv) > 1 else IP_CELULAR
    target_num = sys.argv[2] if len(sys.argv) > 2 else None

    print(f"Destino: http://{target_ip}:{PUERTO}")
    
    # 1. Probar conectividad
    if test_servidor(target_ip, PUERTO):
        print("\n" + "-" * 40)
        # 2. Ejecutar llamada
        disparar_llamadas(target_ip, PUERTO, target_num)
        
        # 3. Monitorear progreso brevemente
        print("\n[*] Consultando progreso de las llamadas en el celular...")
        for _ in range(5):
            time.sleep(3)
            try:
                st = requests.get(f"http://{target_ip}:{PUERTO}/status", timeout=3)
                print(f"    -> Estado actual: {st.text.strip()}")
            except Exception:
                break
    else:
        print("\n[!] Asegurate de colocar la IP correcta del celular.")
        print(f"    Uso: python client_test.py <IP_CELULAR> [NUMERO_OPCIONAL]")
