package com.jinscompany.saveurl.ui.setting

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController

@Composable
fun AppSettingScreen(
    navController: NavHostController
) {

}

@Composable
@Preview(showBackground = true, backgroundColor = 0xFF444444)
fun AppSettingScreenPreview() {
    AppSettingScreen(rememberNavController())
}