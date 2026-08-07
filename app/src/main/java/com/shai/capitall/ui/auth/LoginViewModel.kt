package com.shai.capitall.ui.auth

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shai.capitall.data.model.User
import com.shai.capitall.data.repository.AuthRepository
import kotlinx.coroutines.launch

sealed class LoginState {
    object Idle : LoginState()
    object Loading : LoginState()
    data class Success(val user: User) : LoginState()
    data class Error(val message: String) : LoginState()
}

class LoginViewModel(
    private val authRepository: AuthRepository = com.shai.capitall.di.ServiceLocator.authRepository
) : ViewModel() {

    private val _loginState = MutableLiveData<LoginState>(LoginState.Idle)
    val loginState: LiveData<LoginState> = _loginState

    fun signInWithGoogle(idToken: String) {
        _loginState.value = LoginState.Loading
        viewModelScope.launch {
            authRepository.signInWithGoogle(idToken)
                .onSuccess { user -> _loginState.value = LoginState.Success(user) }
                .onFailure { e -> _loginState.value = LoginState.Error(e.message ?: "שגיאה לא ידועה") }
        }
    }
}