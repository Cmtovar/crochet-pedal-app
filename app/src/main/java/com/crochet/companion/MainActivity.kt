package com.crochet.companion

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.crochet.companion.ui.theme.CrochetCompanionTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CrochetCompanionTheme {
                CountingScreen()
            }
        }
    }
}

data class StitchGroup(val type: String, val count: Int)

@Composable
fun CountingScreen() {
    // Sample pattern (same as preview.html Winter Blanket)
    val pattern = remember {
        listOf(
            StitchGroup("sc", 13),
            StitchGroup("dc", 6),
            StitchGroup("sc", 8),
            StitchGroup("tr", 4),
            StitchGroup("sc", 13)
        )
    }
    val totalStitches = remember { pattern.sumOf { it.count } }

    var currentStitch by remember { mutableIntStateOf(0) }

    // Derive current group and position within it
    val (groupIndex, posInGroup) = remember(currentStitch) {
        var sum = 0
        for ((i, group) in pattern.withIndex()) {
            if (currentStitch < sum + group.count) {
                return@remember i to (currentStitch - sum)
            }
            sum += group.count
        }
        (pattern.lastIndex) to 0
    }
    val currentGroup = pattern[groupIndex]

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top ribbon: position fraction + stitch type
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant,
                        RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$posInGroup/${currentGroup.count}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = currentGroup.type,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Roulette area
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Past stitches (dimmed)
                for (offset in -2..-1) {
                    val s = currentStitch + offset
                    val label = stitchAt(pattern, s)
                    Box(
                        modifier = Modifier.width(48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label ?: "",
                            fontSize = 18.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                            textAlign = TextAlign.Center
                        )
                    }
                }

                // Current stitch (highlighted)
                Box(
                    modifier = Modifier
                        .width(80.dp)
                        .height(80.dp)
                        .background(
                            MaterialTheme.colorScheme.primaryContainer,
                            RoundedCornerShape(12.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = currentGroup.type,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }

                // Future stitches
                for (offset in 1..2) {
                    val s = currentStitch + offset
                    val label = stitchAt(pattern, s)
                    Box(
                        modifier = Modifier.width(48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label ?: "",
                            fontSize = 18.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Overall progress
            Text(
                text = "Stitch ${currentStitch + 1} of $totalStitches",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Pedal buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Button(
                    onClick = {
                        if (currentStitch > 0) currentStitch--
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(64.dp)
                        .padding(end = 8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Text(
                        text = "MENU",
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }

                Button(
                    onClick = {
                        if (currentStitch < totalStitches - 1) currentStitch++
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(64.dp)
                        .padding(start = 8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(
                        text = "NEXT",
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

fun stitchAt(pattern: List<StitchGroup>, index: Int): String? {
    if (index < 0) return null
    var sum = 0
    for (group in pattern) {
        if (index < sum + group.count) return group.type
        sum += group.count
    }
    return null
}
