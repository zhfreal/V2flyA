package com.v2flya.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.RecyclerView
import com.v2flya.R
import com.v2flya.dto.AngConfig
import com.v2flya.dto.EConfigType
import com.v2flya.dto.EConfigType.*
import com.v2flya.extension.toast
import com.v2flya.helper.ItemTouchHelperAdapter
import com.v2flya.helper.ItemTouchHelperViewHolder
import com.v2flya.util.AngConfigManager
import com.v2flya.util.Utils
import com.v2flya.util.V2rayConfigUtil
import kotlinx.android.synthetic.main.item_qrcode.view.*
import kotlinx.android.synthetic.main.item_recycler_main.view.*
import rx.Observable
import rx.android.schedulers.AndroidSchedulers
import java.util.*
import java.util.concurrent.TimeUnit

class MainRecyclerAdapter(val activity: MainActivity) : RecyclerView.Adapter<MainRecyclerAdapter.BaseViewHolder>()
        , ItemTouchHelperAdapter {
    companion object {
        private const val VIEW_TYPE_ITEM = 1
        private const val VIEW_TYPE_FOOTER = 2
    }

    private var mActivity: MainActivity = activity
    private lateinit var configs: AngConfig
    private val share_method: Array<out String> by lazy {
        mActivity.resources.getStringArray(R.array.share_method)
    }

    var changeable: Boolean = true
        set(value) {
            if (field == value)
                return
            field = value
            notifyDataSetChanged()
        }

    init {
        updateConfigList()
    }

    override fun getItemCount() = configs.vmess.count() + 1

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(holder: BaseViewHolder, position: Int) {
        if (holder is MainViewHolder) {
            val configType = EConfigType.fromInt(configs.vmess[position].configType)
            val remarks = configs.vmess[position].remarks
            val subid = configs.vmess[position].subid
            val address = configs.vmess[position].address
            val port = configs.vmess[position].port
            val test_result = configs.vmess[position].testResult

            holder.name.text = remarks
            holder.radio.isChecked = (position == configs.index)
            holder.itemView.setBackgroundColor(Color.TRANSPARENT)
            holder.test_result.text = test_result

            if (TextUtils.isEmpty(subid)) {
                holder.subid.text = ""
            } else {
                holder.subid.text = "S"
            }

            var shareOptions = share_method.asList()
            if (configType == CUSTOM) {
                holder.type.text = mActivity.getString(R.string.server_customize_config)
                val serverOutbound = V2rayConfigUtil.getCustomConfigServerOutbound(mActivity.applicationContext, configs.vmess[position].guid)
                if (serverOutbound == null) {
                    holder.statistics.text = ""
                } else {
                    holder.statistics.text = "${serverOutbound.getServerAddress()} : ${serverOutbound.getServerPort()}"
                }
                shareOptions = shareOptions.takeLast(1)
            } else {
                holder.type.text = configType?.name?.toLowerCase(Locale.ROOT)
                holder.statistics.text = "$address : $port"
            }

            holder.layout_share.setOnClickListener {
                AlertDialog.Builder(mActivity).setItems(shareOptions.toTypedArray()) { _, i ->
                    try {
                        when (i) {
                            0 -> {
                                if (configType == CUSTOM) {
                                    shareFullContent(position)
                                } else {
                                    val iv = mActivity.layoutInflater.inflate(R.layout.item_qrcode, null)
                                    iv.iv_qcode.setImageBitmap(AngConfigManager.share2QRCode(position))
                                    AlertDialog.Builder(mActivity).setView(iv).show()
                                }
                            }
                            1 -> {
                                if (AngConfigManager.share2Clipboard(position) == 0) {
                                    mActivity.toast(R.string.toast_success)
                                } else {
                                    mActivity.toast(R.string.toast_failure)
                                }
                            }
                            2 -> shareFullContent(position)
                            else -> mActivity.toast("else")
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }.show()
            }

            holder.layout_edit.setOnClickListener {
                val intent = Intent().putExtra("position", position)
                        .putExtra("isRunning", !changeable)
                when (configType) {
                    VMESS -> {
                        mActivity.startActivity(intent.setClass(mActivity, VmessActivity::class.java))
                    }
                    CUSTOM -> {
                        mActivity.startActivity(intent.setClass(mActivity, CustomActivity::class.java))
                    }
                    SHADOWSOCKS -> {
                        mActivity.startActivity(intent.setClass(mActivity, ShadowsocksActivity::class.java))
                    }
                    SOCKS -> TODO()
                    null -> TODO()
                }
            }
            holder.layout_remove.setOnClickListener {
                if (configs.index != position) {
                    if (AngConfigManager.removeServer(position) == 0) {
                        notifyItemRemoved(position)
                        updateSelectedItem(position)
                    }
                }
            }

            holder.infoContainer.setOnClickListener {
                if (changeable) {
                    AngConfigManager.setActiveServer(position)
                } else {
                    Utils.stopVService(mActivity)
                    AngConfigManager.setActiveServer(position)
                    Observable.timer(500, TimeUnit.MILLISECONDS)
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe()
                }
                notifyDataSetChanged()
            }
        }
        if (holder is FooterViewHolder) {
            //if (activity?.defaultDPreference?.getPrefBoolean(AppConfig.PREF_INAPP_BUY_IS_PREMIUM, false)) {
            holder.layout_edit.visibility = View.INVISIBLE
        }
    }

    private fun shareFullContent(position: Int) {
        if (AngConfigManager.shareFullContent2Clipboard(position) == 0) {
            mActivity.toast(R.string.toast_success)
        } else {
            mActivity.toast(R.string.toast_failure)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BaseViewHolder {
        return when (viewType) {
            VIEW_TYPE_ITEM ->
                MainViewHolder(LayoutInflater.from(parent.context)
                        .inflate(R.layout.item_recycler_main, parent, false))
            else ->
                FooterViewHolder(LayoutInflater.from(parent.context)
                        .inflate(R.layout.item_recycler_footer, parent, false))
        }
    }

    fun updateConfigList() {
        configs = AngConfigManager.configs
        notifyDataSetChanged()
    }
    fun updateSelectedItem(pos: Int) {
        notifyItemRangeChanged(pos, itemCount - pos)
    }

    override fun getItemViewType(position: Int): Int {
        return if (position == configs.vmess.count()) {
            VIEW_TYPE_FOOTER
        } else {
            VIEW_TYPE_ITEM
        }
    }

    open class BaseViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)

    class MainViewHolder(itemView: View) : BaseViewHolder(itemView), ItemTouchHelperViewHolder {
        val subid = itemView.tv_subid
        val radio = itemView.btn_radio!!
        val name = itemView.tv_name!!
        val test_result = itemView.tv_test_result!!
        val type = itemView.tv_type!!
        val statistics = itemView.tv_statistics!!
        val infoContainer = itemView.info_container!!
        val layout_edit = itemView.layout_edit!!
        val layout_share = itemView.layout_share
        val layout_remove = itemView.layout_remove!!

        override fun onItemSelected() {
            itemView.setBackgroundColor(Color.LTGRAY)
        }

        override fun onItemClear() {
            itemView.setBackgroundColor(0)
        }
    }

    class FooterViewHolder(itemView: View) : BaseViewHolder(itemView), ItemTouchHelperViewHolder {
        val layout_edit = itemView.layout_edit!!

        override fun onItemSelected() {
            itemView.setBackgroundColor(Color.LTGRAY)
        }

        override fun onItemClear() {
            itemView.setBackgroundColor(0)
        }
    }

    override fun onItemDismiss(position: Int) {
        if (configs.index != position) {
//            mActivity.alert(R.string.del_config_comfirm) {
//                positiveButton(android.R.string.ok) {
            if (AngConfigManager.removeServer(position) == 0) {
                notifyItemRemoved(position)
            }
//                }
//                show()
//            }
        }
        updateSelectedItem(position)
    }

    override fun onItemMove(fromPosition: Int, toPosition: Int): Boolean {
        AngConfigManager.swapServer(fromPosition, toPosition)
        notifyItemMoved(fromPosition, toPosition)
        //notifyDataSetChanged()
        updateSelectedItem(if (fromPosition < toPosition) fromPosition else toPosition)
        return true
    }

    override fun onItemMoveCompleted() {
        AngConfigManager.storeConfigFile()
    }
}
