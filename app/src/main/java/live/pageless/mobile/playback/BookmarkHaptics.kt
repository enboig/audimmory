package live.pageless.mobile.playback

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.content.getSystemService

/**
 * A haptic pulse confirming a bookmark was created from the next-track button.
 *
 * The press happens with the phone pocketed and the screen off, which rules out
 * every visual channel: toasts are suppressed for background apps on Android 12+
 * and never render on the lock screen. A vibration reaches the user where they
 * are and, unlike a tone, does not talk over the audiobook.
 *
 * It is not gated on the system's touch-feedback preference: this is a
 * deliberate confirmation of a deliberate action, not incidental UI feedback.
 *
 * Strength matters more here than for ordinary UI haptics — it has to carry
 * through a pocket to someone who is walking and listening to something else —
 * so this uses [VibrationEffect.EFFECT_HEAVY_CLICK] rather than the much
 * fainter `EFFECT_TICK`. Both are tuned per device, so it still feels native
 * rather than like a raw buzz.
 *
 * Its duration is deliberately not ours to choose: predefined effects have no
 * duration parameter, being fixed waveforms the vendor tuned for the device's
 * actuator. Making the length configurable would mean dropping down to
 * `createOneShot`/`createWaveform`, which buys control at the cost of that
 * tuning — on an LRA like the Pixel's, a hand-rolled pulse reads as a buzz
 * rather than a click.
 */
object BookmarkHaptics {
    private const val FALLBACK_DURATION_MS = 70L
    private const val MAX_AMPLITUDE = 255

    fun confirm(context: Context) {
        val vibrator = vibrator(context) ?: return
        if (!vibrator.hasVibrator()) return
        vibrator.vibrate(effect())
    }

    private fun vibrator(context: Context): Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService<VibratorManager>()?.defaultVibrator
        } else {
            context.getSystemService<Vibrator>()
        }

    private fun effect(): VibrationEffect =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK)
        } else {
            // Pre-29 has no predefined effects. Ask for maximum amplitude
            // explicitly rather than DEFAULT_AMPLITUDE, which some devices
            // interpret conservatively; it degrades to plain on/off where there
            // is no amplitude control.
            VibrationEffect.createOneShot(FALLBACK_DURATION_MS, MAX_AMPLITUDE)
        }
}
