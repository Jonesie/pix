package nz.net.jonesie.pix.data.repo

import android.content.Context
import kotlinx.serialization.SerializationException
import nz.net.jonesie.pix.data.api.ApiClient
import nz.net.jonesie.pix.data.api.PixApi
import nz.net.jonesie.pix.data.model.ApiError
import nz.net.jonesie.pix.data.model.PixImage
import nz.net.jonesie.pix.data.model.Tag
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Response
import java.io.File

class ApiException(message: String) : Exception(message)

private fun parseErrorDetail(response: Response<*>, fallback: String): String {
    val body = response.errorBody()?.string()
    if (body.isNullOrBlank()) return fallback
    return try {
        ApiClient.json.decodeFromString(ApiError.serializer(), body).detail ?: fallback
    } catch (e: SerializationException) {
        fallback
    }
}

private fun String.toTextPart() = toRequestBody("text/plain".toMediaTypeOrNull())

class PixRepository(context: Context) {

    private val api: PixApi = ApiClient.get(context)

    suspend fun listImages(query: String?, tag: String?, offset: Int, limit: Int = 30) =
        api.listImages(query?.takeIf { it.isNotBlank() }, tag?.takeIf { it.isNotBlank() }, offset, limit)

    suspend fun getImage(id: String): PixImage = api.getImage(id)

    suspend fun listTags(): List<Tag> = api.listTags().items

    suspend fun isAuthenticated(): Boolean = api.session().authenticated

    suspend fun login(password: String) {
        val response = api.login(password)
        if (!response.isSuccessful) {
            throw ApiException(parseErrorDetail(response, "Wrong password."))
        }
    }

    suspend fun logout() {
        api.logout()
    }

    suspend fun uploadImage(
        file: File,
        mimeType: String,
        originalFilename: String,
        caption: String,
        description: String,
        tags: String,
        createdDate: String?,
        sequence: String?,
        onProgress: (Float) -> Unit,
    ): PixImage {
        val fileBody = ProgressRequestBody(file, mimeType.toMediaTypeOrNull(), onProgress)
        val filePart = MultipartBody.Part.createFormData("file", originalFilename, fileBody)
        val response = api.uploadImage(
            file = filePart,
            caption = caption.toTextPart(),
            description = description.toTextPart(),
            tags = tags.toTextPart(),
            createdDate = createdDate?.takeIf { it.isNotBlank() }?.toTextPart(),
            sequence = sequence?.takeIf { it.isNotBlank() }?.toTextPart(),
        )
        if (!response.isSuccessful) {
            throw ApiException(parseErrorDetail(response, "Upload failed."))
        }
        return response.body() ?: throw ApiException("Upload failed.")
    }

    suspend fun updateImage(
        id: String,
        caption: String,
        description: String,
        tags: String,
        createdDate: String?,
        sequence: String?,
    ): PixImage {
        val response = api.updateImage(id, caption, description, tags, createdDate, sequence)
        if (!response.isSuccessful) {
            throw ApiException(parseErrorDetail(response, "Save failed."))
        }
        return response.body() ?: throw ApiException("Save failed.")
    }

    suspend fun rotateImage(id: String, degrees: Int): PixImage {
        val response = api.rotateImage(id, degrees)
        if (!response.isSuccessful) {
            throw ApiException(parseErrorDetail(response, "Rotate failed."))
        }
        return response.body() ?: throw ApiException("Rotate failed.")
    }

    suspend fun deleteImage(id: String) {
        val response = api.deleteImage(id)
        if (!response.isSuccessful) {
            throw ApiException(parseErrorDetail(response, "Delete failed."))
        }
    }
}
