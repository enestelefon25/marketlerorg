package org.marketler.app.ui

/** Ekran durumu: yükleniyor · içerik · hata. */
sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Content<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

fun Throwable.userMessage(): String = message?.takeIf { it.isNotBlank() } ?: "Beklenmeyen bir hata oluştu."
