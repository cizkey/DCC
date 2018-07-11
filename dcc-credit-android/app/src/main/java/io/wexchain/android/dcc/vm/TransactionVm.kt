package io.wexchain.android.dcc.vm

import android.databinding.Observable
import android.databinding.ObservableBoolean
import android.databinding.ObservableField
import android.graphics.Color
import android.support.v4.content.ContextCompat
import android.text.Spannable
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import io.reactivex.android.schedulers.AndroidSchedulers
import io.wexchain.android.common.stackTrace
import io.wexchain.android.dcc.App
import io.wexchain.android.common.SingleLiveEvent
import io.wexchain.dcc.R
import io.wexchain.digitalwallet.*
import io.wexchain.digitalwallet.proxy.JuzixErc20Agent
import io.wexchain.digitalwallet.util.*
import java.math.BigDecimal
import java.math.BigInteger

/**
 * Created by sisel on 2018/1/22.
 */
class TransactionVm {
    private lateinit var currency: DigitalCurrency
    var isEdit=false
    lateinit var tx: EthsTransaction

    val txTitle = ObservableField<String>()

    val toAddress = ObservableField<String>()

    val amount = ObservableField<String>()

    val remarks = ObservableField<String>()

    /**
     * input gas price in gwei
     */
    val gasPrice = ObservableField<String>()

    val gasLimit = ObservableField<String>()

    /**
     * fee in eth
     */
    private var maxTransactionFee = BigDecimal.ZERO

    val maxTransactionFeeText = ObservableField<String>()

    val txProceedEvent = SingleLiveEvent<EthsTransactionScratch>()

    val inputNotSatisfiedEvent = SingleLiveEvent<String>()

    val dataInvalidatedEvent=SingleLiveEvent<Void>()

    val busyChecking = ObservableBoolean(false)

    val onPrivateChain = ObservableBoolean()

    var feeRate:BigDecimal? = null
    val transferFeeHintText = ObservableField<CharSequence>()

    init {
        val feeChange: Observable.OnPropertyChangedCallback = object : Observable.OnPropertyChangedCallback() {
            override fun onPropertyChanged(p0: Observable?, p1: Int) {
                computeFee()
            }
        }
        this.gasPrice.addOnPropertyChangedCallback(feeChange)
        this.gasLimit.addOnPropertyChangedCallback(feeChange)
    }

    private fun computeFee() {
        val price: BigDecimal? = gasPrice.get()?.toBigDecimalSafe()
        val limit: BigInteger? = gasLimit.get()?.toBigIntegerSafe()
        val fee = if (price != null && price != BigDecimal.ZERO && limit != null && limit != BigDecimal.ZERO) {
            // gwei to wei  then  multiply then to eth
            computeEthTxFee(price, limit)
        } else {
            BigDecimal.ZERO
        }
        maxTransactionFee = fee
        maxTransactionFeeText.set(if (BigDecimal.ZERO == fee) "" else fee.toPlainString() + "ETH")
    }

    fun ensureDigitalCurrency(dc: DigitalCurrency, feeRate: String?) {
        if (this::currency.isInitialized) {
            if (currency != dc) {
                throw IllegalStateException()
            }
        } else {
            if(isEdit&&null!=tx){
               // amount=tx.amount
            }

            currency = dc
            txTitle.set("${dc.symbol} 转账")
            updateGasPrice()
            onPrivateChain.set(dc.chain == Chain.JUZIX_PRIVATE)
            if(dc.chain == Chain.JUZIX_PRIVATE){
                if(dc.symbol == Currencies.FTC.symbol){
                    val decimal = feeRate!!.toBigDecimalSafe()
                    this.feeRate = decimal
                    val rdc = decimal.scaleByPowerOfTen(3).stripTrailingZeros().toPlainString()
                    val colored = SpannableString(rdc).apply {
                        setSpan(ForegroundColorSpan(ContextCompat.getColor(App.get(), R.color.text_red)),0,rdc.length,Spannable.SPAN_INCLUSIVE_EXCLUSIVE)
                    }
                    this.transferFeeHintText.set(colored)
                }else{
                    val hintText =
                        "@DCC业务链\n\nDCC业务链上的token只能在DCC业务链钱包地址间进行互转，DCC业务链的钱包和以太坊的钱包是通用的，您以太坊的钱包地址同时也是DCC业务链的钱包地址。如您需要将DCC业务链上的token转移到以太坊主链上，请使用跨链转移功能。"
                    val dccFeeHint = SpannableStringBuilder(hintText).apply {
                        setSpan(ForegroundColorSpan(ContextCompat.getColor(App.get(), R.color.text_red)),9,hintText.length,Spannable.SPAN_INCLUSIVE_EXCLUSIVE)
                    }
                    this.transferFeeHintText.set(dccFeeHint)
                }
            }
        }
    }

    private fun updateGasPrice() {
        App.get().assetsRepository.getDigitalCurrencyAgent(currency)
                .getGasPrice()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    var fpp=it
                    if(isEdit){
                         fpp = maxOf(
                            tx.gasPrice,
                            it + BigDecimal("0.5").scaleByPowerOfTen(9).toBigInteger()
                        )
                    }
                    gasPrice.set(weiToGwei(fpp).stripTrailingZeros().toPlainString())
                    dataInvalidatedEvent.call()
                }, {
                    stackTrace(it)
                })
    }

    fun updateGasLimit(focus: Boolean) {
        if (focus) {
            val to = toAddress.get()
            if (to == null || !isEthAddress(to)) {
//                inputNotSatisfiedEvent.value = "to address not valid"
                return
            }
            val value = amount.get()?.toBigDecimalSafe()
            if (value == null || value == BigDecimal.ZERO) {
//                inputNotSatisfiedEvent.value = "amount not valid"
                return
            }
            val r = remarks.get()
            val app = App.get()
            val from = app.passportRepository.getCurrentPassport()!!.address
            val dc = currency
            val scratch = EthsTransactionScratch(
                    currency = dc,
                    from = from,
                    to = to,
                    amount = value,
                    gasPrice = BigDecimal.ZERO,
                    gasLimit = BigInteger.ZERO,
                    remarks = r
            )
            app.assetsRepository.getDigitalCurrencyAgent(dc)
                    .getGasLimit(scratch)
                    .observeOn(AndroidSchedulers.mainThread())
                    .onErrorReturn { BigInteger.valueOf(100000) }
                    .subscribe({
                        if (currency == dc && gasLimit.get().isNullOrEmpty()) {
                            gasLimit.set(it.toString())
                            dataInvalidatedEvent.call()
                        }
                    }, {
                        stackTrace(it)
                    })
        }
    }


    fun checkAndProceed(from: String?) {
        from ?: return
        val to = toAddress.get()
        if (to == null || !isEthAddress(to)) {
            inputNotSatisfiedEvent.value = "收款地址不能为空"
            return
        }
        val value = amount.get()?.toBigDecimalSafe()
        if (value == null || value == BigDecimal.ZERO) {
            inputNotSatisfiedEvent.value = "金额不能为空"
            return
        }
        if(isEdit){
            val gasprice = gasPrice.get()?.toBigDecimalSafe()
            if (gasprice == null || gasprice <weiToGwei(tx.gasPrice)) {
                inputNotSatisfiedEvent.value = "Gas Price必须高于原交易的Gas price"
                return
            }
        }
        val dc = currency
        val isOnPrivate = onPrivateChain.get()
        val limit = if (isOnPrivate){
            JuzixErc20Agent.GAS_LIMIT
        } else {
            val inputLimit = gasLimit.get()?.toBigIntegerSafe()
            if (inputLimit == null || inputLimit == BigInteger.ZERO) {
                inputNotSatisfiedEvent.value = "GasLimit不能为空"
                return
            }
            inputLimit
        }
        val scratch = if (isOnPrivate){
            EthsTransactionScratch(
                    dc,
                    from,
                    to,
                    value,
                    JuzixErc20Agent.GAS_PRICE.toBigDecimal().scaleByPowerOfTen(-9),
                    limit,
                    remarks.get(),
                    feeRate
            )
        }else {
            val price = gasPrice.get()?.toBigDecimalSafe()
            if (price == null || value == BigDecimal.ZERO) {
                inputNotSatisfiedEvent.value = "GasPrice不能为空"
                return
            }
            EthsTransactionScratch(
                    dc,
                    from,
                    to,
                    value,
                    price,
                    limit,
                    remarks.get()
            )
        }
        App.get().assetsRepository.getDigitalCurrencyAgent(dc)
                .getGasLimit(scratch)
                .observeOn(AndroidSchedulers.mainThread())
                .doOnSubscribe { busyChecking.set(true) }
                .doFinally { busyChecking.set(false) }
                .subscribe({
                    if (it > limit) {
                        this.inputNotSatisfiedEvent.value = "Gas Limit不足${it}请重新设置"
                    } else {
                        this.txProceedEvent.value = scratch
                    }
                }, {
                    stackTrace(it)
                    //cannot get gas limit
                    //thus proceed as normal
                    this.txProceedEvent.value = scratch
                })
    }
}