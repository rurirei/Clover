package com.namelessnetx.ang.activities

import android.os.Bundle
import com.namelessnetx.ang.R
import com.namelessnetx.ang.activities.BaseActivity
import com.namelessnetx.ang.service.V2RayServiceManager
import com.namelessnetx.ang.util.Utils

class ScSwitchActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        moveTaskToBack(true)

        setContentView(R.layout.activity_none)

        if (V2RayServiceManager.v2rayPoint.isRunning) {
            Utils.stopVService(this)
        } else {
            Utils.startVServiceFromToggle(this)
        }
        finish()
    }
}
