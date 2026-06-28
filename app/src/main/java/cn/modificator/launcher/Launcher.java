package cn.modificator.launcher;

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
import android.content.pm.ResolveInfo;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.Set;

import cn.modificator.launcher.ftpservice.FTPReceiver;
import cn.modificator.launcher.ftpservice.FTPService;
import cn.modificator.launcher.model.AdminReceiver;
import cn.modificator.launcher.model.AppDataCenter;
import cn.modificator.launcher.model.HomeEntranceService;
import cn.modificator.launcher.model.IconCache;
import cn.modificator.launcher.model.WifiControl;
import cn.modificator.launcher.widgets.AppItemBinder;
import cn.modificator.launcher.widgets.BatteryView;
import cn.modificator.launcher.widgets.EInkLauncherView;
import cn.modificator.launcher.widgets.LauncherAdapter;

/**
 * 主界面 Activity - E-Ink 墨水屏桌面启动器。
 */
public class Launcher extends AppCompatActivity
    implements AppItemBinder.Callback, EInkLauncherView.OnPageChangeListener,
    SettingFragment.OnSettingChangeListener {

  private static final int REQUEST_DEVICE_ADMIN = 10001;

  // ---- Views ----
  private EInkLauncherView launcherView;
  private TextView pageStatus;
  private BatteryView batteryProgress;
  private TextView batteryStatus;
  private TextView textClock;
  private ImageView settingIcon;

  // ---- Data ----
  private AppDataCenter dataCenter;
  private Config config;
  private Calendar calendar;
  private boolean isChina = true;
  private IconCache iconCache;
  private LauncherAdapter adapter;
  private AppItemBinder binder;
  private boolean isSystemApp = false;
  private int focusArea = FOCUS_NONE;
  private int lastGridIndex = 0;
  private boolean confirmLongPressed = false;

  // ---- Device Admin ----
  private DevicePolicyManager policyManager;

  private static final int FOCUS_NONE = 0;
  private static final int FOCUS_GRID = 1;
  private static final int FOCUS_BATTERY = 2;
  private static final int FOCUS_SETTING = 3;

  // ---- Receivers ----
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
      iconCache.clearAppCache();
      dataCenter.refreshAppList(binder.isDelete());
    }
  };

  private final BroadcastReceiver usbReceiver = new BroadcastReceiver() {
    @Override
    public void onReceive(Context context, Intent intent) {
      String action = intent.getAction();
      if (Intent.ACTION_MEDIA_MOUNTED.equals(action)) {
        iconCache.markDirty();
        refreshIcons();
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

    // 主题切换
    int themeMode = config.getThemeMode();
    setThemeMode(themeMode);

    WifiControl.init(this);
    applyStatusBarVisibility();

    isChina = getResources().getConfiguration().locale.getCountry().equals("CN");

    initViews();
    registerStaticReceivers();
    checkLaunchHomeNotification();
  }

  private void setThemeMode(int themeMode) {
    Log.d("zyyme设置themeMode", String.valueOf(themeMode));
    if (themeMode == 0) {
      AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
    } else if (themeMode == 1) {
      AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
    } else if (themeMode == 2) {
      AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
    } else if (themeMode == 3) {
      AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
      findViewById(R.id.launcherBg).post(new Runnable() {
        @Override
        public void run() {
          findViewById(R.id.launcherBg).setBackgroundColor(0xffffffff);
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setStatusBarColor(0xffffffff);
            getWindow().setNavigationBarColor(0xffffffff);
          }
        }
      });
    } else if (themeMode == 4) {
      AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
      findViewById(R.id.launcherBg).post(new Runnable() {
        @Override
        public void run() {
          findViewById(R.id.launcherBg).setBackgroundColor(0xff000000);
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setStatusBarColor(0xff000000);
            getWindow().setNavigationBarColor(0xff000000);
          }
        }
      });
    }
  }

  @Override
  protected void onResume() {
    super.onResume();
    registerDynamicReceivers();
    refreshIcons();
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
    unregisterReceiver(appChangeReceiver);
  }

  @Override
  public boolean dispatchTouchEvent(MotionEvent ev) {
    if (ev.getActionMasked() == MotionEvent.ACTION_DOWN) {
      clearKeyboardFocus();
    }
    return super.dispatchTouchEvent(ev);
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

    settingIcon = findViewById(R.id.toSetting);
    settingIcon.setImageDrawable(
        Utils.tintDrawable(getResources().getDrawable(R.drawable.navibar_icon_settings_highlight),
            ColorStateList.valueOf(getResources().getColor(R.color.textColor))));

    // 配置 Binder、Adapter、View
    iconCache = new IconCache();
    binder = new AppItemBinder(getPackageManager());
    binder.setCallback(this);
    binder.setIconCache(iconCache);
    binder.setHideAppPkg(config.getHideApps());
    adapter = new LauncherAdapter();
    adapter.setBinder(binder);
    adapter.setFontSize(config.getFontSize());
    adapter.setAppNameLines(config.getAppNameLines());
    launcherView.setAdapter(adapter);
    launcherView.setOnPageChangeListener(this);

    // 初始化数据中心
    dataCenter = new AppDataCenter(this);
    dataCenter.setSortMode(config.getSortMode());
    dataCenter.setPageStatus(pageStatus);

    // 一次性配置网格参数，避免多次重建
    launcherView.configure(config.getColNum(), config.getRowNum(), config.isHideDivider());
    dataCenter.setGridSize(config.getColNum(), config.getRowNum());
    dataCenter.setAdapter(adapter);
    dataCenter.setHideApps(config.getHideApps());

    // 翻页按钮
    findViewById(R.id.lastPage).setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        showLastPageAndHideSelection();
      }
    });
    findViewById(R.id.nextPage).setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        showNextPageAndHideSelection();
      }
    });

    // 设置按钮
    findViewById(R.id.toSetting).setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        clearKeyboardFocus();
        openSettingsFragment();
      }
    });

    // 管理完成按钮
    findViewById(R.id.deleteFinish).setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        binder.setDelete(false);
        dataCenter.refreshAppList();
        config.setHideApps(dataCenter.getHideApps());
        v.setVisibility(View.GONE);
      }
    });
    // 电池点击打开设置
    batteryProgress.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        clearKeyboardFocus();
        openBatterySettings();
      }
    });

    // 时间显示
    calendar = Calendar.getInstance();
    updateTimeShow();

    // 检测系统应用
    try {
      isSystemApp = !isUserApp(getPackageManager().getPackageInfo(getPackageName(), 0));
    } catch (PackageManager.NameNotFoundException e) {
      e.printStackTrace();
    }
  }

  // =========================================================================
  // SettingFragment.OnSettingChangeListener 实现
  // =========================================================================

  @Override
  public void onRowNumChanged(int rowNum) {
    launcherView.setRowNum(rowNum);
    dataCenter.setRowNum(rowNum);
  }

  @Override
  public void onColNumChanged(int colNum) {
    launcherView.setColNum(colNum);
    dataCenter.setColNum(colNum);
  }

  @Override
  public void onFontSizeChanged(float size) {
    adapter.setFontSize(size);
  }

  @Override
  public void onAppNameLinesChanged(int lines) {
    adapter.setAppNameLines(lines);
  }

  @Override
  public void onHideDividerChanged(boolean hide) {
    launcherView.setHideDivider(hide);
  }

  @Override
  public void onShowStatusBarChanged(boolean show) {
    applyStatusBarVisibility();
    recreate();
  }

  @Override
  public void onShowCustomIconChanged(boolean show) {
    iconCache.markDirty();
    refreshIcons();
  }

  @Override
  public void onEnterManageMode() {
    binder.setDelete(true);
    dataCenter.refreshAppList(true);
    findViewById(R.id.deleteFinish).setVisibility(View.VISIBLE);
  }

  @Override
  public void onSortModeChanged(int mode) {
    dataCenter.setSortMode(mode);
    dataCenter.refreshAppList(binder.isDelete());
  }

  @Override
  public void onThemeModeChanged(int mode) {
    if (mode == config.getThemeMode()) {
      return;
    }
    config.setThemeMode(mode);
    setThemeMode(mode);
    recreate();
  }

  // =========================================================================
  // 布局更新
  // =========================================================================

  private void refreshIcons() {
    if (adapter == null || iconCache == null) return;
    iconCache.refreshCustomIcons(getExternalCacheDir() != null, config.isShowCustomIcon());
    adapter.refreshDisplay();
  }

  // =========================================================================
  // AppItemBinder.Callback 实现
  // =========================================================================

  @Override
  public void onItemClick(ResolveInfo info) {
    String pkgName = info.activityInfo.packageName;

    if (AppDataCenter.LOCK_PACKAGE_NAME.equals(pkgName)) {
      lockScreen();
    } else if (AppDataCenter.WIFI_PACKAGE_NAME.equals(pkgName)) {
      WifiControl.onClickWifiItem();
    } else if (AppDataCenter.CLEAR_PACKAGE_NAME.equals(pkgName)) {
      cleanMemory();
    } else {
      ComponentName comp = new ComponentName(info.activityInfo.packageName, info.activityInfo.name);
      Intent intent = new Intent(Intent.ACTION_MAIN);
      intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
      intent.addCategory(Intent.CATEGORY_LAUNCHER);
      intent.setComponent(comp);
      startActivity(intent);
    }
  }

  @Override
  public void onItemLongClick(View anchor, ResolveInfo info) {
    String packageName = info.activityInfo.packageName;

    if (AppDataCenter.LOCK_PACKAGE_NAME.equals(packageName)) {
      showPowerMenu();
    } else if (AppDataCenter.WIFI_PACKAGE_NAME.equals(packageName)) {
      WifiControl.onLongClickWifiItem();
    } else if (AppDataCenter.CLEAR_PACKAGE_NAME.equals(packageName)) {
      // 内存清理图标，不做任何操作
    } else {
      showAppInfoDialog(info, packageName);
    }
  }

  @Override
  public void onItemDeleteClick(ResolveInfo info) {
    Intent deleteIntent = new Intent(Intent.ACTION_DELETE,
        Uri.parse("package:" + info.activityInfo.packageName));
    startActivity(deleteIntent);
  }

  @Override
  public void onItemHideToggle(String packageName, boolean hidden) {
    // 管理模式下的隐藏切换仅更新 UI 状态，"完成" 按钮处理持久化
  }

  // =========================================================================
  // EInkLauncherView.OnPageChangeListener 实现
  // =========================================================================

  @Override
  public void onPageNext() {
    showNextPageAndHideSelection();
  }

  @Override
  public void onPagePrev() {
    showLastPageAndHideSelection();
  }

  private void showPowerMenu() {
    if (!isSystemApp) return;
    new AlertDialog.Builder(this)
        .setTitle(R.string.power_title)
        .setItems(R.array.power_menu, new DialogInterface.OnClickListener() {
          @Override
          public void onClick(DialogInterface dialog, int which) {
            if (which == 0) {
              Intent intent = new Intent("android.intent.action.ACTION_REQUEST_SHUTDOWN");
              intent.putExtra("android.intent.extra.KEY_CONFIRM", false);
              intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
              startActivity(intent);
            } else {
              PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
              pm.reboot("重启");
            }
          }
        })
        .setPositiveButton(R.string.dialog_cancel, null)
        .show();
  }

  private void showAppInfoDialog(ResolveInfo info, final String packageName) {
    new AlertDialog.Builder(this)
        .setIcon(iconCache.getIcon(packageName, info, getPackageManager()))
        .setTitle(iconCache.getLabel(packageName, info, getPackageManager()))
        .setMessage(getString(R.string.dialog_pkg_name, packageName))
        .setPositiveButton(R.string.dialog_cancel, null)
        .setNeutralButton(R.string.dialog_hide, new DialogInterface.OnClickListener() {
          @Override
          public void onClick(DialogInterface dialog, int which) {
            Set<String> hideApps = binder.getHideAppPkg();
            if (!hideApps.add(packageName)) {
              hideApps.remove(packageName);
            }
            dataCenter.refreshAppList();
          }
        })
        .setNegativeButton(R.string.dialog_uninstall, new DialogInterface.OnClickListener() {
          @Override
          public void onClick(DialogInterface dialog, int which) {
            Intent deleteIntent = new Intent(Intent.ACTION_DELETE,
                Uri.parse("package:" + packageName));
            startActivity(deleteIntent);
          }
        })
        .show();
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
    // 应用安装/卸载广播
    IntentFilter appChangeFilter = new IntentFilter();
    appChangeFilter.addAction(Intent.ACTION_PACKAGE_ADDED);
    appChangeFilter.addAction(Intent.ACTION_PACKAGE_REMOVED);
    appChangeFilter.addAction(Intent.ACTION_PACKAGE_REPLACED);
    appChangeFilter.addDataScheme("package");
    registerCompatReceiver(appChangeReceiver, appChangeFilter);
  }

  /** 注册跟随 onResume/onPause 的动态广播 */
  private void registerDynamicReceivers() {
    if (!batteryRegistered) {
      registerCompatReceiver(batteryReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
      batteryRegistered = true;
    }
    if (!timeRegistered) {
      registerCompatReceiver(timeReceiver, new IntentFilter(Intent.ACTION_TIME_TICK));
      timeRegistered = true;
    }
    updateTimeShow();
    if (!usbRegistered) {
      registerUsbReceiver();
    }
    if (!ftpRegistered) {
      IntentFilter ftpFilter = new IntentFilter(FTPService.ACTION_START_FTPSERVER);
      ftpFilter.addAction(FTPService.ACTION_STOP_FTPSERVER);
      registerCompatReceiver(ftpReceiver, ftpFilter);
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
    registerCompatReceiver(usbReceiver, usbFilter);
    usbRegistered = true;
  }

  private void registerCompatReceiver(BroadcastReceiver receiver, IntentFilter filter) {
    Utils.registerReceiverCompat(this, receiver, filter);
  }

  // =========================================================================
  // 按键处理
  // =========================================================================

  @Override
  public boolean onKeyUp(int keyCode, KeyEvent event) {
    if (getFragmentManager().getBackStackEntryCount() != 0 || !isLauncherNavigationKey(keyCode)) {
      return super.onKeyUp(keyCode, event);
    }
    if (isConfirmKey(keyCode)) {
      if (!confirmLongPressed) {
        performFocusedClick();
      }
      confirmLongPressed = false;
      return true;
    }
    return true;
  }

  @Override
  public boolean onKeyDown(int keyCode, KeyEvent event) {
    boolean hasFragment = getFragmentManager().getBackStackEntryCount() > 0;
    if (hasFragment) {
      if (keyCode == KeyEvent.KEYCODE_BACK) {
        onBackPressed();
        return true;
      }
      return super.onKeyDown(keyCode, event);
    }

    if (keyCode == KeyEvent.KEYCODE_BACK) {
      showFirstPageAndKeepFocusState();
      return true;
    } else if (keyCode == KeyEvent.KEYCODE_PAGE_UP) {
      showLastPageAndSelectFirst();
      return true;
    } else if (keyCode == KeyEvent.KEYCODE_PAGE_DOWN) {
      showNextPageAndSelectFirst();
      return true;
    } else if (isConfirmKey(keyCode)) {
      event.startTracking();
      return true;
    } else if (isDirectionKey(keyCode)) {
      moveSelectionByKey(keyCode);
      return true;
    }
    return super.onKeyDown(keyCode, event);
  }

  @Override
  public boolean onKeyLongPress(int keyCode, KeyEvent event) {
    if (getFragmentManager().getBackStackEntryCount() == 0 && isConfirmKey(keyCode)) {
      confirmLongPressed = true;
      return performFocusedLongClick();
    }
    return super.onKeyLongPress(keyCode, event);
  }

  private boolean isLauncherNavigationKey(int keyCode) {
    return keyCode == KeyEvent.KEYCODE_BACK
        || keyCode == KeyEvent.KEYCODE_PAGE_UP
        || keyCode == KeyEvent.KEYCODE_PAGE_DOWN
        || isConfirmKey(keyCode)
        || isDirectionKey(keyCode);
  }

  private boolean isConfirmKey(int keyCode) {
    return keyCode == KeyEvent.KEYCODE_DPAD_CENTER
        || keyCode == KeyEvent.KEYCODE_ENTER
        || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER;
  }

  private boolean isDirectionKey(int keyCode) {
    return keyCode == KeyEvent.KEYCODE_DPAD_LEFT
        || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT
        || keyCode == KeyEvent.KEYCODE_DPAD_UP
        || keyCode == KeyEvent.KEYCODE_DPAD_DOWN;
  }

  private void moveSelectionByKey(int keyCode) {
    if (focusArea == FOCUS_NONE) {
      focusGrid(launcherView.getSelectedIndex() < 0 ? 0 : launcherView.getSelectedIndex());
      return;
    }

    if (focusArea == FOCUS_BATTERY || focusArea == FOCUS_SETTING) {
      moveFooterFocus(keyCode);
      return;
    }

    if (keyCode == KeyEvent.KEYCODE_DPAD_UP && isOnTopRow()) {
      expandNotifications();
      return;
    }

    if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN && isOnBottomRow()) {
      lastGridIndex = launcherView.getSelectedIndex();
      focusFooter(FOCUS_BATTERY);
      return;
    }

    if (launcherView.moveSelection(keyCode)) return;

    int selectedIndex = launcherView.getSelectedIndex();
    if (selectedIndex < 0) return;

    int colNum = config.getColNum();
    if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && selectedIndex % colNum == 0) {
      if (dataCenter.showLastPage()) {
        launcherView.setSelectedIndex(launcherView.getCrossPageTargetIndex(selectedIndex, false));
        focusGrid(launcherView.getSelectedIndex());
      }
    } else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && selectedIndex % colNum == colNum - 1) {
      if (dataCenter.showNextPage()) {
        launcherView.setSelectedIndex(launcherView.getCrossPageTargetIndex(selectedIndex, true));
        focusGrid(launcherView.getSelectedIndex());
      }
    }
  }

  private void moveFooterFocus(int keyCode) {
    if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
      focusGrid(lastGridIndex);
    } else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && focusArea == FOCUS_BATTERY) {
      focusFooter(FOCUS_SETTING);
    } else if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && focusArea == FOCUS_SETTING) {
      focusFooter(FOCUS_BATTERY);
    }
  }

  private boolean isOnTopRow() {
    int selectedIndex = launcherView.getSelectedIndex();
    return selectedIndex >= 0 && selectedIndex < config.getColNum();
  }

  private boolean isOnBottomRow() {
    int selectedIndex = launcherView.getSelectedIndex();
    if (selectedIndex < 0) return false;
    int colNum = config.getColNum();
    return selectedIndex + colNum >= launcherView.getDisplayedItemCount();
  }

  private void performFocusedClick() {
    if (focusArea == FOCUS_NONE) {
      focusGrid(launcherView.getSelectedIndex() < 0 ? 0 : launcherView.getSelectedIndex());
      return;
    }
    if (focusArea == FOCUS_GRID) {
      launcherView.performSelectedItemClick();
    } else if (focusArea == FOCUS_BATTERY) {
      batteryProgress.performClick();
    } else if (focusArea == FOCUS_SETTING) {
      settingIcon.performClick();
    }
  }

  private boolean performFocusedLongClick() {
    if (focusArea == FOCUS_NONE) {
      focusGrid(launcherView.getSelectedIndex() < 0 ? 0 : launcherView.getSelectedIndex());
    }
    if (focusArea == FOCUS_GRID) {
      return launcherView.performSelectedItemLongClick();
    }
    return false;
  }

  private void focusGrid(int index) {
    focusArea = FOCUS_GRID;
    launcherView.setSelectedIndex(index);
    launcherView.showSelection();
    updateFooterFocus();
  }

  private void focusFooter(int area) {
    focusArea = area;
    launcherView.hideSelection();
    updateFooterFocus();
  }

  private void clearKeyboardFocus() {
    focusArea = FOCUS_NONE;
    confirmLongPressed = false;
    launcherView.hideSelection();
    updateFooterFocus();
  }

  private void updateFooterFocus() {
    if (batteryProgress != null) {
      batteryProgress.setSelected(focusArea == FOCUS_BATTERY);
    }
    if (settingIcon != null) {
      settingIcon.setSelected(focusArea == FOCUS_SETTING);
    }
  }

  private void openSettingsFragment() {
    getFragmentManager().beginTransaction()
        .replace(android.R.id.content, new SettingFragment())
        .addToBackStack(null)
        .commit();
  }

  private void openBatterySettings() {
    Intent intent = new Intent();
    intent.setComponent(new ComponentName("com.android.settings", "com.android.settings.Settings$PowerUsageSummaryActivity"));
    try {
      startActivity(intent);
    } catch (Exception e) {
      Toast.makeText(Launcher.this, "无法打开电池设置", Toast.LENGTH_SHORT).show();
      e.printStackTrace();
    }
  }

  private void expandNotifications() {
    try {
      Object service = getSystemService("statusbar");
      if (service == null) return;
      String methodName = Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1
          ? "expandNotificationsPanel"
          : "expand";
      Method method = service.getClass().getMethod(methodName);
      method.invoke(service);
    } catch (Exception ignored) {
    }
  }

  private void showNextPageAndSelectFirst() {
    if (dataCenter.showNextPage()) {
      focusGrid(0);
    }
  }

  private void showLastPageAndSelectFirst() {
    if (dataCenter.showLastPage()) {
      focusGrid(0);
    }
  }

  private void showFirstPageAndKeepFocusState() {
    boolean hadKeyboardFocus = focusArea != FOCUS_NONE;
    dataCenter.showFirstPage();
    if (hadKeyboardFocus) {
      focusGrid(0);
    } else {
      launcherView.selectFirstAvailable();
      clearKeyboardFocus();
    }
  }

  private void showNextPageAndHideSelection() {
    if (dataCenter.showNextPage()) {
      launcherView.selectFirstAvailable();
      clearKeyboardFocus();
    }
  }

  private void showLastPageAndHideSelection() {
    if (dataCenter.showLastPage()) {
      launcherView.selectFirstAvailable();
      clearKeyboardFocus();
    }
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
  // 内存清理
  // =========================================================================

  private void cleanMemory() {
    String size = null;
    try {
      Toast.makeText(this, R.string.clean_start, Toast.LENGTH_SHORT).show();
      Process shellProcess = new ProcessBuilder(
          getApplicationInfo().nativeLibraryDir + "/libfillRam.so").start();
      java.io.BufferedReader reader = new java.io.BufferedReader(
          new java.io.InputStreamReader(shellProcess.getInputStream()));
      String line = "";
      while (line != null) {
        size = line;
        line = reader.readLine();
      }
    } catch (Exception e) {
      Toast.makeText(this, getString(R.string.clean_error, e.getMessage()), Toast.LENGTH_LONG).show();
      Log.e("MemoryClean", "Error: " + e.getMessage());
    }
    Toast.makeText(this, getString(R.string.clean_done, size), Toast.LENGTH_SHORT).show();
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
