package com.interviewcopilot.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.interviewcopilot.app.llm.DeviceProbe
import com.interviewcopilot.app.llm.DownloadState
import com.interviewcopilot.app.llm.Fit
import com.interviewcopilot.app.llm.LocalModel
import com.interviewcopilot.app.llm.ModelAssessment
import com.interviewcopilot.app.llm.ModelCatalog
import com.interviewcopilot.app.llm.ModelKind
import com.interviewcopilot.app.llm.SpeechModel
import com.interviewcopilot.app.vm.CopilotVM

private fun gb(bytes: Long) = "%.2f GB".format(bytes / 1_000_000_000.0)
private fun mb(bytes: Long) = if (bytes >= 1_000_000_000L) gb(bytes) else "%d MB".format(bytes / 1_000_000L)

@Composable
fun LocalModelsScreen(vm: CopilotVM, onBack: () -> Unit) {
    val c = LocalAppColors.current
    var confirmDelete by remember { mutableStateOf<LocalModel?>(null) }
    val d = vm.device

    Box(Modifier.fillMaxSize()) {
        LargeTitleScreen("Local Models", onBack = onBack, backLabel = "Settings") {
            SectionHeader("This device")
            Card {
                ListRow("Memory (RAM)", trailing = { Txt("%.1f GB".format(d.totalRamGb), Type.body, c.text2) })
                ListRow("Free storage", trailing = { Txt("%.1f GB".format(d.freeStorageMb / 1024.0), Type.body, c.text2) })
                ListRow("CPU", subtitle = DeviceProbe.describeCpu(d))
                ListRow("GPU", subtitle = DeviceProbe.describeGpu(d))
                ListRow("NPU", subtitle = DeviceProbe.describeNpu(d))
                ListRow("System", subtitle = "${d.osName} · ${d.socName}", divider = false)
            }
            SectionFooter(
                "Answers run on the CPU with every available NEON, dot-product and i8mm optimisation. Models are downloaded once and then work offline.",
            )

            SectionHeader("10 on-device models")
            vm.assessments.forEach { a ->
                ModelCard(vm, a) { confirmDelete = a.model }
                Spacer(Modifier.height(12.dp))
            }
            SectionFooter("Quantized GGUF (Q4_K_M) files from Hugging Face. Each download is checked against its SHA-256 before it can be used.")
        }
        IosAlert(
            visible = confirmDelete != null,
            title = "Delete model?",
            message = "${confirmDelete?.name.orEmpty()} will be removed from this phone. You can download it again later.",
            confirmText = "Delete",
            destructive = true,
            onConfirm = {
                confirmDelete?.let { vm.delete(it) }
                confirmDelete = null
            },
            onDismiss = { confirmDelete = null },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ModelCard(vm: CopilotVM, a: ModelAssessment, onDelete: () -> Unit) {
    val c = LocalAppColors.current
    val m = a.model
    val id = "llm:${m.id}"
    val state = vm.stateOf(id)
    val downloaded = vm.isDownloaded(m)
    val selected = vm.g("localModelId") == m.id && downloaded
    var open by remember { mutableStateOf(false) }

    Card(Modifier.animateContentSize()) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth().pressable(haptic = false) { open = !open }, verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Txt(m.family, Type.caption, c.text2)
                    Txt(m.name, Type.headline, c.text)
                }
                Sf(Sym.chevronDown, c.text2, 18.dp)
            }
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (a.recommended) Pill("Recommended for your device", c.success)
                if (selected) Pill("In use", c.primary)
                if (downloaded && !selected) Pill("Downloaded", c.secondary)
                when (a.fit) {
                    Fit.TooLarge -> Pill("Too large for this device", c.danger)
                    Fit.Tight -> Pill("Tight on memory", c.warning)
                    Fit.NoStorage -> Pill("Not enough storage", c.warning)
                    Fit.Good -> Unit
                }
                if (m.kind == ModelKind.Reasoning) Pill("Reasoning", c.accent)
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth()) {
                Stat("Size", gb(m.sizeBytes), Modifier.weight(1f))
                Stat("RAM needed", "%.1f GB".format(m.ramNeededMb / 1024.0), Modifier.weight(1f))
                Stat("Speed", if (a.fit == Fit.TooLarge) "n/a" else "~%.0f tok/s".format(a.tokensPerSecond), Modifier.weight(1f))
            }
            AnimatedVisibility(open, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                Column(Modifier.padding(top = 10.dp)) {
                    Txt(m.note, Type.subhead, c.text)
                    Spacer(Modifier.height(4.dp))
                    Txt("First word in ~%.1f s · %s · %s".format(a.firstWordSeconds, m.params, m.license), Type.footnote, c.text2)
                    Txt("Source: ${m.repo}", Type.footnote, c.text2)
                }
            }
            Spacer(Modifier.height(12.dp))
            DownloadControls(
                state = state,
                downloaded = downloaded,
                selected = selected,
                blocked = a.fit == Fit.TooLarge,
                blockedText = "Too large for this device",
                onDownload = { vm.download(m) },
                onPause = { vm.pause(id) },
                onSelect = { vm.selectModel(m) },
                onDelete = onDelete,
            )
        }
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier = Modifier) {
    val c = LocalAppColors.current
    Column(modifier) {
        Txt(label, Type.caption, c.text2)
        Txt(value, Type.subhead, c.text, maxLines = 1)
    }
}

/** Download, Pause, Resume, Delete and Select with a progress bar. Shared by LLM and Whisper rows. */
@Composable
private fun DownloadControls(
    state: DownloadState,
    downloaded: Boolean,
    selected: Boolean,
    blocked: Boolean,
    blockedText: String,
    onDownload: () -> Unit,
    onPause: () -> Unit,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
) {
    val c = LocalAppColors.current
    when {
        downloaded -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            IosButton(
                if (selected) "Selected" else "Select",
                Modifier.weight(1f),
                kind = ButtonKind.Primary,
                enabled = !selected,
                icon = if (selected) Sym.check else null,
                onClick = onSelect,
            )
            IosButton("Delete", Modifier.width(110.dp), kind = ButtonKind.Danger, icon = Sym.trash, onClick = onDelete)
        }

        state is DownloadState.Downloading -> Column {
            IosProgress(if (state.total > 0) state.done.toFloat() / state.total else 0f)
            Spacer(Modifier.height(6.dp))
            Txt("${mb(state.done)} of ${mb(state.total)} · ${mb(state.bytesPerSec)}/s", Type.footnote, c.text2)
            Spacer(Modifier.height(10.dp))
            IosButton("Pause", Modifier.fillMaxWidth(), kind = ButtonKind.Secondary, icon = Sym.pause, onClick = onPause)
        }

        state is DownloadState.Paused -> Column {
            IosProgress(if (state.total > 0) state.done.toFloat() / state.total else 0f)
            Spacer(Modifier.height(6.dp))
            Txt("Paused at ${mb(state.done)} of ${mb(state.total)}", Type.footnote, c.text2)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                IosButton("Resume", Modifier.weight(1f), kind = ButtonKind.Primary, icon = Sym.play, onClick = onDownload)
                IosButton("Delete", Modifier.width(110.dp), kind = ButtonKind.Danger, icon = Sym.trash, onClick = onDelete)
            }
        }

        state is DownloadState.Verifying -> Column {
            IosProgress(if (state.total > 0) state.done.toFloat() / state.total else 0f, color = c.success)
            Spacer(Modifier.height(6.dp))
            Txt("Verifying checksum…", Type.footnote, c.text2)
        }

        state is DownloadState.Failed -> Column {
            Txt(state.message, Type.footnote, c.text)
            Spacer(Modifier.height(8.dp))
            IosButton(
                if (state.done > 0) "Resume" else "Try again",
                Modifier.fillMaxWidth(),
                kind = ButtonKind.Primary,
                icon = Sym.download,
                onClick = onDownload,
            )
        }

        blocked -> IosButton(blockedText, Modifier.fillMaxWidth(), enabled = false) {}

        else -> IosButton("Download", Modifier.fillMaxWidth(), kind = ButtonKind.Primary, icon = Sym.download, onClick = onDownload)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SpeechModelsScreen(vm: CopilotVM, onBack: () -> Unit) {
    val c = LocalAppColors.current
    var confirmDelete by remember { mutableStateOf<SpeechModel?>(null) }
    val suggested = com.interviewcopilot.app.llm.Recommender.speechModelFor(vm.device)
    Box(Modifier.fillMaxSize()) {
        LargeTitleScreen("Whisper Models", onBack = onBack, backLabel = "Settings") {
            SectionFooter("Whisper runs fully on this phone. Bigger models hear more accurately but start a little slower.")
            Spacer(Modifier.height(8.dp))
            ModelCatalog.speech.forEach { m ->
                val id = "stt:${m.id}"
                val downloaded = vm.isDownloaded(m)
                val selected = vm.g("whisperModelId") == m.id && downloaded
                Card {
                    Column(Modifier.padding(16.dp)) {
                        Txt(m.name, Type.headline)
                        Spacer(Modifier.height(6.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (m.id == suggested.id) Pill("Recommended for your device", c.success)
                            if (selected) Pill("In use", c.primary)
                            if (m.ramNeededMb > vm.device.totalRamMb * 0.5) Pill("Too large for this device", c.danger)
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth()) {
                            Stat("Size", mb(m.sizeBytes), Modifier.weight(1f))
                            Stat("RAM needed", "%.1f GB".format(m.ramNeededMb / 1024.0), Modifier.weight(1f))
                            Stat("Accuracy", "${m.accuracy}/10", Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(4.dp))
                        Txt(m.note, Type.footnote, c.text2)
                        Spacer(Modifier.height(12.dp))
                        DownloadControls(
                            state = vm.stateOf(id),
                            downloaded = downloaded,
                            selected = selected,
                            blocked = m.ramNeededMb > vm.device.totalRamMb * 0.5,
                            blockedText = "Too large for this device",
                            onDownload = { vm.download(m) },
                            onPause = { vm.pause(id) },
                            onSelect = { vm.selectSpeech(m) },
                            onDelete = { confirmDelete = m },
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
        }
        IosAlert(
            visible = confirmDelete != null,
            title = "Delete model?",
            message = "${confirmDelete?.name.orEmpty()} will be removed from this phone.",
            confirmText = "Delete",
            destructive = true,
            onConfirm = {
                confirmDelete?.let { vm.delete(it) }
                confirmDelete = null
            },
            onDismiss = { confirmDelete = null },
        )
    }
}
