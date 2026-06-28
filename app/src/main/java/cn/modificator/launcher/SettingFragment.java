package cn.modificator.launcher;

import android.Manifest;
import android.app.Activity;
import android.app.Fragment;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import cn.modificator.launcher.ftpservice.FTPService;
import cn.modificator.launcher.model.AppSortComparator;
import cn.modificator.launcher.model.WifiControl;

/**
 * 设置页面 Fragment。
 */
public class SettingFragment extends Fragment implements View.OnClickListener {

  /** 设置变更回调接口：宿主 Activity 应实现此接口以响应设置变更。 */
  public interface OnSettingChangeListener {
    void onRowNumChanged(int rowNum);
    void onColNumChanged(int colNum);
    void onFontSizeChanged(float size);
    void onAppNameLinesChanged(int lines);
    void onHideDividerChanged(boolean hide);
    void onShowStatusBarChanged(boolean show);
    void onShowCustomIconChanged(boolean show);
    void onSortModeChanged(int mode);
    void onThemeModeChanged(int mode);
    void onEnterManageMode();
  }

  private OnSettingChangeListener listener;

  private Spinner colNumSpinner;
  private Spinner rowNumSpinner;
  private Spinner appNameLinesSpinner;
  private Spinner sortModeSpinner;
  private Spinner themeModeSpinner;
  private SeekBar fontControl;
  private View rootView;
  private TextView hideDivider;
  private TextView ftpAddr;
  private TextView ftpStatus;
  private TextView showStatusBar;
  private TextView showCustomIcon;
  private Config config;
  private View changeFontSize;
  private View deleteApp;
  private View helpAbout;
  private View menuFtp;
  private View openDeviceManager;
  private View showWifiName;

  @SuppressWarnings("deprecation")
  @Override
  public void onAttach(Activity activity) {
    super.onAttach(activity);
    if (activity instanceof OnSettingChangeListener) {
      listener = (OnSettingChangeListener) activity;
    } else {
      throw new ClassCastException(activity.toString() + " must implement OnSettingChangeListener");
    }
  }

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    return inflater.inflate(R.layout.activity_setting, null);
  }

  @Override
  public void onActivityCreated(Bundle savedInstanceState) {
    super.onActivityCreated(savedInstanceState);
    rootView = getView();
    config = new Config(getActivity());
    initViews();
    initSpinners();
    initFontControl();
    updateFtpStatus();
  }

  // =========================================================================
  // 初始化
  // =========================================================================

  private void initViews() {
    View toBack = rootView.findViewById(R.id.toBack);
    View btnHideFontControl = rootView.findViewById(R.id.btnHideFontControl);

    toBack.setOnClickListener(this);
    rootView.findViewById(R.id.rootView).setOnClickListener(this);
    btnHideFontControl.setOnClickListener(this);

    showStatusBar = rootView.findViewById(R.id.showStatusBar);
    showCustomIcon = rootView.findViewById(R.id.showCustomIcon);
    showWifiName = rootView.findViewById(R.id.showWifiName);
    ftpStatus = rootView.findViewById(R.id.ftp_status);
    ftpAddr = rootView.findViewById(R.id.ftp_addr);
    hideDivider = rootView.findViewById(R.id.hideDivider);
    fontControl = rootView.findViewById(R.id.font_control);
    colNumSpinner = rootView.findViewById(R.id.col_num_spinner);
    rowNumSpinner = rootView.findViewById(R.id.row_num_spinner);
    appNameLinesSpinner = rootView.findViewById(R.id.appNameLine);
    sortModeSpinner = rootView.findViewById(R.id.sortModeSpinner);
    themeModeSpinner = rootView.findViewById(R.id.theme_mode_spinner);
    changeFontSize = rootView.findViewById(R.id.changeFontSize);
    deleteApp = rootView.findViewById(R.id.deleteApp);
    helpAbout = rootView.findViewById(R.id.helpAbout);
    menuFtp = rootView.findViewById(R.id.menu_ftp);
    openDeviceManager = rootView.findViewById(R.id.openDeviceManager);

    showStatusBar.setOnClickListener(this);
    hideDivider.setOnClickListener(this);
    showCustomIcon.setOnClickListener(this);
    showWifiName.setOnClickListener(this);
    changeFontSize.setOnClickListener(this);
    deleteApp.setOnClickListener(this);
    helpAbout.setOnClickListener(this);
    menuFtp.setOnClickListener(this);
    openDeviceManager.setOnClickListener(this);

    initDpadFocus(toBack, btnHideFontControl);

    // 初始化 UI 状态
    showStatusBar.getPaint().setStrikeThruText(config.isShowStatusBar());
    hideDivider.getPaint().setStrikeThruText(config.isHideDivider());
    hideDivider.setText(config.isHideDivider() ? "显示分隔线" : "隐藏分隔线");
    showCustomIcon.getPaint().setStrikeThruText(config.isShowCustomIcon());
    fontControl.setProgress((int) ((config.getFontSize() - 10) * 10));
  }

  private void initDpadFocus(View toBack, View btnHideFontControl) {
    View[] menuItems = new View[] {
        colNumSpinner,
        rowNumSpinner,
        appNameLinesSpinner,
        sortModeSpinner,
        hideDivider,
        showStatusBar,
        showWifiName,
        showCustomIcon,
        changeFontSize,
        deleteApp,
        themeModeSpinner,
        openDeviceManager,
        helpAbout,
        menuFtp
    };

    for (View item : menuItems) {
      makeFocusable(item);
    }
    makeFocusable(toBack);
    makeFocusable(btnHideFontControl);
    makeFocusable(fontControl);

    colNumSpinner.requestFocus();
  }

  private void makeFocusable(View view) {
    view.setFocusable(true);
    view.setFocusableInTouchMode(false);
    if (!(view instanceof Spinner) && !(view instanceof SeekBar)) {
      view.setBackgroundResource(R.drawable.setting_item_focus);
    }
  }

  private void initSpinners() {
    rowNumSpinner.setSelection(config.getRowNum() - 2, false);
    rowNumSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
      @Override
      public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        int rowNum = position + 2;
        config.setRowNum(rowNum);
        listener.onRowNumChanged(rowNum);
      }

      @Override
      public void onNothingSelected(AdapterView<?> parent) {
      }
    });

    colNumSpinner.setSelection(config.getColNum() - 2, false);
    colNumSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
      @Override
      public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        int colNum = position + 2;
        config.setColNum(colNum);
        listener.onColNumChanged(colNum);
      }

      @Override
      public void onNothingSelected(AdapterView<?> parent) {
      }
    });

    appNameLinesSpinner.setSelection(getAppLineSpinnerSelectPosition(), false);
    appNameLinesSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
      @Override
      public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        int lines = (position == 3) ? Integer.MAX_VALUE : position;
        config.setAppNameLines(lines);
        listener.onAppNameLinesChanged(lines);
      }

      @Override
      public void onNothingSelected(AdapterView<?> parent) {
      }
    });

    sortModeSpinner.setSelection(config.getSortMode(), false);
    sortModeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
      @Override
      public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        if (AppSortComparator.modeNeedsUsageStats(position)
            && !AppSortComparator.hasUsageStatsPermission(getActivity())) {
          Toast.makeText(getActivity(), R.string.sort_need_usage_permission, Toast.LENGTH_LONG).show();
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));
          }
          sortModeSpinner.setSelection(config.getSortMode(), false);
          return;
        }
        config.setSortMode(position);
        listener.onSortModeChanged(position);
      }

      @Override
      public void onNothingSelected(AdapterView<?> parent) {
      }
    });

    themeModeSpinner.setSelection(config.getThemeMode(), false);
    themeModeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
      @Override
      public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        if (position == config.getThemeMode()) {
          return;
        }
        config.setThemeMode(position);
        listener.onThemeModeChanged(position);
      }

      @Override
      public void onNothingSelected(AdapterView<?> parent) {
      }
    });
  }

  private void initFontControl() {
    fontControl.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
      @Override
      public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        if (fromUser) {
          float newSize = 10 + progress / 10f;
          config.setFontSize(newSize);
          listener.onFontSizeChanged(newSize);
        }
      }

      @Override
      public void onStartTrackingTouch(SeekBar seekBar) {
      }

      @Override
      public void onStopTrackingTouch(SeekBar seekBar) {
      }
    });
  }

  private int getAppLineSpinnerSelectPosition() {
    int lines = config.getAppNameLines();
    return (lines <= 2) ? lines : 3;
  }

  // =========================================================================
  // 点击处理
  // =========================================================================

  @Override
  public void onClick(View v) {
    int id = v.getId();
    if (id == R.id.toBack || id == R.id.rootView) {
      getActivity().onBackPressed();
    } else if (id == R.id.deleteApp) {
      handleDeleteApp();
    } else if (id == R.id.showStatusBar) {
      handleToggleStatusBar();
    } else if (id == R.id.helpAbout) {
      AboutDialog.getInstance(getActivity()).show();
    } else if (id == R.id.btnHideFontControl) {
      rootView.findViewById(R.id.menuList).setVisibility(View.VISIBLE);
      rootView.findViewById(R.id.font_control_p).setVisibility(View.GONE);
      changeFontSize.requestFocus();
    } else if (id == R.id.changeFontSize) {
      rootView.findViewById(R.id.menuList).setVisibility(View.GONE);
      rootView.findViewById(R.id.font_control_p).setVisibility(View.VISIBLE);
      fontControl.requestFocus();
    } else if (id == R.id.hideDivider) {
      handleToggleDivider();
    } else if (id == R.id.menu_ftp) {
      handleFtp();
    } else if (id == R.id.showWifiName) {
      handleShowWifiName();
    } else if (id == R.id.showCustomIcon) {
      handleToggleCustomIcon();
    } else if (id == R.id.openDeviceManager) {
      startActivity(new Intent().setComponent(
          new ComponentName("com.android.settings", "com.android.settings.DeviceAdminSettings")));
    }
  }

  private void handleDeleteApp() {
    listener.onEnterManageMode();
    getActivity().onBackPressed();
  }

  private void handleToggleStatusBar() {
    boolean newValue = !config.isShowStatusBar();
    config.setShowStatusBar(newValue);
    listener.onShowStatusBarChanged(newValue);
    getActivity().onBackPressed();
  }

  private void handleToggleDivider() {
    boolean newValue = !config.isHideDivider();
    config.setHideDivider(newValue);
    hideDivider.setText(newValue ? "显示分隔线" : "隐藏分隔线");
    listener.onHideDividerChanged(newValue);
    getActivity().onBackPressed();
  }

  private void handleFtp() {
    Utils.checkStoragePermission(getActivity(), new Runnable() {
      @Override
      public void run() {
        if (!FTPService.isRunning()) {
          if (FTPService.isConnectedToWifi(getActivity())) {
            startFtpServer();
          } else {
            Toast.makeText(getActivity(), "大哥诶，麻烦先把WIFI连上吧", Toast.LENGTH_SHORT).show();
          }
        } else {
          stopFtpServer();
        }
      }
    });
  }

  private void handleShowWifiName() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 10002);
    }
  }

  private void handleToggleCustomIcon() {
    Utils.checkStoragePermission(getActivity(), new Runnable() {
      @Override
      public void run() {
        boolean newValue = !config.isShowCustomIcon();
        config.setShowCustomIcon(newValue);
        listener.onShowCustomIconChanged(newValue);
        getActivity().onBackPressed();
      }
    });
  }

  @Override
  public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    if (requestCode == 10002) {
      WifiControl.reloadWifiName();
      getActivity().onBackPressed();
    }
  }

  // =========================================================================
  // 生命周期
  // =========================================================================

  @Override
  public void onResume() {
    super.onResume();
    updateFtpStatus();

    IntentFilter wifiFilter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
    Utils.registerReceiverCompat(getActivity(), wifiReceiver, wifiFilter);

    IntentFilter ftpFilter = new IntentFilter();
    ftpFilter.addAction(FTPService.ACTION_STARTED);
    ftpFilter.addAction(FTPService.ACTION_STOPPED);
    ftpFilter.addAction(FTPService.ACTION_FAILEDTOSTART);
    Utils.registerReceiverCompat(getActivity(), ftpReceiver, ftpFilter);
  }

  @Override
  public void onPause() {
    super.onPause();
    getActivity().unregisterReceiver(wifiReceiver);
    getActivity().unregisterReceiver(ftpReceiver);
  }

  // =========================================================================
  // FTP 控制
  // =========================================================================

  private void startFtpServer() {
    getActivity().sendBroadcast(new Intent(FTPService.ACTION_START_FTPSERVER));
    updateFtpStatus();
  }

  private void stopFtpServer() {
    getActivity().sendBroadcast(new Intent(FTPService.ACTION_STOP_FTPSERVER));
    updateFtpStatus();
  }

  private void updateFtpStatus() {
    if (FTPService.isConnectedToWifi(getActivity())) {
      if (FTPService.isRunning()) {
        ftpStatus.setText(R.string.setting_cloud_manager_on);
        ftpAddr.setVisibility(View.VISIBLE);
        String address = getFTPAddressString();
        if (address != null) {
          ftpAddr.setText(address);
        } else {
          ftpAddr.setVisibility(View.GONE);
        }
      } else {
        ftpStatus.setText(R.string.setting_cloud_manager_off);
        ftpAddr.setVisibility(View.GONE);
      }
    } else {
      ftpStatus.setText(R.string.setting_cloud_manager_wifi_off);
      ftpAddr.setVisibility(View.GONE);
    }
  }

  private String getFTPAddressString() {
    java.net.InetAddress address = FTPService.getLocalInetAddress(getActivity());
    if (address == null) {
      return null;
    }
    return "ftp://" + address.getHostAddress() + ":" + FTPService.getPort();
  }

  // =========================================================================
  // 广播接收器
  // =========================================================================

  private final BroadcastReceiver wifiReceiver = new BroadcastReceiver() {
    @Override
    public void onReceive(Context context, Intent intent) {
      ConnectivityManager conMan = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
      NetworkInfo netInfo = conMan.getActiveNetworkInfo();
      if (netInfo == null || netInfo.getType() != ConnectivityManager.TYPE_WIFI) {
        stopFtpServer();
      }
      updateFtpStatus();
    }
  };

  private final BroadcastReceiver ftpReceiver = new BroadcastReceiver() {
    @Override
    public void onReceive(Context context, Intent intent) {
      if (FTPService.ACTION_FAILEDTOSTART.equals(intent.getAction())) {
        Toast.makeText(getActivity(), "网络传书启动失败", Toast.LENGTH_SHORT).show();
      }
      updateFtpStatus();
    }
  };
}
