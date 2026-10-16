package com.vasu.assistant.ui.command

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vasu.assistant.core.command.CommandRegistry
import com.vasu.assistant.core.command.CommandSpec
import com.vasu.assistant.ui.components.VasuCard
import com.vasu.assistant.ui.theme.VasuCrimson
import com.vasu.assistant.ui.theme.VasuDarkBg
import com.vasu.assistant.ui.theme.VasuSuccess
import com.vasu.assistant.ui.theme.VasuTextPrimary
import com.vasu.assistant.ui.theme.VasuTextSecondary

/**
 * FIX-8: Command Library — renders [CommandRegistry.ALL] so the catalogue of
 * locally-routed voice commands is discoverable in-app. Every example shown
 * here is replayed through the real matcher by `CommandRegistryTest`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommandLibraryScreen(onNavigateBack: () -> Unit = {}) {
    Scaffold(
        containerColor = VasuDarkBg,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Voice Command Library",
                        fontWeight = FontWeight.Bold,
                        color = VasuCrimson,
                        fontSize = 18.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = VasuTextSecondary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = VasuDarkBg)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            item(key = "intro") {
                Text(
                    text = "Say these to VASU — they run instantly on your phone, even without internet.",
                    color = VasuTextSecondary,
                    fontSize = 13.sp
                )
            }
            CommandRegistry.byCategory().forEach { (category, commands) ->
                item(key = "cat_$category") {
                    Text(
                        text = category.uppercase(),
                        color = VasuSuccess,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                items(commands, key = { it.id }) { spec ->
                    CommandCard(spec)
                }
            }
        }
    }
}

@Composable
private fun CommandCard(spec: CommandSpec) {
    VasuCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Text(
                text = spec.title,
                color = VasuTextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
            Text(
                text = spec.examples.joinToString("   •   "),
                color = VasuTextSecondary,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
            Text(
                text = "works offline",
                color = VasuSuccess,
                fontSize = 10.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}
