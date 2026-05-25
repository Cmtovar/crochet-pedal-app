package com.crochet.companion

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.crochet.companion.ble.ConnectionState
import com.crochet.companion.ble.PedalConnectionService
import com.crochet.companion.ble.PedalEvent
import com.crochet.companion.ui.theme.CrochetCompanionTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var pedalService: PedalConnectionService

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        pedalService = PedalConnectionService(this)

        // Request BLE permissions then connect
        val permissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { grants ->
            if (grants.values.all { it }) {
                pedalService.connect()
            }
        }

        val permissions = mutableListOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT
        ).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Already covered above for API 31+
            } else {
                add(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }

        permissionLauncher.launch(permissions.toTypedArray())

        setContent {
            val windowSizeClass = calculateWindowSizeClass(this)
            CrochetCompanionTheme {
                CrochetApp(
                    widthSizeClass = windowSizeClass.widthSizeClass,
                    pedalService = pedalService
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CrochetApp(
    widthSizeClass: WindowWidthSizeClass,
    pedalService: PedalConnectionService
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val updater = remember { AppUpdater(BuildConfig.GITHUB_REPO) }
    var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    var showUpdateDialog by remember { mutableStateOf(false) }
    var isDownloading by remember { mutableStateOf(false) }

    val connectionState by pedalService.connectionState.collectAsState()
    var lastEvent by remember { mutableStateOf<PedalEvent?>(null) }

    LaunchedEffect(Unit) {
        pedalService.onPedalEvent = { event ->
            lastEvent = event
        }
    }

    LaunchedEffect(Unit) {
        val info = updater.checkForUpdate(BuildConfig.VERSION_NAME)
        if (info != null) {
            updateInfo = info
            showUpdateDialog = true
        }
    }

    if (showUpdateDialog && updateInfo != null) {
        AlertDialog(
            onDismissRequest = { if (!isDownloading) showUpdateDialog = false },
            title = { Text("Update Available") },
            text = {
                if (isDownloading) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.padding(end = 16.dp))
                        Text("Downloading update...")
                    }
                } else {
                    Text("A new version is available: ${updateInfo!!.releaseName}\n\nCurrent: v${BuildConfig.VERSION_NAME}")
                }
            },
            confirmButton = {
                if (!isDownloading) {
                    TextButton(onClick = {
                        isDownloading = true
                        scope.launch {
                            updater.downloadAndInstall(context, updateInfo!!)
                            isDownloading = false
                            showUpdateDialog = false
                        }
                    }) { Text("Update") }
                }
            },
            dismissButton = {
                if (!isDownloading) {
                    TextButton(onClick = { showUpdateDialog = false }) { Text("Later") }
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Crochet Companion") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { innerPadding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            color = MaterialTheme.colorScheme.background
        ) {
            ConnectionScreen(
                connectionState = connectionState,
                lastEvent = lastEvent,
                onConnect = { pedalService.connect() },
                onDisconnect = { pedalService.disconnect() }
            )
        }
    }
}

@Composable
fun ConnectionScreen(
    connectionState: ConnectionState,
    lastEvent: PedalEvent?,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Pedal Bridge",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "Status: ${connectionState.name}",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = when (connectionState) {
                        ConnectionState.CONNECTED -> "Pedals connected and ready"
                        ConnectionState.CONNECTING -> "Connecting to CrochetPedals..."
                        ConnectionState.SCANNING -> "Scanning for device..."
                        ConnectionState.DISCONNECTED -> "Tap Connect to find pedals"
                        ConnectionState.DISCONNECTING -> "Disconnecting..."
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (lastEvent != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "Last Event",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = lastEvent.name,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        when (connectionState) {
            ConnectionState.DISCONNECTED -> {
                Button(onClick = onConnect) { Text("Connect") }
            }
            ConnectionState.CONNECTED -> {
                Button(onClick = onDisconnect) { Text("Disconnect") }
            }
            else -> {
                CircularProgressIndicator()
            }
        }
    }
}
