package com.guima.esa.data

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.guima.esa.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BillingUiState(
    val isReady: Boolean = false,
    val isPremium: Boolean = false,
    val priceLabel: String = "R$ 5,00"
)

object BillingRepository {
    private const val TAG = "BillingRepository"
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _uiState = MutableStateFlow(BillingUiState(isPremium = UserRepository.isPremium()))
    val uiState: StateFlow<BillingUiState> = _uiState.asStateFlow()

    private var appContext: Context? = null
    private var billingClient: BillingClient? = null
    private var premiumProductDetails: ProductDetails? = null
    private var premiumOfferToken: String? = null
    private var premiumProductId: String = "esa_premium"
    private var fallbackPriceLabel: String = "R$ 5,00"

    private val purchasesUpdatedListener = PurchasesUpdatedListener { billingResult, purchases ->
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            handlePurchases(purchases)
        } else if (billingResult.responseCode == BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED) {
            restorePurchasesForCurrentAccount()
            showBillingMessage("Você já possui o Premium neste Google Play. Toque em Restaurar compra.")
        } else if (billingResult.responseCode != BillingClient.BillingResponseCode.USER_CANCELED) {
            showBillingMessage(resolveBillingMessage(billingResult))
        }
    }

    fun initialize(context: Context) {
        if (billingClient != null) {
            refreshPurchases()
            return
        }

        appContext = context.applicationContext
        premiumProductId = context.getString(R.string.premium_product_id)
        fallbackPriceLabel = context.getString(R.string.premium_fallback_price)
        _uiState.update { it.copy(priceLabel = fallbackPriceLabel, isPremium = UserRepository.isPremium()) }

        billingClient = BillingClient.newBuilder(appContext!!)
            .setListener(purchasesUpdatedListener)
            .enablePendingPurchases(
                PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()
            )
            .build()

        startConnection()
    }

    fun launchPremiumPurchase(activity: Activity) {
        val client = billingClient ?: return
        val currentAccountId = UserRepository.getAuthenticatedCloudUserId()
        if (currentAccountId.isNullOrBlank()) {
            showBillingMessage("Entre com sua conta Google antes de comprar o Premium.")
            return
        }
        val productDetails = premiumProductDetails ?: run {
            queryProductDetails()
            showBillingMessage("O produto premium ainda está carregando. Tente novamente em alguns segundos.")
            return
        }

        val productDetailsParamsBuilder = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(productDetails)

        premiumOfferToken
            ?.takeIf { it.isNotBlank() }
            ?.let { productDetailsParamsBuilder.setOfferToken(it) }

        val productDetailsParams = productDetailsParamsBuilder.build()

        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productDetailsParams))
            .setObfuscatedAccountId(currentAccountId)
            .build()

        val billingResult = client.launchBillingFlow(activity, flowParams)
        if (billingResult.responseCode == BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED) {
            restorePurchasesForCurrentAccount()
            showBillingMessage("Você já possui o Premium neste Google Play. Toque em Restaurar compra.")
        } else if (billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
            showBillingMessage(resolveBillingMessage(billingResult))
        }
    }

    fun refreshPurchases() {
        val client = billingClient ?: return
        if (!client.isReady) {
            startConnection()
            return
        }

        client.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        ) { billingResult, purchases ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                handlePurchases(purchases)
            }
        }
    }

    fun restorePurchasesForCurrentAccount() {
        refreshPurchases()
    }

    fun onGoogleAccountChanged() {
        val isPremiumForCurrentAccount = UserRepository.isGoogleSignedIn() && UserRepository.isPremium()
        _uiState.update { it.copy(isPremium = isPremiumForCurrentAccount) }
        if (UserRepository.isGoogleSignedIn()) {
            refreshPurchases()
        }
    }

    private fun startConnection() {
        val client = billingClient ?: return
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    _uiState.update { it.copy(isReady = true) }
                    queryProductDetails()
                    refreshPurchases()
                }
            }

            override fun onBillingServiceDisconnected() {
                _uiState.update { it.copy(isReady = false) }
            }
        })
    }

    private fun queryProductDetails() {
        val client = billingClient ?: return
        val product = QueryProductDetailsParams.Product.newBuilder()
            .setProductId(premiumProductId)
            .setProductType(BillingClient.ProductType.INAPP)
            .build()

        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(listOf(product))
            .build()

        client.queryProductDetailsAsync(params) { billingResult, productDetailsResult ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                premiumProductDetails = productDetailsResult.productDetailsList.firstOrNull()
                val selectedOffer = premiumProductDetails
                    ?.oneTimePurchaseOfferDetailsList
                    ?.firstOrNull()

                premiumOfferToken = selectedOffer?.offerToken

                val dynamicPrice = selectedOffer
                    ?.formattedPrice
                    ?: premiumProductDetails
                        ?.oneTimePurchaseOfferDetails
                        ?.formattedPrice
                    ?: fallbackPriceLabel
                _uiState.update {
                    it.copy(
                        priceLabel = dynamicPrice
                    )
                }
            }
        }
    }

    private fun handlePurchases(purchases: List<Purchase>) {
        val currentAccountId = UserRepository.getAuthenticatedCloudUserId()
        if (currentAccountId.isNullOrBlank()) {
            UserRepository.clearPremiumStatus()
            _uiState.update { it.copy(isPremium = false) }
            return
        }

        val premiumPurchase = purchases.firstOrNull { purchase ->
            purchase.products.contains(premiumProductId) &&
                purchase.purchaseState == Purchase.PurchaseState.PURCHASED &&
                purchaseBelongsToCurrentAccount(
                    purchase = purchase,
                    currentAccountId = currentAccountId
                )
        }

        if (premiumPurchase == null) {
            UserRepository.clearPremiumStatus()
            _uiState.update { it.copy(isPremium = false) }
            repositoryScope.launch {
                CloudSyncRepository.safeSyncCurrentUser()
            }
            return
        }

        if (!premiumPurchase.isAcknowledged) {
            acknowledgePurchase(premiumPurchase)
        }

        UserRepository.savePremiumStatus(
            isPremium = true,
            productId = premiumProductId,
            purchaseToken = premiumPurchase.purchaseToken,
            purchaseTime = premiumPurchase.purchaseTime
        )
        _uiState.update { it.copy(isPremium = true) }

        repositoryScope.launch {
            CloudSyncRepository.safeSyncCurrentUser()
        }
    }

    private fun purchaseBelongsToCurrentAccount(
        purchase: Purchase,
        currentAccountId: String
    ): Boolean {
        val obfuscatedAccountId = purchase.accountIdentifiers?.obfuscatedAccountId
        if (!obfuscatedAccountId.isNullOrBlank()) {
            return obfuscatedAccountId == currentAccountId
        }

        val savedPurchaseToken = UserRepository.getPremiumPurchaseToken()
        if (savedPurchaseToken.isNotBlank()) {
            return savedPurchaseToken == purchase.purchaseToken
        }

        return false
    }

    private fun acknowledgePurchase(purchase: Purchase) {
        val client = billingClient ?: return
        client.acknowledgePurchase(
            AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()
        ) { }
    }

    private fun resolveBillingMessage(billingResult: BillingResult): String {
        Log.w(TAG, "Billing code=${billingResult.responseCode} message=${billingResult.debugMessage}")
        return when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.BILLING_UNAVAILABLE ->
                "O faturamento do Google Play não está disponível neste aparelho/conta."
            BillingClient.BillingResponseCode.ITEM_UNAVAILABLE ->
                "O produto premium não está disponível para esta conta, país ou versão do app."
            BillingClient.BillingResponseCode.DEVELOPER_ERROR ->
                "A Google Play recusou a compra por configuração do produto ou da versão do app."
            BillingClient.BillingResponseCode.SERVICE_DISCONNECTED,
            BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE,
            BillingClient.BillingResponseCode.ERROR ->
                "Não foi possível conectar ao Google Play para concluir a compra."
            else ->
                "Não foi possível iniciar a compra agora. Código: ${billingResult.responseCode}."
        }
    }

    private fun showBillingMessage(message: String) {
        val context = appContext ?: return
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }
}

