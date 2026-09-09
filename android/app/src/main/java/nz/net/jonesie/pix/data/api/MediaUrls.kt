package nz.net.jonesie.pix.data.api

object MediaUrls {
    fun thumb(filename: String) = "${ApiConfig.BASE_URL}images/thumb/$filename"
    fun full(filename: String) = "${ApiConfig.BASE_URL}images/full/$filename"
}
