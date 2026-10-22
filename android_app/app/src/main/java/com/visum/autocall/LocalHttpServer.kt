package com.visum.autocall

import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class LocalHttpServer(
    private val port: Int = 8080,
    private val onLog: (String) -> Unit,
    private val onCallTriggered: (List<String>) -> Unit,
    private val onStopTriggered: () -> Unit,
    private val getStatus: () -> Map<String, Any>
) {
    private var serverSocket: ServerSocket? = null
    private val isRunning = AtomicBoolean(false)
    private val threadPool = Executors.newCachedThreadPool()

    fun start() {
        if (isRunning.get()) return

        threadPool.execute {
            try {
                serverSocket = ServerSocket(port)
                isRunning.set(true)
                onLog("[+] Servidor HTTP iniciado y escuchando en el puerto $port")

                while (isRunning.get()) {
                    try {
                        val clientSocket = serverSocket?.accept() ?: break
                        threadPool.execute { handleClient(clientSocket) }
                    } catch (e: Exception) {
                        if (!isRunning.get()) break
                    }
                }
            } catch (e: Exception) {
                onLog("[-] Error iniciando servidor en puerto $port: ${e.message}")
            } finally {
                stop()
            }
        }
    }

    private fun handleClient(socket: Socket) {
        try {
            socket.use { s ->
                val reader = BufferedReader(InputStreamReader(s.getInputStream()))
                val firstLine = reader.readLine() ?: return
                
                // Formato: "GET /llamar?numero=+1234 HTTP/1.1"
                val parts = firstLine.split(" ")
                if (parts.size < 2) return

                val method = parts[0]
                val fullPath = parts[1]
                val pathAndQuery = fullPath.split("?", limit = 2)
                val path = pathAndQuery[0]
                val queryParams = if (pathAndQuery.size > 1) parseQuery(pathAndQuery[1]) else emptyMap()

                onLog("[HTTP] $method $fullPath desde ${s.inetAddress.hostAddress}")

                val output = s.getOutputStream()

                when {
                    path == "/" -> {
                        sendResponse(output, 200, "text/html; charset=UTF-8", """
                            <!DOCTYPE html>
                            <html>
                            <head><title>AutoCall Server</title><meta charset='UTF-8'></head>
                            <body style='font-family: sans-serif; background: #0f172a; color: #f8fafc; padding: 20px;'>
                                <h2>AutoCall Server Activo</h2>
                                <p>Servidor local en funcionamiento.</p>
                                <ul>
                                    <li><a style='color:#38bdf8' href='/status'>/status</a> - Ver estado JSON</li>
                                    <li><a style='color:#38bdf8' href='/llamar'>/llamar</a> - Iniciar secuencia de 3 llamadas predeterminadas</li>
                                    <li><a style='color:#38bdf8' href='/llamar?numero=+123456789'>/llamar?numero=...</a> - Llamar a un número específico</li>
                                </ul>
                            </body>
                            </html>
                        """.trimIndent())
                    }

                    path == "/status" -> {
                        val status = getStatus()
                        val json = buildJson(status)
                        sendResponse(output, 200, "application/json", json)
                    }

                    path == "/llamar" -> {
                        val singleNumber = queryParams["numero"]
                        val num1 = queryParams["numero1"]
                        val num2 = queryParams["numero2"]
                        val num3 = queryParams["numero3"]

                        val listToCall = mutableListOf<String>()
                        when {
                            !singleNumber.isNullOrBlank() -> {
                                listToCall.add(singleNumber)
                            }
                            !num1.isNullOrBlank() || !num2.isNullOrBlank() || !num3.isNullOrBlank() -> {
                                num1?.takeIf { it.isNotBlank() }?.let { listToCall.add(it) }
                                num2?.takeIf { it.isNotBlank() }?.let { listToCall.add(it) }
                                num3?.takeIf { it.isNotBlank() }?.let { listToCall.add(it) }
                            }
                            else -> {
                                // Llamar a la lista por defecto configurada en la App
                            }
                        }

                        onCallTriggered(listToCall)

                        val responseJson = """
                            {
                                "success": true,
                                "message": "Peticion de llamada recibida correctamente",
                                "target": "${singleNumber ?: "3 numeros preestablecidos"}"
                            }
                        """.trimIndent()
                        sendResponse(output, 200, "application/json", responseJson)
                    }

                    path == "/detener" -> {
                        onStopTriggered()
                        sendResponse(output, 200, "application/json", "{\"success\":true,\"message\":\"Secuencia detenida\"}")
                    }

                    else -> {
                        sendResponse(output, 404, "application/json", "{\"error\":\"Ruta no encontrada\"}")
                    }
                }
            }
        } catch (e: Exception) {
            // Ignorar desconexiones abruptas del cliente
        }
    }

    private fun sendResponse(output: OutputStream, statusCode: Int, contentType: String, body: String) {
        val statusText = if (statusCode == 200) "OK" else "Not Found"
        val bytes = body.toByteArray(Charsets.UTF_8)
        val header = "HTTP/1.1 $statusCode $statusText\r\n" +
                "Content-Type: $contentType\r\n" +
                "Content-Length: ${bytes.size}\r\n" +
                "Access-Control-Allow-Origin: *\r\n" +
                "Connection: close\r\n\r\n"
        output.write(header.toByteArray(Charsets.UTF_8))
        output.write(bytes)
        output.flush()
    }

    private fun parseQuery(query: String): Map<String, String> {
        val map = mutableMapOf<String, String>()
        for (param in query.split("&")) {
            val pair = param.split("=", limit = 2)
            if (pair.size == 2) {
                val key = URLDecoder.decode(pair[0], "UTF-8")
                val value = URLDecoder.decode(pair[1], "UTF-8")
                map[key] = value
            }
        }
        return map
    }

    private fun buildJson(map: Map<String, Any>): String {
        val items = map.entries.joinToString(",") { (k, v) ->
            val valStr = when (v) {
                is Boolean, is Number -> "$v"
                else -> "\"$v\""
            }
            "\"$k\": $valStr"
        }
        return "{$items}"
    }

    fun stop() {
        if (!isRunning.getAndSet(false)) return
        try {
            serverSocket?.close()
        } catch (e: Exception) {
            // Ignore
        }
        serverSocket = null
        onLog("[*] Servidor HTTP detenido.")
    }
}
