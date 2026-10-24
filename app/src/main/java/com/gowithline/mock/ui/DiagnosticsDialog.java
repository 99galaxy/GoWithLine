package com.gowithline.mock.ui;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.widget.Toast;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.gowithline.mock.R;
import com.gowithline.mock.service.ServiceDiagnostics;
import com.gowithline.mock.util.DeviceState;
import com.gowithline.mock.util.MockLocationHelper;

import java.util.Locale;

/**
 * Self-check dialog shared by the settings screen.
 */
public final class DiagnosticsDialog {
    private DiagnosticsDialog() {
    }

    public static void show(Activity activity, ServiceDiagnostics diagnostics,
                            boolean simulationActive) {
        String message = buildText(activity, diagnostics, simulationActive);
        new MaterialAlertDialogBuilder(activity)
                .setTitle("自检")
                .setMessage(message)
                .setPositiveButton(R.string.dialog_ok, null)
                .setNeutralButton("复制", (dialog, which) -> copy(activity, message))
                .show();
    }

    public static String buildText(Activity activity, ServiceDiagnostics diagnostics,
                                   boolean simulationActive) {
        StringBuilder builder = new StringBuilder();
        builder.append("开发者选项：")
                .append(MockLocationHelper.isDeveloperOptionsEnabled(activity)
                        ? "已开启" : "未开启")
                .append('\n');

        boolean mockSelected = MockLocationHelper.isMockAppOpAllowed(activity);
        if (!mockSelected && !simulationActive) {
            // The provider probe adds/removes a test provider; only run it
            // while no simulation providers are registered.
            mockSelected = MockLocationHelper.isMockLocationSelected(activity);
        }
        builder.append("模拟位置应用：")
                .append(mockSelected ? "已选中" : "未选中")
                .append('\n');
        builder.append("系统定位服务：")
                .append(DeviceState.isLocationServiceEnabled(activity) ? "已开启" : "已关闭")
                .append('\n');
        builder.append("Wi-Fi：")
                .append(DeviceState.isWifiEnabled(activity)
                        ? "已开启（可能导致闪回）" : "已关闭")
                .append('\n');
        builder.append("精确位置权限：")
                .append(DeviceState.hasFineLocationPermission(activity)
                        ? "已授予" : "未授予")
                .append('\n');

        if (diagnostics == null) {
            builder.append("模拟服务：未绑定\n");
        } else if (!diagnostics.running && diagnostics.injectedCount == 0) {
            builder.append("模拟服务：未启动模拟\n");
        } else {
            builder.append("模拟服务：")
                    .append(diagnostics.running ? "运行中" : "已停止")
                    .append('\n');
            appendProvider(builder, "GPS",
                    diagnostics.gpsReady, diagnostics.gpsError);
            appendProvider(builder, "NETWORK",
                    diagnostics.networkReady, diagnostics.networkError);
            appendProvider(builder, "FUSED",
                    diagnostics.fusedReady, diagnostics.fusedError);
            builder.append("已注入：")
                    .append(diagnostics.injectedCount)
                    .append(" 次\n");
            if (diagnostics.echoCount > 0) {
                builder.append("系统回读：")
                        .append(String.format(Locale.US,
                                "成功 %d 次（%.5f, %.5f）",
                                diagnostics.echoCount,
                                diagnostics.echoLat,
                                diagnostics.echoLng))
                        .append('\n');
            } else {
                builder.append("系统回读：未收到匹配位置\n");
            }
            builder.append("最近注入错误：")
                    .append(diagnostics.lastInjectionError == null
                            ? "无" : diagnostics.lastInjectionError)
                    .append('\n');
        }

        builder.append("\n若“模拟位置应用”未选中，请在开发者选项中选择“GoWithLine”。")
                .append("\n若“系统回读”一直未收到，说明系统没有把测试位置投递给监听者。");
        return builder.toString();
    }

    private static void appendProvider(StringBuilder builder, String name,
                                       boolean ready, String error) {
        builder.append(name).append(" provider：")
                .append(ready ? "成功" : "失败");
        if (!ready && error != null) {
            builder.append("（").append(error).append("）");
        }
        builder.append('\n');
    }

    private static void copy(Context context, String message) {
        ClipboardManager clipboard =
                (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(
                    ClipData.newPlainText("GoWithLine diagnostics", message));
            Toast.makeText(context, R.string.toast_copied, Toast.LENGTH_SHORT).show();
        }
    }
}
