package com.interviewcopilot.app.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.interviewcopilot.app.vm.CopilotVM

@Composable
fun JobsScreen(vm: CopilotVM) {
    val c = LocalAppColors.current
    var query by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    val shown = vm.jobs.filter { query.isBlank() || it.title.contains(query, true) || it.description.contains(query, true) }

    LargeTitleScreen("Jobs") {
        IosField(query, { query = it }, "Search saved jobs")
        Spacer(Modifier.height(14.dp))
        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Txt("Add a job", Type.headline)
                IosField(title, { title = it }, "Job title and company", fillColor = c.background)
                IosField(desc, { desc = it }, "Paste the job description", singleLine = false, minLines = 5, fillColor = c.background)
                IosButton("Save job", Modifier.fillMaxWidth(), enabled = title.isNotBlank() && desc.isNotBlank()) {
                    vm.addJob(title, desc)
                    title = ""
                    desc = ""
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        if (shown.isEmpty()) {
            Txt("No saved jobs yet. The active job tailors every answer.", Type.subhead, c.text2, Modifier.padding(8.dp))
        }
        shown.forEach { job ->
            Card(Modifier.padding(bottom = 12.dp).animateContentSize()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Txt(job.title, Type.headline, modifier = Modifier.weight(1f))
                        if (job.active) Pill("Active", c.success)
                    }
                    Txt(job.description.take(200) + if (job.description.length > 200) "…" else "", Type.subhead, c.text2)
                    if (job.keywords.isNotBlank()) Txt(job.keywords, Type.subhead, c.text)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IosButton(if (job.active) "Deactivate" else "Set active", Modifier.weight(1f), kind = ButtonKind.Secondary) { vm.setActiveJob(job.id) }
                        IosButton(if (vm.jobBusyId == job.id) "Analysing…" else "Analyse", Modifier.weight(1f), kind = ButtonKind.Accent) {
                            vm.analyzeJob(job.id)
                        }
                        IosButton("Delete", Modifier.weight(1f), kind = ButtonKind.Danger) { vm.deleteJob(job.id) }
                    }
                }
            }
        }
    }
}
