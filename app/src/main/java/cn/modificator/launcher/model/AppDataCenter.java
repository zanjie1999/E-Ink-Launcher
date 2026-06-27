package cn.modificator.launcher.model;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ResolveInfo;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import cn.modificator.launcher.R;
import cn.modificator.launcher.widgets.AppItemBinder;
import cn.modificator.launcher.widgets.LauncherAdapter;

/**
 * 应用数据管理中心，负责加载应用列表和分页逻辑。
 */
public class AppDataCenter {

  /** 虚拟包名：Wifi 控制入口 */
  public static final String WIFI_PACKAGE_NAME = "E-ink_Launcher.WiFi";
  /** 虚拟包名：一键锁屏入口 */
  public static final String LOCK_PACKAGE_NAME = "E-ink_Launcher.Lock";
  /** 虚拟包名：后台清理入口 */
  public static final String CLEAR_PACKAGE_NAME = "E-ink_Launcher.Clear";

  private final Context mContext;
  private final List<ResolveInfo> mApps = new ArrayList<>();
  private int pageIndex = 0;
  private int pageCount = 0;
  private int colNum = 5;
  private int rowNum = 5;
  private LauncherAdapter adapter;
  private AppItemBinder binder;
  private TextView pageStatus;
  private final Set<String> hideApps = new HashSet<>();
  private int sortMode = AppSortComparator.SORT_NAME_ASC;

  public AppDataCenter(Context context) {
    this.mContext = context;
  }

  // =========================================================================
  // Adapter / Binder 绑定
  // =========================================================================

  public void setAdapter(LauncherAdapter adapter) {
    this.adapter = adapter;
    this.binder = adapter.getBinder();
    if (binder != null) {
      binder.setHideAppPkg(hideApps);
    }
    setPageShow();
  }

  public void setPageStatus(TextView pageStatus) {
    this.pageStatus = pageStatus;
    pageStatus.setText((pageIndex + 1) + "/" + (pageCount + 1));
  }

  // =========================================================================
  // 隐藏应用管理
  // =========================================================================

  public void setHideApps(Set<String> hideApps) {
    this.hideApps.clear();
    this.hideApps.addAll(hideApps);
    loadApps();
  }

  public Set<String> getHideApps() {
    return hideApps;
  }

  // =========================================================================
  // 列数/行数
  // =========================================================================

  public void setColRowNum(int colNum, int rowNum) {
    this.colNum = colNum;
    this.rowNum = rowNum;
    updatePageCount();
    setPageShow();
  }
  public void setColNum(int colNum) {
    this.colNum = colNum;
    updatePageCount();
    setPageShow();
  }

  public void setRowNum(int rowNum) {
    this.rowNum = rowNum;
    updatePageCount();
    setPageShow();
  }

  /** 批量设置行列数，只触发一次分页更新 */
  public void setGridSize(int colNum, int rowNum) {
    this.colNum = colNum;
    this.rowNum = rowNum;
    updatePageCount();
    setPageShow();
  }

  // =========================================================================
  // 排序
  // =========================================================================

  public void setSortMode(int sortMode) {
    this.sortMode = sortMode;
  }

  public int getSortMode() {
    return sortMode;
  }

  // =========================================================================
  // 翻页
  // =========================================================================

  public boolean showNextPage() {
    if (pageIndex >= pageCount) return false;
    pageIndex++;
    setPageShow();
    return true;
  }

  public boolean showLastPage() {
    if (pageIndex <= 0) return false;
    pageIndex--;
    setPageShow();
    return true;
  }

  public boolean showFirstPage() {
    if (pageIndex == 0) return false;
    pageIndex = 0;
    setPageShow();
    return true;
  }

  // =========================================================================
  // 刷新
  // =========================================================================

  public void refreshAppList() {
    refreshAppList(false);
  }

  public void refreshAppList(boolean showAll) {
    if (showAll) {
      loadAllApps();
    } else {
      loadApps();
    }
    setPageShow();
  }

  // =========================================================================
  // 内部加载
  // =========================================================================

  private void loadApps() {
    Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
    mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);

    if (binder != null) {
      hideApps.clear();
      hideApps.addAll(binder.getHideAppPkg());
    }

    mApps.clear();
    for (ResolveInfo resolveInfo : mContext.getPackageManager().queryIntentActivities(mainIntent, 0)) {
      if ("cn.modificator.launcher.Launcher".equals(resolveInfo.activityInfo.name)) continue;
      if (!hideApps.contains(resolveInfo.activityInfo.packageName)) {
        mApps.add(resolveInfo);
      }
    }

    if (!hideApps.contains(LOCK_PACKAGE_NAME)) {
      mApps.add(createPowerIcon());
    }
    if (!hideApps.contains(WIFI_PACKAGE_NAME)) {
      mApps.add(createWifiIcon());
    }
    if (!hideApps.contains(CLEAR_PACKAGE_NAME)) {
      mApps.add(createClearIcon());
    }
    sortApps();
    updatePageCount();
  }

  private void loadAllApps() {
    Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
    mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);

    mApps.clear();
    mApps.addAll(mContext.getPackageManager().queryIntentActivities(mainIntent, 0));
    mApps.add(createPowerIcon());
    mApps.add(createWifiIcon());
    mApps.add(createClearIcon());
    if (binder != null) {
      binder.setHideAppPkg(hideApps);
    }
    sortApps();
    updatePageCount();
  }

  private void setPageShow() {
    int itemCount = colNum * rowNum;
    int pageStart = pageIndex * itemCount;
    int pageEnd = Math.min(pageStart + itemCount, mApps.size());
    adapter.setAppList(mApps.subList(pageStart, pageEnd));
    pageStatus.setText((pageIndex + 1) + "/" + (pageCount + 1));
  }

  private void updatePageCount() {
    int itemCount = colNum * rowNum;
    pageCount = mApps.size() / itemCount - (mApps.size() % itemCount == 0 ? 1 : 0);
    pageCount = Math.max(pageCount, 0);
    pageIndex = Math.min(pageIndex, pageCount);
  }

  private void sortApps() {
    Collections.sort(mApps, new AppSortComparator(mContext, mContext.getPackageManager(), sortMode));
  }

  // =========================================================================
  // 虚拟图标创建
  // =========================================================================

  private ResolveInfo createWifiIcon() {
    ResolveInfo resolveInfo = new ResolveInfo();
    resolveInfo.icon = R.drawable.wifi_on;
    resolveInfo.activityInfo = new ActivityInfo();
    resolveInfo.activityInfo.packageName = WIFI_PACKAGE_NAME;
    return resolveInfo;
  }

  private ResolveInfo createPowerIcon() {
    ResolveInfo resolveInfo = new ResolveInfo();
    resolveInfo.icon = R.drawable.ic_onekeylock;
    resolveInfo.activityInfo = new ActivityInfo();
    resolveInfo.activityInfo.packageName = LOCK_PACKAGE_NAME;
    return resolveInfo;
  }

  private ResolveInfo createClearIcon(){
    ResolveInfo resolveInfo = new ResolveInfo();
    resolveInfo.icon = R.drawable.ic_onekeyclear;
    resolveInfo.activityInfo = new ActivityInfo();
    resolveInfo.activityInfo.packageName = CLEAR_PACKAGE_NAME;
    return resolveInfo;
  }
}
