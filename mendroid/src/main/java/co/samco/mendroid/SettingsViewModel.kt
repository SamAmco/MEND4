package co.samco.mendroid

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val propertyManager: PropertyManager
) : ViewModel() {
    private val hasLogDir = propertyManager.logDirUri.map { it != null }
    private val hasEncDir = propertyManager.encDirUri.map { it != null }
    private val hasConfig = propertyManager.hasConfig

    val showSettings = combine(
        listOf(
            hasConfig,
            hasLogDir,
            hasEncDir
        )
    ) { list -> list.any { !it } }
        .stateIn(viewModelScope, SharingStarted.Lazily, true)

}