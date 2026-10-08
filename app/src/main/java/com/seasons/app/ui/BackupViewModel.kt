package com.seasons.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seasons.app.data.BackupData
import com.seasons.app.data.BackupException
import com.seasons.app.data.Repository
import com.seasons.app.data.logsToCsv
import com.seasons.app.data.parseBackup
import com.seasons.app.data.toJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant

/** A backup that was read and checked, waiting for the user to confirm the replace. */
data class PendingImport(val data: BackupData) {
    val summary: String
        get() = listOf(
            count(data.trackers.size, "tracker"),
            count(data.logs.size, "log entry", "log entries"),
            count(data.groups.size, "group"),
            count(data.summaries.size, "season summary", "season summaries"),
        ).joinToString(", ")

    private fun count(n: Int, one: String, many: String = one + "s") = "$n ${if (n == 1) one else many}"
}

class BackupViewModel(private val repo: Repository) : ViewModel() {
    private val _pending = MutableStateFlow<PendingImport?>(null)
    val pending: StateFlow<PendingImport?> = _pending

    /** One-shot result text for the user ("Imported ..." or "Could not import ..."). */
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    fun clearMessage() {
        _message.value = null
    }

    fun cancelImport() {
        _pending.value = null
    }

    suspend fun backupJson(): String = withContext(Dispatchers.Default) { repo.readBackup().toJson(Instant.now()) }

    suspend fun logsCsv(): String = withContext(Dispatchers.Default) {
        val data = repo.readBackup()
        logsToCsv(data.trackers, data.groups, data.logs)
    }

    /** Reads, parses and fully checks the file. Nothing is changed yet. */
    fun prepareImport(readText: suspend () -> String) {
        viewModelScope.launch {
            try {
                val text = withContext(Dispatchers.IO) { readText() }
                _pending.value = PendingImport(withContext(Dispatchers.Default) { parseBackup(text) })
            } catch (e: BackupException) {
                _message.value = "Could not import: ${e.message}"
            } catch (e: java.io.IOException) {
                _message.value = "Could not read that file."
            }
        }
    }

    fun confirmImport() {
        val pending = _pending.value ?: return
        _pending.value = null
        viewModelScope.launch {
            try {
                repo.replaceAll(pending.data)
                _message.value = "Imported ${pending.summary}."
            } catch (e: Exception) {
                // The transaction rolled back, so nothing changed.
                _message.value = "Import failed and nothing was changed (${e.message?.take(80)})."
            }
        }
    }
}
