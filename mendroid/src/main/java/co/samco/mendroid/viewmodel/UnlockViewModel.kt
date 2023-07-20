package co.samco.mendroid.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.samco.mend4.core.crypto.CryptoProvider
import co.samco.mend4.core.crypto.UnlockResult
import co.samco.mendroid.model.ErrorToastManager
import co.samco.mendroid.R
import co.samco.mendroid.model.LockEventManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.security.PrivateKey
import javax.inject.Inject

@HiltViewModel
class UnlockViewModel @Inject constructor(
    private val errorToastManager: ErrorToastManager,
    private val cryptoProvider: CryptoProvider,
    private val lockEventManager: LockEventManager
) : ViewModel() {

    var password by mutableStateOf(TextFieldValue(""))

    var privateKey: PrivateKey? by mutableStateOf(null)
        private set

    var unlocking by mutableStateOf(false)
        private set

    init {
        viewModelScope.launch {
            lockEventManager.lockEvents.collect {
                privateKey = null
                //Short delay before setting password to empty because if we're navigating away we
                // can get a race condition where the UI tries to set it back to the old value
                delay(10)
                password = TextFieldValue("")
            }
        }
    }

    fun onUnlockPressed() {
        viewModelScope.launch(Dispatchers.IO) {
            unlocking = true
            privateKey = null

            val unlockResult = try {
                cryptoProvider.unlock(password.text.toCharArray())
            } catch (e: Exception) {
                e.printStackTrace()
                UnlockResult.Failure
                errorToastManager.showErrorToast(R.string.unlock_crashed)
            }

            if (unlockResult is UnlockResult.Success) {
                privateKey = unlockResult.privateKey
                password = TextFieldValue("")
            } else {
                errorToastManager.showErrorToast(R.string.unlock_failed)
            }

            unlocking = false
        }
    }

}