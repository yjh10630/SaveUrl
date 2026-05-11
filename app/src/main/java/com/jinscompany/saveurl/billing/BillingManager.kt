package com.jinscompany.saveurl.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.consumePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

object BillingProducts {
    const val REMOVE_ADS = "com.jinscompany.saveurl.remove_ads"
    const val SUPPORT_COFFEE = "com.jinscompany.saveurl.support_coffee"
    const val SUPPORT_SNACK = "com.jinscompany.saveurl.support_snack"

    val consumableIds = setOf(SUPPORT_COFFEE, SUPPORT_SNACK)
}

sealed class BillingUiEffect {
    data class PurchaseSuccess(val productId: String) : BillingUiEffect()
    data class PurchaseFailed(val message: String) : BillingUiEffect()
    object PurchaseCancelled : BillingUiEffect()
}

enum class ProductsLoadState { Loading, Success, Error }

@Singleton
class BillingManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _isAdsRemoved = MutableStateFlow(false)
    val isAdsRemoved: StateFlow<Boolean> = _isAdsRemoved.asStateFlow()

    private val _productDetails = MutableStateFlow<List<ProductDetails>>(emptyList())
    val productDetails: StateFlow<List<ProductDetails>> = _productDetails.asStateFlow()

    private val _productsLoadState = MutableStateFlow(ProductsLoadState.Loading)
    val productsLoadState: StateFlow<ProductsLoadState> = _productsLoadState.asStateFlow()

    private val _uiEffect = MutableStateFlow<BillingUiEffect?>(null)
    val uiEffect: StateFlow<BillingUiEffect?> = _uiEffect.asStateFlow()

    private var retryCount = 0
    private val maxRetry = 3

    private val purchasesUpdatedListener = PurchasesUpdatedListener { billingResult, purchases ->
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                purchases?.forEach { scope.launch { handlePurchase(it) } }
            }
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
                // 광고 제거를 이미 샀는데 재시도한 경우 → 복원 처리
                scope.launch { restorePurchases() }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                _uiEffect.value = BillingUiEffect.PurchaseCancelled
            }
            else -> {
                _uiEffect.value = BillingUiEffect.PurchaseFailed(billingResult.debugMessage)
            }
        }
    }

    private val billingClient = BillingClient.newBuilder(context)
        .setListener(purchasesUpdatedListener)
        .enablePendingPurchases()
        .build()

    init {
        connectAndRestore()
    }

    private fun connectAndRestore() {
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    retryCount = 0
                    scope.launch {
                        queryProductDetails()
                        restorePurchases()
                    }
                } else {
                    _productsLoadState.value = ProductsLoadState.Error
                }
            }
            override fun onBillingServiceDisconnected() {
                if (retryCount < maxRetry) {
                    retryCount++
                    scope.launch {
                        delay(2000L * retryCount)
                        connectAndRestore()
                    }
                } else {
                    _productsLoadState.value = ProductsLoadState.Error
                }
            }
        })
    }

    private suspend fun queryProductDetails() {
        _productsLoadState.value = ProductsLoadState.Loading
        val productList = listOf(
            BillingProducts.REMOVE_ADS,
            BillingProducts.SUPPORT_COFFEE,
            BillingProducts.SUPPORT_SNACK
        ).map {
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(it)
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        }

        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(productList)
            .build()

        val result = billingClient.queryProductDetails(params)
        if (result.billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
            val details = result.productDetailsList ?: emptyList()
            _productDetails.value = details
            _productsLoadState.value = if (details.isNotEmpty()) ProductsLoadState.Success else ProductsLoadState.Error
        } else {
            _productsLoadState.value = ProductsLoadState.Error
        }
    }

    suspend fun restorePurchases() {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()

        val result = billingClient.queryPurchasesAsync(params)
        if (result.billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
            val hasRemoveAds = result.purchasesList.any { purchase ->
                purchase.products.contains(BillingProducts.REMOVE_ADS) &&
                purchase.purchaseState == Purchase.PurchaseState.PURCHASED
            }
            _isAdsRemoved.value = hasRemoveAds
        }
    }

    fun launchBillingFlow(activity: Activity, productId: String) {
        val details = _productDetails.value.find { it.productId == productId }
        if (details == null) {
            _uiEffect.value = BillingUiEffect.PurchaseFailed("상품 정보를 불러오지 못했습니다. 잠시 후 다시 시도해주세요.")
            return
        }
        val productDetailsParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(details)
            .build()
        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productDetailsParams))
            .build()
        billingClient.launchBillingFlow(activity, flowParams)
    }

    private suspend fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return

        val productId = purchase.products.firstOrNull() ?: return

        if (BillingProducts.consumableIds.contains(productId)) {
            val consumeParams = ConsumeParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()
            val result = billingClient.consumePurchase(consumeParams)
            if (result.billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                _uiEffect.value = BillingUiEffect.PurchaseSuccess(productId)
            }
        } else {
            if (!purchase.isAcknowledged) {
                val ackParams = AcknowledgePurchaseParams.newBuilder()
                    .setPurchaseToken(purchase.purchaseToken)
                    .build()
                val result = billingClient.acknowledgePurchase(ackParams)
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    if (productId == BillingProducts.REMOVE_ADS) _isAdsRemoved.value = true
                    _uiEffect.value = BillingUiEffect.PurchaseSuccess(productId)
                }
            } else {
                if (productId == BillingProducts.REMOVE_ADS) _isAdsRemoved.value = true
            }
        }
    }

    fun clearUiEffect() {
        _uiEffect.value = null
    }
}
