@file:OptIn(ExperimentalCoroutinesApi::class)

package co.samco.mendroid.viewmodel

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import co.samco.mendroid.model.LogLine
import co.samco.mendroid.model.PrivateKeyManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class LogViewData(
    val text: String,
    val dateTime: String?,
    val index: Int
)

@HiltViewModel
class DecryptedLogViewModel @Inject constructor(
    private val privateKeyManager: PrivateKeyManager,
    application: Application
) : AndroidViewModel(application) {

    var searchText by mutableStateOf(TextFieldValue(""))

    var filterEnabled by mutableStateOf(true)

    val logLines: StateFlow<List<LogViewData>> = combine(
        privateKeyManager.decryptedLogLines,
        snapshotFlow { filterEnabled },
        snapshotFlow { searchText }
    ) { lines, filterEnabled, search ->
        if (!filterEnabled) return@combine lines.mapIndexed { i, l -> l.asViewData(i) }

        val ignoreCase = search.text.none { it.isUpperCase() }

        if (search.text.isEmpty()) emptyList()
        else {
            lines
                .asSequence()
                .mapIndexed { index, line -> line.asViewData(index) }
                .filter { it.text.contains(search.text, ignoreCase = ignoreCase) }
                .toList()
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private fun LogLine.asViewData(index: Int) = LogViewData(
        text = text,
        dateTime = dateTime?.format(DateTimeFormatter.ofPattern("EEE, yyyy-MM-dd HH:mm:ss")),
        index = index
    )

    val decryptingLog = privateKeyManager.decryptingLog
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _onScrollToIndex = MutableSharedFlow<Int>()
    val scrollToIndex: Flow<Int> = _onScrollToIndex
        .flatMapLatest { index ->
            logLines.drop(1).map {
                delay(100)
                index
            }
        }
        .shareIn(viewModelScope, SharingStarted.Eagerly, replay = 0)

    fun onLogLineClicked(logViewData: LogViewData) {
        filterEnabled = false
        viewModelScope.launch { _onScrollToIndex.emit(logViewData.index) }
    }
}