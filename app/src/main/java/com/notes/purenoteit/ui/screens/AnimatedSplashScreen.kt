package com.notes.purenoteit.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.notes.purenoteit.R
import com.notes.purenoteit.ui.theme.PureWhite
import com.notes.purenoteit.ui.theme.SoftOrangeLight
import com.notes.purenoteit.ui.theme.VibrantOrange
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

/**
 * Cinematic Animated Splash Screen ("Animasyon Şovu").
 * Features:
 * - Authentic high-res PureNoteIt app logo (R.drawable.app_logo).
 * - Concentric expanding neon energy shockwave rings.
 * - Orbiting glowing neon energy particles in brand colors.
 * - Elastic bounce entrance with fluid breathing physics.
 * - Staggered title and slogan badge slide-in.
 * - Jumping neon dot-wave progress indicator.
 */
@Composable
fun AnimatedSplashScreen(
    onAnimationFinish: () -> Unit
) {
    val logoScale = remember { Animatable(0.15f) }
    val logoAlpha = remember { Animatable(0f) }
    val textAlpha = remember { Animatable(0f) }
    val textSlideY = remember { Animatable(30f) }
    val badgeAlpha = remember { Animatable(0f) }
    val exitAlpha = remember { Animatable(1f) }

    val infiniteTransition = rememberInfiniteTransition(label = "splashShow")

    // Continuous breathing pulse of the logo
    val breathingScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathingScale"
    )

    // Outer Neon Shockwave Ring 1
    val wave1Scale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.55f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave1Scale"
    )
    val wave1Alpha by infiniteTransition.animateFloat(
        initialValue = 0.65f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave1Alpha"
    )

    // Outer Neon Shockwave Ring 2 (delayed phase)
    val wave2Scale by infiniteTransition.animateFloat(
        initialValue = 0.75f,
        targetValue = 1.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(2100, delayMillis = 350, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave2Scale"
    )
    val wave2Alpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2100, delayMillis = 350, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave2Alpha"
    )

    // Rotation for orbiting particles
    val particleAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(6500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "particleAngle"
    )

    // Jumping indicator dots
    val dot1Offset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -7f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot1"
    )
    val dot2Offset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -7f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, delayMillis = 130, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot2"
    )
    val dot3Offset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -7f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, delayMillis = 260, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot3"
    )

    LaunchedEffect(Unit) {
        // Stage 1: Elastic logo entrance with bouncy spring
        logoAlpha.animateTo(1f, animationSpec = tween(300))
        logoScale.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            )
        )

        // Stage 2: Staggered title entrance
        textSlideY.animateTo(0f, animationSpec = tween(400, easing = FastOutSlowInEasing))
        textAlpha.animateTo(1f, animationSpec = tween(400))

        // Stage 3: Slogan badge entrance
        badgeAlpha.animateTo(1f, animationSpec = tween(350))

        // Hold so user can admire the animation show
        delay(1200L)

        // Stage 4: Smooth exit fade
        exitAlpha.animateTo(0f, animationSpec = tween(250))
        onAnimationFinish()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .alpha(exitAlpha.value)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.background,
                        SoftOrangeLight.copy(alpha = 0.35f)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Central Logo with Animated Neon Glow Rings & Orbiting Particles
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(240.dp)
            ) {
                // Expanding Shockwave Ring 2
                Box(
                    modifier = Modifier
                        .size(160.dp)
                        .scale(wave2Scale)
                        .alpha(wave2Alpha)
                        .clip(CircleShape)
                        .border(1.5.dp, VibrantOrange.copy(alpha = 0.5f), CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(VibrantOrange.copy(alpha = 0.15f), Color.Transparent)
                            )
                        )
                )

                // Expanding Shockwave Ring 1
                Box(
                    modifier = Modifier
                        .size(140.dp)
                        .scale(wave1Scale)
                        .alpha(wave1Alpha)
                        .clip(CircleShape)
                        .border(2.dp, Color(0xFFFFB03A), CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(SoftOrangeLight, Color.Transparent)
                            )
                        )
                )

                // Orbiting Neon Particles Canvas
                Canvas(modifier = Modifier.size(220.dp)) {
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val particleRadii = listOf(70f, 85f, 98f, 75f, 90f, 80f)
                    val baseAngles = listOf(0.0, 60.0, 120.0, 180.0, 240.0, 300.0)

                    baseAngles.forEachIndexed { idx, baseAngle ->
                        val currentRad = Math.toRadians(baseAngle + particleAngle * (if (idx % 2 == 0) 1 else -1))
                        val distance = particleRadii[idx % particleRadii.size]
                        val px = center.x + (distance * cos(currentRad)).toFloat()
                        val py = center.y + (distance * sin(currentRad)).toFloat()

                        // Outer particle glow
                        drawCircle(
                            color = VibrantOrange.copy(alpha = 0.4f),
                            radius = 6f,
                            center = Offset(px, py)
                        )
                        // Inner bright core
                        drawCircle(
                            color = Color(0xFFFFE082),
                            radius = 2.8f,
                            center = Offset(px, py)
                        )
                    }
                }

                // Main Brand Logo Card with Spring + Breathing Scale
                Surface(
                    modifier = Modifier
                        .size(108.dp)
                        .scale(logoScale.value * breathingScale)
                        .alpha(logoAlpha.value),
                    shape = RoundedCornerShape(30.dp),
                    color = PureWhite,
                    shadowElevation = 14.dp,
                    border = androidx.compose.foundation.BorderStroke(2.dp, VibrantOrange.copy(alpha = 0.6f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(PureWhite, SoftOrangeLight.copy(alpha = 0.5f))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.app_logo),
                            contentDescription = "PureNoteIt Logo",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .size(76.dp)
                                .clip(RoundedCornerShape(22.dp))
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Cinematic App Name with Shimmer & Slide
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .graphicsLayer { translationY = textSlideY.value }
                    .alpha(textAlpha.value)
            ) {
                Text(
                    text = "PureNoteIt",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onBackground,
                    letterSpacing = 1.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Modern Slogan Badge with Soft Glow
                Surface(
                    modifier = Modifier.alpha(badgeAlpha.value),
                    shape = RoundedCornerShape(16.dp),
                    color = SoftOrangeLight,
                    border = androidx.compose.foundation.BorderStroke(1.dp, VibrantOrange.copy(alpha = 0.35f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "✨", fontSize = 12.sp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Düşüncelerinizin En Saf Hali",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = VibrantOrange
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(44.dp))

            // Jumping Neon Wave Dots Indicator
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .offset(y = dot1Offset.dp)
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(VibrantOrange)
                )
                Box(
                    modifier = Modifier
                        .offset(y = dot2Offset.dp)
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFFFB03A))
                )
                Box(
                    modifier = Modifier
                        .offset(y = dot3Offset.dp)
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(VibrantOrange)
                )
            }
        }
    }
}
