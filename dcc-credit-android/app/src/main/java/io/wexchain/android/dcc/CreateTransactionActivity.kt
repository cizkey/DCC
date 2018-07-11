package io.wexchain.android.dcc

import android.arch.lifecycle.Observer
import android.content.Intent
import android.os.Bundle
import io.wexchain.android.dcc.constant.RequestCodes
import io.wexchain.android.dcc.base.BindActivity
import io.wexchain.android.dcc.constant.Extras
import io.wexchain.android.dcc.view.dialog.CustomDialog
import io.wexchain.android.dcc.view.dialog.TransactionConfirmDialogFragment
import io.wexchain.android.dcc.vm.TransactionVm
import io.wexchain.dcc.R
import io.wexchain.dcc.databinding.ActivityCreateTransactionBinding
import io.wexchain.digitalwallet.DigitalCurrency
import io.wexchain.digitalwallet.EthsTransaction
import io.wexchain.digitalwallet.EthsTransactionScratch
import java.math.BigDecimal

class CreateTransactionActivity : BindActivity<ActivityCreateTransactionBinding>() {
    override val contentLayoutId: Int = R.layout.activity_create_transaction
     var isEdit=false//是否是编辑转账
    val txVm = TransactionVm()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initToolbar(true)
        var dc = intent.getSerializableExtra(Extras.EXTRA_DIGITAL_CURRENCY) as? DigitalCurrency
        val feeRate = intent.getStringExtra(Extras.EXTRA_FTC_TRANSFER_FEE_RATE)

        val tx = intent.getSerializableExtra(Extras.EXTRA_EDIT_TRANSACTION) as? EthsTransaction
        if(null!=tx){
            isEdit=true
            txVm.isEdit=true
            txVm.tx=tx
            dc=tx.digitalCurrency
            binding.etInputAmount.setText(dc.toDecimalAmount(tx.amount).toString())
            txVm.toAddress.set(tx.to)
         //   binding.etInputAddress.setText(tx.to)
         //   binding.etInputGasPrice.setText(dc.toDecimalAmount(tx.gasPrice).toString())
        }
        title = ("${dc!!.symbol} 转账")
        setupEvents(dc,feeRate)
        setupButtons()

    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            RequestCodes.SCAN -> {
                val address = data?.getStringExtra(QrScannerActivity.EXTRA_SCAN_RESULT)
                val txVm = binding.tx
                if (address != null && txVm != null) {
                    txVm.toAddress.set(address)
                    binding.executePendingBindings()
                    binding.etInputAddress.setSelection(address.length)
                }
            }
            else -> super.onActivityResult(requestCode, resultCode, data)
        }
    }

    private fun setupButtons() {
        binding.ibScanAddress.setOnClickListener {
            startActivityForResult(Intent(this, QrScannerActivity::class.java).apply {
                putExtra(Extras.EXPECTED_SCAN_TYPE, QrScannerActivity.SCAN_TYPE_ADDRESS)
            }, RequestCodes.SCAN)
        }
    }

    private fun setupEvents(dc: DigitalCurrency, feeRate: String?) {
        val observer = Observer<EthsTransactionScratch> {
            it?.let {
                showConfirmDialog(it)
            }
        }
        binding.tx = txVm.apply {
            ensureDigitalCurrency(dc,feeRate)
            this.txProceedEvent.observe(this@CreateTransactionActivity, observer)
            this.inputNotSatisfiedEvent.observe(this@CreateTransactionActivity, Observer {
                it?.let {
                    CustomDialog(this@CreateTransactionActivity).apply {
                        textContent = it
                    }.assembleAndShow()
                }
            })
            this.dataInvalidatedEvent.observe(this@CreateTransactionActivity, Observer {
                binding.executePendingBindings()
            })
        }
        binding.etInputGasLimit.setOnFocusChangeListener { v, hasFocus ->
            binding.tx?.updateGasLimit(hasFocus)
        }
        App.get().passportRepository.currPassport.observe(this, Observer {
            it?.let {
                binding.passport = it
            }
        })
    }

    private fun showConfirmDialog(ethsTransactionScratch: EthsTransactionScratch) {
        TransactionConfirmDialogFragment.create(ethsTransactionScratch,isEdit)
                .show(supportFragmentManager, null)
    }
}
