package com.jinscompany.saveurl.ui.setting

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.transition.Visibility
import com.jinscompany.saveurl.MainActivity
import com.jinscompany.saveurl.SaveUrlApplication
import com.jinscompany.saveurl.SharedViewModel
import com.jinscompany.saveurl.ui.composable.singleClick
import com.jinscompany.saveurl.ui.navigation.navigateToAppSetting
import com.jinscompany.saveurl.ui.navigation.navigateToTrash
import com.jinscompany.saveurl.utils.getCurrentAppVersion


@Composable
fun AppSettingScreen(
    navController: NavHostController,
    sharedViewModel: SharedViewModel = hiltViewModel(LocalActivity.current as MainActivity),
) {
    val context = LocalContext.current
    Scaffold { paddingValue ->
        AppSettingScreen(
            paddingValues = paddingValue,
            popBackStack = { navController.popBackStack() },
            shareMyApp = {
                val sendIntent = Intent().apply {
                    action = Intent.ACTION_SEND
                    putExtra(Intent.EXTRA_TEXT, "https://play.google.com/store/apps/details?id=${context.packageName}")
                    type = "text/plain"
                }
                val shareIntent = Intent.createChooser(sendIntent, "")
                context.startActivity(shareIntent)
            },
            updateClick = {
                val playStoreIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${context.packageName}"))
                try {
                    context.startActivity(playStoreIntent)
                } catch (e: ActivityNotFoundException) {
                    // Play 스토어 앱이 없으면 브라우저로 대체
                    val webIntent = Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://play.google.com/store/apps/details?id=${context.packageName}")
                    )
                    context.startActivity(webIntent)
                }
            },
            emailClick = {
                val emailSelectorIntent = Intent(Intent.ACTION_SENDTO)
                emailSelectorIntent.setData(Uri.parse("mailto:"))

                val intent = Intent(Intent.ACTION_SEND)
                intent.putExtra(Intent.EXTRA_EMAIL, arrayOf("jinscompany25@gmail.com"))    //todo 출시 전 메일 받을 주소 업데이트 하기
                intent.putExtra(Intent.EXTRA_SUBJECT, "SaveLink App 오류 신고")
                intent.putExtra(Intent.EXTRA_TEXT, "사진 및 영상을 올려주신 다면, 신속한 오류 개선에 도움이 됩니다.")
                intent.selector = emailSelectorIntent
                if (intent.resolveActivity(context.packageManager) != null)
                    context.startActivity(intent)
            },
            trashClick = {
                navController.navigateToTrash()
            },
            currentAppVersion = getCurrentAppVersion(context),
            isUpdatable = sharedViewModel.isFlexibleUpdatable
        )
    }
}

@Composable
fun AppSettingScreen(
    paddingValues: PaddingValues = PaddingValues(),
    currentAppVersion: String = "1.0.0",
    popBackStack: () -> Unit = {},
    shareMyApp: () -> Unit = {},
    updateClick: () -> Unit = {},
    emailClick: () -> Unit = {},
    trashClick: () -> Unit = {},
    isUpdatable: Boolean = true
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .background(Color.DarkGray)
    ) {
        item {
            IconButton(onClick = popBackStack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.LightGray,
                )
            }
        }
        item { Spacer(modifier = Modifier.height(12.dp)) }
        item { SettingItem(onClick = emailClick, text = "앱 오류 신고") }
        item { Divider() }
        item { SettingItem(onClick = shareMyApp, text = "친구 초대") }
        item { Divider() }
        item { SettingItem(onClick = trashClick, text = "휴지통") }
        item { Divider() }
        item { AppVersionItem(onClick = updateClick, currentVersion = currentAppVersion, isUpdateAble = isUpdatable) }
        //todo 인앱 결제 추가 예정
        //todo 구글 드라이브를 이용해여 백업 및 가져오기 기능 추가 예정
    }
}

@Composable
fun Divider() {
    HorizontalDivider(
        color = Color.Gray,
        thickness = 1.dp,
        modifier = Modifier.padding(horizontal = 12.dp)
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SettingItem(
    onClick: () -> Unit,
    text: String,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .wrapContentHeight()
            .combinedClickable(
                onClick = singleClick { onClick.invoke() },
            )
    ) {
        Row (
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                modifier = Modifier.padding(start = 12.dp),
                text = text,
                fontSize = 16.sp,
                fontWeight = FontWeight.Normal,
                color = Color.LightGray
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppVersionItem(onClick: () -> Unit, currentVersion: String, isUpdateAble: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .wrapContentHeight()
            .combinedClickable(
                onClick = {
                    onClick.invoke()
                },
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                modifier = Modifier.padding(start = 12.dp),
                text = "버전정보",
                fontSize = 16.sp,
                fontWeight = FontWeight.Normal,
                color = Color.LightGray
            )
            Row (
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    modifier = Modifier.padding(end = 12.dp),
                    text = currentVersion,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Normal,
                    color = Color.Gray
                )
                if (isUpdateAble) {
                    Icon(
                        modifier = Modifier.size(16.dp),
                        imageVector = Icons.Rounded.Autorenew,
                        contentDescription = "update",
                        tint = Color.Red
                    )
                }
            }
        }
    }
}

@Composable
@Preview(showBackground = true, backgroundColor = 0xFF444444)
fun AppSettingScreenPreview() {
    AppSettingScreen()
}