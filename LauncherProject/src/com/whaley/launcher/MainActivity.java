package com.whaley.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {

    private static final String PREFS_NAME = "LauncherPrefs";
    private static final String KEY_DEFAULT_SOURCE = "default_source"; 
    private static final String KEY_PINNED_APPS_PREFIX = "pinned_app_slot_"; 
    
    private LinearLayout cardHdmi1;
    private LinearLayout cardHdmi2;
    private LinearLayout cardAv;
    private LinearLayout cardSettings;
    
    private TextView starHdmi1;
    private TextView starHdmi2;
    private TextView starAv;
    
    private TextView tvStatusHdmi1;
    private TextView tvStatusHdmi2;
    private TextView tvStatusAv;

    private TextView tvClockTime;
    private TextView tvClockDate;

    private TextView tvCountdown;
    private ProgressBar progressBar;
    private LinearLayout countdownContainer; 
    
    private LinearLayout appsContainer;
    
    private TextView tvSourceLabel;
    private LinearLayout layoutSourceRow;
    private TextView tvAppsLabel;
    private boolean showSources = true;
    
    // Wi-Fi 状态图标组件
    private ImageView ivWifiStatus;
    private WifiReceiver wifiReceiver;

    private int defaultSourceIndex = 1; 
    private Runnable clockRunnable;
    private Runnable signalPollRunnable;
    private int secondsLeft = 5;
    private boolean isCountdownCancelled = false;
    private Handler handler = new Handler();
    private Runnable countdownRunnable;
    private int elapsedProgress = 0;
    private static final int TOTAL_DURATION_MS = 5000;
    private static final int INTERVAL_MS = 50;

    private List<AppInfoModel> allInstalledApps = new ArrayList<AppInfoModel>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 初始化组件
        cardHdmi1 = (LinearLayout) findViewById(R.id.card_hdmi1);
        cardHdmi2 = (LinearLayout) findViewById(R.id.card_hdmi2);
        cardAv = (LinearLayout) findViewById(R.id.card_av);
        cardSettings = (LinearLayout) findViewById(R.id.card_settings);

        starHdmi1 = (TextView) findViewById(R.id.star_hdmi1);
        starHdmi2 = (TextView) findViewById(R.id.star_hdmi2);
        starAv = (TextView) findViewById(R.id.star_av);

        tvCountdown = (TextView) findViewById(R.id.tv_countdown);
        progressBar = (ProgressBar) findViewById(R.id.progress_bar);
        countdownContainer = (LinearLayout) findViewById(R.id.countdown_container);
        
        appsContainer = (LinearLayout) findViewById(R.id.apps_container);
        
        ivWifiStatus = (ImageView) findViewById(R.id.iv_wifi_status);

        tvStatusHdmi1 = (TextView) findViewById(R.id.tv_status_hdmi1);
        tvStatusHdmi2 = (TextView) findViewById(R.id.tv_status_hdmi2);
        tvStatusAv = (TextView) findViewById(R.id.tv_status_av);

        tvClockTime = (TextView) findViewById(R.id.tv_clock_time);
        tvClockDate = (TextView) findViewById(R.id.tv_clock_date);

        tvSourceLabel = (TextView) findViewById(R.id.tv_source_label);
        layoutSourceRow = (LinearLayout) findViewById(R.id.layout_source_row);
        tvAppsLabel = (TextView) findViewById(R.id.tv_apps_label);

        // 读取默认源配置
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        defaultSourceIndex = prefs.getInt(KEY_DEFAULT_SOURCE, 1); 
        updateStarIndicators();

        showSources = prefs.getBoolean("show_sources", isWhaleyDevice());
        applySourcesVisibility();

        // 监听焦点自动暂停倒计时
        View.OnFocusChangeListener focusListener = new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (hasFocus) {
                    cancelCountdown();
                }
            }
        };
        cardHdmi1.setOnFocusChangeListener(focusListener);
        cardHdmi2.setOnFocusChangeListener(focusListener);
        cardAv.setOnFocusChangeListener(focusListener);
        cardSettings.setOnFocusChangeListener(focusListener);

        // 信号源点击
        cardHdmi1.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                launchTvPlayer("HDMI 1");
            }
        });
        cardHdmi2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                launchTvPlayer("HDMI 2");
            }
        });
        cardAv.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                launchTvPlayer("AV");
            }
        });
        cardSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                launchSystemSettings();
            }
        });

        // 信号源长按绑定默认开机源
        View.OnLongClickListener longClickListener = new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                int id = v.getId();
                if (id == R.id.card_hdmi1) {
                    setDefaultSource(0);
                } else if (id == R.id.card_hdmi2) {
                    setDefaultSource(1);
                } else if (id == R.id.card_av) {
                    setDefaultSource(2);
                }
                return true;
            }
        };
        cardHdmi1.setOnLongClickListener(longClickListener);
        cardHdmi2.setOnLongClickListener(longClickListener);
        cardAv.setOnLongClickListener(longClickListener);

        // 建议 4：长按“系统设置”卡片，弹出防砖一键激活官方桌面选项
        cardSettings.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                showRestoreHeliosLauncherDialog();
                return true;
            }
        });


        // 动态广播注册，实时刷新 Wi-Fi 强度图标
        registerWifiReceiver();

        scanAllApps();
        renderAppShelf();
        initTvInputManager();
        startClockUpdates();
        startCountdown();
    }

    // 设置开机默认信号源
    private void setDefaultSource(int index) {
        defaultSourceIndex = index;
        SharedPreferences.Editor editor = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit();
        editor.putInt(KEY_DEFAULT_SOURCE, index);
        editor.apply();

        updateStarIndicators();
        String sourceName = index == 0 ? "HDMI 1" : (index == 1 ? "HDMI 2" : "AV 信号");
        Toast.makeText(this, "⭐ 已成功将 [" + sourceName + "] 设为开机默认直达", Toast.LENGTH_SHORT).show();
        cancelCountdown();
    }

    private void updateStarIndicators() {
        starHdmi1.setVisibility(defaultSourceIndex == 0 ? View.VISIBLE : View.GONE);
        starHdmi2.setVisibility(defaultSourceIndex == 1 ? View.VISIBLE : View.GONE);
        starAv.setVisibility(defaultSourceIndex == 2 ? View.VISIBLE : View.GONE);
    }

    private void startCountdown() {
        progressBar.setMax(TOTAL_DURATION_MS);
        progressBar.setProgress(TOTAL_DURATION_MS);
        
        final String defaultSourceName = defaultSourceIndex == 0 ? "HDMI 1" : (defaultSourceIndex == 1 ? "HDMI 2" : "AV");
        tvCountdown.setText("将在 " + secondsLeft + " 秒后自动连接 to 开机默认源: " + defaultSourceName);

        countdownRunnable = new Runnable() {
            @Override
            public void run() {
                elapsedProgress += INTERVAL_MS;
                int progress = TOTAL_DURATION_MS - elapsedProgress;
                progressBar.setProgress(progress);

                if (elapsedProgress % 1000 == 0) {
                    secondsLeft--;
                    tvCountdown.setText("将在 " + Math.max(0, secondsLeft) + " 秒后自动连接 to 开机默认源: " + defaultSourceName);
                }

                if (elapsedProgress >= TOTAL_DURATION_MS) {
                    launchTvPlayer(defaultSourceName);
                } else {
                    handler.postDelayed(this, INTERVAL_MS);
                }
            }
        };
        handler.postDelayed(countdownRunnable, INTERVAL_MS);
    }

    private void cancelCountdown() {
        if (isCountdownCancelled) return;
        isCountdownCancelled = true;
        handler.removeCallbacks(countdownRunnable);
        countdownContainer.setVisibility(View.GONE);
    }

    // 建议 4：弹窗恢复官方原厂桌面组件，免 Root 自救
    private void showRestoreHeliosLauncherDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("⚠️ 桌面自救防砖选项");
        builder.setMessage("是否确认重新启用原厂的官方 [微鲸 Helios 桌面]？\n\n重新启用后，点击主页键您可以自由选择切换回原厂官方桌面。");
        builder.setPositiveButton("确认启用", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                try {
                    // 动态启用原厂桌面核心组件
                    PackageManager pm = getPackageManager();
                    ComponentName comp = new ComponentName("com.helios.launcher", "com.helios.launcher.Launcher");
                    pm.setComponentEnabledSetting(
                            comp,
                            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                            PackageManager.DONT_KILL_APP
                    );
                    Toast.makeText(MainActivity.this, "🎉 原厂官方桌面已成功启用！可以点按主页键返回原厂系统。", Toast.LENGTH_LONG).show();
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, "启用失败，包名冲突或系统已彻底清除: " + e.getMessage(), Toast.LENGTH_LONG).show();
                }
            }
        });
        builder.setNegativeButton("取消", null);
        builder.create().show();
    }

    // 建议 2：广播实时监听网络 Wi-Fi 信号强度更新
    private void registerWifiReceiver() {
        wifiReceiver = new WifiReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(WifiManager.RSSI_CHANGED_ACTION);
        filter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        filter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
        registerReceiver(wifiReceiver, filter);
        updateWifiStatusIcon(); // 首次主动触发
    }

    private void updateWifiStatusIcon() {
        try {
            WifiManager wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
            if (wifiManager == null) return;
            
            if (!wifiManager.isWifiEnabled()) {
                ivWifiStatus.setImageResource(R.drawable.wifi_offline); // 使用本地断网图标
                ivWifiStatus.setColorFilter(Color.parseColor("#ef4444")); // 红色
                return;
            }

            WifiInfo info = wifiManager.getConnectionInfo();
            if (info != null && info.getNetworkId() != -1) {
                ivWifiStatus.setImageResource(R.drawable.wifi_online); // 使用本地联网图标
                ivWifiStatus.setColorFilter(Color.parseColor("#10b981")); // 绿色正常
            } else {
                ivWifiStatus.setImageResource(R.drawable.wifi_offline); 
                ivWifiStatus.setColorFilter(Color.parseColor("#f59e0b")); // 橙色警告
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private class WifiReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateWifiStatusIcon();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (wifiReceiver != null) {
            unregisterReceiver(wifiReceiver);
        }
        if (clockRunnable != null) {
            handler.removeCallbacks(clockRunnable);
        }
        if (signalPollRunnable != null) {
            handler.removeCallbacks(signalPollRunnable);
        }
    }

    // 建议 2：直接拉起系统网络设置
    private void launchWifiSettings() {
        cancelCountdown();
        try {
            // 尝试打开系统通用设置，这对电视兼容性最好
            Intent intent = new Intent(android.provider.Settings.ACTION_SETTINGS);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "打开设置失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void scanAllApps() {
        allInstalledApps.clear();
        PackageManager pm = getPackageManager();
        Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
        mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        
        List<ResolveInfo> resolveInfos = pm.queryIntentActivities(mainIntent, 0);
        if (resolveInfos == null) return;

        String selfPackage = getPackageName();

        for (ResolveInfo info : resolveInfos) {
            String pkgName = info.activityInfo.packageName;
            if (pkgName.equals(selfPackage) 
                || pkgName.equals("com.helios.launcher") 
                || pkgName.equals("com.android.systemui")
                || pkgName.equals("com.android.keychain")) {
                continue;
            }
            AppInfoModel model = new AppInfoModel();
            model.packageName = pkgName;
            model.label = info.loadLabel(pm).toString();
            model.icon = info.loadIcon(pm);
            allInstalledApps.add(model);
        }
    }

    private void renderAppShelf() {
        appsContainer.removeAllViews();
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        for (int i = 0; i < 5; i++) {
            final int slotIndex = i;
            String savedPkg = prefs.getString(KEY_PINNED_APPS_PREFIX + slotIndex, null);
            
            if (savedPkg == null && allInstalledApps.size() > slotIndex) {
                savedPkg = allInstalledApps.get(slotIndex).packageName;
            }

            AppInfoModel activeApp = null;
            if (savedPkg != null) {
                for (AppInfoModel model : allInstalledApps) {
                    if (model.packageName.equals(savedPkg)) {
                        activeApp = model;
                        break;
                    }
                }
            }

            if (activeApp == null && !allInstalledApps.isEmpty()) {
                activeApp = allInstalledApps.get(0);
            }

            View appItemView = createAppItemView(activeApp, slotIndex);
            appsContainer.addView(appItemView);
        }

        View allAppsButton = createAllAppsButton();
        appsContainer.addView(allAppsButton);

        View launcherSettingsButton = createLauncherSettingsButton();
        appsContainer.addView(launcherSettingsButton);
    }

    private View createAppItemView(final AppInfoModel app, final int slotIndex) {
        LinearLayout appItem = new LinearLayout(this);
        appItem.setOrientation(LinearLayout.VERTICAL);
        appItem.setGravity(Gravity.CENTER_HORIZONTAL);
        appItem.setFocusable(true);
        appItem.setClickable(true);
        appItem.setBackgroundResource(R.drawable.app_item_selector);
        
        int itemWidthDp = showSources ? 110 : 130;
        int iconSizeDp = showSources ? 64 : 80;
        int textSizeSp = showSources ? 12 : 14;
        int paddingDp = showSources ? 15 : 20;

        int itemWidthPx = (int) (itemWidthDp * getResources().getDisplayMetrics().density);
        LinearLayout.LayoutParams itemParams = new LinearLayout.LayoutParams(itemWidthPx, ViewGroup.LayoutParams.WRAP_CONTENT);
        itemParams.setMargins(10, 10, 10, 10);
        appItem.setLayoutParams(itemParams);
        
        int paddingPx = (int) (paddingDp * getResources().getDisplayMetrics().density);
        appItem.setPadding(10, paddingPx, 10, paddingPx);

        ImageView ivIcon = new ImageView(this);
        if (app != null) {
            ivIcon.setImageDrawable(app.icon);
        } else {
            ivIcon.setImageResource(android.R.drawable.sym_def_app_icon);
        }
        int iconSizePx = (int) (iconSizeDp * getResources().getDisplayMetrics().density);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(iconSizePx, iconSizePx);
        ivIcon.setLayoutParams(iconParams);
        appItem.addView(ivIcon);

        TextView tvName = new TextView(this);
        tvName.setText(app != null ? app.label : "待自定义");
        tvName.setTextColor(Color.parseColor("#9ca3af"));
        tvName.setTextSize(textSizeSp);
        tvName.setGravity(Gravity.CENTER_HORIZONTAL);
        tvName.setSingleLine(true);
        tvName.setEllipsize(android.text.TextUtils.TruncateAt.END);
        
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        nameParams.setMargins(0, 8, 0, 0);
        tvName.setLayoutParams(nameParams);
        appItem.addView(tvName);

        appItem.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                cancelCountdown();
                if (app != null) {
                    Intent launchIntent = getPackageManager().getLaunchIntentForPackage(app.packageName);
                    if (launchIntent != null) {
                        try {
                            startActivity(launchIntent);
                        } catch (Exception e) {
                            Toast.makeText(MainActivity.this, "启动失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    }
                } else {
                    openAppSelectionDialog(slotIndex);
                }
            }
        });

        appItem.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                openAppSelectionDialog(slotIndex);
                return true;
            }
        });

        appItem.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (hasFocus) {
                    cancelCountdown();
                    ((TextView) ((LinearLayout) v).getChildAt(1)).setTextColor(Color.WHITE);
                } else {
                    ((TextView) ((LinearLayout) v).getChildAt(1)).setTextColor(Color.parseColor("#9ca3af"));
                }
            }
        });

        return appItem;
    }

    private View createAllAppsButton() {
        LinearLayout appItem = new LinearLayout(this);
        appItem.setOrientation(LinearLayout.VERTICAL);
        appItem.setGravity(Gravity.CENTER_HORIZONTAL);
        appItem.setFocusable(true);
        appItem.setClickable(true);
        appItem.setBackgroundResource(R.drawable.app_item_selector);
        
        int itemWidthDp = showSources ? 110 : 130;
        int iconSizeDp = showSources ? 64 : 80;
        int textSizeSp = showSources ? 12 : 14;
        int paddingDp = showSources ? 15 : 20;

        int itemWidthPx = (int) (itemWidthDp * getResources().getDisplayMetrics().density);
        LinearLayout.LayoutParams itemParams = new LinearLayout.LayoutParams(itemWidthPx, ViewGroup.LayoutParams.WRAP_CONTENT);
        itemParams.setMargins(10, 10, 10, 10);
        appItem.setLayoutParams(itemParams);
        
        int paddingPx = (int) (paddingDp * getResources().getDisplayMetrics().density);
        appItem.setPadding(10, paddingPx, 10, paddingPx);

        ImageView ivIcon = new ImageView(this);
        ivIcon.setImageResource(R.drawable.ic_all_apps); 
        ivIcon.setColorFilter(Color.parseColor("#888899"));
        int iconSizePx = (int) (iconSizeDp * getResources().getDisplayMetrics().density);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(iconSizePx, iconSizePx);
        ivIcon.setLayoutParams(iconParams);
        appItem.addView(ivIcon);

        TextView tvName = new TextView(this);
        tvName.setText("全部应用");
        tvName.setTextColor(Color.parseColor("#9ca3af"));
        tvName.setTextSize(textSizeSp);
        tvName.setGravity(Gravity.CENTER_HORIZONTAL);
        tvName.setSingleLine(true);
        
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        nameParams.setMargins(0, 8, 0, 0);
        tvName.setLayoutParams(nameParams);
        appItem.addView(tvName);

        appItem.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openAllAppsSelectorDialog();
            }
        });

        appItem.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (hasFocus) {
                    cancelCountdown();
                    ((TextView) ((LinearLayout) v).getChildAt(1)).setTextColor(Color.WHITE);
                } else {
                    ((TextView) ((LinearLayout) v).getChildAt(1)).setTextColor(Color.parseColor("#9ca3af"));
                }
            }
        });

        return appItem;
    }

    private View createLauncherSettingsButton() {
        LinearLayout appItem = new LinearLayout(this);
        appItem.setOrientation(LinearLayout.VERTICAL);
        appItem.setGravity(Gravity.CENTER_HORIZONTAL);
        appItem.setFocusable(true);
        appItem.setClickable(true);
        appItem.setBackgroundResource(R.drawable.app_item_selector);
        
        int itemWidthDp = showSources ? 110 : 130;
        int iconSizeDp = showSources ? 64 : 80;
        int textSizeSp = showSources ? 12 : 14;
        int paddingDp = showSources ? 15 : 20;

        int itemWidthPx = (int) (itemWidthDp * getResources().getDisplayMetrics().density);
        LinearLayout.LayoutParams itemParams = new LinearLayout.LayoutParams(itemWidthPx, ViewGroup.LayoutParams.WRAP_CONTENT);
        itemParams.setMargins(10, 10, 10, 10);
        appItem.setLayoutParams(itemParams);
        
        int paddingPx = (int) (paddingDp * getResources().getDisplayMetrics().density);
        appItem.setPadding(10, paddingPx, 10, paddingPx);

        ImageView ivIcon = new ImageView(this);
        ivIcon.setImageResource(R.drawable.ic_launcher_settings); 
        ivIcon.setColorFilter(Color.parseColor("#5c6bc0"));
        int iconSizePx = (int) (iconSizeDp * getResources().getDisplayMetrics().density);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(iconSizePx, iconSizePx);
        ivIcon.setLayoutParams(iconParams);
        appItem.addView(ivIcon);

        TextView tvName = new TextView(this);
        tvName.setText("桌面设置");
        tvName.setTextColor(Color.parseColor("#9ca3af"));
        tvName.setTextSize(textSizeSp);
        tvName.setGravity(Gravity.CENTER_HORIZONTAL);
        tvName.setSingleLine(true);
        
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        nameParams.setMargins(0, 8, 0, 0);
        tvName.setLayoutParams(nameParams);
        appItem.addView(tvName);

        appItem.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showLauncherSettingsDialog();
            }
        });

        appItem.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (hasFocus) {
                    cancelCountdown();
                    ((TextView) ((LinearLayout) v).getChildAt(1)).setTextColor(Color.WHITE);
                } else {
                    ((TextView) ((LinearLayout) v).getChildAt(1)).setTextColor(Color.parseColor("#9ca3af"));
                }
            }
        });

        return appItem;
    }

    private void showLauncherSettingsDialog() {
        cancelCountdown();
        final SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        boolean currentShow = prefs.getBoolean("show_sources", isWhaleyDevice());
        
        String[] options = new String[] { "显示直达与信号源" };
        final boolean[] checkedItems = new boolean[] { currentShow };
        
        AlertDialog.Builder builder = new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert);
        builder.setTitle("桌面设置");
        builder.setMultiChoiceItems(options, checkedItems, new DialogInterface.OnMultiChoiceClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which, boolean isChecked) {
                prefs.edit().putBoolean("show_sources", isChecked).commit();
                showSources = isChecked;
                applySourcesVisibility();
                renderAppShelf();
            }
        });
        builder.setPositiveButton("确定", null);
        builder.show();
    }

    private boolean isWhaleyDevice() {
        try {
            Class.forName("com.mstar.android.tvapi.common.TvManager");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    private void applySourcesVisibility() {
        if (tvSourceLabel != null && layoutSourceRow != null) {
            if (showSources) {
                tvSourceLabel.setVisibility(View.VISIBLE);
                layoutSourceRow.setVisibility(View.VISIBLE);
                
                LinearLayout.LayoutParams labelParams = (LinearLayout.LayoutParams) tvAppsLabel.getLayoutParams();
                labelParams.topMargin = 0;
                tvAppsLabel.setLayoutParams(labelParams);
            } else {
                tvSourceLabel.setVisibility(View.GONE);
                layoutSourceRow.setVisibility(View.GONE);
                
                LinearLayout.LayoutParams labelParams = (LinearLayout.LayoutParams) tvAppsLabel.getLayoutParams();
                int marginPx = (int) (120 * getResources().getDisplayMetrics().density);
                labelParams.topMargin = marginPx;
                tvAppsLabel.setLayoutParams(labelParams);
            }
        }
    }

    private void openAppSelectionDialog(final int slotIndex) {
        if (allInstalledApps.isEmpty()) {
            Toast.makeText(this, "未找到其他已安装应用", Toast.LENGTH_SHORT).show();
            return;
        }

        final String[] appNames = new String[allInstalledApps.size()];
        for (int i = 0; i < allInstalledApps.size(); i++) {
            appNames[i] = allInstalledApps.get(i).label;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("选择要固定到此位置的应用");
        builder.setItems(appNames, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                AppInfoModel selectedApp = allInstalledApps.get(which);
                
                SharedPreferences.Editor editor = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit();
                editor.putString(KEY_PINNED_APPS_PREFIX + slotIndex, selectedApp.packageName);
                editor.apply();

                renderAppShelf();
                Toast.makeText(MainActivity.this, "已成功固定: " + selectedApp.label, Toast.LENGTH_SHORT).show();
            }
        });
        builder.create().show();
    }

    private void openAllAppsSelectorDialog() {
        if (allInstalledApps.isEmpty()) {
            Toast.makeText(this, "未找到已安装应用", Toast.LENGTH_SHORT).show();
            return;
        }

        final android.app.Dialog dialog = new android.app.Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        dialog.setContentView(R.layout.dialog_all_apps);
        
        android.widget.GridView gridView = (android.widget.GridView) dialog.findViewById(R.id.apps_grid);
        
        gridView.setAdapter(new android.widget.BaseAdapter() {
            @Override
            public int getCount() {
                return allInstalledApps.size();
            }

            @Override
            public Object getItem(int position) {
                return allInstalledApps.get(position);
            }

            @Override
            public long getItemId(int position) {
                return position;
            }

            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                final AppInfoModel app = allInstalledApps.get(position);
                
                LinearLayout itemView = new LinearLayout(MainActivity.this);
                itemView.setOrientation(LinearLayout.VERTICAL);
                itemView.setGravity(Gravity.CENTER_HORIZONTAL);
                itemView.setFocusable(true); 
                itemView.setBackgroundResource(R.drawable.app_item_selector);
                
                int itemWidthPx = (int) (110 * getResources().getDisplayMetrics().density);
                itemView.setLayoutParams(new android.widget.AbsListView.LayoutParams(itemWidthPx, ViewGroup.LayoutParams.WRAP_CONTENT));
                itemView.setPadding(10, 15, 10, 15);

                ImageView ivIcon = new ImageView(MainActivity.this);
                ivIcon.setImageDrawable(app.icon);
                int iconSizePx = (int) (64 * getResources().getDisplayMetrics().density);
                LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(iconSizePx, iconSizePx);
                ivIcon.setLayoutParams(iconParams);
                itemView.addView(ivIcon);

                TextView tvName = new TextView(MainActivity.this);
                tvName.setText(app.label);
                tvName.setTextColor(Color.parseColor("#9ca3af"));
                tvName.setTextSize(12);
                tvName.setGravity(Gravity.CENTER_HORIZONTAL);
                tvName.setSingleLine(true);
                tvName.setEllipsize(android.text.TextUtils.TruncateAt.END);
                
                LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                nameParams.setMargins(0, 8, 0, 0);
                tvName.setLayoutParams(nameParams);
                itemView.addView(tvName);

                itemView.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                    @Override
                    public void onFocusChange(View v, boolean hasFocus) {
                        if (hasFocus) {
                            ((TextView) ((LinearLayout) v).getChildAt(1)).setTextColor(Color.WHITE);
                        } else {
                            ((TextView) ((LinearLayout) v).getChildAt(1)).setTextColor(Color.parseColor("#9ca3af"));
                        }
                    }
                });

                return itemView;
            }
        });

        gridView.setOnItemClickListener(new android.widget.AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(android.widget.AdapterView<?> parent, View view, int position, long id) {
                dialog.dismiss();
                AppInfoModel app = allInstalledApps.get(position);
                Intent launchIntent = getPackageManager().getLaunchIntentForPackage(app.packageName);
                if (launchIntent != null) {
                    try {
                        startActivity(launchIntent);
                    } catch (Exception e) {
                        Toast.makeText(MainActivity.this, "启动失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                }
            }
        });

        dialog.show();
    }

    private void launchTvPlayer(final String sourceName) {
        cancelCountdown();
        Toast.makeText(this, "正在启动 " + sourceName + " 信号源...", Toast.LENGTH_SHORT).show();

        final int inputSrcOrdinal;
        if (sourceName.equals("HDMI 1")) {
            inputSrcOrdinal = 4;
        } else if (sourceName.equals("HDMI 2")) {
            inputSrcOrdinal = 5;
        } else if (sourceName.equals("AV")) {
            inputSrcOrdinal = 7;
        } else {
            inputSrcOrdinal = 1;
        }

        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                try {
                    Intent intent = new Intent("com.whaley.tv.tvplayer.ui.START_TV_PLAYER");
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    intent.putExtra("inputSrc", inputSrcOrdinal);
                    startActivity(intent);
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, "启动失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
                }
            }
        }, 200);
    }

    private void launchSystemSettings() {
        cancelCountdown();
        try {
            Intent intent = new Intent();
            intent.setComponent(new ComponentName("com.helios.setting", "com.helios.activity.StartActivity"));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Exception e) {
            try {
                Intent intent = new Intent(android.provider.Settings.ACTION_SETTINGS);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
            } catch (Exception ex) {
                Toast.makeText(this, "启动系统设置失败: " + ex.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        cancelCountdown();
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            return true; 
        }
        return super.onKeyDown(keyCode, event);
    }

    private void initTvInputManager() {
        try {
            final TvInputManager tvInputManager = (TvInputManager) getSystemService(Context.TV_INPUT_SERVICE);
            if (tvInputManager != null) {
                tvInputManager.registerCallback(new TvInputManager.TvInputCallback() {
                    @Override
                    public void onInputStateChanged(String inputId, int state) {
                        updateSignalStates();
                    }
                }, new Handler());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        startSignalPolling();
    }

    private void startSignalPolling() {
        signalPollRunnable = new Runnable() {
            @Override
            public void run() {
                updateSignalStates();
                handler.postDelayed(this, 2000);
            }
        };
        handler.post(signalPollRunnable);
    }

    private void updateSignalStates() {
        boolean hdmi1Connected = false;
        boolean hdmi2Connected = false;
        boolean avConnected = false;
        boolean mstarSuccess = false;

        // Try Mstar TvManager reflection first
        try {
            Class<?> tvManagerClass = Class.forName("com.mstar.android.tvapi.common.TvManager");
            Method getInstanceMethod = tvManagerClass.getMethod("getInstance");
            Object tvManager = getInstanceMethod.invoke(null);
            if (tvManager != null) {
                Method setTvosCommonCommandMethod = tvManagerClass.getMethod("setTvosCommonCommand", String.class);
                short[] sourceStatus = (short[]) setTvosCommonCommandMethod.invoke(tvManager, "GetInputSourceStatus");
                if (sourceStatus != null) {
                    if (sourceStatus.length > 23) hdmi1Connected = (sourceStatus[23] != 0);
                    if (sourceStatus.length > 24) hdmi2Connected = (sourceStatus[24] != 0);
                    if (sourceStatus.length > 2) avConnected = (sourceStatus[2] != 0);
                    mstarSuccess = true;
                }
            }
        } catch (Exception e) {
            // Mstar reflection failed
        }

        // If Mstar failed, fallback to standard TvInputManager
        if (!mstarSuccess) {
            try {
                TvInputManager tvInputManager = (TvInputManager) getSystemService(Context.TV_INPUT_SERVICE);
                if (tvInputManager != null) {
                    List<TvInputInfo> inputs = tvInputManager.getTvInputList();
                    for (TvInputInfo info : inputs) {
                        String id = info.getId().toLowerCase();
                        int state = tvInputManager.getInputState(info.getId());
                        boolean isConnected = (state == TvInputManager.INPUT_STATE_CONNECTED || state == TvInputManager.INPUT_STATE_CONNECTED_STANDBY);
                        
                        if (id.contains("hdmi1") || id.contains("hdmi/hw1") || id.contains("hw4")) {
                            if (isConnected) hdmi1Connected = true;
                        } else if (id.contains("hdmi2") || id.contains("hdmi/hw2") || id.contains("hw5")) {
                            if (isConnected) hdmi2Connected = true;
                        } else if (id.contains("av") || id.contains("composite") || id.contains("hw7")) {
                            if (isConnected) avConnected = true;
                        } else if (info.getType() == TvInputInfo.TYPE_HDMI) {
                            if (id.contains("port1")) {
                                if (isConnected) hdmi1Connected = true;
                            } else if (id.contains("port2")) {
                                if (isConnected) hdmi2Connected = true;
                            }
                        }
                    }
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }

        updateSignalIndicator(tvStatusHdmi1, hdmi1Connected);
        updateSignalIndicator(tvStatusHdmi2, hdmi2Connected);
        updateSignalIndicator(tvStatusAv, avConnected);
    }

    private void updateSignalIndicator(TextView tv, boolean connected) {
        if (tv == null) return;
        if (connected) {
            tv.setText("● 已连接");
            tv.setTextColor(Color.parseColor("#10b981"));
        } else {
            tv.setText("● 无信号");
            tv.setTextColor(Color.parseColor("#9ca3af"));
        }
    }

    private void startClockUpdates() {
        clockRunnable = new Runnable() {
            @Override
            public void run() {
                updateClock();
                handler.postDelayed(this, 1000);
            }
        };
        handler.post(clockRunnable);
    }

    private void updateClock() {
        if (tvClockTime == null || tvClockDate == null) return;
        try {
            java.util.Calendar cal = java.util.Calendar.getInstance();
            int hour = cal.get(java.util.Calendar.HOUR_OF_DAY);
            int minute = cal.get(java.util.Calendar.MINUTE);
            
            String timeStr = String.format(java.util.Locale.getDefault(), "%02d:%02d", hour, minute);
            tvClockTime.setText(timeStr);
            
            int year = cal.get(java.util.Calendar.YEAR);
            int month = cal.get(java.util.Calendar.MONTH) + 1;
            int date = cal.get(java.util.Calendar.DAY_OF_MONTH);
            int dayOfWeek = cal.get(java.util.Calendar.DAY_OF_WEEK);
            
            String[] days = {"", "星期日", "星期一", "星期二", "星期三", "星期四", "星期五", "星期六"};
            String dateStr = String.format(java.util.Locale.getDefault(), "%d年%d月%d日 %s", year, month, date, days[dayOfWeek]);
            tvClockDate.setText(dateStr);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static class AppInfoModel {
        public String packageName;
        public String label;
        public Drawable icon;
    }
}
