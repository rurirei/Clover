package com.namelessnetx.ang.ui.home

import android.content.Intent
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.Gson
import com.namelessnetx.ang.MainActivity
import com.namelessnetx.ang.R
import com.namelessnetx.ang.activities.ServerActivity
import com.namelessnetx.ang.activities.ServerCustomConfigActivity
import com.namelessnetx.ang.api.EConfigType
import com.namelessnetx.ang.api.SubscriptionItem
import com.namelessnetx.ang.databinding.ItemQrcodeBinding
import com.namelessnetx.ang.databinding.ItemRecyclerFooterBinding
import com.namelessnetx.ang.databinding.RecyclerViewBinding
import com.namelessnetx.ang.ext.toast
import com.namelessnetx.ang.service.V2RayServiceManager
import com.namelessnetx.ang.util.AngConfigManager
import com.namelessnetx.ang.util.MmkvManager
import com.namelessnetx.ang.util.Utils
import com.tencent.mmkv.MMKV
import rx.Observable
import rx.android.schedulers.AndroidSchedulers
import java.util.concurrent.TimeUnit

class RecyclerAdapter(val activity: MainActivity) :
    RecyclerView.Adapter<RecyclerAdapter.BaseViewHolder>() {
    companion object {
        private const val VIEW_TYPE_ITEM = 1
        private const val VIEW_TYPE_FOOTER = 2
    }

    private var mActivity: MainActivity = activity
    private val mainStorage by lazy {
        MMKV.mmkvWithID(
            MmkvManager.ID_MAIN,
            MMKV.MULTI_PROCESS_MODE
        )
    }
    private val subStorage by lazy { MMKV.mmkvWithID(MmkvManager.ID_SUB, MMKV.MULTI_PROCESS_MODE) }
    private val share_method: Array<out String> by lazy {
        mActivity.resources.getStringArray(R.array.share_method)
    }
    var isRunning = false

    override fun getItemCount() = mActivity.mainViewModel.serverList.size + 1

    override fun onBindViewHolder(holder: BaseViewHolder, position: Int) {
        if (holder is MainViewHolder) {
            val guid = mActivity.mainViewModel.serverList.getOrNull(position) ?: return
            val config = mActivity.mainViewModel.serversCache.getOrElse(
                guid,
                { MmkvManager.decodeServerConfig(guid) }) ?: return
            val outbound = config.getProxyOutbound()
            val aff = MmkvManager.decodeServerAffiliationInfo(guid)

            holder.itemMainBinding.tvName.text = config.remarks
            /**
             *  Update color depending on whether the config is active or not
             */
            if (guid == mainStorage?.decodeString(MmkvManager.KEY_SELECTED_SERVER)) {
                holder.itemMainBinding.btnRadio.setBackgroundColor(
                    ContextCompat.getColor(
                        mActivity,
                        R.color.design_default_color_secondary
                    )
                )
            } else {
                holder.itemMainBinding.btnRadio.setBackgroundColor(Color.LTGRAY)
            }
            holder.itemView.setBackgroundColor(Color.TRANSPARENT)
            holder.itemMainBinding.tvTestResult.text = aff?.getTestDelayString() ?: ""
            if (aff?.testDelayMillis ?: 0L < 0L) {
                holder.itemMainBinding.tvTestResult.setTextColor(
                    ContextCompat.getColor(
                        mActivity,
                        android.R.color.holo_red_dark
                    )
                )
            } else {
                holder.itemMainBinding.tvTestResult.setTextColor(
                    ContextCompat.getColor(
                        mActivity,
                        R.color.colorPing
                    )
                )
            }
            holder.itemMainBinding.tvSubscription.text = ""
            val json = subStorage?.decodeString(config.subscriptionId)
            if (!json.isNullOrBlank()) {
                val sub = Gson().fromJson(json, SubscriptionItem::class.java)
                holder.itemMainBinding.tvSubscription.text = sub.remarks
            }

            var shareOptions = share_method.asList()
            if (config.configType == EConfigType.CUSTOM) {
                holder.itemMainBinding.tvType.text =
                    mActivity.getString(R.string.server_customize_config)
                shareOptions = shareOptions.takeLast(1)
            } else if (config.configType == EConfigType.VLESS) {
                holder.itemMainBinding.tvType.text = config.configType.name
            } else {
                holder.itemMainBinding.tvType.text = config.configType.name.lowercase()
            }
            holder.itemMainBinding.tvStatistics.text =
                "${outbound?.getServerAddress()} : ${outbound?.getServerPort()}"

            holder.itemMainBinding.layoutShare.setOnClickListener {
                AlertDialog.Builder(mActivity).setItems(shareOptions.toTypedArray()) { _, i ->
                    try {
                        when (i) {
                            0 -> {
                                if (config.configType == EConfigType.CUSTOM) {
                                    shareFullContent(guid)
                                } else {
                                    val ivBinding =
                                        ItemQrcodeBinding.inflate(LayoutInflater.from(mActivity))
                                    ivBinding.ivQcode.setImageBitmap(
                                        AngConfigManager.share2QRCode(
                                            guid
                                        )
                                    )
                                    AlertDialog.Builder(mActivity).setView(ivBinding.root).show()
                                }
                            }
                            1 -> {
                                if (AngConfigManager.share2Clipboard(mActivity, guid) == 0) {
                                    mActivity.toast(R.string.toast_success)
                                } else {
                                    mActivity.toast(R.string.toast_failure)
                                }
                            }
                            2 -> shareFullContent(guid)
                            else -> mActivity.toast("else")
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }.show()
            }

            holder.itemMainBinding.layoutEdit.setOnClickListener {
                val intent = Intent().putExtra("guid", guid)
                    .putExtra("isRunning", isRunning)
                if (config.configType == EConfigType.CUSTOM) {
                    mActivity.startActivity(
                        intent.setClass(
                            mActivity,
                            ServerCustomConfigActivity::class.java
                        )
                    )
                } else {
                    mActivity.startActivity(intent.setClass(mActivity, ServerActivity::class.java))
                }
            }
            holder.itemMainBinding.layoutRemove.setOnClickListener {
                if (guid != mainStorage?.decodeString(MmkvManager.KEY_SELECTED_SERVER)) {
                    mActivity.mainViewModel.removeServer(guid)
                    notifyItemRemoved(position)
                    notifyItemRangeChanged(position, mActivity.mainViewModel.serverList.size)
                }
            }

            holder.itemMainBinding.infoContainer.setOnClickListener {
                val selected = mainStorage?.decodeString(MmkvManager.KEY_SELECTED_SERVER)
                if (guid != selected) {
                    mainStorage?.encode(MmkvManager.KEY_SELECTED_SERVER, guid)
                    notifyItemChanged(mActivity.mainViewModel.serverList.indexOf(selected))
                    notifyItemChanged(mActivity.mainViewModel.serverList.indexOf(guid))
                    if (isRunning) {
                        mActivity.showCircle()
                        Utils.stopVService(mActivity)
                        Observable.timer(500, TimeUnit.MILLISECONDS)
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe {
                                V2RayServiceManager.startV2Ray(mActivity)
                                mActivity.hideCircle()
                            }
                    }
                }
            }
        }
        if (holder is FooterViewHolder) {
            //if (activity?.defaultDPreference?.getPrefBoolean(AppConfig.PREF_INAPP_BUY_IS_PREMIUM, false)) {
            if (true) {
                holder.itemFooterBinding.layoutEdit.visibility = View.INVISIBLE
            } else {
                holder.itemFooterBinding.layoutEdit.setOnClickListener {
                    Utils.openUri(
                        mActivity,
                        "${Utils.decode("https://mrbhai.ml/")}?t=${System.currentTimeMillis()}"
                    )
                }
            }
        }
    }

    private fun shareFullContent(guid: String) {
        if (AngConfigManager.shareFullContent2Clipboard(mActivity, guid) == 0) {
            mActivity.toast(R.string.toast_success)
        } else {
            mActivity.toast(R.string.toast_failure)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BaseViewHolder {
        return when (viewType) {
            VIEW_TYPE_ITEM ->
                MainViewHolder(
                    RecyclerViewBinding.inflate(
                        LayoutInflater.from(parent.context),
                        parent,
                        false
                    )
                )
            else ->
                FooterViewHolder(
                    ItemRecyclerFooterBinding.inflate(
                        LayoutInflater.from(parent.context),
                        parent,
                        false
                    )
                )
        }
    }

    override fun getItemViewType(position: Int): Int {
        return if (position == mActivity.mainViewModel.serverList.size) {
            VIEW_TYPE_FOOTER
        } else {
            VIEW_TYPE_ITEM
        }
    }

    open class BaseViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        fun onItemSelected() {
            itemView.setBackgroundColor(Color.LTGRAY)
        }

        fun onItemClear() {
            itemView.setBackgroundColor(0)
        }
    }

    class MainViewHolder(val itemMainBinding: RecyclerViewBinding) :
        BaseViewHolder(itemMainBinding.root)

    class FooterViewHolder(val itemFooterBinding: ItemRecyclerFooterBinding) :
        BaseViewHolder(itemFooterBinding.root)

    fun onItemDismiss(position: Int) {
        val guid = mActivity.mainViewModel.serverList.getOrNull(position) ?: return
        if (guid != mainStorage?.decodeString(MmkvManager.KEY_SELECTED_SERVER)) {
//            mActivity.alert(R.string.del_config_comfirm) {
//                positiveButton(android.R.string.ok) {
            mActivity.mainViewModel.removeServer(guid)
            notifyItemRemoved(position)
//                }
//                show()
//            }
        }
    }

    fun onItemMove(fromPosition: Int, toPosition: Int): Boolean {
        mActivity.mainViewModel.swapServer(fromPosition, toPosition)
        notifyItemMoved(fromPosition, toPosition)
        // position is changed, since position is used by click callbacks, need to update range
        if (toPosition > fromPosition)
            notifyItemRangeChanged(fromPosition, toPosition - fromPosition + 1)
        else
            notifyItemRangeChanged(toPosition, fromPosition - toPosition + 1)
        return true
    }

    fun onItemMoveCompleted() {
        // do nothing
    }
}
