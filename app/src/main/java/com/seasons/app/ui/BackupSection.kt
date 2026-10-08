package com.seasons.app.ui

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.seasons.app.data.ExportFiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.LocalDate

private const val MAX_IMPORT_BYTES = 100L * 1024 * 1024

private fun readUriText(context: Context, uri: Uri): String {
    val stream = context.contentResolver.openInputStream(uri) ?: throw IOException("Cannot open the file")
    stream.use { input ->
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var total = 0L
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            total += n
            if (total > MAX_IMPORT_BYTES) throw com.seasons.app.data.BackupException("The file is too large to be a backup.")
            out.write(buffer, 0, n)
        }
        return out.toString(Charsets.UTF_8.name())
    }
}

/** Export (JSON, CSV) and import, shown inside Settings. No cloud, no accounts: files go through the share sheet and picker. */
@Composable
fun BackupSection(vm: BackupViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pending by vm.pending.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()

    // Shows every file, because some apps label a JSON file as generic binary. A wrong file is rejected by the check.
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.prepareImport { readUriText(context, uri) }
    }

    fun export(fileName: String, mime: String, title: String, build: suspend () -> String) {
        scope.launch {
            val text = build()
            val file = withContext(Dispatchers.IO) { ExportFiles.write(context, fileName, text) }
            ExportFiles.share(context, file, mime, title)
        }
    }

    pending?.let { p ->
        AlertDialog(
            onDismissRequest = vm::cancelImport,
            title = { Text("Replace all data?") },
            text = {
                Text(
                    "This file has ${p.summary}.\n\n" +
                        "Importing deletes everything in the app now and replaces it with the file. " +
                        "This cannot be undone. Export a backup first if you want to keep the current data.",
                )
            },
            confirmButton = { TextButton(onClick = vm::confirmImport) { Text("Replace", color = Amber) } },
            dismissButton = { TextButton(onClick = vm::cancelImport) { Text("Cancel") } },
        )
    }
    message?.let { m ->
        AlertDialog(
            onDismissRequest = vm::clearMessage,
            text = { Text(m) },
            confirmButton = { TextButton(onClick = vm::clearMessage) { Text("OK") } },
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Backup", color = Grey1, style = MaterialTheme.typography.labelLarge)
        OutlinedButton(
            onClick = { export("seasons-backup-${LocalDate.now()}.json", "application/json", "Export backup", vm::backupJson) },
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) { Text("Export backup (JSON)") }
        OutlinedButton(
            onClick = { export("seasons-logs-${LocalDate.now()}.csv", "text/csv", "Export logs", vm::logsCsv) },
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) { Text("Export logs (CSV)") }
        OutlinedButton(
            onClick = { picker.launch(arrayOf("*/*")) },
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) { Text("Import backup") }
        Text(
            "Files go through the share sheet and the file picker. Nothing is sent anywhere by the app.",
            color = Grey1,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
