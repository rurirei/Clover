package com.namelessnetx.ang.activities

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.Context
import android.os.Bundle
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.text.TextUtils
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import rx.android.schedulers.AndroidSchedulers
import rx.schedulers.Schedulers
import java.text.Collator
import java.util.*
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import com.namelessnetx.ang.R
import com.namelessnetx.ang.api.AppInfo
import com.namelessnetx.ang.app
import com.namelessnetx.ang.app.PACKAGE
import com.namelessnetx.ang.databinding.ActivityBypassListBinding
import com.namelessnetx.ang.ext.toast
import com.namelessnetx.ang.ext.v2RayApplication
import com.namelessnetx.ang.util.AppManagerUtil
import com.namelessnetx.ang.util.Utils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.net.URL

class PerAppProxyActivity : BaseActivity() {
    private lateinit var binding: ActivityBypassListBinding

    private var adapter: PerAppProxyAdapter? = null
    private var appsAll: List<AppInfo>? = null
    private val defaultSharedPreferences by lazy { PreferenceManager.getDefaultSharedPreferences(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBypassListBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val dividerItemDecoration = DividerItemDecoration(this, LinearLayoutManager.VERTICAL)
        binding.recyclerView.addItemDecoration(dividerItemDecoration)

        val blacklist = defaultSharedPreferences.getStringSet(app.PREF_PER_APP_PROXY_SET, null)

        AppManagerUtil.rxLoadNetworkAppList(this)
                .subscribeOn(Schedulers.io())
                .map {
                    if (blacklist != null) {
                        it.forEach { one ->
                            if ((blacklist.contains(one.packageName))) {
                                one.isSelected = 1
                            } else {
                                one.isSelected = 0
                            }
                        }
                        val comparator = object : Comparator<AppInfo> {
                            override fun compare(p1: AppInfo, p2: AppInfo): Int = when {
                                p1.isSelected > p2.isSelected -> -1
                                p1.isSelected == p2.isSelected -> 0
                                else -> 1
                            }
                        }
                        it.sortedWith(comparator)
                    } else {
                        val comparator = object : Comparator<AppInfo> {
                            val collator = Collator.getInstance()
                            override fun compare(o1: AppInfo, o2: AppInfo) = collator.compare(o1.appName, o2.appName)
                        }
                        it.sortedWith(comparator)
                    }
                }
//                .map {
//                    val comparator = object : Comparator<AppInfo> {
//                        val collator = Collator.getInstance()
//                        override fun compare(o1: AppInfo, o2: AppInfo) = collator.compare(o1.appName, o2.appName)
//                    }
//                    it.sortedWith(comparator)
//                }
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe {
                    appsAll = it
                    adapter = PerAppProxyAdapter(this, it, blacklist)
                    binding.recyclerView.adapter = adapter
                    binding.pbWaiting.visibility = View.GONE
                }

        binding.recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            var dst = 0
            val threshold = resources.getDimensionPixelSize(R.dimen.bypass_list_header_height) * 3
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                dst += dy
                if (dst > threshold) {
                    binding.headerView.hide()
                    dst = 0
                } else if (dst < -20) {
                    binding.headerView.show()
                    dst = 0
                }
            }

            var hiding = false
            fun View.hide() {
                val target = -height.toFloat()
                if (hiding || translationY == target) return
                animate()
                        .translationY(target)
                        .setInterpolator(AccelerateInterpolator(2F))
                        .setListener(object : AnimatorListenerAdapter() {
                            override fun onAnimationEnd(animation: Animator?) {
                                hiding = false
                            }
                        })
                hiding = true
            }

            var showing = false
            fun View.show() {
                val target = 0f
                if (showing || translationY == target) return
                animate()
                        .translationY(target)
                        .setInterpolator(DecelerateInterpolator(2F))
                        .setListener(object : AnimatorListenerAdapter() {
                            override fun onAnimationEnd(animation: Animator?) {
                                showing = false
                            }
                        })
                showing = true
            }
        })

        binding.switchPerAppProxy.setOnCheckedChangeListener { _, isChecked ->
            defaultSharedPreferences.edit().putBoolean(app.PREF_PER_APP_PROXY, isChecked).apply()
        }
        binding.switchPerAppProxy.isChecked = defaultSharedPreferences.getBoolean(app.PREF_PER_APP_PROXY, false)

        binding.switchBypassApps.setOnCheckedChangeListener { _, isChecked ->
            defaultSharedPreferences.edit().putBoolean(app.PREF_BYPASS_APPS, isChecked).apply()
        }
        binding.switchBypassApps.isChecked = defaultSharedPreferences.getBoolean(app.PREF_BYPASS_APPS, false)

        binding.etSearch.setOnEditorActionListener { v, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                //hide
                var imm: InputMethodManager = v.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.toggleSoftInput(0, InputMethodManager.HIDE_NOT_ALWAYS)

                val key = v.text.toString().uppercase()
                val apps = ArrayList<AppInfo>()
                if (TextUtils.isEmpty(key)) {
                    appsAll?.forEach {
                        apps.add(it)
                    }
                } else {
                    appsAll?.forEach {
                        if (it.appName.uppercase().indexOf(key) >= 0) {
                            apps.add(it)
                        }
                    }
                }
                adapter = PerAppProxyAdapter(this, apps, adapter?.blacklist)
                binding.recyclerView.adapter = adapter
                adapter?.notifyDataSetChanged()
                true
            } else {
                false
            }
        }
    }

    override fun onPause() {
        super.onPause()
        adapter?.let {
            defaultSharedPreferences.edit().putStringSet(app.PREF_PER_APP_PROXY_SET, it.blacklist).apply()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_bypass_list, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        R.id.select_all -> adapter?.let {
            val pkgNames = it.apps.map { it.packageName }
            if (it.blacklist.containsAll(pkgNames)) {
                it.apps.forEach {
                    val packageName = it.packageName
                    adapter?.blacklist!!.remove(packageName)
                }
            } else {
                it.apps.forEach {
                    val packageName = it.packageName
                    adapter?.blacklist!!.add(packageName)
                }

            }
            it.notifyDataSetChanged()
            true
        } ?: false
        R.id.select_proxy_app -> {
            selectProxyApp()

            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    private fun selectProxyApp() {
        toast(R.string.msg_downloading_content)
        val url = app.androidpackagenamelistUrl
        GlobalScope.launch(Dispatchers.IO) {
            val content = try {
                URL(url).readText()
            } catch (e: Exception) {
                e.printStackTrace()
                ""
            }
            launch(Dispatchers.Main) {
                Log.d(PACKAGE, content)
                selectProxyApp(content)
                toast(R.string.toast_success)
            }
        }
    }

    private fun selectProxyApp(content: String): Boolean {
        try {
            var proxyApps = content
            if (TextUtils.isEmpty(content)) {
                val assets = Utils.readTextFromAssets(v2RayApplication, "proxy_packagename.txt")
                proxyApps = assets.lines().toString()
            }
            if (TextUtils.isEmpty(proxyApps)) {
                return false
            }

            adapter?.blacklist!!.clear()

            if (binding.switchBypassApps.isChecked) {
                adapter?.let {
                    it.apps.forEach block@{
                        val packageName = it.packageName
                        Log.d(PACKAGE, packageName)
                        if (proxyApps.indexOf(packageName) < 0) {
                            adapter?.blacklist!!.add(packageName)
                            println(packageName)
                            return@block
                        }
                    }
                    it.notifyDataSetChanged()
                }
            } else {
                adapter?.let {
                    it.apps.forEach block@{
                        val packageName = it.packageName
                        Log.d(PACKAGE, packageName)
                        if (proxyApps.indexOf(packageName) >= 0) {
                            adapter?.blacklist!!.add(packageName)
                            println(packageName)
                            return@block
                        }
                    }
                    it.notifyDataSetChanged()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
        return true
    }
}
