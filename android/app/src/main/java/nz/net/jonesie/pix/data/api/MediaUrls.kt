package nz.net.jonesie.pix.data.api

import android.content.Context

object MediaUrls {
    fun thumb(context: Context, filename: String) =
        "${ServerConfig.getBaseUrl(context)}images/thumb/$filename"

    fun full(context: Context, filename: String) =
        "${ServerConfig.getBaseUrl(context)}images/full/$filename"
}
