package com.stalker.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import com.stalker.tv.ui.browse.BrowseScreen
import com.stalker.tv.ui.login.LoginScreen
import com.stalker.tv.ui.player.PlayerScreen

class MainActivity : ComponentActivity() {
    private val viewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface {
                    val screen by viewModel.screen.collectAsState()
                    val isLoading by viewModel.isLoading.collectAsState()
                    val error by viewModel.error.collectAsState()
                    val rows by viewModel.rows.collectAsState()

                    when (val s = screen) {
                        is Screen.Login -> LoginScreen(
                            isLoading = isLoading,
                            error = error,
                            onSubmit = { url, user, pass -> viewModel.login(url, user, pass) }
                        )
                        is Screen.Browse -> BrowseScreen(
                            rows = rows,
                            isLoading = isLoading,
                            onChannelSelected = { viewModel.playChannel(it) }
                        )
                        is Screen.Player -> PlayerScreen(streamUrl = s.url)
                    }
                }
            }
        }
    }

    override fun onBackPressed() {
        if (viewModel.screen.value is Screen.Player) {
            viewModel.backToBrowse()
        } else {
            super.onBackPressed()
        }
    }
}
