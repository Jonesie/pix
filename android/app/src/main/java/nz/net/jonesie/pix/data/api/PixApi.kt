package nz.net.jonesie.pix.data.api

import nz.net.jonesie.pix.data.model.ImageListResponse
import nz.net.jonesie.pix.data.model.PixImage
import nz.net.jonesie.pix.data.model.SessionResponse
import nz.net.jonesie.pix.data.model.TagListResponse
import okhttp3.MultipartBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.DELETE
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

interface PixApi {

    @GET("api/images")
    suspend fun listImages(
        @Query("q") query: String? = null,
        @Query("tag") tag: String? = null,
        @Query("offset") offset: Int = 0,
        @Query("limit") limit: Int = 30,
    ): ImageListResponse

    @GET("api/images/{id}")
    suspend fun getImage(@Path("id") id: String): PixImage

    @GET("api/tags")
    suspend fun listTags(): TagListResponse

    @FormUrlEncoded
    @POST("api/admin/login")
    suspend fun login(@Field("password") password: String): Response<ResponseBody>

    @POST("api/admin/logout")
    suspend fun logout(): Response<ResponseBody>

    @GET("api/admin/session")
    suspend fun session(): SessionResponse

    @Multipart
    @POST("api/admin/images")
    suspend fun uploadImage(
        @Part file: MultipartBody.Part,
        @Part("caption") caption: okhttp3.RequestBody,
        @Part("description") description: okhttp3.RequestBody,
        @Part("tags") tags: okhttp3.RequestBody,
        @Part("created_date") createdDate: okhttp3.RequestBody?,
        @Part("sequence") sequence: okhttp3.RequestBody?,
    ): Response<PixImage>

    @FormUrlEncoded
    @PUT("api/admin/images/{id}")
    suspend fun updateImage(
        @Path("id") id: String,
        @Field("caption") caption: String,
        @Field("description") description: String,
        @Field("tags") tags: String,
        @Field("created_date") createdDate: String?,
        @Field("sequence") sequence: String?,
    ): Response<PixImage>

    @FormUrlEncoded
    @POST("api/admin/images/{id}/rotate")
    suspend fun rotateImage(
        @Path("id") id: String,
        @Field("degrees") degrees: Int,
    ): Response<PixImage>

    @DELETE("api/admin/images/{id}")
    suspend fun deleteImage(@Path("id") id: String): Response<ResponseBody>
}
