package me.rerere.rikkahub.ui.activity

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.view.animation.PathInterpolator
import kotlin.math.abs

/** A retained fade that reverses from its presented value and never completes a cancelled hide. */
internal class OverlayOpacity(initial: Float, private val onValue: (Float) -> Unit) {
    var value = initial
        private set
    private var target = initial
    private var revision = 0
    private var animator: ValueAnimator? = null
    private var completion: (() -> Unit)? = null

    fun animateTo(target: Float, immediate: Boolean = false, onFinished: (() -> Unit)? = null) {
        if (!immediate && this.target == target && animator?.isRunning == true) {
            completion = onFinished
            return
        }
        cancel()
        this.target = target
        completion = onFinished
        if (immediate || !ValueAnimator.areAnimatorsEnabled() || abs(value - target) < 0.001f) {
            value = target
            onValue(value)
            completion?.also { completion = null }?.invoke()
            return
        }
        val current = revision
        animator = ValueAnimator.ofFloat(value, target).apply {
            duration = if (target > value) 220 else 180
            interpolator = PathInterpolator(0.2f, 0f, 0f, 1f)
            addUpdateListener {
                if (current == revision) {
                    value = it.animatedValue as Float
                    onValue(value)
                }
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (current != revision) return
                    animator = null
                    completion?.also { completion = null }?.invoke()
                }
            })
            start()
        }
    }

    fun cancel() {
        revision++
        completion = null
        animator?.cancel()
        animator = null
    }
}
