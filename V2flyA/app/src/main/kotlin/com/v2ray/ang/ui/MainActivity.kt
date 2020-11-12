package com.v2ray.ang.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.net.VpnService
import android.os.Bundle
import com.google.android.material.navigation.NavigationView
import androidx.core.view.GravityCompat
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import android.text.TextUtils
import android.util.Log
import android.view.*
import com.tbruyelle.rxpermissions.RxPermissions
import com.v2ray.ang.AppConfig
import com.v2ray.ang.BuildConfig
import com.v2ray.ang.R
import com.v2ray.ang.dto.EConfigType
import com.v2ray.ang.extension.defaultDPreference
import com.v2ray.ang.extension.toast
import com.v2ray.ang.helper.SimpleItemTouchHelperCallback
import com.v2ray.ang.util.*
import com.v2ray.ang.util.AngConfigManager.configs
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.coroutines.*
import libv2ray.Libv2ray
import rx.Observable
import rx.android.schedulers.AndroidSchedulers
import java.lang.ref.SoftReference
import java.net.URL
import java.util.concurrent.TimeUnit

class MainActivity : BaseActivity(), NavigationView.OnNavigationItemSelectedListener {
    companion object {
        private const val REQUEST_CODE_VPN_PREPARE = 0
        private const val REQUEST_SCAN = 1
        private const val REQUEST_FILE_CHOOSER = 2
        private const val REQUEST_SCAN_URL = 3
    }

    var isRunning = false
        set(value) {
            field = value
            adapter.changeable = !value
            if (value) {
                fab.setImageResource(R.drawable.ic_v)
                tv_test_state.text = getString(R.string.connection_connected)
            } else {
                fab.setImageResource(R.drawable.ic_v_idle)
                tv_test_state.text = getString(R.string.connection_not_connected)
            }
            hideCircle()
        }
    private val adapter by lazy { MainRecyclerAdapter(this) }
    private var mItemTouchHelper: ItemTouchHelper? = null
    private val tcpingTestScope by lazy { CoroutineScope(Dispatchers.IO) }

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        title = getString(R.string.title_server)
        setSupportActionBar(toolbar)

        fab.setOnClickListener {
            if (isRunning) {
                Utils.stopVService(this)
            } else if (defaultDPreference.getPrefString(AppConfig.PREF_MODE, "VPN") == "VPN") {
                val intent = VpnService.prepare(this)
                if (intent == null) {
                    startV2Ray()
                } else {
                    startActivityForResult(intent, REQUEST_CODE_VPN_PREPARE)
                }
            } else {
                startV2Ray()
            }
        }
        layout_test.setOnClickListener {
            if (isRunning) {
                val socksPort = 10808
                //val socksPort = Utils.parseInt(defaultDPreference.getPrefString(SettingsActivity.PREF_SOCKS_PORT, "10808"))
                tv_test_state.text = getString(R.string.connection_test_testing)
                GlobalScope.launch(Dispatchers.IO) {
                    val result = Utils.testConnection(this@MainActivity, socksPort)
                    launch(Dispatchers.Main) {
                        tv_test_state.text = Utils.getEditable(result)
                    }
                }
            }
        }

        recycler_view.setHasFixedSize(true)
        recycler_view.layoutManager = LinearLayoutManager(this)
        recycler_view.adapter = adapter

        val callback = SimpleItemTouchHelperCallback(adapter)
        mItemTouchHelper = ItemTouchHelper(callback)
        mItemTouchHelper?.attachToRecyclerView(recycler_view)


        val toggle = ActionBarDrawerToggle(
                this, drawer_layout, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close)
        drawer_layout.addDrawerListener(toggle)
        toggle.syncState()
        nav_view.setNavigationItemSelectedListener(this)
        version.text = "v${BuildConfig.VERSION_NAME} (${Libv2ray.checkVersionX()})"
    }

    private fun startV2Ray() {
        if (configs.index < 0) {
            return
        }
        showCircle()
        if (!Utils.startVService(this)) {
            hideCircle()
        }
    }

    override fun onStart() {
        super.onStart()
        isRunning = false
        mMsgReceive = ReceiveMessageHandler(this@MainActivity)
        registerReceiver(mMsgReceive, IntentFilter(AppConfig.BROADCAST_ACTION_ACTIVITY))
        MessageUtil.sendMsg2Service(this, AppConfig.MSG_REGISTER_CLIENT, "")
    }

    override fun onStop() {
        super.onStop()
        if (mMsgReceive != null) {
            unregisterReceiver(mMsgReceive)
            mMsgReceive = null
        }
    }

    public override fun onResume() {
        super.onResume()
        adapter.updateConfigList()
    }

    public override fun onPause() {
        super.onPause()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQUEST_CODE_VPN_PREPARE ->
                if (resultCode == RESULT_OK) {
                    startV2Ray()
                }
            REQUEST_SCAN ->
                if (resultCode == RESULT_OK) {
                    importBatchConfig(data?.getStringExtra("SCAN_RESULT"))
                }
            REQUEST_FILE_CHOOSER -> {
                val uri = data?.data
                if (resultCode == RESULT_OK && uri != null) {
                    readContentFromUri(uri)
                }
            }
            REQUEST_SCAN_URL ->
                if (resultCode == RESULT_OK) {
                    importConfigCustomUrl(data?.getStringExtra("SCAN_RESULT"))
                }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    private fun getOptionIntent() = Intent().putExtra("position", -1)
            .putExtra("isRunning", isRunning)

    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        R.id.import_qrcode -> {
            importQRcode(REQUEST_SCAN)
            true
        }
        R.id.import_manually_vmess -> {
            startActivity(getOptionIntent().setClass(this, VmessActivity::class.java))
            adapter.updateConfigList()
            true
        }
        R.id.import_manually_ss -> {
            startActivity(getOptionIntent().setClass(this, ShadowsocksActivity::class.java))
            adapter.updateConfigList()
            true
        }

        R.id.import_config_custom_clipboard -> {
            importConfigCustomClipboard()
            true
        }
        R.id.import_config_custom_local -> {
            importConfigCustomLocal()
            true
        }
        R.id.import_config_custom_url -> {
            importConfigCustomUrlClipboard()
            true
        }
        R.id.import_config_custom_url_scan -> {
            importQRcode(REQUEST_SCAN_URL)
            true
        }
        R.id.sub_update -> {
            importConfigViaSub()
            true
        }
        R.id.export_all -> {
            if (AngConfigManager.shareAll2Clipboard() == 0) {
            } else {
                toast(R.string.toast_failure)
            }
            true
        }
        R.id.ping_all -> {
            tcpingTestScope.coroutineContext[Job]?.cancelChildren()
            Utils.closeAllTcpSockets()
            for (k in 0 until configs.vmess.count()) {
                configs.vmess[k].testResult = ""
                adapter.updateConfigList()
            }
            for (k in 0 until configs.vmess.count()) {
                var serverAddress = configs.vmess[k].address
                var serverPort = configs.vmess[k].port
                if (configs.vmess[k].configType == EConfigType.CUSTOM.value) {
                    val serverOutbound = V2rayConfigUtil.getCustomConfigServerOutbound(applicationContext, configs.vmess[k].guid)
                            ?: continue
                    serverAddress = serverOutbound.getServerAddress() ?: continue
                    serverPort = serverOutbound.getServerPort() ?: continue
                }
                tcpingTestScope.launch {
                    configs.vmess.getOrNull(k)?.let {  // check null in case array is modified during testing
                        it.testResult = Utils.tcping(serverAddress, serverPort)
                        launch(Dispatchers.Main) {
                            adapter.updateSelectedItem(k)
                        }
                    }
                }
            }
            true
        }
        else -> super.onOptionsItemSelected(item)
    }


    private fun importQRcode(requestCode: Int): Boolean {
        RxPermissions(this)
                .request(Manifest.permission.CAMERA)
                .subscribe {
                    if (it)
                        startActivityForResult(Intent(this, ScannerActivity::class.java), requestCode)
                    else
                        toast(R.string.toast_permission_denied)
                }
        return true
    }

    /**
     * import config from clipboard
     */
    private fun importClipboard()
            : Boolean {
        try {
            val clipboard = Utils.getClipboard(this)
            importBatchConfig(clipboard)
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
        return true
    }

    private fun importBatchConfig(server: String?, subid: String = "") {
        val count = AngConfigManager.importBatchConfig(server, subid)
        if (count > 0) {
            toast(R.string.toast_success)
            adapter.updateConfigList()
        } else {
            toast(R.string.toast_failure)
        }
    }

    private fun importConfigCustomClipboard()
            : Boolean {
        try {
            val configText = Utils.getClipboard(this)
            if (TextUtils.isEmpty(configText)) {
                toast(R.string.toast_none_data_clipboard)
                return false
            }
            importCustomizeConfig(configText)
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    /**
     * import config from local config file
     */
    private fun importConfigCustomLocal(): Boolean {
        try {
            showFileChooser()
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
        return true
    }

    private fun importConfigCustomUrlClipboard()
            : Boolean {
        try {
            val url = Utils.getClipboard(this)
            if (TextUtils.isEmpty(url)) {
                toast(R.string.toast_none_data_clipboard)
                return false
            }
            return importConfigCustomUrl(url)
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    /**
     * import config from url
     */
    private fun importConfigCustomUrl(url: String?): Boolean {
        try {
            if (!Utils.isValidUrl(url)) {
                toast(R.string.toast_invalid_url)
                return false
            }
            GlobalScope.launch(Dispatchers.IO) {
                val configText = try {
                    URL(url).readText()
                } catch (e: Exception) {
                    e.printStackTrace()
                    ""
                }
                launch(Dispatchers.Main) {
                    importCustomizeConfig(configText)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
        return true
    }

    /**
     * import config from sub
     */
    private fun importConfigViaSub()
            : Boolean {
        try {
            toast(R.string.title_sub_update)
            val subItem = configs.subItem
            for (k in 0 until subItem.count()) {
                if (TextUtils.isEmpty(subItem[k].id)
                        || TextUtils.isEmpty(subItem[k].remarks)
                        || TextUtils.isEmpty(subItem[k].url)
                ) {
                    continue
                }
                val id = subItem[k].id
                val url = subItem[k].url
                if (!Utils.isValidUrl(url)) {
                    continue
                }
                Log.d("Main", url)
                GlobalScope.launch(Dispatchers.IO) {
                    val configText = try {
                        URL(url).readText()
                    } catch (e: Exception) {
                        e.printStackTrace()
                        ""
                    }
                    launch(Dispatchers.Main) {
                        importBatchConfig(Utils.decode(configText), id)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
        return true
    }

    /**
     * show file chooser
     */
    private fun showFileChooser() {
        val intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.type = "*/*"
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        try {
            startActivityForResult(
                    Intent.createChooser(intent, getString(R.string.title_file_chooser)),
                    REQUEST_FILE_CHOOSER)
        } catch (ex: android.content.ActivityNotFoundException) {
            toast(R.string.toast_require_file_manager)
        }
    }

    /**
     * read content from uri
     */
    private fun readContentFromUri(uri: Uri) {
        RxPermissions(this)
                .request(Manifest.permission.READ_EXTERNAL_STORAGE)
                .subscribe { it ->
                    if (it) {
                        try {
                            contentResolver.openInputStream(uri).use {
                                val configText = it?.bufferedReader()?.readText()
                                importCustomizeConfig(configText)
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    } else
                        toast(R.string.toast_permission_denied)
                }
    }

    /**
     * import customize config
     */
    private fun importCustomizeConfig(server: String?) {
        if (server == null) {
            return
        }
        if (!V2rayConfigUtil.isValidConfig(server)) {
            toast(R.string.toast_config_file_invalid)
            return
        }
        val resId = AngConfigManager.importCustomizeConfig(server)
        if (resId > 0) {
            toast(resId)
        } else {
            toast(R.string.toast_success)
            adapter.updateConfigList()
        }
    }

//    val mConnection = object : ServiceConnection {
//        override fun onServiceDisconnected(name: ComponentName?) {
//        }
//
//        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
//            sendMsg(AppConfig.MSG_REGISTER_CLIENT, "")
//        }
//    }

    private
    var mMsgReceive: BroadcastReceiver? = null

    private class ReceiveMessageHandler(activity: MainActivity) : BroadcastReceiver() {
        var mReference: SoftReference<MainActivity> = SoftReference(activity)
        override fun onReceive(ctx: Context?, intent: Intent?) {
            val activity = mReference.get()
            when (intent?.getIntExtra("key", 0)) {
                AppConfig.MSG_STATE_RUNNING -> {
                    activity?.isRunning = true
                }
                AppConfig.MSG_STATE_NOT_RUNNING -> {
                    activity?.isRunning = false
                }
                AppConfig.MSG_STATE_START_SUCCESS -> {
                    activity?.toast(R.string.toast_services_success)
                    activity?.isRunning = true
                }
                AppConfig.MSG_STATE_START_FAILURE -> {
                    activity?.toast(R.string.toast_services_failure)
                    activity?.isRunning = false
                }
                AppConfig.MSG_STATE_STOP_SUCCESS -> {
                    activity?.isRunning = false
                }
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            moveTaskToBack(false)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    fun showCircle() {
        fabProgressCircle?.show()
    }

    fun hideCircle() {
        try {
            Observable.timer(300, TimeUnit.MILLISECONDS)
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe {
                        if (fabProgressCircle.isShown) {
                            fabProgressCircle.hide()
                        }
                    }
        } catch (e: Exception) {
        }
    }

    override fun onBackPressed() {
        if (drawer_layout.isDrawerOpen(GravityCompat.START)) {
            drawer_layout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        // Handle navigation view item clicks here.
        when (item.itemId) {
            //R.id.server_profile -> activityClass = MainActivity::class.java
            R.id.sub_setting -> {
                startActivity(Intent(this, SubSettingActivity::class.java))
            }
            R.id.settings -> {
                startActivity(Intent(this, SettingsActivity::class.java)
                        .putExtra("isRunning", isRunning))
            }

            R.id.logcat -> {
                startActivity(Intent(this, LogcatActivity::class.java))
            }
        }
        drawer_layout.closeDrawer(GravityCompat.START)
        return true
    }
}
