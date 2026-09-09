package nz.net.jonesie.pix.data.model

import kotlinx.serialization.Serializable

@Serializable
data class PixImage(
    val id: String,
    val filename_full: String,
    val filename_thumb: String,
    val caption: String,
    val description: String,
    val sequence: Int? = null,
    val created_date: String? = null,
    val uploaded_at: String? = null,
    val media_type: String,
    val tags: List<String> = emptyList(),
    val content_md: String? = null,
    val prev_id: String? = null,
    val next_id: String? = null,
) {
    val isVideo: Boolean get() = media_type == "video"
}

@Serializable
data class ImageListResponse(
    val items: List<PixImage>,
    val has_more: Boolean,
    val next_offset: Int,
)

@Serializable
data class Tag(
    val name: String,
    val count: Int,
)

@Serializable
data class TagListResponse(
    val items: List<Tag>,
)

@Serializable
data class SessionResponse(
    val authenticated: Boolean,
)

@Serializable
data class ApiError(
    val detail: String? = null,
)
