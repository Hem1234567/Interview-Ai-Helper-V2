package com.interviewcopilot.app.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.interviewcopilot.app.logic.Bank
import com.interviewcopilot.app.vm.CopilotVM

@Composable
fun PracticeScreen(vm: CopilotVM) {
    val c = LocalAppColors.current
    var topic by remember { mutableStateOf("Java") }
    var index by remember { mutableIntStateOf(0) }
    var answer by remember { mutableStateOf("") }
    val questions = Bank.topics[topic].orEmpty()
    val question = questions.getOrElse(index % questions.size.coerceAtLeast(1)) { "" }
    val scores = vm.practiceScores()

    LargeTitleScreen("Practice") {
        if (scores.isNotEmpty()) {
            Txt("Answered ${scores.size} · average ${"%.1f".format(scores.average())}/10", Type.subhead, c.text2, Modifier.padding(bottom = 10.dp))
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Bank.topics.keys.forEach { t ->
                val on = t == topic
                Box(
                    Modifier.clip(RoundedCornerShape(50)).background(if (on) c.primary else c.surface)
                        .pressable {
                            topic = t
                            index = 0
                            answer = ""
                            vm.practiceOut = ""
                        }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                ) { Txt(t, Type.subhead, if (on) c.onPastel else c.text, maxLines = 1) }
            }
        }
        Spacer(Modifier.height(14.dp))
        Card {
            Column(Modifier.padding(16.dp)) {
                Txt("Question", Type.footnote, c.text2)
                Spacer(Modifier.height(6.dp))
                Txt(question, Type.title3)
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    IosButton("Ask aloud", Modifier.weight(1f), kind = ButtonKind.Secondary, icon = Sym.waveform) { vm.speak(question) }
                    IosButton("Next", Modifier.weight(1f), kind = ButtonKind.Accent, icon = Sym.chevronRight) {
                        index += 1
                        answer = ""
                        vm.practiceOut = ""
                    }
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Card {
            Column(Modifier.padding(16.dp)) {
                Txt("Your answer", Type.footnote, c.text2)
                Spacer(Modifier.height(8.dp))
                IosField(answer, { answer = it }, "Type your answer", singleLine = false, minLines = 5, fillColor = c.background)
                Spacer(Modifier.height(12.dp))
                IosButton(
                    if (vm.practiceBusy) "Scoring…" else "Score my answer",
                    Modifier.fillMaxWidth(),
                    kind = ButtonKind.Success,
                    enabled = !vm.practiceBusy && answer.isNotBlank(),
                    icon = Sym.sparkles,
                ) { vm.scorePractice(question, answer) }
            }
        }
        if (vm.practiceOut.isNotBlank()) {
            Spacer(Modifier.height(14.dp))
            Card(Modifier.animateContentSize()) {
                Column(Modifier.padding(16.dp)) {
                    Txt("Feedback", Type.title3)
                    Spacer(Modifier.height(8.dp))
                    Txt(vm.practiceOut, Type.callout, c.text)
                }
            }
        }
    }
}
