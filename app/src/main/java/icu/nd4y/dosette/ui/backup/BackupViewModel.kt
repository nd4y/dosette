package icu.nd4y.dosette.ui.backup

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import icu.nd4y.dosette.data.backup.BackupManager
import icu.nd4y.dosette.data.backup.BackupPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class BackupResult { EXPORTED, IMPORTED, ERROR }

data class BackupUiState(
    val busy: Boolean = false,
    val result: BackupResult? = null,
    /** Raw reason for [BackupResult.ERROR]. */
    val errorDetail: String? = null,
    /** Parsed file waiting for the user's confirmation. */
    val pendingImport: BackupPreview? = null,
)

@HiltViewModel
class BackupViewModel
    @Inject
    constructor(
        private val backupManager: BackupManager,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(BackupUiState())
        val uiState: StateFlow<BackupUiState> = _uiState

        private var pendingUri: Uri? = null

        fun export(uri: Uri) =
            launchOp({ backupManager.exportTo(uri) }) {
                _uiState.update { it.copy(result = BackupResult.EXPORTED) }
            }

        fun requestImport(uri: Uri) =
            launchOp({ backupManager.preview(uri) }) { preview ->
                pendingUri = uri
                _uiState.update { it.copy(pendingImport = preview) }
            }

        fun confirmImport() {
            val uri = pendingUri ?: return
            _uiState.update { it.copy(pendingImport = null) }
            launchOp({ backupManager.importFrom(uri) }) {
                pendingUri = null
                _uiState.update { it.copy(result = BackupResult.IMPORTED) }
            }
        }

        fun dismissImport() {
            pendingUri = null
            _uiState.update { it.copy(pendingImport = null) }
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
                runCatching { op() }
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
