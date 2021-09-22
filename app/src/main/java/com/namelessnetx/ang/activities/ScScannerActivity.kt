package com.namelessnetx.ang.activities

import android.Manifest
import android.content.*
import com.tbruyelle.rxpermissions.RxPermissions
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import com.namelessnetx.ang.MainActivity
import com.namelessnetx.ang.R
import com.namelessnetx.ang.extension.toast
import com.namelessnetx.ang.util.AngConfigManager

class ScScannerActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_none)
        importQRcode()
    }

    private fun importQRcode(): Boolean {
        RxPermissions(this)
                .request(Manifest.permission.CAMERA)
                .subscribe {
                    if (it)
                        scanQRCode.launch(Intent(this, ScannerActivity::class.java))
                    else
                        toast(R.string.toast_permission_denied)
                }

        return true
    }

    private val scanQRCode = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == RESULT_OK) {
            val count = AngConfigManager.importBatchConfig(it.data?.getStringExtra("SCAN_RESULT"), "")
            if (count > 0) {
                toast(R.string.toast_success)
            } else {
                toast(R.string.toast_failure)
            }
            startActivity(Intent(this, MainActivity::class.java))
        }
        finish()
    }
}
