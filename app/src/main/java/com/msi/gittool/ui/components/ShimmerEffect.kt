package com.msi.gittool.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun ShimmerItem(brush: Brush) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp, horizontal = 16.dp)
    ) {
        Spacer(
            modifier = Modifier
                .fillMaxWidth(0.4f)
                .height(20.dp)
                .background(brush, RoundedCornerShape(4.dp))
        )
        Spacer(modifier = Modifier.height(8.dp))
        Spacer(
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .height(14.dp)
                .background(brush, RoundedCornerShape(4.dp))
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(
                modifier = Modifier
                    .width(60.dp)
                    .height(14.dp)
                    .background(brush, RoundedCornerShape(2.dp))
            )
            Spacer(
                modifier = Modifier
                    .width(60.dp)
                    .height(14.dp)
                    .background(brush, RoundedCornerShape(2.dp))
            )
        }
    }
}

@Composable
fun ShimmerEffectList() {
    val shimmerColors = listOf(
        Color.LightGray.copy(alpha = 0.6f),
        Color.LightGray.copy(alpha = 0.2f),
        Color.LightGray.copy(alpha = 0.6f),
    )

    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_anim"
    )

    val brush = Brush.linearGradient(
        colors = shimmerColors,
        start = Offset.Zero,
        end = Offset(x = translateAnim.value, y = translateAnim.value)
    )

    Column {
        repeat(5) {
            ShimmerItem(brush = brush)
            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}

@Composable
fun RepoSkeletonItem(brush: Brush) {
    ElevatedCard(
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Spacer(
                    modifier = Modifier
                        .fillMaxWidth(0.5f)
                        .height(20.dp)
                        .background(brush, RoundedCornerShape(4.dp))
                )
                Spacer(
                    modifier = Modifier
                        .width(60.dp)
                        .height(18.dp)
                        .background(brush, RoundedCornerShape(4.dp))
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Spacer(
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .height(14.dp)
                    .background(brush, RoundedCornerShape(4.dp))
            )
            Spacer(modifier = Modifier.height(6.dp))
            Spacer(
                modifier = Modifier
                    .fillMaxWidth(0.6f)
                    .height(14.dp)
                    .background(brush, RoundedCornerShape(4.dp))
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Spacer(
                            modifier = Modifier
                                .size(10.dp)
                                .background(brush, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Spacer(
                            modifier = Modifier
                                .width(45.dp)
                                .height(12.dp)
                                .background(brush, RoundedCornerShape(2.dp))
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Spacer(
                            modifier = Modifier
                                .size(14.dp)
                                .background(brush, RoundedCornerShape(2.dp))
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Spacer(
                            modifier = Modifier
                                .width(20.dp)
                                .height(12.dp)
                                .background(brush, RoundedCornerShape(2.dp))
                        )
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Spacer(
                        modifier = Modifier
                            .size(32.dp)
                            .background(brush, CircleShape)
                    )
                    Spacer(
                        modifier = Modifier
                            .size(32.dp)
                            .background(brush, CircleShape)
                    )
                }
            }
        }
    }
}

@Composable
fun RepoSkeletonList(modifier: Modifier = Modifier) {
    val shimmerColors = listOf(
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f),
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.22f),
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f),
    )

    val transition = rememberInfiniteTransition(label = "repo_shimmer")
    val translateAnim = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "repo_shimmer_anim"
    )

    val brush = Brush.linearGradient(
        colors = shimmerColors,
        start = Offset.Zero,
        end = Offset(x = translateAnim.value, y = translateAnim.value)
    )

    Column(modifier = modifier) {
        repeat(5) {
            RepoSkeletonItem(brush = brush)
        }
    }
}
