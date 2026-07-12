package com.stalker.tv.ui.browse

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Card
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.stalker.tv.data.Category
import com.stalker.tv.data.LiveStream

data class CategoryRow(val category: Category, val channels: List<LiveStream>)

@Composable
fun BrowseScreen(
    rows: List<CategoryRow>,
    isLoading: Boolean,
    onChannelSelected: (LiveStream) -> Unit
) {
    if (isLoading) {
        Box(Modifier.fillMaxSize()) { Text("Loading channels...", modifier = Modifier.padding(32.dp)) }
        return
    }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        items(rows) { row ->
            Column(modifier = Modifier.padding(bottom = 24.dp)) {
                Text(text = row.category.categoryName, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                LazyRow {
                    items(row.channels) { channel ->
                        ChannelTile(channel = channel, onClick = { onChannelSelected(channel) })
                    }
                }
            }
        }
    }
}

@Composable
private fun ChannelTile(channel: LiveStream, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .padding(end = 12.dp)
            .width(160.dp)
            .height(100.dp)
    ) {
        Box(Modifier.fillMaxSize().padding(8.dp)) {
            Text(text = channel.name, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
