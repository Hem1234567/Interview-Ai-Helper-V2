package com.interviewcopilot.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.interviewcopilot.app.llm.ModelCatalog
import com.interviewcopilot.app.vm.CopilotVM
import com.interviewcopilot.app.voice.SttLanguages

enum class SettingsPage { Root, LocalModels, WhisperModels, Language, Style, Online, Personal }

private val LENGTHS = listOf("Short" to "Fast", "Balanced" to "Standard", "Detailed" to "Deep")
private val STYLES = listOf("Concise", "Confident", "STAR", "Simple English")

@Composable
fun SettingsHost(vm: CopilotVM, initial: SettingsPage, onClose: () -> Unit) {
    var page by remember { mutableStateOf(initial) }
    BackHandler { if (page == SettingsPage.Root) onClose() else page = SettingsPage.Root }
    val back = { page = SettingsPage.Root }
    when (page) {
        SettingsPage.Root -> SettingsRoot(vm, onClose) { page = it }
        SettingsPage.LocalModels -> LocalModelsScreen(vm, back)
        SettingsPage.WhisperModels -> SpeechModelsScreen(vm, back)
        SettingsPage.Language -> LanguagePage(vm, back)
        SettingsPage.Style -> StylePage(vm, back)
        SettingsPage.Online -> OnlinePage(vm, back)
        SettingsPage.Personal -> PersonalPage(vm, back)
    }
}

@Composable
private fun SettingsRoot(vm: CopilotVM, onClose: () -> Unit, go: (SettingsPage) -> Unit) {
    val c = LocalAppColors.current
    var confirmWipe by remember { mutableStateOf(false) }
    val selected = vm.selectedModel?.takeIf { vm.isDownloaded(it) }
    val whisper = vm.selectedSpeech?.takeIf { vm.isDownloaded(it) }
    val lengthLabel = LENGTHS.firstOrNull { it.second == vm.g("speed") }?.first ?: "Short"

    Box(Modifier.fillMaxSize()) {
        LargeTitleScreen("Settings", onBack = onClose, backLabel = "Done") {
            SectionHeader("Answers")
            Card {
                ListRow(
                    "Local Models",
                    icon = Sym.cpu,
                    iconBg = c.primary,
                    chevron = true,
                    onClick = { go(SettingsPage.LocalModels) },
                    subtitle = selected?.name ?: "No model downloaded",
                )
                ListRow(
                    "Online engines",
                    icon = Sym.globe,
                    iconBg = c.secondary,
                    chevron = true,
                    onClick = { go(SettingsPage.Online) },
                    subtitle = "OpenRouter, Gemini",
                )
                ListRow(
                    "Answer style",
                    icon = Sym.sparkles,
                    iconBg = c.accent,
                    chevron = true,
                    onClick = { go(SettingsPage.Style) },
                    subtitle = vm.g("style"),
                )
                ListRow(
                    "Résumé and vocabulary",
                    icon = Sym.person,
                    iconBg = c.success,
                    chevron = true,
                    divider = false,
                    onClick = { go(SettingsPage.Personal) },
                )
            }
            Spacer(Modifier.height(12.dp))
            Card {
                Box(Modifier.padding(16.dp)) {
                    Segmented(LENGTHS.map { it.first }, lengthLabel, { l -> vm.put("speed", LENGTHS.first { it.first == l }.second) })
                }
                ListRow("Primary engine", divider = false, subtitle = "Falls back to the next ready engine if it fails")
                Box(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                    Segmented(listOf("Local", "OpenRouter", "Gemini"), vm.g("provider"), { vm.put("provider", it) })
                }
            }
            SectionFooter("Short answers start fastest. Detailed answers take longer.")
            Spacer(Modifier.height(12.dp))
            Card {
                ListRow("Auto fallback", subtitle = "Use another engine if the primary is slow", trailing = {
                    IosSwitch(vm.b("fallback")) { vm.put("fallback", it.toString()) }
                })
                ListRow("Conversation memory", subtitle = "Off: every question is answered on its own", divider = false, trailing = {
                    IosSwitch(vm.b("memory")) { vm.put("memory", it.toString()) }
                })
            }

            SectionHeader("Speech")
            Card {
                Box(Modifier.padding(16.dp)) {
                    Segmented(listOf("Auto", "Whisper", "Phone"), when (vm.g("sttEngine")) { "Platform" -> "Phone"; else -> vm.g("sttEngine") }, {
                        vm.put("sttEngine", if (it == "Phone") "Platform" else it)
                    })
                }
                ListRow(
                    "Whisper models",
                    icon = Sym.waveform,
                    iconBg = c.primary,
                    chevron = true,
                    onClick = { go(SettingsPage.WhisperModels) },
                    subtitle = whisper?.name ?: "None downloaded",
                )
                ListRow(
                    "Language",
                    icon = Sym.globe,
                    iconBg = c.secondary,
                    chevron = true,
                    divider = false,
                    onClick = { go(SettingsPage.Language) },
                    subtitle = SttLanguages.label(vm.g("sttLang")),
                )
            }
            SectionFooter(
                "Auto uses Whisper when a model is downloaded and the phone's recognizer otherwise. The microphone uses noise suppression, echo cancellation and auto gain, and silence is trimmed before transcription.",
            )

            SectionHeader("Appearance")
            Card { Box(Modifier.padding(16.dp)) { Segmented(listOf("System", "Light", "Dark"), vm.g("theme"), { vm.put("theme", it) }) } }

            SectionHeader("Privacy")
            Card {
                ListRow(
                    "Delete all data",
                    subtitle = "Keys, history, jobs, résumé and downloaded models",
                    divider = false,
                    onClick = { confirmWipe = true },
                    trailing = { Pill("Delete", c.danger) },
                )
            }
            SectionFooter("API keys are stored encrypted on this device. On-device models never send your questions anywhere.")

            SectionHeader("About")
            Card {
                ListRow("Interview Copilot", subtitle = "Version 2.0 · ${ModelCatalog.models.size} on-device models", divider = false)
            }
            SectionFooter("Follow the rules of any interview or employer about AI assistance. Best used for practice and preparation.")
        }
        IosAlert(
            visible = confirmWipe,
            title = "Delete all data?",
            message = "This removes keys, history, jobs, your résumé and every downloaded model.",
            confirmText = "Delete",
            destructive = true,
            onConfirm = {
                vm.wipeAll()
                confirmWipe = false
            },
            onDismiss = { confirmWipe = false },
        )
    }
}

@Composable
private fun LanguagePage(vm: CopilotVM, onBack: () -> Unit) {
    val c = LocalAppColors.current
    LargeTitleScreen("Language", onBack = onBack, backLabel = "Settings") {
        Card {
            SttLanguages.all.forEachIndexed { i, l ->
                ListRow(l.label, divider = i != SttLanguages.all.lastIndex, onClick = { vm.put("sttLang", l.code) }, trailing = {
                    if (l.code == vm.g("sttLang")) Sf(Sym.check, c.text, 20.dp)
                })
            }
        }
        SectionFooter("Auto-detect lets Whisper recognise the language you speak. Pick one language for the best accuracy.")
    }
}

@Composable
private fun StylePage(vm: CopilotVM, onBack: () -> Unit) {
    val c = LocalAppColors.current
    LargeTitleScreen("Answer style", onBack = onBack, backLabel = "Settings") {
        Card {
            STYLES.forEachIndexed { i, s ->
                ListRow(s, divider = i != STYLES.lastIndex, onClick = { vm.put("style", s) }, trailing = {
                    if (s == vm.g("style")) Sf(Sym.check, c.text, 20.dp)
                })
            }
        }
    }
}

@Composable
private fun OnlinePage(vm: CopilotVM, onBack: () -> Unit) {
    LargeTitleScreen("Online engines", onBack = onBack, backLabel = "Settings") {
        SectionHeader("OpenRouter")
        IosField(vm.g("orKey"), { vm.put("orKey", it) }, "API key", secret = true)
        Spacer(Modifier.height(8.dp))
        IosField(vm.g("orModel"), { vm.put("orModel", it) }, "Model id, e.g. google/gemini-2.0-flash-001")
        SectionHeader("Google Gemini")
        IosField(vm.g("geminiKey"), { vm.put("geminiKey", it) }, "Google AI Studio API key", secret = true)
        Spacer(Modifier.height(8.dp))
        IosField(vm.g("geminiModel"), { vm.put("geminiModel", it) }, "Model id, e.g. gemini-2.0-flash")
        SectionFooter("Optional. Keys are stored encrypted. Questions are sent to these services only when you choose them as the engine.")
    }
}

@Composable
private fun PersonalPage(vm: CopilotVM, onBack: () -> Unit) {
    LargeTitleScreen("Résumé and vocabulary", onBack = onBack, backLabel = "Settings") {
        SectionHeader("Résumé")
        IosField(vm.g("resume"), { vm.put("resume", it) }, "Paste your résumé text", singleLine = false, minLines = 7)
        SectionFooter("Used to personalise answers. Long text is shortened to keep answers fast.")
        SectionHeader("Custom vocabulary")
        IosField(vm.g("vocab"), { vm.put("vocab", it) }, "spring boot=Spring Boot", singleLine = false, minLines = 4)
        SectionFooter("One rule per line, wrong=correct. Corrects transcription and helps Whisper spell your terms.")
    }
}
