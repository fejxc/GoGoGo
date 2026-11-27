package com.sunyun.anygo.utils;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.PowerManager;
import android.provider.Settings;

import java.util.ArrayList;
import java.util.List;

/**
 * 模拟启动前置条件自检：一次性检查全部条件，替代逐项 Toast，
 * 未满足项支持点击直达对应系统设置页。
 */
public class PreconditionChecker {

    public static class Item {
        public final String name;       // 检查项名称
        public final boolean satisfied; // 是否满足
        public final Intent fixIntent;  // 修复跳转（null 表示无直达页面）

        Item(String name, boolean satisfied, Intent fixIntent) {
            this.name = name;
            this.satisfied = satisfied;
            this.fixIntent = fixIntent;
        }
    }

    public static List<Item> checkAll(Context ctx) {
        List<Item> list = new ArrayList<>();

        list.add(new Item("网络可用",
                GoUtils.isNetworkAvailable(ctx),
                new Intent(Settings.ACTION_WIRELESS_SETTINGS)));

        list.add(new Item("GPS 已开启",
                GoUtils.isGpsOpened(ctx),
                new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)));

        list.add(new Item("已授权模拟位置（开发者选项）",
                GoUtils.isAllowMockLocation(ctx),
                new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)));

        list.add(new Item("已授予悬浮窗权限",
                Settings.canDrawOverlays(ctx),
                new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + ctx.getPackageName()))));

        // 后台运行：国产 ROM 省电策略可能杀掉前台服务导致模拟中断（无标准 API，跳转电池优化列表）
        PowerManager pm = (PowerManager) ctx.getSystemService(Context.POWER_SERVICE);
        boolean ignoring = pm != null && pm.isIgnoringBatteryOptimizations(ctx.getPackageName());
        list.add(new Item("允许后台持续运行（省电白名单）",
                ignoring,
                new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)));

        return list;
    }

    /** 返回未满足的检查项，全部满足时为空列表 */
    public static List<Item> checkUnmet(Context ctx) {
        List<Item> unmet = new ArrayList<>();
        for (Item item : checkAll(ctx)) {
            if (!item.satisfied) {
                unmet.add(item);
            }
        }
        return unmet;
    }
}
