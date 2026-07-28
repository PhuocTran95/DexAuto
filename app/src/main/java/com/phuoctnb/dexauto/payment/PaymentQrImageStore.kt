package com.phuoctnb.dexauto.payment

import android.content.Context
import android.graphics.BitmapFactory
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

class PaymentQrImageStore(context: Context) {
    private val imageDirectory = File(context.applicationContext.filesDir, DIRECTORY_NAME)

    fun cachedImage(config: PaymentQrConfig): File? {
        if (VietQr.imageUrl(config.bankCode, config.accountNumber) == null) return null
        val filePrefix = "${configKey(config)}_"
        return imageDirectory.listFiles()
            ?.filter { it.isFile && it.name.startsWith(filePrefix) }
            ?.maxByOrNull(File::lastModified)
            ?.takeIf { it.length() > 0L }
    }

    fun update(config: PaymentQrConfig): Result<File> = runCatching {
        val imageUrl = VietQr.imageUrl(config.bankCode, config.accountNumber)
            ?: throw IllegalArgumentException("Bank and account number are required")
        imageDirectory.mkdirs()
        val temporaryFile = File(imageDirectory, TEMPORARY_FILE_NAME)
        try {
            download(imageUrl, temporaryFile)
            validateImage(temporaryFile)
            val targetFile = File(
                imageDirectory,
                "${configKey(config)}_${System.currentTimeMillis()}.image"
            )
            if (!temporaryFile.renameTo(targetFile)) {
                temporaryFile.copyTo(targetFile, overwrite = true)
                temporaryFile.delete()
            }
            targetFile
        } catch (error: Throwable) {
            temporaryFile.delete()
            throw error
        }
    }

    fun retain(imageFile: File) {
        imageDirectory.listFiles()
            ?.filter { it.isFile && it != imageFile }
            ?.forEach(File::delete)
    }

    private fun download(imageUrl: String, target: File) {
        val connection = URL(imageUrl).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = NETWORK_TIMEOUT_MS
            connection.readTimeout = NETWORK_TIMEOUT_MS
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("User-Agent", "DexAuto")
            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                throw IOException("VietQR returned HTTP $responseCode")
            }
            connection.inputStream.use { input ->
                target.outputStream().use(input::copyTo)
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun validateImage(file: File) {
        if (file.length() == 0L) throw IOException("VietQR returned an empty image")
        val bitmap = BitmapFactory.decodeFile(file.absolutePath)
            ?: throw IOException("VietQR returned invalid image data")
        bitmap.recycle()
    }

    private fun configKey(config: PaymentQrConfig): String {
        val raw = "${config.bankCode.trim()}:${config.accountNumber.trim()}"
        return MessageDigest.getInstance("SHA-256")
            .digest(raw.toByteArray())
            .take(12)
            .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xFF) }
    }

    private companion object {
        const val DIRECTORY_NAME = "payment_qr"
        const val TEMPORARY_FILE_NAME = "payment_qr.tmp"
        const val NETWORK_TIMEOUT_MS = 20_000
    }
}
