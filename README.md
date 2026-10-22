# AutoCall Server - Sistema de Llamadas Automáticas vía HTTP Local

Este proyecto permite controlar un celular Android desde un computador conectado a la misma red Wi-Fi para realizar **3 llamadas automáticas consecutivas** a números preestablecidos (o a un número específico enviado por parámetro), dejando timbrar aproximadamente 5 veces (~22 segundos) antes de pasar automáticamente a la siguiente llamada.

---

## 📋 Arquitectura del Proyecto

1. **Servidor HTTP en Android (Foreground Service):**
   - Corre dentro de un servicio en segundo plano persistente con `WakeLock` y `WifiLock` para que el sistema operativo no lo detenga.
   - Escucha en el puerto `8080` de la red Wi-Fi local (ej: `http://192.168.1.50:8080`).
   - Dispone de endpoints HTTP (`/llamar`, `/status`, `/detener`).

2. **Motor de Llamadas (`CallEngine.kt`):**
   - Utiliza `Intent.ACTION_CALL` para marcar directamente a la red móvil.
   - Cuenta el tiempo de timbrado (~5 timbres = 22 segundos configurables).
   - Corta la llamada automáticamente usando `TelecomManager.endCall()` o detecta cuando finaliza para avanzar.
   - Realiza una breve pausa de enfriamiento (3 segundos) entre llamadas para que el módem telefónico se reinicie correctamente antes del siguiente número.

3. **Cliente de Control en Computador (`client_test.py`):**
   - Script en Python ejecutado dentro del entorno virtual creado en este repositorio.
   - Se comunica con el celular mediante `requests`.

---

## 📁 Estructura del Repositorio

```text
celular/
│
├── .gitignore                   # Exclusiones de Git (venv, temporales, gradle, build)
├── README.md                    # Esta documentación paso a paso
├── client_test.py               # Script cliente en Python para enviar peticiones HTTP
├── venv/                        # Entorno virtual de Python configurado
│
└── android_app/                 # Proyecto completo de Android Studio
    ├── build.gradle             # Configuración raíz de Gradle
    ├── settings.gradle          # Inclusión del módulo :app
    ├── gradle.properties        # Propiedades de compilación
    ├── local.properties         # Ruta al SDK de Android
    └── app/
        ├── build.gradle         # Dependencias AndroidX, Material 3 y Coroutines
        └── src/main/
            ├── AndroidManifest.xml   # Permisos de llamada, red y servicio
            ├── java/com/visum/autocall/
            │   ├── MainActivity.kt    # Interfaz visual, permisos y consola en vivo
            │   ├── AutoCallService.kt # Servicio HTTP en primer plano
            │   ├── CallEngine.kt      # Lógica secuencial de llamadas y timbrado
            │   ├── LocalHttpServer.kt # Servidor HTTP socket embebido
            │   └── NetworkUtils.kt    # Utilidad para detectar IP Wi-Fi
            └── res/                   # Layouts, temas oscuros y recursos
```

---

## 🚀 Pasos Realizados en el Proyecto

### 1. Inicialización de Git y Entorno Virtual
- Se inicializó el repositorio Git en la carpeta raíz (`git init`).
- Se configuró el archivo `.gitignore` para ignorar archivos binarios y entornos.
- Se creó el entorno virtual de Python en la carpeta `venv/`.
- Se instaló la librería `requests` dentro de `venv/` para realizar las pruebas.

### 2. Construcción de la App Android
- Se implementó la interfaz moderna (tema oscuro) con:
  - Tarjeta de estado del servidor y botón Iniciar/Detener.
  - Campos para configurar los 3 números telefónicos predeterminados.
  - Campo para ajustar los segundos de timbrado (por defecto 22 segundos, ~5 timbres).
  - Consola de eventos en vivo que muestra peticiones HTTP y progreso de llamadas.
- Se configuraron los permisos necesarios en `AndroidManifest.xml`:
  - `android.permission.CALL_PHONE`: Para lanzar llamadas sin intervención manual.
  - `android.permission.READ_PHONE_STATE`: Para monitorear el estado de la línea.
  - `android.permission.ANSWER_PHONE_CALLS`: Para colgar automáticamente al cumplirse los 5 timbres.
  - `android.permission.FOREGROUND_SERVICE`: Para mantener el servidor activo en segundo plano.
  - `android.permission.INTERNET` y `ACCESS_WIFI_STATE`: Para el servidor HTTP local.

---

## 📱 Cómo Compilar e Instalar la App en el Celular

### Opción A: Desde Android Studio
1. Abre **Android Studio**.
2. Selecciona **Open** y navega a la carpeta `android_app`.
3. Conecta tu celular por cable USB con la **Depuración USB** activada (o usa emparejamiento Wi-Fi ADB).
4. Presiona el botón **Run** (ícono verde de reproducir) para compilar e instalar la app directamente.

### Opción B: Permisos en el Celular
Al abrir la app por primera vez:
1. Acepta los permisos de **Teléfono / Realizar llamadas** y **Notificaciones**.
2. Ingresa los 3 números preestablecidos en la pantalla y pulsa **GUARDAR**.
3. Pulsa el botón **INICIAR SERVIDOR**.
4. La pantalla mostrará la URL local del celular, por ejemplo:
   ```text
   http://192.168.1.50:8080
   ```
*(Asegúrate de que el celular esté conectado a la misma red Wi-Fi que el computador).*

> **Recomendación para segundo plano:**  
> Ve a la configuración de aplicaciones de Android -> **AutoCall Server** -> **Batería** -> Selecciona **"Sin restricciones"** (evita que el sistema duerma el servidor HTTP).

---

## 💻 Cómo Usar desde el Computador

### 1. Activar el Entorno Virtual de Python
Abre una terminal PowerShell o CMD en esta carpeta:

```powershell
.\venv\Scripts\activate
```

### 2. Ejecutar Petición con el Script Incluido
Puedes disparar la llamada con el script `client_test.py`:

```powershell
# Llamar a los 3 números preestablecidos en la app:
python client_test.py 192.168.1.50

# O llamar a un número específico:
python client_test.py 192.168.1.50 +573001234567
```

### 3. Ejecutar desde tu propio código Python
Tal como lo requieres:

```python
import requests

# Reemplaza con la IP que muestra la pantalla del celular
ip_celular = "192.168.1.50"
numero = "+123456789"

# Opción 1: Disparar las 3 llamadas preestablecidas
response = requests.get(f"http://{ip_celular}:8080/llamar")
print(response.json())

# Opción 2: Pasar un número específico
response = requests.get(f"http://{ip_celular}:8080/llamar?numero={numero}")
print(response.json())
```

### 4. Consultar Estado en Cualquier Momento
```bash
curl http://192.168.1.50:8080/status
```
Responde con un JSON indicando si el servidor está en línea y si está ejecutando llamadas en ese instante.

### 5. Detener Secuencia en Curso
```bash
curl http://192.168.1.50:8080/detener
```
Cancela la secuencia y cuelga de inmediato.
