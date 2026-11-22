package org.marketler.app

import android.view.View
import com.google.android.material.snackbar.Snackbar

/** Kullanıcıya kısa bildirimler (Snackbar) gösterir. */
class UiMessenger(private val anchor: View) {

    fun show(message: CharSequence) {
        Snackbar.make(anchor, message, Snackbar.LENGTH_LONG).show()
    }

    fun show(message: CharSequence, actionLabel: CharSequence, action: () -> Unit) {
        Snackbar.make(anchor, message, Snackbar.LENGTH_LONG)
            .setAction(actionLabel) { action() }
            .show()
    }
}
