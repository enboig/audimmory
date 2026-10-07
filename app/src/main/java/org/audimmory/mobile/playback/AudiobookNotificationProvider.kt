package org.audimmory.mobile.playback

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import com.google.common.collect.ImmutableList
import org.audimmory.mobile.R

/**
 * Media notification whose transport row reads "back 15 / play / forward 30".
 *
 * The jump buttons themselves come from the session's media button preferences
 * (see [JumpControls]); all this provider does is interleave them with
 * play/pause. `DefaultMediaNotificationProvider` would otherwise append custom
 * buttons *after* play/pause, producing "play / back / forward".
 *
 * Note this only styles the notification Media3 posts. On Android 13+ the shade
 * and lock screen render their own panel from the session's `PlaybackState`
 * instead, which is why the same buttons must also be published as custom
 * actions rather than as player commands.
 */
@OptIn(markerClass = [UnstableApi::class])
class AudiobookNotificationProvider(
    private val context: Context,
) : DefaultMediaNotificationProvider(context) {
    override fun getMediaButtons(
        session: MediaSession,
        playerCommands: Player.Commands,
        mediaButtonPreferences: ImmutableList<CommandButton>,
        showPauseButton: Boolean,
    ): ImmutableList<CommandButton> {
        val buttons = ImmutableList.builder<CommandButton>()

        // Media3 has already filtered mediaButtonPreferences down to enabled
        // custom-command buttons before calling us.
        val byAction = mediaButtonPreferences.associateBy { it.sessionCommand?.customAction }
        byAction[JumpControls.ACTION_JUMP_BACKWARD]?.let { buttons.add(it) }

        if (playerCommands.contains(Player.COMMAND_PLAY_PAUSE)) {
            buttons.add(
                CommandButton
                    .Builder(if (showPauseButton) CommandButton.ICON_PAUSE else CommandButton.ICON_PLAY)
                    .setPlayerCommand(Player.COMMAND_PLAY_PAUSE)
                    .setDisplayName(
                        context.getString(
                            if (showPauseButton) {
                                R.string.notification_action_pause
                            } else {
                                R.string.notification_action_play
                            },
                        ),
                    ).build(),
            )
        }

        byAction[JumpControls.ACTION_JUMP_FORWARD]?.let { buttons.add(it) }

        // Anything else the session asked for keeps the superclass' trailing
        // placement. The app publishes no other custom buttons today.
        mediaButtonPreferences
            .filter { it.sessionCommand?.customAction !in JUMP_ACTIONS }
            .forEach { buttons.add(it) }

        return buttons.build()
    }

    private companion object {
        val JUMP_ACTIONS =
            setOf(JumpControls.ACTION_JUMP_BACKWARD, JumpControls.ACTION_JUMP_FORWARD)
    }
}
