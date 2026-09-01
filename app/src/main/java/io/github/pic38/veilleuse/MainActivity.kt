package io.github.pic38.veilleuse

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import io.github.pic38.veilleuse.databinding.ActivityMainBinding
import kotlin.math.floor
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: SharedPreferences

    private var cameraManager: CameraManager? = null
    private var torchCameraId: String? = null
    private var hasFlash = false
    private var maxTorchStrength = 1
    private var supportsVariableTorch = false

    private var countDownTimer: CountDownTimer? = null
    private var fadeRunnable: Runnable? = null
    private var fadeStarted = false
    private var fadeStartElapsedMs: Long = 0
    private var fadeTickIndex = 0
    private var screenOffReceiver: BroadcastReceiver? = null

    private var totalDurationMs: Long = 0
    private var fadeDurationMs: Long = 0
    private var isProgressive = false
    private var useFlash = true
    private var warmFraction = 0.4f
    private var brightnessFraction = 0.6f

    private val handler = Handler(Looper.getMainLooper())
    private var hideControlsRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        installCrashLogger()
        super.onCreate(savedInstanceState)

        val diagnosticPrefs = getSharedPreferences("veilleuse_prefs", Context.MODE_PRIVATE)
        val previousCrash = diagnosticPrefs.getString("last_crash", null)
        if (previousCrash != null) {
            diagnosticPrefs.edit().remove("last_crash").apply()

            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Crash Veilleuse", previousCrash))

            val textView = android.widget.TextView(this).apply {
                text = previousCrash
                setTextIsSelectable(true)
                setPadding(48, 32, 48, 32)
                textSize = 12f
            }
            val scroll = android.widget.ScrollView(this).apply { addView(textView) }

            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Dernier crash (déjà copié dans le presse-papier)")
                .setView(scroll)
                .setPositiveButton("OK") { _, _ -> finishAffinity() }
                .setCancelable(false)
                .show()
            return
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                else
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        // L'écran de réglages (non immersif) ne doit pas passer sous les barres système :
        // seul l'écran "veilleuse" actif doit être edge-to-edge (voir enter/exitImmersiveMode).
        ViewCompat.setOnApplyWindowInsetsListener(binding.setupContainer) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(view.paddingLeft, bars.top, view.paddingRight, bars.bottom)
            insets
        }

        prefs = diagnosticPrefs
        detectFlash()
        loadPreferences()
        setupUi()
    }

    /** Diagnostic temporaire : capture le prochain crash non rattrapé et l'affiche
     *  au lancement suivant, pour pouvoir le lire sans adb/logcat. */
    private fun installCrashLogger() {
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val sw = java.io.StringWriter()
                throwable.printStackTrace(java.io.PrintWriter(sw))
                getSharedPreferences("veilleuse_prefs", Context.MODE_PRIVATE)
                    .edit()
                    .putString("last_crash", sw.toString())
                    .commit()
            } catch (_: Throwable) {
                // Rien à faire si l'écriture du log échoue elle-même.
            }
            previousHandler?.uncaughtException(thread, throwable)
        }
    }

    // ---------------------------------------------------------------------
    // Détection du flash
    // ---------------------------------------------------------------------

    private fun detectFlash() {
        cameraManager = getSystemService(Context.CAMERA_SERVICE) as? CameraManager
        val manager = cameraManager ?: return
        try {
            val backId = manager.cameraIdList.firstOrNull { id ->
                val c = manager.getCameraCharacteristics(id)
                c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true &&
                    c.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
            }
            val anyId = backId ?: manager.cameraIdList.firstOrNull { id ->
                manager.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
            torchCameraId = anyId
            hasFlash = anyId != null

            if (hasFlash && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val chars = manager.getCameraCharacteristics(anyId!!)
                val maxLevel = chars.get(CameraCharacteristics.FLASH_INFO_STRENGTH_MAXIMUM_LEVEL) ?: 1
                maxTorchStrength = if (maxLevel < 1) 1 else maxLevel
                supportsVariableTorch = maxTorchStrength > 1
            }
        } catch (e: CameraAccessException) {
            hasFlash = false
        }
    }

    // ---------------------------------------------------------------------
    // Préférences
    // ---------------------------------------------------------------------

    private fun loadPreferences() {
        useFlash = prefs.getBoolean("use_flash", true) && hasFlash
        warmFraction = prefs.getFloat("warm_fraction", 0.4f)
        brightnessFraction = prefs.getFloat("brightness_fraction", 0.6f)
        isProgressive = prefs.getBoolean("progressive", false)
    }

    private fun savePreferences() {
        prefs.edit()
            .putBoolean("use_flash", useFlash)
            .putFloat("warm_fraction", warmFraction)
            .putFloat("brightness_fraction", brightnessFraction)
            .putBoolean("progressive", isProgressive)
            .putFloat("duration_min", binding.durationSlider.value)
            .putFloat("fade_seconds", binding.fadeDurationSlider.value)
            .apply()
    }

    // ---------------------------------------------------------------------
    // Configuration de l'interface
    // ---------------------------------------------------------------------

    private fun setupUi() = with(binding) {
        if (!hasFlash) {
            btnSourceFlash.isEnabled = false
            Toast.makeText(this@MainActivity, R.string.flash_unavailable, Toast.LENGTH_LONG).show()
        }

        if (useFlash && hasFlash) sourceToggleGroup.check(R.id.btnSourceFlash)
        else sourceToggleGroup.check(R.id.btnSourceScreen)
        screenOptionsGroup.visibility = if (useFlash && hasFlash) View.GONE else View.VISIBLE

        if (isProgressive) extinctionToggleGroup.check(R.id.btnExtinctionProgressive)
        else extinctionToggleGroup.check(R.id.btnExtinctionInstant)
        fadeDurationGroup.visibility = if (isProgressive) View.VISIBLE else View.GONE

        durationSlider.value = prefs.getFloat("duration_min", 15f)
        fadeDurationSlider.value = prefs.getFloat("fade_seconds", 30f)
        warmColorSlider.value = warmFraction * 100f
        brightnessSlider.value = brightnessFraction * 100f

        updateDurationLabel(durationSlider.value)
        updateFadeDurationBounds(durationSlider.value)
        updateFadeLabel(fadeDurationSlider.value)
        updateColorPreview()
        versionText.text = getString(R.string.version_format, appVersionName())

        sourceToggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            useFlash = checkedId == R.id.btnSourceFlash
            screenOptionsGroup.visibility = if (useFlash) View.GONE else View.VISIBLE
        }

        extinctionToggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            isProgressive = checkedId == R.id.btnExtinctionProgressive
            fadeDurationGroup.visibility = if (isProgressive) View.VISIBLE else View.GONE
        }

        durationSlider.addOnChangeListener { _, value, _ ->
            updateDurationLabel(value)
            updateFadeDurationBounds(value)
        }
        fadeDurationSlider.addOnChangeListener { _, value, _ -> updateFadeLabel(value) }

        warmColorSlider.addOnChangeListener { _, value, _ ->
            warmFraction = value / 100f
            updateColorPreview()
        }
        brightnessSlider.addOnChangeListener { _, value, _ ->
            brightnessFraction = value / 100f
        }

        btnStart.setOnClickListener {
            savePreferences()
            startNightLight()
        }
        btnStop.setOnClickListener { stopNightLight(returnToSetup = true) }
        lightSurface.setOnClickListener { showControls() }
        controlsOverlay.setOnClickListener { hideControls() }
    }

    private fun updateDurationLabel(value: Float) {
        binding.durationLabel.text = "${getString(R.string.duration_title)} — ${
            getString(R.string.duration_format, value.toInt())
        }"
    }

    private fun updateFadeLabel(value: Float) {
        binding.fadeDurationLabel.text = "${getString(R.string.fade_duration_title)} — ${
            getString(R.string.fade_duration_format, value.toInt())
        }"
    }

    /** Le fondu ne doit jamais durer plus longtemps que la veilleuse elle-même
     *  (mais peut désormais aller jusqu'à la durée totale complète). */
    private fun updateFadeDurationBounds(durationMinutes: Float) = with(binding) {
        val maxFadeSeconds = (durationMinutes * 60f).coerceAtLeast(fadeDurationSlider.valueFrom)
        fadeDurationSlider.valueTo = maxFadeSeconds
        if (fadeDurationSlider.value > maxFadeSeconds) {
            fadeDurationSlider.value = maxFadeSeconds
            updateFadeLabel(maxFadeSeconds)
        }
    }

    private fun appVersionName(): String =
        try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: "?"
        } catch (e: PackageManager.NameNotFoundException) {
            "?"
        }

    private fun updateColorPreview() {
        val drawable = binding.colorPreview.background as? GradientDrawable
            ?: GradientDrawable().also {
                it.cornerRadius = 20f * resources.displayMetrics.density
                binding.colorPreview.background = it
            }
        drawable.setColor(computeWarmColor(warmFraction))
    }

    // ---------------------------------------------------------------------
    // Démarrage / arrêt de la veilleuse
    // ---------------------------------------------------------------------

    private fun startNightLight() = with(binding) {
        totalDurationMs = durationSlider.value.toLong() * 60_000L
        fadeDurationMs = if (isProgressive) {
            (fadeDurationSlider.value.toLong() * 1000L).coerceAtMost(totalDurationMs)
        } else 0L
        fadeStarted = false

        setupContainer.visibility = View.GONE
        runningContainer.visibility = View.VISIBLE
        controlsOverlay.visibility = View.GONE
        waitingForSleepText.visibility = View.GONE
        lightSurface.setOnClickListener { showControls() }

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enterImmersiveMode()

        if (useFlash && hasFlash) {
            lightSurface.setBackgroundColor(Color.BLACK)
            setTorch(true)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && supportsVariableTorch) {
                setTorchStrength(maxTorchStrength)
            }
        } else {
            lightSurface.alpha = brightnessFraction
            lightSurface.setBackgroundColor(computeWarmColor(warmFraction))
            window.attributes = window.attributes.apply { screenBrightness = brightnessFraction }
        }

        remainingTimeText.text = formatRemaining(totalDurationMs)

        countDownTimer = object : CountDownTimer(totalDurationMs, 1000L) {
            override fun onTick(remaining: Long) {
                remainingTimeText.text = formatRemaining(remaining)
                if (isProgressive && !fadeStarted && remaining <= fadeDurationMs) {
                    startFade(remaining)
                }
            }

            override fun onFinish() {
                finishNightLight(closeApp = true)
            }
        }.start()
    }

    /** Fondu piloté manuellement via SystemClock plutôt qu'un ValueAnimator : certains
     *  appareils/réglages système mettent l'échelle de durée d'animation à 0, ce qui rend
     *  un ValueAnimator instantané (coupure nette au lieu d'un fondu). */
    private fun startFade(remainingMs: Long) {
        fadeStarted = true

        if (useFlash && hasFlash && !supportsVariableTorch) {
            Toast.makeText(this, R.string.fade_flash_unsupported, Toast.LENGTH_SHORT).show()
            return
        }

        val duration = remainingMs.coerceAtLeast(1L)
        val tickMs = if (useFlash && hasFlash) FADE_TICK_MS_FLASH else FADE_TICK_MS_SCREEN
        fadeStartElapsedMs = SystemClock.elapsedRealtime()
        fadeTickIndex = 0

        val tick = object : Runnable {
            override fun run() {
                val elapsed = SystemClock.elapsedRealtime() - fadeStartElapsedMs
                val fraction = (elapsed.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
                applyFadeProgress(fraction)
                fadeTickIndex++
                if (fraction < 1f) {
                    handler.postDelayed(this, tickMs)
                }
            }
        }
        fadeRunnable = tick
        handler.post(tick)
    }

    /** Le nombre de paliers physiques de la torche est limité par le matériel (souvent
     *  seulement quelques niveaux). On simule ~[DITHER_STEPS] fois plus de paliers perçus
     *  en alternant rapidement entre les deux niveaux matériels voisins (dithering temporel :
     *  l'œil moyenne les alternances rapides en une luminosité intermédiaire). */
    private fun applyFadeProgress(fraction: Float) {
        if (useFlash && hasFlash) {
            val target = maxTorchStrength - fraction * (maxTorchStrength - 1)
            val lower = floor(target).toInt().coerceIn(1, maxTorchStrength)
            val upper = (lower + 1).coerceAtMost(maxTorchStrength)
            val onSteps = ((target - lower).coerceIn(0f, 1f) * DITHER_STEPS).roundToInt()
            val level = if (fadeTickIndex % DITHER_STEPS < onSteps) upper else lower
            setTorchStrength(level)
        } else {
            binding.lightSurface.alpha = brightnessFraction * (1f - fraction)
        }
    }

    private fun cancelFade() {
        fadeRunnable?.let { handler.removeCallbacks(it) }
        fadeRunnable = null
    }

    /** Arrête la veilleuse. Si [returnToSetup] est vrai (arrêt manuel), revient à l'écran de configuration.
     *  Sinon (fin de minuteur), ferme l'application pour laisser le téléphone passer en veille. */
    private fun stopNightLight(returnToSetup: Boolean) {
        finishNightLight(closeApp = !returnToSetup)
    }

    private fun finishNightLight(closeApp: Boolean = false) {
        countDownTimer?.cancel()
        countDownTimer = null
        cancelFade()

        setTorch(false)
        binding.lightSurface.alpha = 1f
        binding.lightSurface.setBackgroundColor(Color.BLACK)
        window.attributes = window.attributes.apply {
            screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        }

        cancelHideControls()
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        binding.controlsOverlay.visibility = View.GONE

        if (closeApp) {
            // Ne pas révéler l'écran d'accueil (lumineux) tout de suite : on reste en
            // plein écran noir et on attend que le téléphone s'endorme réellement avant
            // de fermer l'app, sinon le launcher s'affiche en pleine luminosité le temps
            // que l'extinction automatique du système se déclenche.
            binding.lightSurface.setOnClickListener(null)
            binding.waitingForSleepText.visibility = View.VISIBLE
            waitForScreenOffThenClose()
        } else {
            exitImmersiveMode()
            binding.runningContainer.visibility = View.GONE
            binding.setupContainer.visibility = View.VISIBLE
        }
    }

    private fun waitForScreenOffThenClose() {
        if (screenOffReceiver != null) return
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                unregisterScreenOffReceiver()
                finishAffinity()
            }
        }
        screenOffReceiver = receiver
        ContextCompat.registerReceiver(
            this,
            receiver,
            IntentFilter(Intent.ACTION_SCREEN_OFF),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    private fun unregisterScreenOffReceiver() {
        screenOffReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (_: IllegalArgumentException) {
                // Déjà désenregistré.
            }
        }
        screenOffReceiver = null
    }

    // ---------------------------------------------------------------------
    // Torche
    // ---------------------------------------------------------------------

    private fun setTorch(on: Boolean) {
        val id = torchCameraId ?: return
        try {
            cameraManager?.setTorchMode(id, on)
        } catch (e: CameraAccessException) {
            // Aucune action possible si la caméra n'est plus accessible.
        }
    }

    private fun setTorchStrength(level: Int) {
        val id = torchCameraId ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && supportsVariableTorch) {
            try {
                cameraManager?.turnOnTorchWithStrengthLevel(id, level.coerceIn(1, maxTorchStrength))
            } catch (e: CameraAccessException) {
                // Ignoré : le fondu s'arrêtera à l'extinction finale.
            }
        }
    }

    // ---------------------------------------------------------------------
    // Couleur chaude
    // ---------------------------------------------------------------------

    private fun computeWarmColor(fraction: Float): Int {
        val from = Color.parseColor("#FFF4D9")
        val to = Color.parseColor("#FF5A00")
        val f = fraction.coerceIn(0f, 1f)
        val inv = 1f - f
        val r = (Color.red(from) * inv + Color.red(to) * f).toInt()
        val g = (Color.green(from) * inv + Color.green(to) * f).toInt()
        val b = (Color.blue(from) * inv + Color.blue(to) * f).toInt()
        return Color.rgb(r, g, b)
    }

    // ---------------------------------------------------------------------
    // Commandes tactiles (afficher/masquer pendant la veille)
    // ---------------------------------------------------------------------

    private fun showControls() {
        binding.controlsOverlay.visibility = View.VISIBLE
        cancelHideControls()
        hideControlsRunnable = Runnable { hideControls() }.also {
            handler.postDelayed(it, 4000L)
        }
    }

    private fun hideControls() {
        cancelHideControls()
        binding.controlsOverlay.visibility = View.GONE
    }

    private fun cancelHideControls() {
        hideControlsRunnable?.let { handler.removeCallbacks(it) }
        hideControlsRunnable = null
    }

    // ---------------------------------------------------------------------
    // Mode immersif
    // ---------------------------------------------------------------------

    private fun enterImmersiveMode() {
        val controller = WindowInsetsControllerCompat(window, binding.root)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    private fun exitImmersiveMode() {
        val controller = WindowInsetsControllerCompat(window, binding.root)
        controller.show(WindowInsetsCompat.Type.systemBars())
    }

    // ---------------------------------------------------------------------
    // Divers
    // ---------------------------------------------------------------------

    private fun formatRemaining(ms: Long): String {
        val totalSeconds = (ms / 1000).coerceAtLeast(0)
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return getString(R.string.time_remaining_format, minutes, seconds)
    }

    override fun onBackPressed() {
        if (::binding.isInitialized && binding.runningContainer.visibility == View.VISIBLE) {
            stopNightLight(returnToSetup = true)
        } else {
            super.onBackPressed()
        }
    }

    override fun onPause() {
        super.onPause()
        if (::binding.isInitialized && binding.runningContainer.visibility == View.VISIBLE && isFinishing) {
            setTorch(false)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel()
        cancelFade()
        setTorch(false)
        unregisterScreenOffReceiver()
    }

    private companion object {
        const val FADE_TICK_MS_SCREEN = 5L
        const val FADE_TICK_MS_FLASH = 50L
        const val DITHER_STEPS = 2
    }
}
