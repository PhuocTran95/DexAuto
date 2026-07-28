package com.phuoctnb.dexauto.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.SubcomposeAsyncImage
import com.phuoctnb.dexauto.R
import com.phuoctnb.dexauto.payment.BankOption
import com.phuoctnb.dexauto.payment.PaymentQrConfig
import com.phuoctnb.dexauto.payment.PaymentQrImageStore
import com.phuoctnb.dexauto.payment.VietQrBankCatalog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun PaymentQrPopup(
    initialConfig: PaymentQrConfig,
    onUpdate: (PaymentQrConfig) -> Unit,
    onInputFocusChanged: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val imageStore = remember(context) { PaymentQrImageStore(context) }
    val banks = remember { VietQrBankCatalog.banks }
    var selectedBank by remember(initialConfig.bankCode) {
        mutableStateOf(banks.firstOrNull { it.code == initialConfig.bankCode })
    }
    var accountNumber by remember(initialConfig.accountNumber) {
        mutableStateOf(initialConfig.accountNumber)
    }
    var bankMenuExpanded by remember { mutableStateOf(false) }
    var qrImage by remember(initialConfig) {
        mutableStateOf(
            imageStore.cachedImage(initialConfig)
                ?.takeIf {
                    initialConfig.bankCode.isNotBlank() &&
                        initialConfig.accountNumber.isNotBlank()
                }
        )
    }
    var updating by remember { mutableStateOf(false) }
    var updateFailed by remember { mutableStateOf(false) }

    Card(
        modifier = modifier.fillMaxSize(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF151A20)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize().clipToBounds()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.QrCode2,
                        contentDescription = null,
                        tint = Color(0xFF8CC7FF),
                        modifier = Modifier.size(28.dp)
                    )
                    Text(
                        text = stringResource(R.string.payment_qr_title),
                        color = Color.White,
                        fontSize = 16.sp
                    )
                }

                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = selectedBank?.label.orEmpty(),
                        onValueChange = {},
                        readOnly = true,
                        singleLine = true,
                        label = { Text(stringResource(R.string.payment_qr_bank)) },
                        trailingIcon = {
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = null
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clickable { bankMenuExpanded = !bankMenuExpanded }
                    )
                }

                OutlinedTextField(
                    value = accountNumber,
                    onValueChange = { value ->
                        accountNumber = value.filter(Char::isDigit).take(MAX_ACCOUNT_LENGTH)
                    },
                    singleLine = true,
                    label = { Text(stringResource(R.string.payment_qr_account_number)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { onInputFocusChanged(it.isFocused) }
                )

                Button(
                    onClick = {
                        val config = PaymentQrConfig(
                            bankCode = selectedBank?.code.orEmpty(),
                            accountNumber = accountNumber.trim()
                        )
                        coroutineScope.launch {
                            updating = true
                            updateFailed = false
                            val result = withContext(Dispatchers.IO) {
                                imageStore.update(config)
                            }
                            result.onSuccess { imageFile ->
                                qrImage = imageFile
                                onUpdate(config)
                                withContext(Dispatchers.IO) {
                                    runCatching { imageStore.retain(imageFile) }
                                }
                            }.onFailure {
                                updateFailed = true
                            }
                            updating = false
                        }
                    },
                    enabled = selectedBank != null && accountNumber.isNotBlank() && !updating,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (updating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(stringResource(R.string.payment_qr_update))
                    }
                }

                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    val imageFile = qrImage
                    if (imageFile == null) {
                        Text(
                            text = stringResource(
                                if (updateFailed) {
                                    R.string.payment_qr_update_error
                                } else {
                                    R.string.payment_qr_empty
                                }
                            ),
                            color = if (updateFailed) {
                                Color(0xFFFFB4AB)
                            } else {
                                Color.White.copy(alpha = 0.48f)
                            },
                            fontSize = 13.sp
                        )
                    } else {
                        SubcomposeAsyncImage(
                            model = imageFile,
                            contentDescription = stringResource(R.string.content_desc_payment_qr),
                            contentScale = ContentScale.Fit,
                            loading = {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator()
                                }
                            },
                            error = {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text(
                                        text = stringResource(R.string.payment_qr_load_error),
                                        color = Color(0xFFFFB4AB),
                                        fontSize = 13.sp
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                        if (updateFailed) {
                            Text(
                                text = stringResource(R.string.payment_qr_update_error),
                                color = Color(0xFFFFB4AB),
                                fontSize = 12.sp,
                                modifier = Modifier.align(Alignment.BottomCenter)
                            )
                        }
                    }
                }
            }

            if (bankMenuExpanded) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .zIndex(1f)
                        .clickable { bankMenuExpanded = false }
                )
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .offset(y = BANK_MENU_TOP_OFFSET)
                        .heightIn(max = BANK_MENU_MAX_HEIGHT)
                        .zIndex(2f),
                    color = Color(0xFF202832),
                    shape = RoundedCornerShape(6.dp),
                    shadowElevation = 8.dp
                ) {
                    LazyColumn(modifier = Modifier.fillMaxWidth()) {
                        items(
                            items = banks,
                            key = BankOption::code
                        ) { bank ->
                            BankDropdownItem(bank) {
                                selectedBank = bank
                                bankMenuExpanded = false
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BankDropdownItem(
    bank: BankOption,
    onClick: () -> Unit
) {
    DropdownMenuItem(
        text = {
            Text(
                text = bank.label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    )
}

private const val MAX_ACCOUNT_LENGTH = 32
private val BANK_MENU_TOP_OFFSET = 104.dp
private val BANK_MENU_MAX_HEIGHT = 220.dp

@Preview(widthDp = 400, heightDp = 400, showBackground = true)
@Composable
private fun PaymentQrPopupPreview() {
    PaymentQrPopup(
        initialConfig = PaymentQrConfig(),
        onUpdate = {}
    )
}
