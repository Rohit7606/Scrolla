package com.scrolla.ui.screens

import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.scrolla.device.BatteryWhitelistHelper

/**
 * Battery whitelist screen (S1.A7) - shows manufacturer-specific instructions
 * for adding Scroll to the device's battery optimization whitelist.
 */
@Composable
fun BatteryWhitelistScreen() {
    val context = LocalContext.current
    val helper = remember { BatteryWhitelistHelper() }
    val instructions = helper.getInstructions(Build.MANUFACTURER)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Manufacturer detected header
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Battery settings — your phone: ${instructions.manufacturer}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Scrolla keeps running in the background to track scroll distance. " +
                                "Add it to your battery whitelist so it doesn't get stopped.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }

            // Steps section
            Text(
                text = instructions.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 24.dp)
            )

            LazyColumn(
                modifier = Modifier.padding(top = 8.dp)
            ) {
                items(instructions.steps.size) { index ->
                    val step = instructions.steps[index]
                    StepItem(step = step, stepNumber = index + 1)
                }
            }

            // Open settings button
            Button(
                onClick = {
                    helper.openBatterySettings(context)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp)
            ) {
                Text(text = "Open battery settings")
            }

            // Help tooltip
            Text(
                text = "If Scroll is being killed while idle, it's likely due to battery optimization. Follow the steps above to whitelist it.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .padding(top = 16.dp)
                    .align(Alignment.CenterHorizontally)
            )
        }
    }
}

@Composable
private fun StepItem(step: String, stepNumber: Int) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = false) { }
            .padding(vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth()
        ) {
            // Step number indicator
            Card(
                modifier = Modifier.size(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text(
                    text = stepNumber.toString(),
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(2.dp),
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Text(
                text = step,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
    }
}