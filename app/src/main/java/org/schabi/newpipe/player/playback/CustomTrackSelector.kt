package org.schabi.newpipe.player.playback

import android.text.TextUtils

import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.Format
import com.google.android.exoplayer2.source.TrackGroup
import com.google.android.exoplayer2.source.TrackGroupArray
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.trackselection.FixedTrackSelection
import com.google.android.exoplayer2.trackselection.TrackSelection
import com.google.android.exoplayer2.util.Assertions

/**
 * This class allows irregular text language labels for use when selecting text captions and
 * is mostly a copy-paste From [DefaultTrackSelector].
 *
 * This is a hack and should be removed once ExoPlayer fixes language normalization to accept
 * a broader set of languages.
 */
class CustomTrackSelector(adaptiveTrackSelectionFactory: TrackSelection.Factory) : DefaultTrackSelector(adaptiveTrackSelectionFactory) {

    var preferredTextLanguage: String? = null
        set(label) {
            Assertions.checkNotNull(label)
            if (label != this.preferredTextLanguage) {
                field = label
                invalidate()
            }
        }

    /** @see DefaultTrackSelector.selectTextTrack
     */
    override fun selectTextTrack(groups: TrackGroupArray,
                                 formatSupport: Array<IntArray>,
                                 params: DefaultTrackSelector.Parameters): TrackSelection? {
        var selectedGroup: TrackGroup? = null
        var selectedTrackIndex = 0
        var selectedTrackScore = 0
        for (groupIndex in 0 until groups.length) {
            val trackGroup = groups.get(groupIndex)
            val trackFormatSupport = formatSupport[groupIndex]
            for (trackIndex in 0 until trackGroup.length) {
                if (DefaultTrackSelector.isSupported(trackFormatSupport[trackIndex],
                                params.exceedRendererCapabilitiesIfNecessary)) {
                    val format = trackGroup.getFormat(trackIndex)
                    val maskedSelectionFlags = format.selectionFlags and params.disabledTextTrackSelectionFlags.inv()
                    val isDefault = maskedSelectionFlags and C.SELECTION_FLAG_DEFAULT != 0
                    val isForced = maskedSelectionFlags and C.SELECTION_FLAG_FORCED != 0
                    var trackScore: Int
                    val preferredLanguageFound = formatHasLanguage(format, this.preferredTextLanguage)
                    if (preferredLanguageFound || params.selectUndeterminedTextLanguage && formatHasNoLanguage(format)) {
                        trackScore = when {
                            isDefault -> 8
                            !isForced -> // Prefer non-forced to forced if a preferred text language has been specified. Where
                                // both are provided the non-forced track will usually contain the forced subtitles as
                                // a subset.
                                6
                            else -> 4
                        }
                        trackScore += if (preferredLanguageFound) 1 else 0
                    } else if (isDefault) {
                        trackScore = 3
                    } else if (isForced) {
                        trackScore = if (formatHasLanguage(format, params.preferredAudioLanguage)) 2 else 1

                    } else {
                        // Track should not be selected.
                        continue
                    }
                    if (DefaultTrackSelector.isSupported(trackFormatSupport[trackIndex], false)) {
                        trackScore += WITHIN_RENDERER_CAPABILITIES_BONUS
                    }
                    if (trackScore > selectedTrackScore) {
                        selectedGroup = trackGroup
                        selectedTrackIndex = trackIndex
                        selectedTrackScore = trackScore
                    }
                }
            }
        }
        return if (selectedGroup == null)
            null
        else
            FixedTrackSelection(selectedGroup, selectedTrackIndex)
    }

    companion object {
        private const val WITHIN_RENDERER_CAPABILITIES_BONUS = 1000

        /** @see DefaultTrackSelector.formatHasLanguage
         */
        private fun formatHasLanguage(format: Format, language: String?): Boolean {
            return language != null && TextUtils.equals(language, format.language)
        }

        /** @see DefaultTrackSelector.formatHasNoLanguage
         */
        private fun formatHasNoLanguage(format: Format): Boolean {
            return TextUtils.isEmpty(format.language) || formatHasLanguage(format, C.LANGUAGE_UNDETERMINED)
        }
    }
}
