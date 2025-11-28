package net.burningtnt.terracotta;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.VpnService;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import java.io.IOException;

@SuppressLint("VpnServicePolicy")
public class TerracottaVPNService extends VpnService {

    private static final String TAG = "Terracotta VPN";
    private static final int VPN_NOTIFICATION = 1;

    public static final String ACTION_START = "net.burningtnt.terracotta.action.START";
    public static final String ACTION_STOP  = "net.burningtnt.terracotta.action.STOP";

    private NotificationManager notificationManager;
    private ParcelFileDescriptor vpnInterface;

    private void cleanup() {
        Log.d(TAG, "cleanup(): close tun & cancel notification");

        if (notificationManager != null) {
            notificationManager.cancel(VPN_NOTIFICATION);
        }

        if (vpnInterface != null) {
            try {
                vpnInterface.close();
            } catch (IOException ignored) {
            }
            vpnInterface = null;
        }
    }

    @Override
    public void onRevoke() {
        Log.w(TAG, "onRevoke(): preempted by another VPN or revoked by user; tearing down.");

        // 系统从「设置- VPN」那边把你断开时会走这里
        TerracottaAndroidAPI.setWaiting();

        cleanup();
        stopForeground(true);
        stopSelf();
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy(): vpn service finished");
        cleanup();
        super.onDestroy();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        String action = intent != null ? intent.getAction() : null;
        Log.d(TAG, "onStartCommand, action = " + action);

        // ===== 收到 STOP 命令：自己把自己干掉 =====
        if (ACTION_STOP.equals(action)) {
            cleanup();
            stopForeground(true);
            stopSelf();
            return Service.START_NOT_STICKY;
        }

        // 1. 创建 NotificationChannel（API 26+）
        notificationManager =
                (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        if (notificationManager != null) {
            NotificationChannel channel = new NotificationChannel(
                    "vpn_channel",
                    "Terracotta VPN",
                    NotificationManager.IMPORTANCE_LOW
            );
            // 原字节码里是通过 e/a0.b() / e/a0.e() 之类的封装方法创建 / 注册的
            notificationManager.createNotificationChannel(channel);
        }

        // 2. 构建前台通知
        Notification.Builder builder;
        builder = new Notification.Builder(this, "vpn_channel");

        // 标题 / 文本字符串和图标资源，完全取自字节码常量池：
        // "Terracotta Connection"
        // "Your network traffic is routed through Terracotta"
        builder
                .setWhen(System.currentTimeMillis())
                .setSmallIcon(R.drawable.enchantnet)
                .setContentTitle("Terracotta Connection")
                .setContentText("Your network traffic is routed through Terracotta")
                .setOngoing(true);

        // 大图标：R.drawable.red_glazed_terracotta，并做了缩放，这里用简单写法
        Bitmap largeIcon = BitmapFactory.decodeResource(
                getResources(), R.drawable.enchantnet);
        if (largeIcon != null) {
            builder.setLargeIcon(largeIcon);
        }

        // 真正 build Notification
        Notification notification = builder.build();

        startForeground(VPN_NOTIFICATION, notification);

        // 4. 构建 VpnService.Builder，并交给 Terracotta 后端使用
        VpnService.Builder vpnBuilder = new VpnService.Builder().setSession("Terracotta Connection");

        try {
            vpnBuilder.addDisallowedApplication(getPackageName());
        } catch (PackageManager.NameNotFoundException ignored) {
            // 忽略异常
        }

        // 5. 通过 TerracottaAndroidAPI 里挂起的 VpnServiceRequest 真正建立 TUN 接口
        TerracottaAndroidAPI.VpnServiceRequest request =
                TerracottaAndroidAPI.getPendingVpnServiceRequest();
        vpnInterface = request.startVpnService(vpnBuilder);

        // 字节码直接返回常量 1，对应 Service.START_STICKY
        return Service.START_STICKY;
    }

}
