package com.visum.autocall

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.telecom.TelecomManager
import android.telephony.PhoneStateListener
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
    private val callFinishedSignal = CompletableDeferred<Unit>()

    fun isRunning(): Boolean = isExecuting.get()

    /**
     * Inicia la secuencia de llamadas a la lista de numeros provista
     * @param numbers Lista con los números telefónicos
     * @param ringTimeoutSecs Segundos máximos para dejar timbrar (~22 seg para 5 timbres)
     */
    fun startCallSequence(numbers: List<String>, ringTimeoutSecs: Int = 22) {
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
                onLog("[*] Tiempo de timbrado configurado: ${ringTimeoutSecs}s (~5 timbres)")
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

                    // 2. Esperar que transcurra el tiempo de timbrado o que cuelguen
                    onLog("[*] Esperando timbrado (máx ${ringTimeoutSecs}s)...")
                    withTimeoutOrNull(ringTimeoutSecs * 1000L) {
                        // Esperar hasta que termine o se agote el tiempo
                        val pollInterval = 1000L
                        var elapsed = 0
                        while (elapsed < ringTimeoutSecs && isActive) {
                            delay(pollInterval)
                            elapsed++
                            if (elapsed % 5 == 0) {
                                onLog("    ... timbrando (${elapsed}s / ${ringTimeoutSecs}s)")
                            }
                        }
                    }

                    // 3. Colgar la llamada si aun sigue timbrando
                    onLog("[*] Tiempo de timbrado cumplido. Procediendo a colgar...")
                    hangUpCall()

                    // 4. Pausa de enfriamiento entre llamadas (3 segundos)
                    if (callNumber < cleanList.size) {
                        onLog("[*] Esperando 3 segundos antes de la siguiente llamada...")
                        delay(3000L)
                    }
                }

                onLog("\n[+] SECUENCIA COMPLETADA: Se finalizaron todas las llamadas.")
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
     * Lanza el Intent para llamar directamente al numero
     */
    @SuppressLint("MissingPermission")
    private fun makeSingleCall(number: String): Boolean {
        return try {
            val clean = number.trim()
            val uri = Uri.parse("tel:${Uri.encode(clean)}")
            val callIntent = Intent(Intent.ACTION_CALL, uri).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            context.startActivity(callIntent)
            onLog("[+] Marcando: $clean")
            true
        } catch (e: Exception) {
            onLog("[-] Falló startActivity(ACTION_CALL): ${e.localizedMessage}")
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
