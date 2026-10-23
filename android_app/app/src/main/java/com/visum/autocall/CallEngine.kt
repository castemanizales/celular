package com.visum.autocall

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.telecom.TelecomManager
import android.telephony.TelephonyManager
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicBoolean

class CallEngine(
    private val context: Context,
    private val onLog: (String) -> Unit
) {
    private val coroutineScope = CoroutineScope(Dispatchers.Default + Job())
    private val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
    private val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager

    private val isExecuting = AtomicBoolean(false)
    private var currentJob: Job? = null

    fun isRunning(): Boolean = isExecuting.get()

    /**
     * Inicia la secuencia de llamadas a la lista de numeros provista
     * @param numbers Lista con los números telefónicos
     * @param ringTimeoutSecs Segundos máximos para dejar timbrar (por defecto 12s)
     */
    fun startCallSequence(numbers: List<String>, ringTimeoutSecs: Int = 12) {
        if (isExecuting.getAndSet(true)) {
            onLog("[!] Ya hay una secuencia de llamadas en curso. Ignorando nueva solicitud.")
            return
        }

        currentJob = coroutineScope.launch {
            try {
                val cleanList = numbers.filter { it.isNotBlank() }
                if (cleanList.isEmpty()) {
                    onLog("[-] Error: No hay números configurados para llamar.")
                    return@launch
                }

                onLog("==========================================")
                onLog("[*] INICIANDO SECUENCIA: ${cleanList.size} llamada(s)")
                onLog("[*] Tiempo máx. de timbrado: ${ringTimeoutSecs}s por número")
                onLog("==========================================")

                for ((index, number) in cleanList.withIndex()) {
                    if (!isActive) break

                    val callNumber = index + 1
                    onLog("\n>>> LLAMADA $callNumber DE ${cleanList.size}: $number <<<")
                    
                    // 1. Ejecutar llamada
                    val success = makeSingleCall(number)
                    if (!success) {
                        onLog("[-] No se pudo marcar el número: $number")
                        continue
                    }

                    // Breve pausa inicial para que el marcador procese la llamada
                    delay(1500L)

                    // 2. Esperar a que la llamada termine y el marcador se estabilice
                    onLog("[*] Esperando finalización de llamada...")
                    waitForCallCompletion(ringTimeoutSecs)

                    // 3. Colgar por seguridad
                    hangUpCall()

                    // 4. Pausa de enfriamiento entre llamadas (2 segundos para asegurar cierre del dialer)
                    if (callNumber < cleanList.size) {
                        onLog("[*] Esperando 2s antes de la siguiente llamada...")
                        delay(2000L)
                    }
                }

                onLog("\n[+] SECUENCIA COMPLETADA: Se procesaron todas las llamadas.")
            } catch (e: CancellationException) {
                onLog("[!] Secuencia de llamadas cancelada por el usuario.")
            } catch (e: Exception) {
                onLog("[-] Error en secuencia de llamadas: ${e.message}")
            } finally {
                hangUpCall()
                isExecuting.set(false)
            }
        }
    }

    /**
     * Espera activamente hasta que el estado del teléfono regrese a IDLE (llamada finalizada) y se estabilice.
     */
    @Suppress("DEPRECATION")
    @SuppressLint("MissingPermission")
    private suspend fun waitForCallCompletion(maxTimeoutSecs: Int) {
        val timeoutMs = maxTimeoutSecs * 1000L
        val startTime = System.currentTimeMillis()
        val pollInterval = 500L
        var stateDetected = false

        while (System.currentTimeMillis() - startTime < timeoutMs && currentCoroutineContext().isActive) {
            val state = try {
                telephonyManager.callState
            } catch (e: Exception) {
                TelephonyManager.CALL_STATE_IDLE
            }

            if (state == TelephonyManager.CALL_STATE_OFFHOOK || state == TelephonyManager.CALL_STATE_RINGING) {
                stateDetected = true
            } else if (state == TelephonyManager.CALL_STATE_IDLE && stateDetected) {
                onLog("[*] Llamada finalizada detectada. Estabilizando marcador...")
                // Esperar 1.5s adicionales en IDLE para asegurar que la app nativa de llamadas se cierre completamente
                delay(1500L)
                break
            }

            delay(pollInterval)
        }
    }

    /**
     * Realiza la llamada utilizando TelecomManager (soporta bloqueo de pantalla y segundo plano) con fallback a Intent
     */
    @SuppressLint("MissingPermission")
    private fun makeSingleCall(number: String): Boolean {
        return try {
            val clean = number.trim()
            val uri = Uri.parse("tel:${Uri.encode(clean)}")

            // TelecomManager.placeCall permite realizar llamadas en segundo plano o con pantalla bloqueada
            if (telecomManager != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                try {
                    val extras = Bundle().apply {
                        putBoolean(TelecomManager.EXTRA_START_CALL_WITH_SPEAKERPHONE, false)
                    }
                    telecomManager.placeCall(uri, extras)
                    onLog("[+] Marcando (TelecomManager): $clean")
                    return true
                } catch (e: Exception) {
                    onLog("[*] placeCall falló (${e.message}), usando Intent...")
                }
            }

            // Fallback a Intent ACTION_CALL
            val callIntent = Intent(Intent.ACTION_CALL, uri).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            context.startActivity(callIntent)
            onLog("[+] Marcando (Intent): $clean")
            true
        } catch (e: Exception) {
            onLog("[-] Falló la realización de llamada: ${e.localizedMessage}")
            false
        }
    }

    /**
     * Cuelga la llamada activa utilizando TelecomManager si está soportado (Android 9+)
     */
    @SuppressLint("MissingPermission")
    private fun hangUpCall() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val hungUp = telecomManager?.endCall() ?: false
                if (hungUp) {
                    onLog("[+] Llamada finalizada via TelecomManager.endCall()")
                } else {
                    onLog("[*] No había llamada activa para finalizar o no se pudo terminar.")
                }
            } else {
                onLog("[*] Versión de Android previa a Pie. Dial intent cerrado.")
            }
        } catch (e: SecurityException) {
            onLog("[!] Requiere permiso ANSWER_PHONE_CALLS para colgar automáticamente.")
        } catch (e: Exception) {
            onLog("[!] No se pudo colgar llamada: ${e.message}")
        }
    }

    /**
     * Cancela la secuencia actual
     */
    fun cancelSequence() {
        currentJob?.cancel()
        hangUpCall()
        isExecuting.set(false)
        onLog("[!] Secuencia detenida.")
    }
}
