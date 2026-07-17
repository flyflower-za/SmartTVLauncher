package com.whaley.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
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

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {

    private static final String PREFS_NAME = "LauncherPrefs";
    private static final String KEY_DEFAULT_SOURCE = "default_source"; 
    private static final String KEY_PINNED_APPS_PREFIX = "pinned_app_slot_"; // 保存5个自定义槽位包名
    
    private LinearLayout cardHdmi1;
    private LinearLayout cardHdmi2;
    private LinearLayout cardAv;
    private LinearLayout cardSettings;
    
    private TextView starHdmi1;
    private TextView starHdmi2;
    private TextView starAv;
    
    private TextView tvCountdown;
    private ProgressBar progressBar;
    private LinearLayout countdownContainer; // 倒计时容器
    
    private LinearLayout appsContainer;

    private int defaultSourceIndex = 1; 
    private int secondsLeft = 5;
    private boolean isCountdownCancelled = false;
    private Handler handler = new Handler();
    private Runnable countdownRunnable;
    private int elapsedProgress = 0;
    private static final int TOTAL_DURATION_MS = 5000;
    private static final int INTERVAL_MS = 50;

    // 已安装的所有应用缓存
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

        // 读取保存的默认信号源
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        defaultSourceIndex = prefs.getInt(KEY_DEFAULT_SOURCE, 1); 
        updateStarIndicators();

        // 监听焦点以自动暂停倒计时
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

        // 设置点击事件
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

        // 设置长按设置默认信号源
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

        // 预载全部应用信息
        scanAllApps();
        
        // 渲染并固定 5个自定义 + 1个全部应用
        renderAppShelf();

        // 启动自启倒计时
        startCountdown();
    }

    // 设置默认开机信号源
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

    // 开启倒计时
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

    // 暂停/取消倒计时，按需求隐藏掉整个倒计时栏，不显字
    private void cancelCountdown() {
        if (isCountdownCancelled) return;
        isCountdownCancelled = true;
        handler.removeCallbacks(countdownRunnable);
        countdownContainer.setVisibility(View.GONE);
    }

    // 扫描系统中的应用列表
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

    // 渲染常用位与全部应用
    private void renderAppShelf() {
        appsContainer.removeAllViews();
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        // 1. 动态生成 5 个自定义槽位
        for (int i = 0; i < 5; i++) {
            final int slotIndex = i;
            String savedPkg = prefs.getString(KEY_PINNED_APPS_PREFIX + slotIndex, null);
            
            // 默认兜底：如果没有配置，则取系统扫描到的前几个
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

            // 如果该应用已被卸载，使用第一个存在的应用兜底
            if (activeApp == null && !allInstalledApps.isEmpty()) {
                activeApp = allInstalledApps.get(0);
            }

            View appItemView = createAppItemView(activeApp, slotIndex);
            appsContainer.addView(appItemView);
        }

        // 2. 加载第 6 个固定位：全部应用
        View allAppsButton = createAllAppsButton();
        appsContainer.addView(allAppsButton);
    }

    // 创建单个卡片的 View 结构 (包含 64dp 大图标布局与长按监听)
    private View createAppItemView(final AppInfoModel app, final int slotIndex) {
        LinearLayout appItem = new LinearLayout(this);
        appItem.setOrientation(LinearLayout.VERTICAL);
        appItem.setGravity(Gravity.CENTER_HORIZONTAL);
        appItem.setFocusable(true);
        appItem.setClickable(true);
        appItem.setBackgroundResource(R.drawable.app_item_selector);
        
        // 110dp 宽度自适应
        int itemWidthPx = (int) (110 * getResources().getDisplayMetrics().density);
        LinearLayout.LayoutParams itemParams = new LinearLayout.LayoutParams(itemWidthPx, ViewGroup.LayoutParams.WRAP_CONTENT);
        itemParams.setMargins(10, 10, 10, 10);
        appItem.setLayoutParams(itemParams);
        appItem.setPadding(10, 15, 10, 15);

        // 图标：改为 64dp (64 * density)
        ImageView ivIcon = new ImageView(this);
        if (app != null) {
            ivIcon.setImageDrawable(app.icon);
        } else {
            ivIcon.setImageResource(android.R.drawable.sym_def_app_icon);
        }
        int iconSizePx = (int) (64 * getResources().getDisplayMetrics().density);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(iconSizePx, iconSizePx);
        ivIcon.setLayoutParams(iconParams);
        appItem.addView(ivIcon);

        // 标题
        TextView tvName = new TextView(this);
        tvName.setText(app != null ? app.label : "待自定义");
        tvName.setTextColor(Color.parseColor("#9ca3af"));
        tvName.setTextSize(12);
        tvName.setGravity(Gravity.CENTER_HORIZONTAL);
        tvName.setSingleLine(true);
        tvName.setEllipsize(android.text.TextUtils.TruncateAt.END);
        
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        nameParams.setMargins(0, 8, 0, 0);
        tvName.setLayoutParams(nameParams);
        appItem.addView(tvName);

        // 点击逻辑
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

        // 长按遥控器 OK / 确认键进行更换应用逻辑
        appItem.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                openAppSelectionDialog(slotIndex);
                return true;
            }
        });

        // 聚焦动画
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

    // 全部应用按纽
    private View createAllAppsButton() {
        LinearLayout appItem = new LinearLayout(this);
        appItem.setOrientation(LinearLayout.VERTICAL);
        appItem.setGravity(Gravity.CENTER_HORIZONTAL);
        appItem.setFocusable(true);
        appItem.setClickable(true);
        appItem.setBackgroundResource(R.drawable.app_item_selector);
        
        int itemWidthPx = (int) (110 * getResources().getDisplayMetrics().density);
        LinearLayout.LayoutParams itemParams = new LinearLayout.LayoutParams(itemWidthPx, ViewGroup.LayoutParams.WRAP_CONTENT);
        itemParams.setMargins(10, 10, 10, 10);
        appItem.setLayoutParams(itemParams);
        appItem.setPadding(10, 15, 10, 15);

        ImageView ivIcon = new ImageView(this);
        ivIcon.setImageResource(android.R.drawable.ic_menu_manage); // 默认灰色齿轮/网格图标
        ivIcon.setColorFilter(Color.parseColor("#888899"));
        int iconSizePx = (int) (64 * getResources().getDisplayMetrics().density);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(iconSizePx, iconSizePx);
        ivIcon.setLayoutParams(iconParams);
        appItem.addView(ivIcon);

        TextView tvName = new TextView(this);
        tvName.setText("全部应用");
        tvName.setTextColor(Color.parseColor("#9ca3af"));
        tvName.setTextSize(12);
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

    // 弹出自定义更换的 AlertDialog 应用选择框
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
                
                // 写入 Preference 存盘
                SharedPreferences.Editor editor = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit();
                editor.putString(KEY_PINNED_APPS_PREFIX + slotIndex, selectedApp.packageName);
                editor.apply();

                // 重新渲染底栏
                renderAppShelf();
                Toast.makeText(MainActivity.this, "已成功固定: " + selectedApp.label, Toast.LENGTH_SHORT).show();
            }
        });
        builder.create().show();
    }

    // 弹出“全部应用”全屏网格列表直接启动
    private void openAllAppsSelectorDialog() {
        if (allInstalledApps.isEmpty()) {
            Toast.makeText(this, "未找到已安装应用", Toast.LENGTH_SHORT).show();
            return;
        }

        // 创建一个全屏无边框的 Dialog
        final android.app.Dialog dialog = new android.app.Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        dialog.setContentView(R.layout.dialog_all_apps);
        
        android.widget.GridView gridView = (android.widget.GridView) dialog.findViewById(R.id.apps_grid);
        
        // 设置适配器把应用数据放入 Grid 列表中
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
                
                // 动态构建单个应用项目的布局，直接重用主页的卡片生成逻辑
                // 开启 focusable，并移去 duplicateParentState 以允许独立焦点变色
                LinearLayout itemView = new LinearLayout(MainActivity.this);
                itemView.setOrientation(LinearLayout.VERTICAL);
                itemView.setGravity(Gravity.CENTER_HORIZONTAL);
                itemView.setFocusable(true); 
                itemView.setBackgroundResource(R.drawable.app_item_selector);
                
                int itemWidthPx = (int) (110 * getResources().getDisplayMetrics().density);
                itemView.setLayoutParams(new android.widget.AbsListView.LayoutParams(itemWidthPx, ViewGroup.LayoutParams.WRAP_CONTENT));
                itemView.setPadding(10, 15, 10, 15);

                // 图标
                ImageView ivIcon = new ImageView(MainActivity.this);
                ivIcon.setImageDrawable(app.icon);
                int iconSizePx = (int) (64 * getResources().getDisplayMetrics().density);
                LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(iconSizePx, iconSizePx);
                ivIcon.setLayoutParams(iconParams);
                itemView.addView(ivIcon);

                // 名称
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

                // 焦点高亮改变字体颜色 (需要手动控制一下 GridView 中文字变白，避免受主题颜色影响)
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

        // 统一在 GridView 的 onItemClickListener 中处理启动，避免冲突
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

    // 广播反射跳转信号源
    private void setWhaleyInputSource(String sourceEnumName) {
        try {
            Context tvPlayerContext = createPackageContext("com.whaley.tv.tvplayer.ui",
                    Context.CONTEXT_INCLUDE_CODE | Context.CONTEXT_IGNORE_SECURITY);
            ClassLoader classLoader = tvPlayerContext.getClassLoader();
            Class<?> managerClass = classLoader.loadClass("tv.whaley.api.manager.WtvApiInputSourceManger");
            Method getInstanceMethod = managerClass.getMethod("getInstance");
            Object managerInstance = getInstanceMethod.invoke(null);
            Class<?> enumClass = classLoader.loadClass("tv.whaley.api.mode.EnumWtvInputSource");
            Object enumVal = Enum.valueOf((Class<Enum>) enumClass, sourceEnumName);
            Method setCurrentInputSourceMethod = managerClass.getMethod("setCurrentInputSource", enumClass);
            setCurrentInputSourceMethod.invoke(managerInstance, enumVal);
        } catch (Exception e) {
            e.printStackTrace();
        }
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
        
        // 关键修复：如果在桌面按返回键，直接拦截并消费，防止桌面 Activity 退出或重启闪烁
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            return true; 
        }
        
        return super.onKeyDown(keyCode, event);
    }

    // 应用信息临时实体模型
    public static class AppInfoModel {
        public String packageName;
        public String label;
        public Drawable icon;
    }
}
