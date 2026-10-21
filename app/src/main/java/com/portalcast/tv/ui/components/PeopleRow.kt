package com.portalcast.tv.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.portalcast.tv.data.model.PersonCredit
import com.portalcast.tv.data.model.TitleDetails
import com.portalcast.tv.ui.theme.AppColors

/** One person in a title's credits, with the line under their name (character or job). */
data class PersonEntry(val person: PersonCredit, val line: String?)

/** Cast first (with characters), then creators, directors and writers (with the job). */
fun TitleDetails.peopleEntries(): List<PersonEntry> = buildList {
    cast.take(20).forEach { add(PersonEntry(it, it.role?.takeIf { r -> r.isNotBlank() })) }
    creators.forEach { add(PersonEntry(it, "Creator")) }
    directors.forEach { add(PersonEntry(it, "Director")) }
    writersPeople.take(4).forEach { add(PersonEntry(it, it.role?.takeIf { r -> r.isNotBlank() } ?: "Writer")) }
}.distinctBy { (it.person.id ?: it.person.name.hashCode()) to it.line }

/**
 * A title's people as a row: each one is focusable and opens their page (every title of theirs
 * in the catalog). The whole tile scales on focus, photo and name together, so the enlarged
 * photo never covers the name under it. People without an id (older servers) are shown, not
 * focusable.
 */
@Composable
fun PeopleRow(details: TitleDetails?, onOpenPerson: (PersonCredit) -> Unit, modifier: Modifier = Modifier) {
    val people = details?.peopleEntries().orEmpty()
    if (people.isEmpty()) return
    Column(modifier) {
        Spacer(Modifier.height(18.dp))
        Text("Cast & crew", style = MaterialTheme.typography.labelLarge, color = AppColors.TextMuted)
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            // Room for the focus scale and ring inside the row, which clips its children.
            contentPadding = PaddingValues(vertical = 10.dp, horizontal = 6.dp),
        ) {
            itemsIndexed(people, key = { i, p -> "$i:${p.person.id}:${p.person.name}" }) { _, p ->
                PersonTile(p, onClick = { onOpenPerson(p.person) })
            }
        }
    }
}

@Composable
fun PersonTile(entry: PersonEntry, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(12.dp)
    val content: @Composable () -> Unit = {
        Column(Modifier.width(104.dp).padding(vertical = 8.dp, horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            PersonPhoto(entry.person.name, entry.person.photo, 72)
            Text(
                entry.person.name,
                style = MaterialTheme.typography.labelMedium,
                color = AppColors.Text,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp),
            )
            entry.line?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = AppColors.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
            }
        }
    }
    if (entry.person.id == null) {
        Box(modifier) { content() }
        return
    }
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
        border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(3.dp, AppColors.Focus), shape = shape)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = androidx.compose.ui.graphics.Color.Transparent,
            focusedContainerColor = AppColors.GlassFillFocused,
            contentColor = AppColors.Text,
            focusedContentColor = AppColors.Text,
        ),
    ) { content() }
}

/** A round photo, or the person's initials. */
@Composable
fun PersonPhoto(name: String, photo: String?, sizeDp: Int) {
    val url = rememberImageUrl(photo)
    Box(Modifier.size(sizeDp.dp).clip(CircleShape).background(AppColors.Surface), contentAlignment = Alignment.Center) {
        if (url != null) {
            AsyncImage(model = url, contentDescription = name, contentScale = ContentScale.Crop, modifier = Modifier.size(sizeDp.dp))
        } else {
            Text(name.split(" ").mapNotNull { it.firstOrNull()?.uppercase() }.take(2).joinToString(""), color = AppColors.TextMuted, style = MaterialTheme.typography.titleMedium)
        }
    }
}

/** Every person of a title in a panel (the series page has no room for a row). */
@Composable
fun CastAndCrewDialog(title: String, details: TitleDetails, onOpenPerson: (PersonCredit) -> Unit, onDismiss: () -> Unit) {
    val people = details.peopleEntries()
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { repeat(10) { withFrameNanos { }; if (runCatching { first.requestFocus() }.isSuccess) return@LaunchedEffect } }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .widthIn(max = 900.dp)
                .heightIn(max = 600.dp)
                .background(AppColors.Surface, RoundedCornerShape(16.dp))
                .padding(28.dp),
        ) {
            Text("$title · Cast & crew", style = MaterialTheme.typography.headlineSmall, color = AppColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(16.dp))
            LazyVerticalGrid(
                columns = GridCells.Adaptive(116.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(people, key = { i, p -> "$i:${p.person.id}:${p.person.name}" }) { i, p ->
                    PersonTile(p, onClick = { onOpenPerson(p.person) }, modifier = if (i == 0) Modifier.focusRequester(first) else Modifier)
                }
            }
        }
    }
}
