package com.jinscompany.saveurl

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.rememberNavController
import com.jinscompany.saveurl.R
import com.jinscompany.saveurl.ui.composable.SetStatusBarColor
import com.jinscompany.saveurl.ui.navigation.AppNavigation
import com.jinscompany.saveurl.ui.navigation.Navigation
import com.jinscompany.saveurl.ui.theme.SaveUrlTheme
import com.jinscompany.saveurl.utils.CmLog
import com.jinscompany.saveurl.utils.InAppUpdateCheck
import com.jinscompany.saveurl.utils.extractUrlFromText
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val sharedViewModel by viewModels<SharedViewModel>()
    private lateinit var inAppUpdateCheck: InAppUpdateCheck
    private val immediateLauncher = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        inAppUpdateCheck.onImmediateActivityResult(resultCode = result.resultCode)
    }
    private val flexibleLauncher = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { }

    private var backPressedTime: Long = 0L
    private lateinit var toast: Toast

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        inAppUpdateCheck = InAppUpdateCheck(this, immediateLauncher, flexibleLauncher, sharedViewModel)
        setupBackPressedHandler()

        enableEdgeToEdge()
        setContent {
            val navController = rememberNavController()
            val context = LocalContext.current
            val activity = context as? Activity

            // 공유 Intent 확인
            LaunchedEffect(Unit) {
                val intent = activity?.intent
                if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
                    val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
                    val realUrl = extractUrlFromText(sharedText ?: "")
                    CmLog.d("sharedText > ${sharedText}\nrealUrl > ${realUrl}")
                    if (!realUrl.isNullOrEmpty()) {
                        val route = "${Navigation.Routes.SAVE_LINK}?url=$realUrl"
                        navController.navigate(route)
                    }
                }
            }

            val darkModePref by sharedViewModel.darkModeEnabled.collectAsState()
            val isDark = darkModePref ?: isSystemInDarkTheme()
            val isFlexibleUpdateDownloaded by sharedViewModel.isFlexibleUpdateDownloaded.collectAsState()
            val snackbarHostState = remember { SnackbarHostState() }

            LaunchedEffect(isFlexibleUpdateDownloaded) {
                if (isFlexibleUpdateDownloaded) {
                    val result = snackbarHostState.showSnackbar(
                        message = getString(R.string.update_downloaded_message),
                        actionLabel = getString(R.string.update_install_action),
                        duration = SnackbarDuration.Indefinite,
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        inAppUpdateCheck.completeFlexibleUpdate()
                    }
                    sharedViewModel.setFlexibleUpdateDownloaded(false)
                }
            }

            SaveUrlTheme(darkTheme = isDark) {
                SetStatusBarColor(color = Color.DarkGray)
                Scaffold(
                    snackbarHost = { SnackbarHost(snackbarHostState) { data -> Snackbar(snackbarData = data) } },
                    containerColor = Color.DarkGray,
                ) { _ ->
                    Surface(modifier = Modifier.fillMaxSize(), color = Color.DarkGray) {
                        AppNavigation(navController)
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (!SaveUrlApplication.DEBUG) inAppUpdateCheck.resumeFlexibleUpdateCheck()
    }

    override fun onDestroy() {
        super.onDestroy()
        inAppUpdateCheck.unregisterListener()
    }

    private fun setupBackPressedHandler() {
        toast = Toast.makeText(this, R.string.back_press_exit_message, Toast.LENGTH_SHORT)
        val callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val currentTime = System.currentTimeMillis()
                if (currentTime - backPressedTime <= 2000L) {
                    toast.cancel()
                    finish()
                } else {
                    backPressedTime = currentTime
                    toast.show()
                }
            }
        }
        onBackPressedDispatcher.addCallback(this, callback)
    }
}