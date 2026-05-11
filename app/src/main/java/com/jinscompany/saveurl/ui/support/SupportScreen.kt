package com.jinscompany.saveurl.ui.support

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.jinscompany.saveurl.MainActivity
import com.jinscompany.saveurl.SharedViewModel
import com.jinscompany.saveurl.billing.BillingProducts
import com.jinscompany.saveurl.billing.ProductsLoadState
import com.jinscompany.saveurl.ui.composable.singleClick
import com.jinscompany.saveurl.ui.theme.AppBackground
import com.jinscompany.saveurl.ui.theme.AppDivider
import com.jinscompany.saveurl.ui.theme.AppPrimary
import com.jinscompany.saveurl.ui.theme.AppSurface
import com.jinscompany.saveurl.ui.theme.AppTextPrimary
import com.jinscompany.saveurl.ui.theme.AppTextSecondary

@Composable
fun SupportScreen(
    popBackStack: () -> Unit,
    sharedViewModel: SharedViewModel = hiltViewModel(LocalActivity.current as MainActivity)
) {
    val activity = LocalActivity.current as MainActivity
    val isAdsRemoved by sharedViewModel.isAdsRemoved.collectAsState()
    val loadState by sharedViewModel.productsLoadState.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackground),
        contentPadding = PaddingValues(bottom = 40.dp)
    ) {
        // 헤더
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = popBackStack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = AppTextSecondary,
                    )
                }
                Text(
                    text = "개발자 응원하기",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppTextPrimary
                )
            }
        }

        // 응원 메시지
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(text = "🙏", fontSize = 40.sp)
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "혼자 만들고 있는 앱이에요",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppTextPrimary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "후원은 앱 개발을 계속하는 데\n큰 힘이 됩니다 감사합니다!",
                    fontSize = 14.sp,
                    color = AppTextSecondary,
                    textAlign = TextAlign.Center,
                    lineHeight = 22.sp
                )
            }
        }

        item { Spacer(modifier = Modifier.height(8.dp)) }

        // 상품 리스트 or 플레이스홀더
        item {
            when (loadState) {
                ProductsLoadState.Loading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = AppPrimary,
                            modifier = Modifier.size(32.dp),
                            strokeWidth = 2.dp
                        )
                    }
                }
                ProductsLoadState.Error -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.WifiOff,
                                contentDescription = null,
                                tint = AppTextSecondary,
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "상품 정보를 불러오지 못했어요",
                                fontSize = 15.sp,
                                color = AppTextSecondary,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "네트워크 연결을 확인하고 다시 시도해주세요",
                                fontSize = 13.sp,
                                color = AppTextSecondary.copy(alpha = 0.6f),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
                ProductsLoadState.Success -> {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = AppSurface,
                        tonalElevation = 0.dp
                    ) {
                        Column {
                            SupportItem(
                                icon = Icons.Default.LocalCafe,
                                iconTint = Color(0xFFB07D54),
                                title = "커피 한 잔 후원",
                                price = "₩1,100",
                                onClick = { sharedViewModel.launchBilling(activity, BillingProducts.SUPPORT_COFFEE) }
                            )
                            HorizontalDivider(color = AppDivider, thickness = 0.5.dp, modifier = Modifier.padding(horizontal = 16.dp))
                            SupportItem(
                                icon = Icons.Default.Cake,
                                iconTint = Color(0xFFE48DAA),
                                title = "간식 후원",
                                price = "₩3,300",
                                onClick = { sharedViewModel.launchBilling(activity, BillingProducts.SUPPORT_SNACK) }
                            )
                            HorizontalDivider(color = AppDivider, thickness = 0.5.dp, modifier = Modifier.padding(horizontal = 16.dp))
                            if (isAdsRemoved) {
                                SupportItem(
                                    icon = Icons.Default.CheckCircle,
                                    iconTint = AppPrimary,
                                    title = "광고 제거됨",
                                    price = "구매 완료 ✓",
                                    showArrow = false,
                                    onClick = {}
                                )
                            } else {
                                SupportItem(
                                    icon = Icons.Default.AutoAwesome,
                                    iconTint = Color(0xFFFFCC00),
                                    title = "광고 제거 (영구)",
                                    price = "₩5,500",
                                    onClick = { sharedViewModel.launchBilling(activity, BillingProducts.REMOVE_ADS) }
                                )
                            }
                            HorizontalDivider(color = AppDivider, thickness = 0.5.dp, modifier = Modifier.padding(horizontal = 16.dp))
                            SupportItem(
                                icon = Icons.Default.Sync,
                                iconTint = AppTextSecondary,
                                title = "구매 복원",
                                price = "재설치 후 복원",
                                onClick = { sharedViewModel.restorePurchases() }
                            )
                        }
                    }
                }
            }
        }

        // 고지 문구 (로딩/에러 시에도 표시)
        if (loadState != ProductsLoadState.Loading) {
            item { Spacer(modifier = Modifier.height(24.dp)) }
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    NoticeText("· 결제는 Google Play를 통해 안전하게 처리됩니다.")
                    NoticeText("· 광고 제거는 동일한 Google 계정으로 재설치 시 자동 복원됩니다.")
                    NoticeText("· 환불은 Google Play 정책에 따릅니다.")
                    NoticeText("· 후원 상품은 앱 기능 변경 없이 개발자를 응원하는 용도입니다.")
                }
            }
        }
    }
}

@Composable
private fun SupportItem(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    price: String,
    showArrow: Boolean = true,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = singleClick { onClick() })
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(14.dp))
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Normal,
                color = AppTextPrimary
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = price,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = AppTextSecondary
            )
            if (showArrow) {
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.NavigateNext,
                    contentDescription = null,
                    tint = AppTextSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun NoticeText(text: String) {
    Text(
        text = text,
        fontSize = 12.sp,
        color = AppTextSecondary.copy(alpha = 0.6f),
        lineHeight = 18.sp
    )
}
