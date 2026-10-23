# AutoCall Server - Sistema de Llamadas Automáticas vía HTTP Local

Este proyecto permite controlar un celular Android desde un computador o cualquier dispositivo conectado a la misma red Wi-Fi para realizar **llamadas automáticas consecutivas** a números preestablecidos (o a un número específico enviado por parámetro HTTP), dejando timbrar el tiempo configurado y colgando/avanzando automáticamente entre cada una.

---

## 📋 Arquitectura y Novedades del Sistema

1. **Servidor HTTP Local en Android (Foreground Service):**
   - Corre dentro de un servicio en primer plano persistente con `WakeLock` y `WifiLock` para evitar que el sistema operativo lo congele o cierre.
   - Escucha en el puerto `8080` de la red Wi-Fi local (ej: `http://192.168.1.50:8080`).
   - Totalmente adaptado para **Android 14 (API 34)** con tipo de servicio en primer plano `dataSync` / `specialUse`.
   - **Panel web interactivo integrado:** Al acceder a la raíz (`http://<IP_CELULAR>:8080/`) desde cualquier navegador web se muestra una interfaz visual para probar llamadas y consultar el estado rápidamente.

2. **Motor de Llamadas Inteligente (`CallEngine.kt`):**
   - Ejecuta llamadas telefónicas directas mediante `Intent.ACTION_CALL`.
   - **Detección activa del estado de llamada (`TelephonyManager.callState`):** Monitorea la línea telefónica en tiempo real. Si la llamada es finalizada por el destinatario, cae a buzón o se cuelga antes del tiempo máximo, la app lo detecta de inmediato y avanza a la siguiente llamada sin tiempos muertos innecesarios.
   - **Cuelgue automático:** Al alcanzar el tiempo máximo de timbrado configurado (por defecto 12s, ajustable en la app o código), cuelga mediante `TelecomManager.endCall()`.
   - **Enfriamiento optimizado:** Aplica una pausa breve de 1.5 segundos entre llamadas para permitir que el módem celular y la red móvil se restablezcan correctamente.

3. **Cliente de Control en Computador:**
   - Script interactivo en Python ([client_test.py](file:///c:/Users/lavoz/Documents/ANTIGRAVITY/visum/celular/client_test.py)) con entorno virtual listo (`venv/`).
   - Compatible con cualquier lenguaje o herramienta HTTP (`curl`, `requests`, PowerShell `Invoke-RestMethod`, Postman, etc.).

---

## 📁 Estructura del Repositorio

```text
celular/
│
├── .gitignore                   # Exclusiones de Git (venv, temporales, gradle, build)
├── README.md                    # Documentación detallada del proyecto
├── client_test.py               # Script cliente en Python para pruebas y automatización
├── venv/                        # Entorno virtual de Python configurado
│
└── android_app/                 # Proyecto de Android Studio (Target SDK 34, Gradle 8.13)
    ├── build.gradle             # Configuración raíz de Gradle (AGP 8.13.2, Kotlin 1.9.24)
    ├── settings.gradle          # Módulos del proyecto
    ├── gradle.properties        # Opciones JVM y rutas
    ├── local.properties         # Ruta al SDK de Android
    └── app/
        ├── build.gradle         # Configuración del módulo de la aplicación
        ├── build/outputs/apk/   # Binarios APK compilados listos para instalar
        │   └── debug/app-debug.apk
        └── src/main/
            ├── AndroidManifest.xml   # Permisos de llamada, red y Foreground Service
            ├── java/com/visum/autocall/
            │   ├── MainActivity.kt    # Interfaz visual, gestión de permisos y consola en vivo
            │   ├── AutoCallService.kt # Servicio en primer plano y ciclo de vida
            │   ├── CallEngine.kt      # Motor secuencial de llamadas y monitoreo de línea
            │   ├── LocalHttpServer.kt # Servidor HTTP socket multihilo y endpoints
            │   └── NetworkUtils.kt    # Detección automática de la IP Wi-Fi
            └── res/                   # Layouts, temas oscuros e iconos de la app
```

---

## 🌐 Endpoints de la API HTTP

El servidor expone los siguientes endpoints en `http://<IP_CELULAR>:8080`:

| Método | Endpoint | Parámetros | Descripción |
| :--- | :--- | :--- | :--- |
| `GET` | `/` | Ninguno | Panel web HTML para comprobar conectividad y ejecutar pruebas desde el navegador. |
| `GET` | `/status` | Ninguno | Retorna el estado en JSON: `{"running": true, "calling": false}`. |
| `GET` | `/llamar` | Ninguno | Inicia la secuencia de las 3 llamadas predeterminadas guardadas en la app. |
| `GET` | `/llamar` | `numero=+573001234567` | Realiza una llamada al número especificado en el parámetro. |
| `GET` | `/llamar` | `numero1=...&numero2=...&numero3=...` | Realiza la secuencia a los números pasados en la URL. |
| `GET` | `/detener` | Ninguno | Cancela la secuencia actual y cuelga de inmediato la llamada en curso. |

---

## 📱 Instalación y Configuración de la App en el Celular

### Opción 1: Instalar directamente el APK precompilado (Más rápido)
El repositorio ya cuenta con el APK listo para instalar en:
`android_app/app/build/outputs/apk/debug/app-debug.apk`

- **Vía cable USB con ADB:**
  ```powershell
  adb install -r android_app/app/build/outputs/apk/debug/app-debug.apk
  ```
- **O copiando el archivo:** Transfiere el archivo `app-debug.apk` a tu celular por WhatsApp, Drive, Telegram o cable USB y pulsa sobre él para instalarlo.

### Opción 2: Compilar desde Android Studio
1. Abre **Android Studio**.
2. Selecciona **Open** y elige la carpeta `android_app`.
3. Conecta tu celular con la **Depuración USB** activada.
4. Presiona el botón verde **Run** (`Shift + F10`) para compilar e instalar.

---

### ⚙️ Permisos y Configuración Inicial en el Celular

1. **Permisos al abrir la app:**
   - **Teléfono / Realizar y gestionar llamadas:** Necesario para marcar (`CALL_PHONE`).
   - **Estado del teléfono:** Necesario para detectar cuándo cuelgan o se corta la llamada (`READ_PHONE_STATE`).
   - **Control de llamadas (Android 9+):** Permiso para colgar automáticamente al cumplirse el tiempo (`ANSWER_PHONE_CALLS`).
   - **Notificaciones (Android 13+):** Necesario para mantener el servicio activo en segundo plano (`POST_NOTIFICATIONS`).

2. **Configuración de números:**
   - Ingresa los números telefónicos deseados en los campos de la interfaz.
   - Ajusta los segundos máximos de timbrado (por defecto 12s o 22s según la necesidad).
   - Presiona **GUARDAR CONFIGURACIÓN**.

3. **Iniciar el servidor:**
   - Presiona el botón **INICIAR SERVIDOR**.
   - En pantalla aparecerá la URL asignada, por ejemplo: `http://192.168.1.50:8080`.

4. **Optimización de Batería (Crítico para segundo plano):**
   - Ve a **Ajustes de Android** -> **Aplicaciones** -> **AutoCall Server**.
   - En **Batería**, selecciona la opción **"Sin restricciones"** (Unrestricted).
   - Esto garantiza que el servidor HTTP siga respondiendo aunque la pantalla se apague.

---

## 💻 Cómo Controlar y Realizar Llamadas desde el Computador

Asegúrate de que el computador y el celular estén conectados a la **misma red Wi-Fi**.

### 1. Usar el Script en Python (`client_test.py`)

Abre una terminal PowerShell o CMD en la carpeta raíz del proyecto:

```powershell
# 1. Activar el entorno virtual:
.\venv\Scripts\activate

# 2. Ejecutar prueba hacia la IP del celular:
# Llama a los 3 números predeterminados en la app:
python client_test.py 192.168.1.50

# O llamar a un número en particular:
python client_test.py 192.168.1.50 +573001234567
```

### 2. Integrar en tu propio código Python

```python
import requests

IP_CELULAR = "192.168.1.50"
BASE_URL = f"http://{IP_CELULAR}:8080"

# 1. Verificar estado del servidor
status = requests.get(f"{BASE_URL}/status", timeout=3).json()
print("Estado del servidor:", status)

# 2. Opción A: Disparar llamadas a la lista preestablecida
resp = requests.get(f"{BASE_URL}/llamar", timeout=5)
print(resp.json())

# 2. Opción B: Disparar llamada a un número específico
numero = "+573001234567"
resp = requests.get(f"{BASE_URL}/llamar?numero={numero}", timeout=5)
print(resp.json())

# 3. Detener secuencia si es necesario
# requests.get(f"{BASE_URL}/detener", timeout=5)
```

### 3. Usar desde Terminal / Consola (cURL o PowerShell)

- **Consultar estado:**
  ```bash
  curl http://192.168.1.50:8080/status
  ```

- **Disparar llamadas preestablecidas:**
  ```bash
  curl http://192.168.1.50:8080/llamar
  ```

- **Llamar a un número específico:**
  ```bash
  curl "http://192.168.1.50:8080/llamar?numero=%2B573001234567"
  ```

- **En PowerShell nativo (sin herramientas externas):**
  ```powershell
  Invoke-RestMethod -Uri "http://192.168.1.50:8080/llamar"
  ```

- **Detener llamadas en curso:**
  ```bash
  curl http://192.168.1.50:8080/detener
  ```

---

## 🛠️ Tecnologías y Dependencias

- **Android SDK:** `minSdk: 26 (Android 8.0)`, `compileSdk / targetSdk: 34 (Android 14)`.
- **Gradle:** Gradle 8.13 y Android Gradle Plugin 8.13.2.
- **Kotlin:** 1.9.24 con Coroutines (`kotlinx-coroutines-android`).
- **Arquitectura:** Foreground Service multihilo con ServerSocket nativo, TelecomManager y TelephonyManager.
- **Python:** 3.10+ con librería `requests`.
