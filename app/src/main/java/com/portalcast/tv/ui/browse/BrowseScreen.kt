package com.portalcast.tv.ui.browse

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Card
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Tab
import androidx.tv.material3.TabRow
import androidx.tv.material3.Text
import com.portalcast.tv.data.Category
import com.portalcast.tv.data.LiveStream
import com.portalcast.tv.data.Series
import com.portalcast.tv.data.VodStream

enum class ContentTab(val label: String) { LIVE("Live TV"), MOVIES("Movies"), SERIES("Series") }

data class CategoryRow<T>(val category: Category, val items: List<T>)

@Composable
fun BrowseScreen(
    selectedTab: ContentTab,
    onTabSelected: (ContentTab) -> Unit,
    liveRows: List<CategoryRow<LiveStream>>,
    vodRows: List<CategoryRow<VodStream>>,
    seriesRows: List<CategoryRow<Series>>,
    isLoading: Boolean,
    onChannelSelected: (LiveStream) -> Unit,
    onMovieSelected: (VodStream) -> Unit,
    onSeriesSelected: (Series) -> Unit
) {
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        TabRow(selectedTabIndex = ContentTab.values().indexOf(selectedTab)) {
            ContentTab.values().forEach { tab ->
                Tab(selected = tab == selectedTab, onFocus = { onTabSelected(tab) }) {
                    Text(text = tab.label, modifier = Modifier.padding(12.dp))
                }
            }
        }
        Spacer(Modifier.height(16.dp))

        if (isLoading && selectedTab == ContentTab.LIVE && liveRows.isEmpty()) {
            Text("Loading...", modifier = Modifier.padding(32.dp))
            return
        }

        when (selectedTab) {
            ContentTab.LIVE -> RowsList(liveRows, { it.name }, onChannelSelected)
            ContentTab.MOVIES -> RowsList(vodRows, { it.name }, onMovieSelected)
            ContentTab.SERIES -> RowsList(seriesRows, { it.name }, onSeriesSelected)
        }
    }
}

@Composable
private fun <T> RowsList(rows: List<CategoryRow<T>>, label: (T) -> String, onClick: (T) -> Unit) {
    if (rows.isEmpty()) {
        Text("Nothing here yet.", modifier = Modifier.padding(16.dp))
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(rows) { row ->
            Column(modifier = Modifier.padding(bottom = 24.dp)) {
                Text(text = row.category.categoryName, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                LazyRow {
                    items(row.items) { item ->
                        Tile(text = label(item), onClick = { onClick(item) })
                    }
                }
            }
        }
    }
}

@Composable
private fun Tile(text: String, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.padding(end = 12.dp).width(160.dp).height(100.dp)
    ) {
        Box(Modifier.fillMaxSize().padding(8.dp)) {
            Text(text = text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
