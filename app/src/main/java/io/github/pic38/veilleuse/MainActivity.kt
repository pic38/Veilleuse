package io.github.pic38.veilleuse

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.ColorStateList
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
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.OnApplyWindowInsetsListener
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import io.github.pic38.veilleuse.databinding.ActivityMainBinding
import kotlin.math.floor
import kotlin.math.roundToInt

private enum class TimeFormat { COMPACT, DETAILED, CLOCK }

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
    private var accentColor: Int = DEFAULT_ACCENT_COLOR
    private var timeFormat: TimeFormat = TimeFormat.COMPACT

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

        // Les écrans non immersifs ne doivent pas passer sous les barres système :
        // seul l'écran "veilleuse" actif doit être edge-to-edge (voir enter/exitImmersiveMode).
        val padForSystemBars = OnApplyWindowInsetsListener { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(view.paddingLeft, bars.top, view.paddingRight, bars.bottom)
            insets
        }
        ViewCompat.setOnApplyWindowInsetsListener(binding.setupContainer, padForSystemBars)
        ViewCompat.setOnApplyWindowInsetsListener(binding.settingsContainer, padForSystemBars)
        ViewCompat.setOnApplyWindowInsetsListener(binding.settingsButton) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val lp = view.layoutParams as ViewGroup.MarginLayoutParams
            lp.topMargin = bars.top + (8 * resources.displayMetrics.density).toInt()
            view.layoutParams = lp
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
        accentColor = prefs.getInt("accent_color", DEFAULT_ACCENT_COLOR)
        timeFormat = TimeFormat.entries.firstOrNull { it.name == prefs.getString("time_format", null) }
            ?: TimeFormat.COMPACT
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
        applyAccentColor(accentColor)
        populateColorSwatches()
        timeFormatToggleGroup.check(
            when (timeFormat) {
                TimeFormat.DETAILED -> R.id.btnFormatDetailed
                TimeFormat.CLOCK -> R.id.btnFormatClock
                TimeFormat.COMPACT -> R.id.btnFormatCompact
            }
        )

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
        updateFadeDurationBounds(durationSlider.value) // met aussi à jour le label du fondu
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

        durationSlider.setLabelFormatter { value -> formatHms(durationSecondsFor(value)) }
        fadeDurationSlider.setLabelFormatter { value -> formatHms(value.roundToInt().toLong()) }

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

        settingsButton.setOnClickListener {
            setupContainer.visibility = View.GONE
            settingsButton.visibility = View.GONE
            settingsContainer.visibility = View.VISIBLE
        }
        settingsBackButton.setOnClickListener { closeSettings() }
        timeFormatToggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            timeFormat = when (checkedId) {
                R.id.btnFormatDetailed -> TimeFormat.DETAILED
                R.id.btnFormatClock -> TimeFormat.CLOCK
                else -> TimeFormat.COMPACT
            }
            prefs.edit().putString("time_format", timeFormat.name).apply()
            updateDurationLabel(durationSlider.value)
            updateFadeLabel(fadeDurationSlider.value)
        }
    }

    /** durationSlider vaut 0 pour le cran supplémentaire "10 s" (test rapide) ;
     *  sinon sa valeur représente des minutes, comme avant. */
    private fun durationSecondsFor(sliderValue: Float): Long =
        if (sliderValue < 1f) 10L else sliderValue.toLong() * 60L

    private fun updateDurationLabel(value: Float) {
        val durationText = formatHms(durationSecondsFor(value))
        binding.durationLabel.text = "${getString(R.string.duration_title)} — $durationText"
    }

    private fun updateFadeLabel(value: Float) {
        val fadeText = formatHms(value.roundToInt().toLong())
        binding.fadeDurationLabel.text = "${getString(R.string.fade_duration_title)} — $fadeText"
    }

    /** Le fondu ne doit jamais durer plus longtemps que la veilleuse elle-même (mais peut
     *  désormais aller jusqu'à la durée totale complète), avec toujours [FADE_DURATION_STEPS]
     *  crans répartis sur toute la plage, quelle que soit sa largeur. */
    private fun updateFadeDurationBounds(durationSliderValue: Float) = with(binding) {
        val maxFadeSeconds = durationSecondsFor(durationSliderValue).toFloat()
            .coerceAtLeast(fadeDurationSlider.valueFrom)
        val stepSize = (maxFadeSeconds - fadeDurationSlider.valueFrom) / (FADE_DURATION_STEPS - 1)

        fadeDurationSlider.valueTo = maxFadeSeconds
        fadeDurationSlider.stepSize = stepSize

        // Réaligne la valeur courante sur la nouvelle grille de pas (obligatoire : Slider
        // exige que value soit un multiple exact de stepSize par rapport à valueFrom).
        val stepIndex = ((fadeDurationSlider.value - fadeDurationSlider.valueFrom) / stepSize)
            .roundToInt()
            .coerceIn(0, FADE_DURATION_STEPS - 1)
        fadeDurationSlider.value = (fadeDurationSlider.valueFrom + stepIndex * stepSize)
            .coerceIn(fadeDurationSlider.valueFrom, maxFadeSeconds)
        updateFadeLabel(fadeDurationSlider.value)
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
    // Couleur d'accentuation (réglages)
    // ---------------------------------------------------------------------

    private fun checkedStateList(checked: Int, unchecked: Int): ColorStateList = ColorStateList(
        arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
        intArrayOf(checked, unchecked)
    )

    /** Retinte tous les éléments dérivés de l'accent (boutons de choix, sliders, bouton
     *  Lancer, titre) avec [color], y compris une variante assombrie pour les états
     *  secondaires (bordure non cochée, halo de slider), équivalente à accent_amber_dim. */
    private fun applyAccentColor(color: Int) = with(binding) {
        val dim = ColorUtils.blendARGB(color, Color.BLACK, 0.45f)
        val textPrimary = ContextCompat.getColor(this@MainActivity, R.color.text_primary)
        val oledBlack = ContextCompat.getColor(this@MainActivity, R.color.oled_black)

        val strokeStates = checkedStateList(color, dim)
        val backgroundStates = checkedStateList(color, Color.TRANSPARENT)
        val textStates = checkedStateList(oledBlack, textPrimary)
        val rippleStates = ColorStateList.valueOf(dim)

        listOf(
            btnSourceFlash, btnSourceScreen,
            btnExtinctionInstant, btnExtinctionProgressive,
            btnFormatCompact, btnFormatDetailed, btnFormatClock
        ).forEach { button ->
            button.strokeColor = strokeStates
            button.backgroundTintList = backgroundStates
            button.setTextColor(textStates)
            button.rippleColor = rippleStates
        }

        listOf(durationSlider, fadeDurationSlider, warmColorSlider, brightnessSlider).forEach { slider ->
            slider.trackActiveTintList = ColorStateList.valueOf(color)
            slider.thumbTintList = ColorStateList.valueOf(color)
            slider.haloTintList = ColorStateList.valueOf(dim)
        }

        btnStart.backgroundTintList = ColorStateList.valueOf(color)
        appTitleText.setTextColor(color)
    }

    /** Palette de [ACCENT_SWATCH_COUNT] couleurs réparties sur toute la teinte (façon
     *  Image Toolbox) : ronds défilables horizontalement, celui sélectionné est cerclé. */
    private fun populateColorSwatches(): Unit = with(binding) {
        val container = colorSwatchContainer
        container.removeAllViews()
        val sizePx = (40 * resources.displayMetrics.density).toInt()
        val marginPx = (8 * resources.displayMetrics.density).toInt()
        val strokePx = (3 * resources.displayMetrics.density).toInt()

        for (i in 0 until ACCENT_SWATCH_COUNT) {
            val hue = i * 360f / ACCENT_SWATCH_COUNT
            val swatchColor = Color.HSVToColor(floatArrayOf(hue, 0.85f, 0.95f))
            val selected = swatchColor == accentColor

            val swatchDrawable = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(swatchColor)
                if (selected) setStroke(strokePx, Color.WHITE)
            }

            val swatch = View(this@MainActivity).apply {
                layoutParams = LinearLayout.LayoutParams(sizePx, sizePx).apply {
                    marginStart = marginPx
                    marginEnd = marginPx
                }
                background = swatchDrawable
                setOnClickListener {
                    accentColor = swatchColor
                    prefs.edit().putInt("accent_color", swatchColor).apply()
                    applyAccentColor(swatchColor)
                    populateColorSwatches()
                }
            }
            container.addView(swatch)
        }
    }

    // ---------------------------------------------------------------------
    // Démarrage / arrêt de la veilleuse
    // ---------------------------------------------------------------------

    private fun startNightLight() = with(binding) {
        totalDurationMs = durationSecondsFor(durationSlider.value) * 1000L
        fadeDurationMs = if (isProgressive) {
            (fadeDurationSlider.value.toLong() * 1000L).coerceAtMost(totalDurationMs)
        } else 0L
        fadeStarted = false

        setupContainer.visibility = View.GONE
        settingsButton.visibility = View.GONE
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
        // Annule une attente d'extinction d'écran en cours : sinon un appui sur "Arrêter"
        // pendant cette attente n'empêcherait pas la fermeture différée de se déclencher
        // plus tard, une fois revenu sur l'écran de réglages.
        unregisterScreenOffReceiver()

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
            // que l'extinction automatique du système se déclenche. Le bouton Arrêter et
            // l'affichage/masquage au tap restent disponibles pendant l'attente.
            binding.remainingTimeText.text = getString(R.string.waiting_for_sleep)
            waitForScreenOffThenClose()
        } else {
            exitImmersiveMode()
            binding.runningContainer.visibility = View.GONE
            binding.setupContainer.visibility = View.VISIBLE
            binding.settingsButton.visibility = View.VISIBLE
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

    /** Format choisi dans les réglages (Compact / Détaillé / HH:MM:SS), utilisé pour la
     *  durée totale et la durée du fondu. Le compte à rebours a son propre format dédié,
     *  voir [formatCountdown]. */
    private fun formatHms(totalSeconds: Long): String {
        val h = totalSeconds / 3600
        val m = (totalSeconds % 3600) / 60
        val s = totalSeconds % 60
        return when (timeFormat) {
            TimeFormat.CLOCK -> getString(R.string.clock_format, h, m, s)
            TimeFormat.DETAILED -> "${getString(R.string.hms_hours, h)} " +
                "${getString(R.string.hms_minutes, m)} ${getString(R.string.hms_seconds, s)}"
            TimeFormat.COMPACT -> formatCompact(h, m, s)
        }
    }

    /** N'affiche que les unités non nulles (ex. "15 min" plutôt que "00 h 15 min 00 s") ;
     *  si tout est à zéro, affiche "0 s" plutôt qu'une chaîne vide. */
    private fun formatCompact(h: Long, m: Long, s: Long): String {
        val parts = mutableListOf<String>()
        if (h > 0) parts += getString(R.string.hms_hours, h)
        if (m > 0) parts += getString(R.string.hms_minutes, m)
        if (s > 0 || parts.isEmpty()) parts += getString(R.string.hms_seconds, s)
        return parts.joinToString(" ")
    }

    /** Pour le compte à rebours : ne masque que les unités nulles de poids fort (à gauche).
     *  Une fois la première unité non nulle atteinte, tout ce qui suit reste affiché même
     *  à zéro (ex. "1 s", "1 min 0 s", "1 h 0 min 0 s"). */
    private fun formatCountdown(totalSeconds: Long): String {
        val h = totalSeconds / 3600
        val m = (totalSeconds % 3600) / 60
        val s = totalSeconds % 60

        val parts = mutableListOf<String>()
        if (h > 0) parts += getString(R.string.hms_hours, h)
        if (h > 0 || m > 0) parts += getString(R.string.hms_minutes, m)
        parts += getString(R.string.hms_seconds, s)
        return parts.joinToString(" ")
    }

    private fun formatRemaining(ms: Long): String {
        val totalSeconds = (ms / 1000).coerceAtLeast(0)
        return getString(R.string.time_remaining_format, formatCountdown(totalSeconds))
    }

    private fun closeSettings() = with(binding) {
        settingsContainer.visibility = View.GONE
        setupContainer.visibility = View.VISIBLE
        settingsButton.visibility = View.VISIBLE
    }

    override fun onBackPressed() {
        when {
            ::binding.isInitialized && binding.runningContainer.visibility == View.VISIBLE ->
                stopNightLight(returnToSetup = true)
            ::binding.isInitialized && binding.settingsContainer.visibility == View.VISIBLE ->
                closeSettings()
            else -> super.onBackPressed()
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
        const val FADE_DURATION_STEPS = 100
        const val ACCENT_SWATCH_COUNT = 20
        const val DEFAULT_ACCENT_COLOR = 0xFFFFB300.toInt() // = @color/accent_amber
    }
}
