package net.reichholf.dreamdroid.room

import androidx.room3.Entity
import net.reichholf.dreamdroid.enigma.Movie

/**
 * Presence row for one movie dirname the user opened while we could sync.
 * Distinguishes a written-empty location from a dirname that was never cached.
 */
@Entity(
    tableName = "movie_list_meta",
    primaryKeys = ["profileId", "dirname"]
)
data class MovieListMetaEntity(val profileId: Int, val dirname: String)

/**
 * One ordered `/web/movielist` row for a profile + dirname. Full [Movie]
 * snapshot so the hub Movies page can paint Offline.
 */
@Entity(
    tableName = "movie_list",
    primaryKeys = ["profileId", "dirname", "position"]
)
data class MovieListEntity(
    val profileId: Int,
    val dirname: String,
    val position: Int,
    val reference: String,
    val title: String,
    val description: String,
    val descriptionExtended: String,
    val serviceName: String,
    val time: String,
    val timeReadable: String,
    val length: String,
    val tags: String,
    val fileName: String,
    val fileSize: String,
    val fileSizeReadable: String
)

fun Movie.toListEntity(profileId: Int, dirname: String, position: Int): MovieListEntity =
    MovieListEntity(
        profileId = profileId,
        dirname = dirname,
        position = position,
        reference = reference,
        title = title,
        description = description,
        descriptionExtended = descriptionExtended,
        serviceName = serviceName,
        time = time,
        timeReadable = timeReadable,
        length = length,
        tags = tags,
        fileName = fileName,
        fileSize = fileSize,
        fileSizeReadable = fileSizeReadable
    )

fun MovieListEntity.toMovie(): Movie = Movie(
    reference = reference,
    title = title,
    description = description,
    descriptionExtended = descriptionExtended,
    serviceName = serviceName,
    time = time,
    timeReadable = timeReadable,
    length = length,
    tags = tags,
    fileName = fileName,
    fileSize = fileSize,
    fileSizeReadable = fileSizeReadable
)
