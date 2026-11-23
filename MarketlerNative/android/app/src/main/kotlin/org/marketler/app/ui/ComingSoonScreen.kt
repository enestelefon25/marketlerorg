package org.marketler.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.marketler.core.ui.MessageState

/** Bu aşamada yapılmamış sekmeler için açık bilgi ekranı (çalışmayan düğme yok). */
@Composable
fun ComingSoonScreen(title: String, message: String) {
    Box(Modifier.fillMaxSize().statusBarsPadding(), contentAlignment = Alignment.Center) {
        MessageState(Icons.Outlined.Schedule, "$title: sonraki aşamada", message)
    }
}
