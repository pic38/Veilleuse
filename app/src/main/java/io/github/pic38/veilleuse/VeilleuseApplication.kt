package io.github.pic38.veilleuse

import android.app.Application

/** Installe un capteur de plantage permanent (voir [CrashLogger]) dès le démarrage du
 *  processus, pour couvrir un crash survenant n'importe où dans l'app, pas seulement dans
 *  MainActivity. */
class VeilleuseApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                CrashLogger.record(this, thread, throwable)
            } catch (e: Throwable) {
                // Ne doit jamais empêcher le gestionnaire de plantage par défaut de s'exécuter.
            }
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }
}
