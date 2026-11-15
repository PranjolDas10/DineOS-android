package dev.dineos.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import dev.dineos.app.data.Session
import dev.dineos.app.ui.LoginScreen
import dev.dineos.app.ui.LoginViewModel
import dev.dineos.app.ui.TodayScreen
import dev.dineos.app.ui.TodayViewModel
import dev.dineos.app.ui.theme.DineTheme
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var session: Session
    private val login: LoginViewModel by viewModels()
    private val today: TodayViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        lifecycleScope.launch { session.load() }
        setContent {
            DineTheme {
                val loaded by session.loaded.collectAsStateWithLifecycle()
                val tokens by session.tokens.collectAsStateWithLifecycle()
                // Two destinations, chosen by one fact (signed in or not): no navigation
                // library until there is a third screen. Nothing is drawn until the saved
                // session has been read, so a signed-in owner never sees the sign-in form flash.
                when {
                    !loaded -> Unit
                    tokens == null -> LoginScreen(login)
                    else -> TodayScreen(today)
                }
            }
        }
    }
}
