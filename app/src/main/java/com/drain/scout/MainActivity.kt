package com.drain.scout

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import rikka.shizuku.Shizuku

class MainActivity : ComponentActivity() {

    private val viewModel: DrainScoutViewModel by viewModels()

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private val permissionListener =
        Shizuku.OnRequestPermissionResultListener { _, grantResult ->
            viewModel.refreshPermissionState()
            if (grantResult != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Shizuku permission denied", Toast.LENGTH_SHORT).show()
            }
        }

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        viewModel.refreshPermissionState()
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        viewModel.refreshPermissionState()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        Shizuku.addRequestPermissionResultListener(permissionListener)
        Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
        Shizuku.addBinderDeadListener(binderDeadListener)

        if (UpdatePrefs.enabled(this)) {
            UpdateScheduler.ensure(this)
            if (!UpdatePrefs.askedPermission(this)) {
                UpdatePrefs.setAskedPermission(this)
                requestNotificationPermission()
            }
        }

        setContent {
            DrainScoutApp(
                viewModel = viewModel,
                onRequestShizuku = ::requestShizuku,
                onShare = ::shareReport,
                onToggleUpdates = ::toggleUpdates
            )
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshPermissionState()
    }

    override fun onDestroy() {
        Shizuku.removeRequestPermissionResultListener(permissionListener)
        Shizuku.removeBinderReceivedListener(binderReceivedListener)
        Shizuku.removeBinderDeadListener(binderDeadListener)
        super.onDestroy()
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun toggleUpdates() {
        viewModel.toggleUpdateAlerts()
        if (viewModel.state.value.updateAlerts) requestNotificationPermission()
    }

    private fun requestShizuku() {
        try {
            if (!Shizuku.pingBinder()) {
                Toast.makeText(this, "Start Shizuku first, then try again", Toast.LENGTH_LONG).show()
                return
            }
            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                viewModel.refreshPermissionState()
                return
            }
            if (Shizuku.shouldShowRequestPermissionRationale()) {
                Toast.makeText(
                    this,
                    "Permission denied permanently. Allow DrainScout inside the Shizuku app.",
                    Toast.LENGTH_LONG
                ).show()
                return
            }
            Shizuku.requestPermission(REQUEST_CODE)
        } catch (e: Exception) {
            Toast.makeText(this, "Shizuku unavailable: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun shareReport(text: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "DrainScout report")
            putExtra(Intent.EXTRA_TEXT, text)
        }
        startActivity(Intent.createChooser(send, "Share report"))
    }

    private companion object {
        const val REQUEST_CODE = 4001
    }
}
