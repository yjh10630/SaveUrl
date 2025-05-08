package com.jinscompany.saveurl.domain.model

import androidx.compose.material3.SnackbarDuration

data class SnackBarModel(
    val message: String,
    val actionLabel: String? = null,
    val withDismissAction: Boolean = false,
    val duration: SnackbarDuration = SnackbarDuration.Short
)
