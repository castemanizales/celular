package com.visum.autocall

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.visum.autocall.databinding.ActivityMainBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var autoCallService: AutoCallService? = null
    private var isBound = false
    private var isServerActive = false

    private val prefs by lazy {
        getSharedPreferences("autocall_prefs", Context.MODE_PRIVATE)
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as AutoCallService.LocalBinder
            autoCallService = binder.getService()
            isBound = true

            autoCallService?.setCallbacks(
                onLog = { msg -> runOnUiThread { appendLog(msg) } },
                onStatus = { active -> runOnUiThread { updateServerStatus(active) } }
            )

            updateServerStatus(true)
            appendLog("[+] Conectado al servicio en segundo plano.")
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            autoCallService = null
            isBound = false
            updateServerStatus(false)
            appendLog("[-] Desconectado del servicio.")
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val callGranted = permissions[Manifest.permission.CALL_PHONE] ?: false
        val stateGranted = permissions[Manifest.permission.READ_PHONE_STATE] ?: false
        
        if (callGranted) {
            appendLog("[✓] Permiso para realizar llamadas concedido.")
        } else {
            appendLog("[!] ALERTA: Permiso de llamadas DENEGADO. La app no podrá marcar.")
            Toast.makeText(this, "Se requiere permiso de llamada", Toast.LENGTH_LONG).show()
        }

        if (stateGranted) {
            appendLog("[✓] Permiso de estado telefónico concedido.")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        loadPreferences()
        setupListeners()
        checkPermissions()
        refreshIpDisplay()
    }

    override fun onResume() {
        super.onResume()
        refreshIpDisplay()
    }

    private fun checkPermissions() {
        val permissionsToRequest = mutableListOf(
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_PHONE_STATE
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            permissionsToRequest.add(Manifest.permission.ANSWER_PHONE_CALLS)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val missing = permissionsToRequest.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
        } else {
            appendLog("[✓] Todos los permisos necesarios están concedidos.")
        }
    }

    private fun setupListeners() {
        binding.btnToggleServer.setOnClickListener {
            if (isServerActive) {
                stopServerService()
            } else {
                startServerService()
            }
        }

        binding.btnSaveConfig.setOnClickListener {
            savePreferences()
            Toast.makeText(this, "Configuración guardada", Toast.LENGTH_SHORT).show()
            appendLog("[✓] Configuración de números y timbrado guardada.")
        }

        binding.btnTestCall.setOnClickListener {
            savePreferences()
            if (autoCallService != null) {
                appendLog("[*] Ejecutando prueba manual desde la app...")
                autoCallService?.triggerCalls()
            } else {
                Toast.makeText(this, "Inicia el servidor primero para inicializar el motor de llamadas", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnClearLogs.setOnClickListener {
            binding.tvLogs.text = ""
        }
    }

    private fun startServerService() {
        val intent = Intent(this, AutoCallService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        isServerActive = true
        updateServerStatus(true)
    }

    private fun stopServerService() {
        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
        }
        val intent = Intent(this, AutoCallService::class.java)
        stopService(intent)
        isServerActive = false
        autoCallService = null
        updateServerStatus(false)
    }

    private fun updateServerStatus(active: Boolean) {
        isServerActive = active
        if (active) {
            binding.tvServerStatusBadge.text = "ACTIVO"
            binding.tvServerStatusBadge.setTextColor(Color.parseColor("#10B981"))
            binding.btnToggleServer.text = getString(R.string.btn_stop_server)
            binding.btnToggleServer.backgroundTintList = ContextCompat.getColorStateList(this, R.color.accent_red)
        } else {
            binding.tvServerStatusBadge.text = "DETENIDO"
            binding.tvServerStatusBadge.setTextColor(Color.parseColor("#EF4444"))
            binding.btnToggleServer.text = getString(R.string.btn_start_server)
            binding.btnToggleServer.backgroundTintList = ContextCompat.getColorStateList(this, R.color.accent_green)
        }
        refreshIpDisplay()
    }

    private fun refreshIpDisplay() {
        val ip = NetworkUtils.getLocalIpAddress(this)
        binding.tvServerUrl.text = "http://$ip:8080"
    }

    private fun loadPreferences() {
        binding.etPhone1.setText(prefs.getString("phone_1", ""))
        binding.etPhone2.setText(prefs.getString("phone_2", ""))
        binding.etPhone3.setText(prefs.getString("phone_3", ""))
        binding.etRingDuration.setText(prefs.getInt("ring_duration", 22).toString())
    }

    private fun savePreferences() {
        val duration = binding.etRingDuration.text.toString().toIntOrNull() ?: 22
        prefs.edit()
            .putString("phone_1", binding.etPhone1.text.toString().trim())
            .putString("phone_2", binding.etPhone2.text.toString().trim())
            .putString("phone_3", binding.etPhone3.text.toString().trim())
            .putInt("ring_duration", duration)
            .apply()
    }

    private fun appendLog(message: String) {
        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val formatted = "[$time] $message\n"
        binding.tvLogs.append(formatted)

        binding.scrollViewConsole.post {
            binding.scrollViewConsole.fullScroll(android.widget.ScrollView.FOCUS_DOWN)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
        }
    }
}
