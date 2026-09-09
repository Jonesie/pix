package nz.net.jonesie.pix

import android.content.Context
import nz.net.jonesie.pix.data.repo.PixRepository
import nz.net.jonesie.pix.data.repo.SessionManager

/** Simple hand-rolled service locator — small app, no need for a DI framework. */
class AppContainer(context: Context) {
    val repository = PixRepository(context)
    val session = SessionManager(repository)
}
