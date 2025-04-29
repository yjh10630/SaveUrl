package com.jinscompany.saveurl.ui.setting

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.NavigateNext
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
import androidx.navigation.NavHostController


@Composable
fun AppSettingScreen(
    navController: NavHostController
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
                val intent = Intent(Intent.ACTION_VIEW)
                intent.setData(Uri.parse("market://details?id=" + context.packageName))
                context.startActivity(intent)
            },
            emailClick = {
                val emailSelectorIntent = Intent(Intent.ACTION_SENDTO)
                emailSelectorIntent.setData(Uri.parse("mailto:"))

                val intent = Intent(Intent.ACTION_SEND)
                intent.putExtra(Intent.EXTRA_EMAIL, arrayOf("yjhzzing@gmail.com"))    //todo 출시 전 메일 받을 주소 업데이트 하기
                intent.putExtra(Intent.EXTRA_SUBJECT, "SaveLink App 오류 신고")
                intent.putExtra(Intent.EXTRA_TEXT, "사진 및 영상을 올려주신 다면, 신속한 오류 개선에 도움이 됩니다.")
                intent.selector = emailSelectorIntent
                if (intent.resolveActivity(context.packageManager) != null)
                    context.startActivity(intent)
            }
        )
    }
}

@Composable
fun AppSettingScreen(
    paddingValues: PaddingValues = PaddingValues(),
    popBackStack: () -> Unit = {},
    shareMyApp: () -> Unit = {},
    updateClick: () -> Unit = {},
    emailClick: () -> Unit = {}
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
        item { AppVersionItem(onClick = updateClick, currentVersion = "1.0.0", isUpdateAble = true) }   //todo 앱 버전 관리는 파이어베이스를 통해 할 예정,
        //todo 휴지통 기능 추가 예정 ( 저장 기간은 최대 한달 )
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
                onClick = onClick,
            )
    ) {
        Row (
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                modifier = Modifier.padding(start = 12.dp),
                text = text,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.LightGray
            )
            Icon(
                modifier = Modifier.padding(end = 12.dp),
                imageVector = Icons.AutoMirrored.Filled.NavigateNext,
                contentDescription = "Back",
                tint = Color.LightGray,
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
                    if (isUpdateAble) {
                        onClick.invoke()
                    }
                },
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                modifier = Modifier.padding(start = 12.dp),
                text = currentVersion,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = Color.LightGray
            )
            if (isUpdateAble) {
                Text(
                    modifier = Modifier.padding(end = 12.dp),
                    text = "업데이트 필요",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Red
                )
            }
        }
    }
}

@Composable
@Preview(showBackground = true, backgroundColor = 0xFF444444)
fun AppSettingScreenPreview() {
    AppSettingScreen()
}