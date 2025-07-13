package com.example.heictojpeg

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import com.android.billingclient.api.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class BillingManager(private val context: Context) : PurchasesUpdatedListener {
    private val billingClient = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases()
        .build()
    
    private val _hasDonated = MutableStateFlow(getStoredDonationStatus())
    val hasDonated: StateFlow<Boolean> = _hasDonated
    
    private val prefs: SharedPreferences? = try {
        context.getSharedPreferences("donations", Context.MODE_PRIVATE)
    } catch (e: Exception) {
        null
    }
    
    init {
        connectToBillingService()
    }
    
    private fun connectToBillingService() {
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    // Connected successfully
                }
            }
            
            override fun onBillingServiceDisconnected() {
                // Try to restart the connection on the next request
            }
        })
    }
    
    fun makeDonation(activity: Activity) {
        if (_hasDonated.value) return // Already donated
        
        // For now, simulate a successful donation
        // In production, you'd query product details first, then launch billing flow
        simulateDonation()
    }
    
    private fun simulateDonation() {
        // Simulate successful donation for testing
        storeDonationStatus(true)
        _hasDonated.value = true
    }
    
    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: MutableList<Purchase>?) {
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            for (purchase in purchases) {
                handlePurchase(purchase)
            }
        }
    }
    
    private fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
            // Acknowledge the purchase
            val acknowledgePurchaseParams = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()
            
            billingClient.acknowledgePurchase(acknowledgePurchaseParams) { billingResult ->
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    // Purchase acknowledged - mark as donated
                    storeDonationStatus(true)
                    _hasDonated.value = true
                }
            }
        }
    }
    
    private fun getStoredDonationStatus(): Boolean {
        return try {
            prefs?.getBoolean("has_donated", false) ?: false
        } catch (e: Exception) {
            false
        }
    }
    
    private fun storeDonationStatus(hasDonated: Boolean) {
        try {
            prefs?.edit()?.putBoolean("has_donated", hasDonated)?.apply()
        } catch (e: Exception) {
            // Ignore storage errors for now
        }
    }
    
    fun endConnection() {
        billingClient.endConnection()
    }
}
