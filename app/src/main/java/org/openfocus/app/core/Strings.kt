package org.openfocus.app.core

import android.content.Context
import androidx.annotation.StringRes

/** Localised text for code that runs outside Compose (repositories, services, workers). */
fun interface Strings {
    fun get(@StringRes id: Int, vararg args: Any): String

    companion object {
        fun of(context: Context): Strings = Strings { id, args -> context.getString(id, *args) }
    }
}
