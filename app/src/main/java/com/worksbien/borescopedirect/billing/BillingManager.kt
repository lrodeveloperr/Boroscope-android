package com.worksbien.borescopedirect.billing

import android.app.Activity
import android.content.Context
import androidx.annotation.StringRes
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.worksbien.borescopedirect.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class BillingUiState(
    val connected: Boolean = false,
    val unlocked: Boolean = false,
    val price: String? = null,
    val loading: Boolean = true,
    val purchaseInProgress: Boolean = false,
    val restoreInProgress: Boolean = false,
    val pendingPurchase: Boolean = false,
    val message: String? = null,
    val messageIsError: Boolean = false,
) {
    val canPurchase: Boolean get() =
        connected && price != null && !loading && !purchaseInProgress && !restoreInProgress && !unlocked
}

class BillingManager(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences("entitlement", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(
        BillingUiState(unlocked = preferences.getBoolean(KEY_UNLOCKED, false)),
    )
    val state: StateFlow<BillingUiState> = _state.asStateFlow()

    private var productDetails: ProductDetails? = null
    private var connecting = false
    private var restoreWhenConnected = false
    private var productQueryGeneration = 0L
    private var purchaseQueryGeneration = 0L
    private val purchaseListener = com.android.billingclient.api.PurchasesUpdatedListener { result, purchases ->
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> processPurchaseUpdate(purchases.orEmpty())
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                _state.value = _state.value.copy(purchaseInProgress = false, message = null, messageIsError = false)
            }
            else -> _state.value = _state.value.copy(
                purchaseInProgress = false,
                message = friendlyBillingMessage(result),
                messageIsError = true,
            )
        }
    }
    private val billingClient = BillingClient.newBuilder(appContext)
        .setListener(purchaseListener)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder().enableOneTimeProducts().build(),
        )
        .enableAutoServiceReconnection()
        .build()

    fun start() {
        if (billingClient.isReady) {
            refresh()
            return
        }
        if (connecting) return
        connecting = true
        _state.value = _state.value.copy(loading = true, message = null, messageIsError = false)
        runCatching {
            billingClient.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(result: BillingResult) {
                    connecting = false
                    if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                        _state.value = _state.value.copy(connected = true)
                        if (restoreWhenConnected) {
                            restoreWhenConnected = false
                            queryProduct()
                            queryOwnedPurchases(isRestore = true)
                        } else {
                            refresh()
                        }
                    } else {
                        _state.value = _state.value.copy(
                            connected = false,
                            loading = false,
                            restoreInProgress = false,
                            message = text(R.string.billing_play_unavailable_free),
                            messageIsError = true,
                        )
                    }
                }

                override fun onBillingServiceDisconnected() {
                    connecting = false
                    _state.value = _state.value.copy(
                        connected = false,
                        restoreInProgress = false,
                        purchaseInProgress = false,
                        message = if (_state.value.restoreInProgress || _state.value.purchaseInProgress) {
                            text(R.string.billing_disconnected)
                        } else {
                            _state.value.message
                        },
                        messageIsError = _state.value.restoreInProgress || _state.value.purchaseInProgress,
                    )
                }
            })
        }.onFailure {
            connecting = false
            _state.value = _state.value.copy(
                connected = false,
                loading = false,
                restoreInProgress = false,
                message = text(R.string.billing_start_failed_free),
                messageIsError = true,
            )
        }
    }

    fun refresh() {
        if (!billingClient.isReady) {
            start()
            return
        }
        queryOwnedPurchases()
        queryProduct()
    }

    private fun queryProduct() {
        val generation = ++productQueryGeneration
        val product = QueryProductDetailsParams.Product.newBuilder()
            .setProductId(PRODUCT_ID)
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        val params = QueryProductDetailsParams.newBuilder().setProductList(listOf(product)).build()
        runCatching {
            billingClient.queryProductDetailsAsync(params) { result, detailsResult ->
                if (generation != productQueryGeneration) return@queryProductDetailsAsync
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    productDetails = detailsResult.productDetailsList.firstOrNull()
                    val offer = productDetails?.oneTimePurchaseOfferDetailsList?.firstOrNull()
                    val currentMessage = _state.value.message
                    _state.value = _state.value.copy(
                        connected = true,
                        loading = false,
                        price = offer?.formattedPrice,
                        message = when {
                            offer == null -> text(R.string.billing_unlock_temp_unavailable)
                            shouldPreserveTransactionStatus(currentMessage) -> currentMessage
                            else -> null
                        },
                        messageIsError = offer == null,
                    )
                } else {
                    _state.value = _state.value.copy(loading = false, message = friendlyBillingMessage(result), messageIsError = true)
                }
            }
        }.onFailure {
            if (generation != productQueryGeneration) return@onFailure
            _state.value = _state.value.copy(loading = false, message = text(R.string.billing_product_lookup_failed), messageIsError = true)
        }
    }

    private fun queryOwnedPurchases(isRestore: Boolean = false) {
        val generation = ++purchaseQueryGeneration
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        runCatching {
            billingClient.queryPurchasesAsync(params) { result, purchases ->
                if (generation != purchaseQueryGeneration) return@queryPurchasesAsync
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    val purchased = purchases.filter {
                        PRODUCT_ID in it.products && it.purchaseState == Purchase.PurchaseState.PURCHASED
                    }
                    val pending = purchases.any {
                        PRODUCT_ID in it.products && it.purchaseState == Purchase.PurchaseState.PENDING
                    }
                    if (purchased.isEmpty()) revokeCachedUnlock() else processPurchases(purchased)
                    _state.value = _state.value.copy(
                        restoreInProgress = false,
                        pendingPurchase = pending,
                        message = when {
                            purchased.isNotEmpty() && isRestore -> text(R.string.billing_purchase_restored)
                            pending -> text(R.string.billing_purchase_pending)
                            isRestore -> text(R.string.billing_no_purchase)
                            else -> _state.value.message
                        },
                        messageIsError = false,
                    )
                } else {
                    _state.value = _state.value.copy(
                        restoreInProgress = false,
                        message = if (isRestore) friendlyBillingMessage(result) else _state.value.message,
                        messageIsError = isRestore,
                    )
                }
            }
        }.onFailure {
            if (generation != purchaseQueryGeneration) return@onFailure
            _state.value = _state.value.copy(
                restoreInProgress = false,
                message = if (isRestore) text(R.string.billing_restore_failed) else _state.value.message,
                messageIsError = isRestore,
            )
        }
    }

    fun restorePurchases() {
        if (!billingClient.isReady) {
            restoreWhenConnected = true
            _state.value = _state.value.copy(
                restoreInProgress = true,
                message = text(R.string.billing_connecting),
                messageIsError = false,
            )
            start()
            return
        }
        _state.value = _state.value.copy(restoreInProgress = true, message = text(R.string.billing_checking_purchases), messageIsError = false)
        queryOwnedPurchases(isRestore = true)
    }

    fun launchPurchase(activity: Activity) {
        if (_state.value.unlocked || _state.value.purchaseInProgress || _state.value.restoreInProgress) return
        val details = productDetails
        val offer = details?.oneTimePurchaseOfferDetailsList?.firstOrNull()
        val offerToken = offer?.offerToken
        if (details == null || offerToken.isNullOrBlank()) {
            _state.value = _state.value.copy(message = text(R.string.billing_details_not_ready), messageIsError = true)
            refresh()
            return
        }
        val detailParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(details)
            .setOfferToken(offerToken)
            .build()
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(detailParams))
            .build()
        val result = runCatching { billingClient.launchBillingFlow(activity, params) }.getOrElse {
            _state.value = _state.value.copy(
                purchaseInProgress = false,
                message = text(R.string.billing_open_purchase_failed),
                messageIsError = true,
            )
            return
        }
        _state.value = if (result.responseCode == BillingClient.BillingResponseCode.OK) {
            _state.value.copy(purchaseInProgress = true, message = null, messageIsError = false)
        } else {
            _state.value.copy(purchaseInProgress = false, message = friendlyBillingMessage(result), messageIsError = true)
        }
    }

    private fun processPurchaseUpdate(purchases: List<Purchase>) {
        val purchased = purchases.filter {
            PRODUCT_ID in it.products && it.purchaseState == Purchase.PurchaseState.PURCHASED
        }
        val pending = purchases.any {
            PRODUCT_ID in it.products && it.purchaseState == Purchase.PurchaseState.PENDING
        }
        processPurchases(purchased)
        _state.value = _state.value.copy(
            purchaseInProgress = false,
            pendingPurchase = pending,
            message = if (pending) {
                text(R.string.billing_purchase_pending)
            } else {
                _state.value.message
            },
            messageIsError = false,
        )
    }

    private fun processPurchases(purchases: List<Purchase>) {
        purchases.filter {
            PRODUCT_ID in it.products && it.purchaseState == Purchase.PurchaseState.PURCHASED
        }.forEach { purchase ->
            grantUnlock()
            if (!purchase.isAcknowledged) {
                val params = AcknowledgePurchaseParams.newBuilder()
                    .setPurchaseToken(purchase.purchaseToken)
                    .build()
                runCatching {
                    billingClient.acknowledgePurchase(params) { result ->
                        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                            _state.value = _state.value.copy(message = text(R.string.billing_confirmation_retry), messageIsError = false)
                        }
                    }
                }.onFailure {
                    _state.value = _state.value.copy(message = text(R.string.billing_confirmation_retry), messageIsError = false)
                }
            }
        }
    }

    fun debugUnlock() = grantUnlock()

    private fun grantUnlock() {
        preferences.edit().putBoolean(KEY_UNLOCKED, true).apply()
        _state.value = _state.value.copy(unlocked = true, loading = false, message = null, messageIsError = false)
    }

    private fun revokeCachedUnlock() {
        preferences.edit().putBoolean(KEY_UNLOCKED, false).apply()
        _state.value = _state.value.copy(unlocked = false, purchaseInProgress = false)
    }

    private fun friendlyBillingMessage(result: BillingResult): String = when (result.responseCode) {
        BillingClient.BillingResponseCode.SERVICE_DISCONNECTED,
        BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE,
        BillingClient.BillingResponseCode.NETWORK_ERROR -> text(R.string.billing_unavailable_connection)
        BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> text(R.string.billing_already_purchased)
        BillingClient.BillingResponseCode.BILLING_UNAVAILABLE -> text(R.string.billing_unavailable_account)
        else -> text(R.string.billing_request_failed)
    }

    private fun shouldPreserveTransactionStatus(message: String?): Boolean = message?.let {
        listOf(
            R.string.billing_purchase_restored,
            R.string.billing_purchase_pending,
            R.string.billing_no_purchase,
            R.string.billing_checking_purchases,
            R.string.billing_connecting,
            R.string.billing_confirmation_retry,
        ).any { id -> it.startsWith(text(id)) }
    } == true

    private fun text(@StringRes id: Int, vararg args: Any): String = appContext.getString(id, *args)

    fun close() {
        connecting = false
        productQueryGeneration++
        purchaseQueryGeneration++
        runCatching { billingClient.endConnection() }
    }

    companion object {
        const val PRODUCT_ID = "borescope_direct_unlock"
        private const val KEY_UNLOCKED = "unlocked"
    }
}
