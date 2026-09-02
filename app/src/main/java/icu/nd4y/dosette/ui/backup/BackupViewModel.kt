package icu.nd4y.dosette.ui.backup

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import icu.nd4y.dosette.data.backup.BackupFormatException
import icu.nd4y.dosette.data.backup.BackupManager
import icu.nd4y.dosette.data.backup.BackupPreview
import icu.nd4y.dosette.di.IoDispatcher
import icu.nd4y.dosette.ui.common.applyAppLanguage
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

enum class BackupResult { EXPORTED, IMPORTED, ERROR }

data class BackupUiState(
    val busy: Boolean = false,
    val result: BackupResult? = null,
    /** Raw reason for [BackupResult.ERROR]. */
    val errorDetail: String? = null,
    /** Parsed file waiting for the user's confirmation. */
    val pendingImport: BackupPreview? = null,
    /** The picked file is encrypted and waits for its password. */
    val passwordNeeded: Boolean = false,
    /** Set when the entered password failed; shown inside the password dialog. */
    val passwordError: Boolean = false,
)

@HiltViewModel
class BackupViewModel
    @Inject
    constructor(
        private val backupManager: BackupManager,
        @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(BackupUiState())
        val uiState: StateFlow<BackupUiState> = _uiState

        private var pendingUri: Uri? = null
        private var exportPassword: String? = null
        private var importPassword: String? = null

        /** Stored from the export dialog; consumed by [export] after SAF returns. */
        fun setExportPassword(password: String?) {
            exportPassword = password?.takeIf { it.isNotBlank() }
        }

        val exportEncrypted: Boolean get() = exportPassword != null

        fun export(uri: Uri) {
            val password = exportPassword
            exportPassword = null
            launchOp({ backupManager.exportTo(uri, password) }) {
                _uiState.update { it.copy(result = BackupResult.EXPORTED) }
            }
        }

        fun requestImport(uri: Uri) =
            launchOp({
                if (backupManager.isEncrypted(uri)) {
                    null
                } else {
                    backupManager.preview(uri, null)
                }
            }) { preview ->
                pendingUri = uri
                importPassword = null
                if (preview == null) {
                    _uiState.update { it.copy(passwordNeeded = true, passwordError = false) }
                } else {
                    _uiState.update { it.copy(pendingImport = preview) }
                }
            }

        /** Password entered for an encrypted file: preview or flag the dialog. */
        fun submitImportPassword(password: String) {
            val uri = pendingUri ?: return
            viewModelScope.launch {
                _uiState.update { it.copy(busy = true, result = null, errorDetail = null) }
                runCatching { withContext(ioDispatcher) { backupManager.preview(uri, password) } }
                    .onSuccess { preview ->
                        importPassword = password
                        _uiState.update {
                            it.copy(passwordNeeded = false, passwordError = false, pendingImport = preview)
                        }
                    }.onFailure { error ->
                        if (error is BackupFormatException) {
                            // Decrypted fine, but not importable: say so instead of blaming the password.
                            _uiState.update {
                                it.copy(
                                    passwordNeeded = false,
                                    result = BackupResult.ERROR,
                                    errorDetail = error.message,
                                )
                            }
                        } else {
                            _uiState.update { it.copy(passwordError = true) }
                        }
                    }
                _uiState.update { it.copy(busy = false) }
            }
        }

        fun confirmImport() {
            val uri = pendingUri ?: return
            val password = importPassword
            _uiState.update { it.copy(pendingImport = null) }
            launchOp({ backupManager.importFrom(uri, password) }) { imported ->
                pendingUri = null
                importPassword = null
                // The restored language only takes effect once applied to the process.
                applyAppLanguage(imported.language)
                _uiState.update { it.copy(result = BackupResult.IMPORTED) }
            }
        }

        fun dismissImport() {
            pendingUri = null
            importPassword = null
            _uiState.update { it.copy(pendingImport = null, passwordNeeded = false, passwordError = false) }
        }

        fun clearResult() {
            _uiState.update { it.copy(result = null, errorDetail = null) }
        }

        private fun <T> launchOp(
            op: suspend () -> T,
            onSuccess: (T) -> Unit,
        ) {
            viewModelScope.launch {
                _uiState.update { it.copy(busy = true, result = null, errorDetail = null) }
                runCatching { withContext(ioDispatcher) { op() } }
                    .onSuccess(onSuccess)
                    .onFailure { error ->
                        _uiState.update {
                            it.copy(result = BackupResult.ERROR, errorDetail = error.message)
                        }
                    }
                _uiState.update { it.copy(busy = false) }
            }
        }
    }
