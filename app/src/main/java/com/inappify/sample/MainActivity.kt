package com.inappify.sample

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.TextViewCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.textfield.TextInputEditText
import com.inappify.sdk.InappifyClient
import com.inappify.sdk.InappifyCustomerInfo
import com.inappify.sdk.InappifyError
import com.inappify.sdk.InappifyListenerRegistration
import com.inappify.sdk.InappifyMarket
import com.inappify.sdk.InappifyOffering
import com.inappify.sdk.InappifyOfferings
import com.inappify.sdk.InappifyOptions
import com.inappify.sdk.InappifyPackage
import com.inappify.sdk.InappifyPurchaseRequest
import com.inappify.sdk.InappifyResult
import com.inappify.sdk.InappifySnapshot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.ArrayDeque
import java.util.UUID

/**
 * Minimal native host used to exercise the SDK's public API against a real
 * Inappify application and Cafe Bazaar account.
 *
 * This Activity deliberately renders only non-secret diagnostic fields. API
 * keys, Bazaar keys, customer identifiers, receipts, and purchase tokens are
 * never copied into the operation log.
 */
class MainActivity : AppCompatActivity() {

    private val client: InappifyClient
        get() = (application as SampleApplication).inappifyClient

    private val activityScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val operationLog = ArrayDeque<String>()
    private val purchaseButtons = mutableListOf<MaterialButton>()

    private var eventRegistration: InappifyListenerRegistration? = null
    private var operationInProgress = false

    private lateinit var apiKeyInput: TextInputEditText
    private lateinit var bazaarKeyInput: TextInputEditText
    private lateinit var appUserIdInput: TextInputEditText
    private lateinit var configureButton: MaterialButton
    private lateinit var customerInfoButton: MaterialButton
    private lateinit var offeringsButton: MaterialButton
    private lateinit var syncPurchasesButton: MaterialButton
    private lateinit var progressIndicator: LinearProgressIndicator
    private lateinit var sessionText: TextView
    private lateinit var customerText: TextView
    private lateinit var offeringsContainer: LinearLayout
    private lateinit var eventsText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bindViews()
        restoreLocalDefaults()
        bindActions()

        eventRegistration = client.addEventListener { event ->
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                renderSnapshot(event.snapshot)
                appendLog("رویداد SDK: ${event.type}")
            }
        }

        renderSnapshot(client.snapshot)
        setBusy(false)
    }

    override fun onDestroy() {
        eventRegistration?.close()
        eventRegistration = null
        activityScope.cancel()

        super.onDestroy()
    }

    private fun bindViews() {
        apiKeyInput = findViewById(R.id.api_key_input)
        bazaarKeyInput = findViewById(R.id.bazaar_key_input)
        appUserIdInput = findViewById(R.id.app_user_id_input)
        configureButton = findViewById(R.id.configure_button)
        customerInfoButton = findViewById(R.id.customer_info_button)
        offeringsButton = findViewById(R.id.offerings_button)
        syncPurchasesButton = findViewById(R.id.sync_purchases_button)
        progressIndicator = findViewById(R.id.progress_indicator)
        sessionText = findViewById(R.id.session_text)
        customerText = findViewById(R.id.customer_text)
        offeringsContainer = findViewById(R.id.offerings_container)
        eventsText = findViewById(R.id.events_text)
    }

    private fun restoreLocalDefaults() {
        apiKeyInput.setText(BuildConfig.INAPPIFY_API_KEY)
        bazaarKeyInput.setText(BuildConfig.BAZAAR_PUBLIC_KEY)
    }

    private fun bindActions() {
        configureButton.setOnClickListener { configureSdk() }

        customerInfoButton.setOnClickListener {
            executeOperation(
                label = "دریافت اطلاعات مشتری",
                operation = client::refreshCustomerInfo,
            ) { customerInfo ->
                renderCustomerInfo(customerInfo)
            }
        }

        offeringsButton.setOnClickListener {
            executeOperation(
                label = "دریافت Offeringها",
                operation = client::refreshOfferings,
            ) { offerings ->
                renderOfferings(offerings)
            }
        }

        syncPurchasesButton.setOnClickListener {
            executeOperation(
                label = "بازیابی خریدهای بازار",
                operation = client::syncPurchases,
            ) { recoveredPurchases ->
                appendLog("تعداد خریدهای بازیابی‌شده: ${recoveredPurchases.size}")
            }
        }
    }

    private fun configureSdk() {
        val apiKey = apiKeyInput.text?.toString()?.trim().orEmpty()
        val bazaarPublicKey = bazaarKeyInput.text?.toString()?.trim().orEmpty()
        val appUserIdentifier = appUserIdInput.text
            ?.toString()
            ?.trim()
            ?.takeIf(String::isNotEmpty)

        apiKeyInput.error = if (apiKey.isEmpty()) getString(R.string.api_key_hint) else null
        bazaarKeyInput.error = if (bazaarPublicKey.isEmpty()) {
            getString(R.string.bazaar_public_key_hint)
        } else {
            null
        }

        if (apiKey.isEmpty() || bazaarPublicKey.isEmpty()) {
            appendLog(getString(R.string.missing_configuration))
            return
        }

        executeOperation(
            label = "Configure",
            operation = {
                client.configure(
                    InappifyOptions(
                        apiKey = apiKey,
                        appUserIdentifier = appUserIdentifier,
                        market = InappifyMarket.BAZAAR,
                        marketKey = bazaarPublicKey,
                        country = BuildConfig.INAPPIFY_COUNTRY,
                        appVersion = BuildConfig.VERSION_NAME,
                    ),
                )
            },
        )
    }

    private fun purchase(
        offering: InappifyOffering,
        inappifyPackage: InappifyPackage,
    ) {
        val offeringIdentifier = offering.identifier?.trim().orEmpty()
        val productIdentifier = inappifyPackage.product?.identifier?.trim().orEmpty()
        val bazaarPublicKey = bazaarKeyInput.text?.toString()?.trim().orEmpty()

        if (
            offeringIdentifier.isEmpty() ||
            productIdentifier.isEmpty() ||
            bazaarPublicKey.isEmpty()
        ) {
            appendLog("خرید اجرا نشد: شناسه Offering، محصول یا کلید بازار معتبر نیست.")
            return
        }

        executeOperation(
            label = "خرید از بازار",
            operation = {
                client.purchase(
                    activity = this@MainActivity,
                    request = InappifyPurchaseRequest(
                        productIdentifier = productIdentifier,
                        offeringIdentifier = offeringIdentifier,
                        packageIdentifier = inappifyPackage.identifier,
                        idempotencyKey = "sample-${UUID.randomUUID()}",
                        discount = inappifyPackage.discountPercent ?: 0L,
                        isCrypto = false,
                        market = InappifyMarket.BAZAAR,
                        marketKey = bazaarPublicKey,
                    ),
                )
            },
        ) { purchase ->
            appendLog("وضعیت نهایی خرید: ${purchase.purchaseStatus}")
        }
    }

    private fun <T> executeOperation(
        label: String,
        operation: suspend () -> InappifyResult<T>,
        onSuccess: (T) -> Unit = {},
    ) {
        if (operationInProgress) {
            appendLog(getString(R.string.operation_in_progress))
            return
        }

        activityScope.launch {
            setBusy(true)
            appendLog("$label: شروع")

            try {
                when (val result = operation()) {
                    is InappifyResult.Success -> {
                        renderSnapshot(result.snapshot)
                        onSuccess(result.data)
                        appendLog("$label: موفق")
                    }

                    is InappifyResult.Failure -> {
                        result.snapshot?.let(::renderSnapshot)
                        renderFailure(label, result.error)
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                // Public SDK failures are typed; this branch surfaces host/programming errors.
                appendLog("$label: خطای غیرمنتظره (${error.javaClass.simpleName})")
            } finally {
                if (!isDestroyed) {
                    setBusy(false)
                }
            }
        }
    }

    private fun renderFailure(label: String, error: InappifyError) {
        val retryHint = if (error.isRetryable) "قابل تلاش مجدد" else "غیرقابل تلاش مجدد"
        appendLog("$label: ناموفق (${error.code}, $retryHint) — ${error.message}")
    }

    private fun renderSnapshot(snapshot: InappifySnapshot) {
        sessionText.text = buildString {
            appendLine("Configure شده: ${snapshot.isConfigured.toPersianState()}")
            appendLine("احراز هویت شده: ${snapshot.isAuthenticated.toPersianState()}")
            appendLine("مارکت: ${snapshot.market ?: "-"}")
            appendLine("کشور: ${snapshot.country ?: "-"}")
            appendLine("نسخه اپ: ${snapshot.appVersion ?: "-"}")
            appendLine("نسخه SDK: ${snapshot.sdkVersion}")
            appendLine("Revision: ${snapshot.revision}")
            appendLine("خطای Customer Info: ${snapshot.failedToLoadCustomerInfo.toPersianState()}")
            append("خطای Offerings: ${snapshot.failedToLoadOfferings.toPersianState()}")
        }

        renderCustomerInfo(snapshot.customerInfo)
        renderOfferings(snapshot.offerings)
    }

    private fun renderCustomerInfo(customerInfo: InappifyCustomerInfo?) {
        if (customerInfo == null) {
            customerText.setText(R.string.no_customer_info)
            return
        }

        customerText.text = buildString {
            appendLine("شناسه کاربر: ${if (customerInfo.originalAppUserId == null) "ثبت نشده" else "دریافت شده (پنهان)"}")
            appendLine("اولین مشاهده: ${customerInfo.firstSeen ?: "-"}")
            appendLine("تاریخ پاسخ: ${customerInfo.requestDate ?: "-"}")
            appendLine("آخرین انقضا: ${customerInfo.latestExpirationDate ?: "-"}")
            appendLine("استفاده از Trial: ${customerInfo.hasUsedTrial.toPersianState()}")

            val entitlements = customerInfo.entitlements.orEmpty()
            appendLine("Entitlementها: ${entitlements.size}")
            entitlements.forEach { entitlement ->
                append("  • ${entitlement.identifier ?: "بدون شناسه"}")
                append(" | فعال: ${entitlement.isActive.toPersianState()}")
                append(" | دوره: ${entitlement.periodType ?: "-"}")
                append(" | انقضا: ${entitlement.expirationDate ?: "-"}")
                appendLine()
            }

            val transactions = customerInfo.transactions.orEmpty()
            appendLine("تراکنش‌ها: ${transactions.size}")
            transactions.take(MAX_VISIBLE_TRANSACTIONS).forEach { transaction ->
                append("  • وضعیت: ${transaction.status ?: "-"}")
                append(" | پکیج: ${transaction.packageName ?: "-"}")
                append(" | Trial: ${transaction.isTrial.toPersianState()}")
                appendLine()
            }
            if (transactions.size > MAX_VISIBLE_TRANSACTIONS) {
                append("  … ${transactions.size - MAX_VISIBLE_TRANSACTIONS} مورد دیگر")
            }
        }.trimEnd()
    }

    private fun renderOfferings(offerings: InappifyOfferings?) {
        offeringsContainer.removeAllViews()
        purchaseButtons.clear()

        val availableOfferings = offerings?.offerings.orEmpty()
        if (availableOfferings.isEmpty()) {
            offeringsContainer.addView(bodyText(getString(R.string.no_offerings)))
            return
        }

        availableOfferings.forEach { offering ->
            offeringsContainer.addView(createOfferingCard(offering))
        }
    }

    private fun createOfferingCard(offering: InappifyOffering): MaterialCardView {
        val card = MaterialCardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = dp(12)
            }
            radius = dp(12).toFloat()
            cardElevation = dp(1).toFloat()
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }

        val title = buildString {
            append(offering.identifier ?: "Offering بدون شناسه")
            if (offering.isDefault == true) {
                append(" — ${getString(R.string.default_offering)}")
            }
        }
        content.addView(titleText(title))

        offering.serverDescription
            ?.takeIf(String::isNotBlank)
            ?.let { description ->
                content.addView(bodyText(description, topMargin = 6))
            }

        val packages = offering.packages.orEmpty()
        if (packages.isEmpty()) {
            content.addView(bodyText("پکیجی در این Offering وجود ندارد.", topMargin = 10))
        } else {
            packages.forEach { inappifyPackage ->
                content.addView(createPackageView(offering, inappifyPackage))
            }
        }

        card.addView(content)
        return card
    }

    private fun createPackageView(
        offering: InappifyOffering,
        inappifyPackage: InappifyPackage,
    ): View {
        val product = inappifyPackage.product
        // Keep the label aligned with the SDK, which treats product.trialDays as authoritative.
        val trialDays = product?.trialDays ?: 0L
        val details = buildString {
            appendLine(inappifyPackage.name ?: product?.name ?: "پکیج بدون نام")
            appendLine("Package ID: ${inappifyPackage.identifier ?: "-"}")
            appendLine("Product ID: ${product?.identifier ?: "-"}")
            appendLine("قیمت: ${product.priceLabel()}")
            append("تخفیف: ${inappifyPackage.discountPercent ?: 0L}%")
            if (trialDays > 0L) {
                append(" | Trial: $trialDays روز")
            }
            inappifyPackage.description
                ?.takeIf(String::isNotBlank)
                ?.let { description -> append("\n$description") }
        }

        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = dp(14)
            }

            addView(bodyText(details))

            val purchaseButton = MaterialButton(this@MainActivity).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply {
                    topMargin = dp(8)
                }
                text = getString(
                    if (trialDays > 0L) {
                        R.string.trial_purchase_action
                    } else {
                        R.string.purchase_action
                    },
                )
                tag = hasPurchasableIdentifiers(offering, inappifyPackage)
                isEnabled = canPurchase(offering, inappifyPackage)
                setOnClickListener { purchase(offering, inappifyPackage) }
            }

            purchaseButtons += purchaseButton
            addView(purchaseButton)
        }
    }

    private fun canPurchase(
        offering: InappifyOffering,
        inappifyPackage: InappifyPackage,
    ): Boolean =
        !operationInProgress &&
            client.snapshot.isConfigured &&
            hasPurchasableIdentifiers(offering, inappifyPackage)

    private fun hasPurchasableIdentifiers(
        offering: InappifyOffering,
        inappifyPackage: InappifyPackage,
    ): Boolean =
        !offering.identifier.isNullOrBlank() &&
            !inappifyPackage.product?.identifier.isNullOrBlank()

    private fun titleText(value: String): TextView = TextView(this).apply {
        text = value
        TextViewCompat.setTextAppearance(
            this,
            com.google.android.material.R.style.TextAppearance_MaterialComponents_Subtitle1,
        )
    }

    private fun bodyText(value: String, topMargin: Int = 0): TextView = TextView(this).apply {
        text = value
        setTextIsSelectable(true)
        TextViewCompat.setTextAppearance(
            this,
            com.google.android.material.R.style.TextAppearance_MaterialComponents_Body2,
        )
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply {
            this.topMargin = dp(topMargin)
        }
    }

    private fun setBusy(isBusy: Boolean) {
        operationInProgress = isBusy
        progressIndicator.visibility = if (isBusy) View.VISIBLE else View.GONE

        configureButton.isEnabled = !isBusy
        val canUseConfiguredSession = !isBusy && client.snapshot.isConfigured
        customerInfoButton.isEnabled = canUseConfiguredSession
        offeringsButton.isEnabled = canUseConfiguredSession
        syncPurchasesButton.isEnabled = canUseConfiguredSession
        purchaseButtons.forEach { button ->
            button.isEnabled = canUseConfiguredSession && button.tag == true
        }
    }

    private fun appendLog(message: String) {
        if (operationLog.size == MAX_LOG_ENTRIES) {
            operationLog.removeLast()
        }
        operationLog.addFirst(message)
        eventsText.text = operationLog.joinToString(separator = "\n")
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun Boolean?.toPersianState(): String = when (this) {
        true -> "بله"
        false -> "خیر"
        null -> "نامشخص"
    }

    private fun com.inappify.sdk.InappifyStoreProduct?.priceLabel(): String {
        if (this == null) return "-"
        val price = prices.orEmpty().firstOrNull()
        val amount = price?.amount ?: price?.price ?: dollarPrice ?: return "-"
        return listOfNotNull(
            amount.toString(),
            price?.currency?.takeIf(String::isNotBlank),
        ).joinToString(separator = " ")
    }

    private companion object {
        const val MAX_VISIBLE_TRANSACTIONS = 10
        const val MAX_LOG_ENTRIES = 40
    }
}
