package com.namelessnetx.ang.activities

import androidx.appcompat.app.AppCompatActivity
import android.view.MenuItem

abstract class BaseActivity : AppCompatActivity() {
    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        android.R.id.home -> {
            onBackPressed()
            true
        }
        else -> super.onOptionsItemSelected(item)
    }
}
