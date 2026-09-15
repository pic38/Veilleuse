package io.github.pic38.veilleuse

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Capture le dernier plantage non rattrapé de l'app dans un fichier interne, pour permettre
 *  à l'utilisateur de le récupérer et le joindre à une issue GitHub. Fonction volontairement
 *  cachée : accessible uniquement par un appui long sur le numéro de version dans MainActivity
 *  (showCrashLogDialog), jamais affichée en usage normal. Ne garde que le dernier plantage
 *  (écrase le précédent) : suffisant pour l'investigation, sans croissance illimitée du fichier. */
object CrashLogger {
    private const val FILE_NAME = "last_crash.txt"

    fun record(context: Context, thread: Thread, throwable: Throwable) {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val versionName = try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"
        } catch (e: Exception) {
            "?"
        }
        val log = buildString {
            appendLine("Veilleuse crash log")
            appendLine("Date: $timestamp")
            appendLine("App version: $versionName")
            appendLine("Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Thread: ${thread.name}")
            appendLine()
            append(Log.getStackTraceString(throwable))
        }
        File(context.filesDir, FILE_NAME).writeText(log)
    }

    fun read(context: Context): String? {
        val file = File(context.filesDir, FILE_NAME)
        return if (file.exists()) file.readText() else null
    }
}
