package co.samco.mendroid.viewmodel

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor() : ViewModel() {
    enum class HomeState(val index: Int) { ENCRYPT(0), DECRYPT(1) }

    private val _state = MutableStateFlow(HomeState.ENCRYPT)
    val state: StateFlow<HomeState> = _state

    fun onUserClickedEncrypt() {
        _state.value = HomeState.ENCRYPT
    }

    fun onUserClickedDecrypt() {
        _state.value = HomeState.DECRYPT
    }
}