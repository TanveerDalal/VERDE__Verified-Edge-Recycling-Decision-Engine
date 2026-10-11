package com.verde.app.pi

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.verde.app.data.PiScanOutcome
import com.verde.app.data.piPreviewFrames
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.roundToInt

@Composable
fun PiScreen(
    modifier: Modifier = Modifier,
    viewModel: PiViewModel = viewModel()
) {
    val state = viewModel.uiState

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Pi camera", style = MaterialTheme.typography.headlineSmall)

        OutlinedTextField(
            value = state.address,
            onValueChange = viewModel::onAddressChange,
            label = { Text("Pi address") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = viewModel::connect,
                enabled = state.connection != ConnectionStatus.Checking && !state.isScanning
            ) {
                Text("Connect")
            }
            ConnectionBadge(state.connection)
        }

        // Live view, only once the Pi is confirmed online
        if (state.connection == ConnectionStatus.Online) {
            PiLivePreview(address = state.address)
        }

        Button(
            onClick = viewModel::scan,
            enabled = state.connection == ConnectionStatus.Online && !state.isScanning,
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            if (state.isScanning) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(10.dp))
                Text("Pi is scanning…")
            } else {
                Text("Scan with Pi camera")
            }
        }

        state.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
        }

        state.outcome?.let { PiResultCard(it) }
    }
}

@Composable
private fun PiLivePreview(address: String) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var frame by remember { mutableStateOf<ImageBitmap?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var retryKey by remember { mutableIntStateOf(0) }

    // Stream only while the app is visible: stops in the background (battery, data, Pi CPU)
    LaunchedEffect(address, retryKey) {
        error = null
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            try {
                piPreviewFrames(address).collect { frame = it.asImageBitmap() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = "Live view stopped (${e.message ?: "connection lost"})"
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f) // Camera Module 3's shape
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        val current = frame
        if (current != null) {
            Image(
                bitmap = current,
                contentDescription = "Live view from the Pi camera",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        } else if (error == null) {
            CircularProgressIndicator()
        }

        // Square guide: the full-height centre square is exactly what the Pi crops
        Box(
            Modifier
                .fillMaxHeight()
                .aspectRatio(1f)
                .border(3.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
        )

        error?.let {
            Column(
                modifier = Modifier.align(Alignment.BottomCenter).padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(it, color = Color.White, style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { retryKey++ }) { Text("Retry live view") }
            }
        }
    }
}

@Composable
private fun ConnectionBadge(status: ConnectionStatus) {
    val (text, color) = when (status) {
        ConnectionStatus.Unknown -> "Not connected" to MaterialTheme.colorScheme.onSurfaceVariant
        ConnectionStatus.Checking -> "Checking…" to MaterialTheme.colorScheme.onSurfaceVariant
        ConnectionStatus.Online -> "● Online" to MaterialTheme.colorScheme.primary
        ConnectionStatus.Offline -> "● Offline" to MaterialTheme.colorScheme.error
    }
    Text(text, color = color, style = MaterialTheme.typography.bodyLarge)
}

@Composable
private fun PiResultCard(outcome: PiScanOutcome) {
    val r = outcome.result
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(r.label ?: "Unknown item", style = MaterialTheme.typography.headlineSmall)

            r.confidence?.let {
                Text("Confidence ${(it * 100).roundToInt()}%")
            }

            if (r.uncertain == true) {
                WarningBox("⚠ VERDE isn't sure about this one. Please check before disposing.")
            }

            r.bin?.let {
                Text(
                    "Bin: $it",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (!r.steps.isNullOrEmpty()) {
                Text("Steps", style = MaterialTheme.typography.titleSmall)
                r.steps.forEachIndexed { i, step -> Text("${i + 1}. $step") }
            }

            if (!r.warnings.isNullOrEmpty()) {
                r.warnings.forEach { WarningBox("⚠ $it") }
            }

            r.whyItMatters?.let {
                Text("Why it matters", style = MaterialTheme.typography.titleSmall)
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }

            HorizontalDivider()

            // Benchmark numbers
            Text("Round trip: ${outcome.roundTripMs} ms", style = MaterialTheme.typography.bodySmall)
            r.timingsMs?.forEach { (stage, ms) ->
                Text("Pi $stage: ${ms.roundToInt()} ms", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

// Amber box for warnings: dark text on a light amber background stays readable
@Composable
private fun WarningBox(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(text, modifier = Modifier.padding(10.dp), style = MaterialTheme.typography.bodyMedium)
    }
}