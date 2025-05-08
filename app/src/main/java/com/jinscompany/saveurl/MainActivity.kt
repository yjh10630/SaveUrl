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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.rememberNavController
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
    private val updateLauncher = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        inAppUpdateCheck.onActivityResult(resultCode = result.resultCode)
    }

    private var backPressedTime: Long = 0L
    private lateinit var toast: Toast

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        inAppUpdateCheck = InAppUpdateCheck(this, updateLauncher, sharedViewModel)
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

            SaveUrlTheme {
                SetStatusBarColor(color = Color.DarkGray)
                Surface(modifier = Modifier.fillMaxSize(), color = Color.DarkGray) {
                    AppNavigation(navController)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (!SaveUrlApplication.DEBUG) inAppUpdateCheck.resumeFlexibleUpdateCheck()

    }

    private fun setupBackPressedHandler() {
        toast = Toast.makeText(this, "뒤로 버튼을 한 번 더 누르면 종료됩니다", Toast.LENGTH_SHORT)
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