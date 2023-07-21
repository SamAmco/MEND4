package co.samco.mendroid.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import co.samco.mendroid.model.PropertyManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class DecryptViewModel @Inject constructor(
    private val propertyManager: PropertyManager,
    application: Application
): AndroidViewModel(application) {

    private val fileHelper = FileHelper(application, propertyManager)

    val availableLogs = fileHelper.logFileNames
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
}