package com.interviewcopilot.app.ui

import android.content.Intent
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.interviewcopilot.app.data.QA
import com.interviewcopilot.app.vm.CopilotVM
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(vm: CopilotVM) {
    val c = LocalAppColors.current
    val ctx = LocalContext.current
    var query by remember { mutableStateOf("") }
    var onlyFav by remember { mutableStateOf(false) }
    var openId by remember { mutableStateOf("") }

    val filtered = vm.history.filter {
        (!onlyFav || it.favorite) && (query.isBlank() || it.question.contains(query, true) || it.answer.contains(query, true))
    }
    val groups = filtered.groupBy { it.session }.entries.sortedByDescending { e -> e.value.maxOf { it.time } }
    val fmt = SimpleDateFormat("d MMM, h:mm a", Locale.getDefault())

    fun share(text: String) {
        val i = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        ctx.startActivity(Intent.createChooser(i, "Share").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    LargeTitleScreen("History") {
        IosField(query, { query = it }, "Search questions and answers")
        Spacer(Modifier.height(12.dp))
        Segmented(listOf("All", "Favorites"), if (onlyFav) "Favorites" else "All", { onlyFav = it == "Favorites" })
        Spacer(Modifier.height(8.dp))
        if (groups.isEmpty()) {
            Txt("No sessions yet. Questions and answers are saved on this phone only.", Type.subhead, c.text2, Modifier.padding(8.dp))
        }
        groups.forEach { (_, items) ->
            val sorted = items.sortedBy { it.time }
            SectionHeader("${fmt.format(Date(sorted.first().time))} · ${sorted.size} question${if (sorted.size == 1) "" else "s"} · ${sorted.first().mode}")
            sorted.forEach { qa -> QaCard(qa, openId == qa.id, { openId = if (openId == qa.id) "" else qa.id }, vm, ::share) }
            Txt(
                "Export session",
                Type.subhead,
                c.text,
                Modifier.pressable {
                    share(sorted.mapIndexed { i, q -> "Q${i + 1}. ${q.question}\n${q.answer}" }.joinToString("\n\n"))
                }.padding(8.dp),
            )
        }
    }
}

@Composable
private fun QaCard(qa: QA, open: Boolean, toggle: () -> Unit, vm: CopilotVM, share: (String) -> Unit) {
    val c = LocalAppColors.current
    val clipboard = LocalClipboardManager.current
    Card(Modifier.padding(bottom = 10.dp).animateContentSize()) {
        Column(Modifier.pressable(haptic = false) { toggle() }.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Txt(qa.question, Type.headline, maxLines = if (open) 10 else 2)
            Txt(qa.answer, Type.subhead, if (open) c.text else c.text2, maxLines = if (open) 200 else 2)
            if (open) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Sf(
                        if (qa.favorite) Sym.star else Sym.star,
                        if (qa.favorite) c.warning else c.text2,
                        24.dp,
                        Modifier.pressable {
                            vm.toggleFavorite(qa.id)
                        },
                        "Favorite",
                    )
                    Spacer(Modifier.width(18.dp))
                    Sf(Sym.copy, c.text, 22.dp, Modifier.pressable { clipboard.setText(AnnotatedString(qa.answer)) }, "Copy")
                    Spacer(Modifier.width(18.dp))
                    Sf(Sym.share, c.text, 22.dp, Modifier.pressable { share("${qa.question}\n\n${qa.answer}") }, "Share")
                    Spacer(Modifier.width(18.dp))
                    Sf(Sym.trash, c.danger, 22.dp, Modifier.pressable { vm.deleteQA(qa.id) }, "Delete")
                }
            }
        }
    }
}
