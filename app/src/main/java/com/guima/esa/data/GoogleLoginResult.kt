package com.guima.esa.data

sealed interface GoogleLoginResult {
    data object Success : GoogleLoginResult
    data object RequiresTakeover : GoogleLoginResult
    data class Error(val message: String) : GoogleLoginResult
}
