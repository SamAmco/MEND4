package co.samco.mendroid.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.samco.mendroid.model.ErrorToastManager
import co.samco.mendroid.R
import co.samco.mendroid.model.PrivateKeyManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class UnlockViewModel @Inject constructor(
    private val errorToastManager: ErrorToastManager,
    private val privateKeyManager: PrivateKeyManager
) : ViewModel() {

    var unlocked = privateKeyManager.unlocked
        .stateIn(viewModelScope, SharingStarted.Lazily, false)

    var password by mutableStateOf(TextFieldValue(""))

    var unlocking by mutableStateOf(false)
        private set

    fun onUnlockPressed() {
        viewModelScope.launch(Dispatchers.IO) {
            unlocking = true

            val passText = password.text.toCharArray()
            password = TextFieldValue("")

            if (!privateKeyManager.unlock(passText)) {
                errorToastManager.showErrorToast(R.string.unlock_failed)
            }

            unlocking = false
        }
    }
}