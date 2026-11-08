package com.interviewcopilot.app

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.interviewcopilot.app.ui.AppRoot
import com.interviewcopilot.app.ui.AppTheme
import com.interviewcopilot.app.vm.CopilotVM

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            val vm: CopilotVM = viewModel()
            AppTheme(vm.g("theme")) {
                AppRoot(vm)
            }
        }
    }
}
