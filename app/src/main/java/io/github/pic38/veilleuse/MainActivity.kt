package io.github.pic38.veilleuse

import android.content.Context
import android.content.SharedPreferences
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
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import io.github.pic38.veilleuse.databinding.ActivityMainBinding

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

    private var totalDurationMs: Long = 0
    private var fadeDurationMs: Long = 0
    private var isProgressive = false
    private var useFlash = true
    private var warmFraction = 0.4f
    private var brightnessFraction = 0.6f

    private val handler = Handler(Looper.getMainLooper())
    private var hideControlsRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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

        prefs = getSharedPreferences("veilleuse_prefs", Context.MODE_PRIVATE)
        detectFlash()
        loadPreferences()
        setupUi()
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

    /** Le fondu ne doit jamais durer plus longtemps que la veilleuse elle-même. */
    private fun updateFadeDurationBounds(durationMinutes: Float) = with(binding) {
        val maxFadeSeconds = (durationMinutes * 60f)
            .coerceAtMost(MAX_FADE_SECONDS)
            .coerceAtLeast(fadeDurationSlider.valueFrom)
        fadeDurationSlider.valueTo = maxFadeSeconds
        if (fadeDurationSlider.value > maxFadeSeconds) {
            fadeDurationSlider.value = maxFadeSeconds
            updateFadeLabel(maxFadeSeconds)
        }
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

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enterImmersiveMode()

        if (useFlash && hasFlash) {
            lightSurface.setBackgroundColor(Color.BLACK)
            setTorch(true)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && supportsVariableTorch) {
                setTorchStrength(maxTorchStrength)
            }
        } else {
            lightSurface.alpha = 1f
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
        fadeStartElapsedMs = SystemClock.elapsedRealtime()

        val tick = object : Runnable {
            override fun run() {
                val elapsed = SystemClock.elapsedRealtime() - fadeStartElapsedMs
                val fraction = (elapsed.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
                applyFadeProgress(fraction)
                if (fraction < 1f) {
                    handler.postDelayed(this, FADE_TICK_MS)
                }
            }
        }
        fadeRunnable = tick
        handler.post(tick)
    }

    private fun applyFadeProgress(fraction: Float) {
        if (useFlash && hasFlash) {
            val level = (maxTorchStrength - fraction * (maxTorchStrength - 1))
                .toInt()
                .coerceIn(1, maxTorchStrength)
            setTorchStrength(level)
        } else {
            binding.lightSurface.alpha = 1f - fraction
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
        window.attributes = window.attributes.apply {
            screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        }

        cancelHideControls()
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        exitImmersiveMode()

        if (closeApp) {
            finishAffinity()
        } else {
            binding.runningContainer.visibility = View.GONE
            binding.controlsOverlay.visibility = View.GONE
            binding.setupContainer.visibility = View.VISIBLE
        }
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
        if (binding.runningContainer.visibility == View.VISIBLE) {
            stopNightLight(returnToSetup = true)
        } else {
            super.onBackPressed()
        }
    }

    override fun onPause() {
        super.onPause()
        if (binding.runningContainer.visibility == View.VISIBLE && isFinishing) {
            setTorch(false)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel()
        cancelFade()
        setTorch(false)
    }

    private companion object {
        const val MAX_FADE_SECONDS = 120f
        const val FADE_TICK_MS = 50L
    }
}
