package com.example.roboticgit.ui.theme

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/**
 * 画面遷移の動き。時間と加減速は直書きせず、テーマの motionScheme（Expressive のばね）から取る。
 * 位置の動きは spatial、透明度は effects を使う。
 */
class ScreenTransitions(motion: MotionScheme) {
    // 進む: 右から少し滑り込みながら現れる
    val enter: EnterTransition = slideInHorizontally(
        initialOffsetX = { fullWidth -> fullWidth / 4 },
        animationSpec = motion.defaultSpatialSpec()
    ) + fadeIn(animationSpec = motion.defaultEffectsSpec())

    // 進む: 今の画面は素早く消える
    val exit: ExitTransition = fadeOut(animationSpec = motion.fastEffectsSpec())

    // 戻る: 前の画面が現れる
    val popEnter: EnterTransition = fadeIn(animationSpec = motion.defaultEffectsSpec())

    // 戻る: 右へ少し滑りながら消える
    val popExit: ExitTransition = slideOutHorizontally(
        targetOffsetX = { fullWidth -> fullWidth / 4 },
        animationSpec = motion.defaultSpatialSpec()
    ) + fadeOut(animationSpec = motion.defaultEffectsSpec())

    // 横並びの画面（タブ）どうしの切り替えは重ねて入れ替えるだけ
    val fadeEnter: EnterTransition = fadeIn(animationSpec = motion.defaultEffectsSpec())

    val fadeExit: ExitTransition = fadeOut(animationSpec = motion.fastEffectsSpec())
}

/** 遷移のたびに作り直さないよう、motionScheme が変わったときだけ組み直す */
@Composable
fun rememberScreenTransitions(): ScreenTransitions {
    val motion = MaterialTheme.motionScheme
    return remember(motion) { ScreenTransitions(motion) }
}
