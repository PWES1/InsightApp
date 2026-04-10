package week11.st991695851.finalproject.util

sealed class UiState {
    object Loading : UiState()
    object AuthRequired : UiState()
    object Authenticated : UiState()
}

sealed class AuthenticatedScreen {
    object Scanner : AuthenticatedScreen()
    object Library : AuthenticatedScreen()
    data class ViewNote(val noteId: String) : AuthenticatedScreen() // Reading one specific scan
}