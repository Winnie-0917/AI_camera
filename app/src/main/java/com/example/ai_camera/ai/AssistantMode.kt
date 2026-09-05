package com.example.ai_camera.ai

import androidx.annotation.StringRes
import com.example.ai_camera.R

/**
 * What the assistant does in the background, chosen by long-pressing its button.
 *
 * The two active modes are deliberately exclusive: the angle guide polls the viewfinder every few
 * seconds, and running that alongside a photo review on every shutter press would multiply an
 * already quota-hungry feature.
 */
enum class AssistantMode(
    @StringRes val labelRes: Int,
    @StringRes val descriptionRes: Int,
) {
    OFF(R.string.mode_off, R.string.mode_off_desc),

    /** Live framing corrections while composing. */
    ANGLE(R.string.ai_angle_title, R.string.mode_angle_desc),

    /** Opens the assistant after each shot and critiques the pose in it. */
    POSE(R.string.mode_pose, R.string.mode_pose_desc),
}
