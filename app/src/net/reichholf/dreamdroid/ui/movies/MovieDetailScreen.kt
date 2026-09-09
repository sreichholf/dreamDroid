package net.reichholf.dreamdroid.ui.movies

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import net.reichholf.dreamdroid.enigma.Movie
import net.reichholf.dreamdroid.helpers.enigma2.Movie as HashMovie

data class MovieDetailContent(
    val title: String,
    val serviceName: String,
    val description: String,
    val descriptionExtended: String,
    val tags: List<String>,
    val length: String,
    val date: String,
    val fileSize: String,
)

fun Movie.toMovieDetailContent(): MovieDetailContent {
    val tagList = if (tags.isBlank()) {
        emptyList()
    } else {
        tags.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    }
    return MovieDetailContent(
        title = title,
        serviceName = serviceName,
        description = description,
        descriptionExtended = descriptionExtended.replace("\\n", "\n"),
        tags = tagList,
        length = length,
        date = timeReadable,
        fileSize = fileSizeReadable,
    )
}

fun HashMovie.toMovieDetailContent(): MovieDetailContent {
    return MovieDetailContent(
        title = title().orEmpty(),
        serviceName = serviceName().orEmpty(),
        description = description().orEmpty(),
        descriptionExtended = descriptionExtended(),
        tags = tags(),
        length = length().orEmpty(),
        date = timeReadable().orEmpty(),
        fileSize = fileSizeReadable().orEmpty(),
    )
}

fun HashMovie.toTypedMovie(): Movie {
    return Movie(
        reference = reference().orEmpty(),
        title = title().orEmpty(),
        description = description().orEmpty(),
        descriptionExtended = descriptionExtended(),
        serviceName = serviceName().orEmpty(),
        time = time().orEmpty(),
        timeReadable = timeReadable().orEmpty(),
        length = length().orEmpty(),
        tags = getString(HashMovie.KEY_TAGS, "").orEmpty(),
        fileName = fileName().orEmpty(),
        fileSize = fileSize().orEmpty(),
        fileSizeReadable = fileSizeReadable().orEmpty(),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MovieDetailScreen(
    content: MovieDetailContent,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = 480.dp)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = 16.dp, bottom = 24.dp),
    ) {
        Text(
            text = content.title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (content.serviceName.isNotEmpty()) {
            Text(
                text = content.serviceName,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        if (content.description.isNotEmpty()) {
            Text(
                text = content.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        if (content.tags.isNotEmpty()) {
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                content.tags.forEach { tag ->
                    Text(
                        text = tag,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier
                            .background(
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                shape = RoundedCornerShape(8.dp),
                            )
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                    )
                }
            }
        }
        if (content.length.isNotEmpty()) {
            Text(
                text = content.length,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        if (content.date.isNotEmpty()) {
            Text(
                text = content.date,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        if (content.descriptionExtended.isNotEmpty()) {
            Text(
                text = content.descriptionExtended,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 8.dp, bottom = 8.dp),
            )
        }
        if (content.fileSize.isNotEmpty()) {
            Text(
                text = content.fileSize,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
