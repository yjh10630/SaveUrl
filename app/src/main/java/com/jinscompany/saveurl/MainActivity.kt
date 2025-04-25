package com.jinscompany.saveurl

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.compose.rememberNavController
import com.jinscompany.saveurl.ui.navigation.AppNavigation
import com.jinscompany.saveurl.ui.navigation.Navigation
import com.jinscompany.saveurl.ui.theme.SaveUrlTheme
import com.jinscompany.saveurl.utils.extractUrlFromText
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        //installSplashScreen()
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
                    Log.d("####", "sharedText > ${sharedText}\nrealUrl > ${realUrl}")
                    if (!realUrl.isNullOrEmpty()) {
                        val route = "${Navigation.Routes.SAVE_LINK}?url=$realUrl"
                        navController.navigate(route)
                    }
                }
            }

            SaveUrlTheme {
                Surface(color = colorResource(R.color.white)) {
                    AppNavigation(navController)
                }
            }
        }
    }
}