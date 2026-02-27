package cn.modificator.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.admin.DevicePolicyManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

import cn.modificator.launcher.ftpservice.FTPReceiver;
import cn.modificator.launcher.ftpservice.FTPService;
import cn.modificator.launcher.model.AdminReceiver;
import cn.modificator.launcher.model.AppDataCenter;
import cn.modificator.launcher.model.HomeEntranceService;
import cn.modificator.launcher.model.WifiControl;
import cn.modificator.launcher.widgets.BatteryView;
import cn.modificator.launcher.widgets.EInkLauncherView;

/**
 * 主界面 Activity - E-Ink 墨水屏桌面启动器。
 */
public class Launcher extends Activity {

  /** 广播 Action：设置页通过广播通知主界面刷新 */
  public static final String ACTION_LAUNCHER_UPDATE = "launcherReceiver";

  private static final int REQUEST_DEVICE_ADMIN = 10001;

  // ---- Views ----
  private EInkLauncherView launcherView;
  private TextView pageStatus;
  private BatteryView batteryProgress;
  private TextView batteryStatus;
  private TextView textClock;

  // ---- Data ----
  private AppDataCenter dataCenter;
  private Config config;
  private Calendar calendar;
  private boolean isChina = true;

  // ---- Device Admin ----
  private DevicePolicyManager policyManager;

  // ---- Receivers ----
  private LauncherUpdateReceiver updateReceiver;
  private FTPReceiver ftpReceiver = new FTPReceiver();
  private boolean batteryRegistered;
  private boolean timeRegistered;
  private boolean usbRegistered;
  private boolean ftpRegistered;

  private final BroadcastReceiver timeReceiver = new BroadcastReceiver() {
    @Override
    public void onReceive(Context context, Intent intent) {
      updateTimeShow();
    }
  };

  private final BroadcastReceiver batteryReceiver = new BroadcastReceiver() {
    @Override
    public void onReceive(Context context, Intent intent) {
      handleBatteryChanged(intent);
    }
  };

  private final BroadcastReceiver appChangeReceiver = new BroadcastReceiver() {
    @Override
    public void onReceive(Context context, Intent intent) {
      launcherView.clearAppCache();
      dataCenter.refreshAppList(launcherView.isDelete());
    }
  };

  private final BroadcastReceiver usbReceiver = new BroadcastReceiver() {
    @Override
    public void onReceive(Context context, Intent intent) {
      String action = intent.getAction();
      if (Intent.ACTION_MEDIA_MOUNTED.equals(action)) {
        launcherView.markIconCacheDirty();
        launcherView.refreshReplaceIcon();
      }
    }
  };

  // =========================================================================
  // Lifecycle
  // =========================================================================

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.launcher_activity);

    config = new Config(this);
    WifiControl.init(this);
    applyStatusBarVisibility();

    isChina = getResources().getConfiguration().locale.getCountry().equals("CN");

    initViews();
    registerStaticReceivers();
    checkLaunchHomeNotification();
  }

  @Override
  protected void onResume() {
    super.onResume();
    registerDynamicReceivers();
    if (launcherView != null) {
      launcherView.refreshReplaceIcon();
    }
  }

  @Override
  protected void onPause() {
    super.onPause();
    unregisterDynamicReceivers();
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
    unregisterDynamicReceivers();
    unregisterReceiver(updateReceiver);
    unregisterReceiver(appChangeReceiver);
  }

  // =========================================================================
  // View 初始化
  // =========================================================================

  private void initViews() {
    policyManager = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);

    launcherView = findViewById(R.id.mList);
    pageStatus = findViewById(R.id.pageStatus);
    batteryProgress = findViewById(R.id.batteryProgress);
    batteryStatus = findViewById(R.id.batteryStatus);
    textClock = findViewById(R.id.textClock);

    ImageView settingIcon = findViewById(R.id.toSetting);
    settingIcon.setImageDrawable(
        Utils.tintDrawable(getResources().getDrawable(R.drawable.navibar_icon_settings_highlight),
            ColorStateList.valueOf(0xff000000)));

    // 配置 LauncherView
    launcherView.setHideAppPkg(config.getHideApps());
    launcherView.setHideDivider(config.isHideDivider());
    launcherView.setFontSize(config.getFontSize());

    // 初始化数据中心
    dataCenter = new AppDataCenter(this);
    dataCenter.setHideApps(config.getHideApps());
    dataCenter.setPageStatus(pageStatus);
    dataCenter.setLauncherView(launcherView);

    // 加载之前保存的桌面布局（批量更新避免双重重建）
    int savedCol = config.getColNum();
    int savedRow = config.getRowNum();
    launcherView.setGridSize(savedCol, savedRow);
    dataCenter.setGridSize(savedCol, savedRow);

    // 翻页按钮
    findViewById(R.id.lastPage).setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        dataCenter.showLastPage();
      }
    });
    findViewById(R.id.nextPage).setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        dataCenter.showNextPage();
      }
    });

    // 设置按钮
    findViewById(R.id.toSetting).setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        getFragmentManager().beginTransaction()
            .replace(android.R.id.content, new SettingFragment())
            .addToBackStack(null)
            .commit();
      }
    });

    // 管理完成按钮
    findViewById(R.id.deleteFinish).setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        launcherView.setDelete(false);
        dataCenter.refreshAppList();
        config.setHideApps(dataCenter.getHideApps());
        v.setVisibility(View.GONE);
      }
    });

    // 滑动翻页
    launcherView.setTouchListener(new EInkLauncherView.TouchListener() {
      @Override
      public void toNext() {
        dataCenter.showNextPage();
      }

      @Override
      public void toLast() {
        dataCenter.showLastPage();
      }
    });

    // 时间显示
    calendar = Calendar.getInstance();
    updateTimeShow();

    // 检测是否是系统应用
    try {
      launcherView.setSystemApp(!isUserApp(getPackageManager().getPackageInfo(getPackageName(), 0)));
    } catch (PackageManager.NameNotFoundException e) {
      e.printStackTrace();
    }
  }

  // =========================================================================
  // 布局更新
  // =========================================================================

  private void updateRowNum(int rowNum) {
    launcherView.setRowNum(rowNum);
    dataCenter.setRowNum(rowNum);
    config.setRowNum(rowNum);
  }

  private void updateColNum(int colNum) {
    launcherView.setColNum(colNum);
    dataCenter.setColNum(colNum);
    config.setColNum(colNum);
  }

  // =========================================================================
  // 时间显示
  // =========================================================================

  private void updateTimeShow() {
    if (textClock == null || calendar == null) return;

    boolean is24Hour = DateFormat.is24HourFormat(this);
    calendar.setTimeInMillis(System.currentTimeMillis());

    StringBuilder sb = new StringBuilder("yyyy-MM-dd ");
    if (!is24Hour && isChina) {
      sb.append(Utils.getAMPMCNString(calendar.get(Calendar.HOUR), calendar.get(Calendar.AM_PM)));
    }
    sb.append(is24Hour ? "HH:mm" : "hh:mm");
    if (!is24Hour && !isChina) {
      sb.append(" a");
    }
    sb.append(" EEEE");

    textClock.setText(new SimpleDateFormat(sb.toString(), Locale.getDefault()).format(calendar.getTime()));
  }

  // =========================================================================
  // 电池信息
  // =========================================================================

  private void handleBatteryChanged(Intent intent) {
    int rawLevel = intent.getIntExtra("level", -1);
    int scale = intent.getIntExtra("scale", -1);
    int status = intent.getIntExtra("status", -1);
    int health = intent.getIntExtra("health", -1);

    int level = (rawLevel >= 0 && scale > 0) ? (rawLevel * 100) / scale : -1;
    batteryProgress.setProgress(level);
    batteryStatus.setVisibility(View.VISIBLE);

    if (BatteryManager.BATTERY_HEALTH_OVERHEAT == health) {
      batteryStatus.setText(R.string.battery_heat);
      return;
    }

    switch (status) {
      case BatteryManager.BATTERY_STATUS_UNKNOWN:
        batteryStatus.setText(R.string.battery_unknown);
        break;
      case BatteryManager.BATTERY_STATUS_CHARGING:
        batteryStatus.setText(R.string.battery_charging);
        break;
      case BatteryManager.BATTERY_STATUS_DISCHARGING:
      case BatteryManager.BATTERY_STATUS_NOT_CHARGING:
        if (level < 15) {
          batteryStatus.setText(R.string.battery_low);
        } else {
          batteryStatus.setVisibility(View.GONE);
        }
        break;
      case BatteryManager.BATTERY_STATUS_FULL:
        batteryStatus.setText(R.string.battery_full);
        break;
      default:
        batteryStatus.setText(R.string.battery_wtf);
        break;
    }
  }

  // =========================================================================
  // 广播注册/注销
  // =========================================================================

  /** 注册生命周期不变的静态广播 */
  private void registerStaticReceivers() {
    // Launcher 设置更新广播
    updateReceiver = new LauncherUpdateReceiver();
    IntentFilter launcherFilter = new IntentFilter(ACTION_LAUNCHER_UPDATE);
    registerReceiver(updateReceiver, launcherFilter);

    // 应用安装/卸载广播
    IntentFilter appChangeFilter = new IntentFilter();
    appChangeFilter.addAction(Intent.ACTION_PACKAGE_ADDED);
    appChangeFilter.addAction(Intent.ACTION_PACKAGE_REMOVED);
    appChangeFilter.addAction(Intent.ACTION_PACKAGE_REPLACED);
    appChangeFilter.addDataScheme("package");
    registerReceiver(appChangeReceiver, appChangeFilter);
  }

  /** 注册跟随 onResume/onPause 的动态广播 */
  private void registerDynamicReceivers() {
    if (!batteryRegistered) {
      registerReceiver(batteryReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
      batteryRegistered = true;
    }
    if (!timeRegistered) {
      registerReceiver(timeReceiver, new IntentFilter(Intent.ACTION_TIME_TICK));
      timeRegistered = true;
    }
    updateTimeShow();
    if (!usbRegistered) {
      registerUsbReceiver();
    }
    if (!ftpRegistered) {
      IntentFilter ftpFilter = new IntentFilter(FTPService.ACTION_START_FTPSERVER);
      ftpFilter.addAction(FTPService.ACTION_STOP_FTPSERVER);
      registerReceiver(ftpReceiver, ftpFilter);
      ftpRegistered = true;
    }
  }

  private void unregisterDynamicReceivers() {
    if (batteryRegistered) {
      unregisterReceiver(batteryReceiver);
      batteryRegistered = false;
    }
    if (timeRegistered) {
      unregisterReceiver(timeReceiver);
      timeRegistered = false;
    }
    if (usbRegistered) {
      unregisterReceiver(usbReceiver);
      usbRegistered = false;
    }
    if (ftpRegistered) {
      unregisterReceiver(ftpReceiver);
      ftpRegistered = false;
    }
  }

  private void registerUsbReceiver() {
    IntentFilter usbFilter = new IntentFilter();
    usbFilter.addAction(Intent.ACTION_UMS_DISCONNECTED);
    usbFilter.addAction(Intent.ACTION_MEDIA_MOUNTED);
    usbFilter.addAction(Intent.ACTION_MEDIA_UNMOUNTED);
    usbFilter.addAction(Intent.ACTION_MEDIA_REMOVED);
    usbFilter.addDataScheme("file");
    registerReceiver(usbReceiver, usbFilter);
    usbRegistered = true;
  }

  // =========================================================================
  // 设置更新广播接收器
  // =========================================================================

  private class LauncherUpdateReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
      Bundle bundle = intent.getExtras();
      if (bundle == null) return;

      if (bundle.containsKey(Config.KEY_ROW_NUM)) {
        updateRowNum(bundle.getInt(Config.KEY_ROW_NUM));
      } else if (bundle.containsKey(Config.KEY_COL_NUM)) {
        updateColNum(bundle.getInt(Config.KEY_COL_NUM));
      } else if (bundle.containsKey(Config.KEY_HIDE_APPS)) {
        launcherView.setDelete(true);
        dataCenter.refreshAppList(true);
        findViewById(R.id.deleteFinish).setVisibility(View.VISIBLE);
      } else if (bundle.containsKey(Config.KEY_FONT_SIZE)) {
        launcherView.setFontSize(bundle.getFloat(Config.KEY_FONT_SIZE));
      } else if (bundle.containsKey(Config.KEY_HIDE_DIVIDER)) {
        boolean hide = bundle.getBoolean(Config.KEY_HIDE_DIVIDER);
        launcherView.setHideDivider(hide);
        config.setHideDivider(hide);
      } else if (bundle.containsKey(Config.KEY_SHOW_STATUS_BAR)) {
        config.setShowStatusBar(bundle.getBoolean(Config.KEY_SHOW_STATUS_BAR));
        applyStatusBarVisibility();
      } else if (bundle.containsKey(Config.KEY_SHOW_CUSTOM_ICON)) {
        config.setShowCustomIcon(bundle.getBoolean(Config.KEY_SHOW_CUSTOM_ICON));
        launcherView.markIconCacheDirty();
        launcherView.refreshReplaceIcon();
      } else if (bundle.containsKey(Config.KEY_APP_NAME_LINES)) {
        int lines = bundle.getInt(Config.KEY_APP_NAME_LINES);
        if (lines == 3) lines = Integer.MAX_VALUE;
        config.setAppNameLines(lines);
        launcherView.updateAppNameLines();
      }
    }
  }

  // =========================================================================
  // 按键处理
  // =========================================================================

  @Override
  public boolean onKeyUp(int keyCode, KeyEvent event) {
    if (keyCode == KeyEvent.KEYCODE_PAGE_UP) {
      dataCenter.showLastPage();
      return true;
    } else if (keyCode == KeyEvent.KEYCODE_PAGE_DOWN) {
      dataCenter.showNextPage();
      return true;
    } else if (keyCode == KeyEvent.KEYCODE_BACK) {
      return true;
    }
    return super.onKeyUp(keyCode, event);
  }

  @Override
  public boolean onKeyDown(int keyCode, KeyEvent event) {
    if (keyCode == KeyEvent.KEYCODE_BACK && getFragmentManager().getBackStackEntryCount() == 0) {
      return true;
    }
    return super.onKeyDown(keyCode, event);
  }

  @Override
  public void onBackPressed() {
    if (getFragmentManager().getBackStackEntryCount() > 0) {
      super.onBackPressed();
      config.setFontSize(config.getFontSize());
    }
  }

  // =========================================================================
  // 锁屏
  // =========================================================================

  public void lockScreen() {
    try {
      if (policyManager.isAdminActive(new ComponentName(this, AdminReceiver.class))) {
        policyManager.lockNow();
      } else {
        requestDeviceAdmin();
      }
    } catch (Exception e) {
      showDeviceAdminDialog();
    }
  }

  private void requestDeviceAdmin() {
    Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
    intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, new ComponentName(this, AdminReceiver.class));
    intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "E-Ink Launcher 获取锁屏权限");
    startActivity(intent);
  }

  private void showDeviceAdminDialog() {
    new AlertDialog.Builder(this)
        .setTitle(R.string.launch_failed)
        .setMessage(R.string.launch_devicemanager_failed)
        .setPositiveButton(R.string.launch_devicemanager, new DialogInterface.OnClickListener() {
          @Override
          public void onClick(DialogInterface dialog, int which) {
            try {
              Intent intent = Intent.parseUri(
                  "intent:#Intent;component=com.android.settings/.DeviceAdminSettings;end",
                  Intent.URI_INTENT_SCHEME);
              intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
              startActivity(intent);
            } catch (Exception e) {
              e.printStackTrace();
            }
          }
        })
        .setNegativeButton(R.string.dialog_cancel, null)
        .show();
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    if (resultCode == RESULT_OK && requestCode == REQUEST_DEVICE_ADMIN) {
      policyManager.lockNow();
    }
  }

  // =========================================================================
  // 状态栏/系统应用判断/通知栏
  // =========================================================================

  public void applyStatusBarVisibility() {
    int flags = WindowManager.LayoutParams.FLAG_FULLSCREEN;
    if (config.isShowStatusBar()) {
      getWindow().setFlags(flags, flags);
    } else {
      getWindow().clearFlags(flags);
    }
  }

  public boolean isUserApp(PackageInfo pInfo) {
    return (pInfo.applicationInfo.flags & (ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) == 0;
  }

  private void checkLaunchHomeNotification() {
    if (!TextUtils.equals(Build.DEVICE, "virgo-perf1")) return;
    Intent service = new Intent(this, HomeEntranceService.class);
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      startForegroundService(service);
    } else {
      startService(service);
    }
  }
}
