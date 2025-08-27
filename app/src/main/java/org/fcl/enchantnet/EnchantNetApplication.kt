package org.fcl.enchantnet

import android.app.Application
import org.fcl.enchantnetcore.EnchantNet
import org.fcl.enchantnetcore.state.GuestNoticeRes
import org.fcl.enchantnetcore.state.HostNoticeRes
import org.fcl.enchantnetcore.state.LanScanNoticeRes

class EnchantNetApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        val icon = org.fcl.enchantnetcore.R.drawable.enchantnet

        EnchantNet.init(
            applicationContext,
            // LanScanNoticeRes(int icon, String titleScanning, String textScanning)
            LanScanNoticeRes(
                icon,
                getString(R.string.enchantnet_notice_lan_scan_title),
                getString(R.string.enchantnet_notice_lan_scan_text)
            ),
            // HostNoticeRes(int icon, String titleConnecting, String textConnecting, String titleConnected, String textConnected,
            //               String titleFailBoot, String textFailBoot, String titleFailCrash, String textFailCrash,
            //               String titleFailConn, String textFailConn, String btnExit, String btnCopy)
            HostNoticeRes(
                icon,
                getString(R.string.enchantnet_notice_host_connecting_title),
                getString(R.string.enchantnet_notice_host_connecting_text),
                getString(R.string.enchantnet_notice_host_connected_title),
                getString(R.string.enchantnet_notice_host_connected_text),
                getString(R.string.enchantnet_notice_host_fail_title),
                getString(R.string.enchantnet_notice_host_fail_text),
                getString(R.string.enchantnet_notice_host_crash_title),
                getString(R.string.enchantnet_notice_host_crash_text),
                getString(R.string.enchantnet_notice_host_lost_title),
                getString(R.string.enchantnet_notice_host_lost_text),
                getString(R.string.enchantnet_notice_host_btn_exit),
                getString(R.string.enchantnet_notice_host_btn_copy)
            ),
            // GuestNoticeRes(int icon, String titleConnecting, String textConnecting, String titleConnected, String textConnected,
            //                String titleFailBoot, String textFailBoot, String titleFailCrash, String textFailCrash,
            //                String titleFailConn, String textFailConn, String btnExit, String motd)
            GuestNoticeRes(
                icon,
                getString(R.string.enchantnet_notice_guest_connecting_title),
                getString(R.string.enchantnet_notice_guest_connecting_text),
                getString(R.string.enchantnet_notice_guest_connected_title),
                getString(R.string.enchantnet_notice_guest_connected_text),
                getString(R.string.enchantnet_notice_guest_fail_title),
                getString(R.string.enchantnet_notice_guest_fail_text),
                getString(R.string.enchantnet_notice_guest_crash_title),
                getString(R.string.enchantnet_notice_guest_crash_text),
                getString(R.string.enchantnet_notice_guest_lost_title),
                getString(R.string.enchantnet_notice_guest_lost_text),
                getString(R.string.enchantnet_notice_guest_btn_exit),
                getString(R.string.enchantnet_notice_guest_motd)
            )
        )
    }
}