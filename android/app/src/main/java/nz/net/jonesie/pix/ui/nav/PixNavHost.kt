package nz.net.jonesie.pix.ui.nav

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import nz.net.jonesie.pix.ui.detail.DetailScreen
import nz.net.jonesie.pix.ui.edit.EditScreen
import nz.net.jonesie.pix.ui.gallery.GalleryScreen
import nz.net.jonesie.pix.ui.login.LoginScreen
import nz.net.jonesie.pix.ui.upload.UploadScreen

private const val GALLERY = "gallery"
private const val LOGIN = "login"
private const val UPLOAD = "upload"
private const val DETAIL = "detail/{id}"
private const val EDIT = "edit/{id}"

private fun signalGalleryRefresh(navController: NavHostController) {
    runCatching {
        navController.getBackStackEntry(GALLERY).savedStateHandle["refresh"] = true
    }
}

@Composable
fun PixNavHost() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = GALLERY) {
        composable(GALLERY) {
            GalleryScreen(
                navController = navController,
                onOpenDetail = { id -> navController.navigate("detail/$id") },
                onOpenLogin = { navController.navigate(LOGIN) },
                onOpenUpload = { navController.navigate(UPLOAD) },
                onOpenEdit = { id -> navController.navigate("edit/$id") },
            )
        }
        composable(LOGIN) {
            LoginScreen(
                onBack = { navController.popBackStack() },
                onLoggedIn = { navController.popBackStack() },
            )
        }
        composable(UPLOAD) {
            UploadScreen(
                onBack = { navController.popBackStack() },
                onUploaded = {
                    signalGalleryRefresh(navController)
                    navController.popBackStack()
                },
            )
        }
        composable(DETAIL) { backStackEntry ->
            val id = backStackEntry.arguments?.getString("id") ?: return@composable
            DetailScreen(
                imageId = id,
                onBack = { navController.popBackStack() },
                onOpenEdit = { editId -> navController.navigate("edit/$editId") },
                onNavigateTo = { nextId -> navController.navigate("detail/$nextId") },
                onDeleted = {
                    signalGalleryRefresh(navController)
                    navController.popBackStack(GALLERY, inclusive = false)
                },
            )
        }
        composable(EDIT) { backStackEntry ->
            val id = backStackEntry.arguments?.getString("id") ?: return@composable
            EditScreen(
                imageId = id,
                onBack = { navController.popBackStack() },
                onSaved = {
                    signalGalleryRefresh(navController)
                    navController.popBackStack(GALLERY, inclusive = false)
                },
            )
        }
    }
}
