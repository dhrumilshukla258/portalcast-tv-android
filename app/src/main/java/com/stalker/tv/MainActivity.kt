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
                    val selectedTab by viewModel.selectedTab.collectAsState()
                    val liveRows by viewModel.liveRows.collectAsState()
                    val vodRows by viewModel.vodRows.collectAsState()
                    val seriesRows by viewModel.seriesRows.collectAsState()

                    when (val s = screen) {
                        is Screen.Login -> LoginScreen(
                            isLoading = isLoading,
                            error = error,
                            onSubmit = { url, user, pass -> viewModel.login(url, user, pass) }
                        )
                        is Screen.Browse -> BrowseScreen(
                            selectedTab = selectedTab,
                            onTabSelected = { viewModel.selectTab(it) },
                            liveRows = liveRows,
                            vodRows = vodRows,
                            seriesRows = seriesRows,
                            isLoading = isLoading,
                            onChannelSelected = { viewModel.playChannel(it) },
                            onMovieSelected = { viewModel.playVod(it) },
                            onSeriesSelected = { /* episode picker not yet implemented */ }
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
