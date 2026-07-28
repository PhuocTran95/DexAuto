package com.phuoctnb.dexauto.payment

data class PaymentQrConfig(
    val bankCode: String = "",
    val accountNumber: String = ""
)

object VietQr {
    fun imageUrl(bankCode: String, accountNumber: String): String? {
        val normalizedBankCode = bankCode.trim()
        val normalizedAccountNumber = accountNumber.trim()
        if (normalizedBankCode.isBlank() || normalizedAccountNumber.isBlank()) return null
        return "$IMAGE_BASE_URL/$normalizedBankCode-$normalizedAccountNumber-qr_only.png"
    }

    private const val IMAGE_BASE_URL = "https://img.vietqr.io/image"
}
