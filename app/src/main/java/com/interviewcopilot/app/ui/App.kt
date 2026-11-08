package com.interviewcopilot.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.interviewcopilot.app.vm.CopilotVM

@Composable
fun AppRoot(vm: CopilotVM) {
    val c = LocalAppColors.current
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    var settingsStart by rememberSaveable { mutableStateOf(SettingsPage.Root) }
    BackHandler(enabled = !settingsOpen && tab != 0) { tab = 0 }

    Box(Modifier.fillMaxSize().background(c.background)) {
        // Soft pastel glow behind the glass surfaces.
        Glow(c.primary, Modifier.align(Alignment.TopStart).offset(x = (-80).dp, y = (-60).dp))
        Glow(c.accent, Modifier.align(Alignment.BottomEnd).offset(x = 70.dp, y = 40.dp))
        Box(Modifier.fillMaxSize().imePadding()) {
            AnimatedContent(
                targetState = tab,
                transitionSpec = {
                    val dir = if (targetState > initialState) 1 else -1
                    (
                        fadeIn(tween(220)) + slideInHorizontally(spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)) { full ->
                            dir * full / 12
                        }
                    ) togetherWith fadeOut(tween(120))
                },
                label = "tabs",
            ) { t ->
                when (t) {
                    0 -> HomeScreen(
                        vm,
                        onOpenSettings = { settingsStart = SettingsPage.Root; settingsOpen = true },
                        onOpenModels = { settingsStart = SettingsPage.LocalModels; settingsOpen = true },
                    )

                    1 -> PracticeScreen(vm)

                    2 -> JobsScreen(vm)

                    else -> HistoryScreen(vm)
                }
            }
            Glass(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth()) {
                Row(Modifier.padding(6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TabBarItem("Assistant", Sym.mic, tab == 0, Modifier.weight(1f)) { tab = 0 }
                    TabBarItem("Practice", Sym.book, tab == 1, Modifier.weight(1f)) { tab = 1 }
                    TabBarItem("Jobs", Sym.briefcase, tab == 2, Modifier.weight(1f)) { tab = 2 }
                    TabBarItem("History", Sym.clock, tab == 3, Modifier.weight(1f)) { tab = 3 }
                }
            }
        }

        AnimatedVisibility(
            visible = settingsOpen,
            enter = slideInHorizontally(spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow)) { it } + fadeIn(),
            exit = slideOutHorizontally(tween(220)) { it } + fadeOut(tween(200)),
        ) {
            Box(Modifier.fillMaxSize().background(c.background)) {
                SettingsHost(vm, settingsStart) { settingsOpen = false }
            }
        }
    }
}

@Composable
private fun Glow(color: Color, modifier: Modifier) {
    Box(modifier.size(250.dp).blur(90.dp).clip(CircleShape).background(color.copy(alpha = 0.35f)))
}
