package cn.modificator.launcher.widgets;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Point;
import android.net.Uri;
import android.os.Environment;
import android.os.PowerManager;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.File;
import android.graphics.drawable.Drawable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Observer;
import java.util.Set;

import cn.modificator.launcher.Config;
import cn.modificator.launcher.Launcher;
import cn.modificator.launcher.R;
import cn.modificator.launcher.model.AppDataCenter;
import cn.modificator.launcher.model.ObservableFloat;
import cn.modificator.launcher.model.WifiControl;

import static android.view.View.MeasureSpec.EXACTLY;
import static android.view.View.MeasureSpec.makeMeasureSpec;

/**
 * 自定义 ViewGroup：E-Ink 桌面网格布局，负责应用图标的展示和交互。
 */
public class EInkLauncherView extends ViewGroup {

  private int rowNum = 5;
  private int colNum = 5;
  private float dragDistance = 0;
  private final List<ResolveInfo> dataList = new ArrayList<>();
  private final PackageManager packageManager;
  private TouchListener touchListener;
  private boolean isDelete = false;
  private float fontSize = 14;
  private final ObservableFloat fontSizeObservable = new ObservableFloat();
  private boolean isSystemApp = false;
  private final Set<String> hideAppPkg = new HashSet<>();
  private OnSingleAppHideChange onSingleAppHideChange;
  private final Map<String, File> iconReplaceMap = new HashMap<>();
  private boolean iconCacheDirty = true;
  private final Map<String, Drawable> iconCache = new HashMap<>();
  private final Map<String, CharSequence> labelCache = new HashMap<>();
  private boolean hideDivider = false;
  private Config config;

  // 触摸起点
  private Point touchDown;

  public EInkLauncherView(Context context) {
    super(context);
    packageManager = context.getPackageManager();
    config = new Config(context);
  }

  public EInkLauncherView(Context context, AttributeSet attrs) {
    super(context, attrs);
    packageManager = context.getPackageManager();
    config = new Config(context);
  }

  public EInkLauncherView(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    packageManager = context.getPackageManager();
    config = new Config(context);
  }

  // =========================================================================
  // 公开属性设置
  // =========================================================================

  public void setHideAppPkg(Set<String> hideAppPkg) {
    this.hideAppPkg.clear();
    this.hideAppPkg.addAll(hideAppPkg);
  }

  public Set<String> getHideAppPkg() {
    return hideAppPkg;
  }

  public void setHideDivider(boolean hideDivider) {
    this.hideDivider = hideDivider;
    resetIconLayout();
  }

  public void setTouchListener(TouchListener touchListener) {
    this.touchListener = touchListener;
  }

  public void setColNum(int colNum) {
    this.colNum = colNum;
    resetIconLayout();
  }

  public void setRowNum(int rowNum) {
    this.rowNum = rowNum;
    resetIconLayout();
  }

  public void setDelete(boolean delete) {
    isDelete = delete;
    changeItemStatus();
  }

  public boolean isDelete() {
    return isDelete;
  }

  public void setFontSize(float fontSize) {
    this.fontSize = fontSize;
    fontSizeObservable.set(fontSize);
  }

  public float getFontSize() {
    return fontSize;
  }

  public void setSystemApp(boolean systemApp) {
    isSystemApp = systemApp;
  }

  /** 批量设置行列数，只触发一次布局重建 */
  public void setGridSize(int colNum, int rowNum) {
    this.colNum = colNum;
    this.rowNum = rowNum;
    resetIconLayout();
  }

  /** 标记自定义图标缓存为脏，下次 refreshReplaceIcon 时重新扫描文件系统 */
  public void markIconCacheDirty() {
    iconCacheDirty = true;
  }

  /** 清除应用图标和标签的内存缓存（应用安装/卸载时调用） */
  public void clearAppCache() {
    iconCache.clear();
    labelCache.clear();
  }

  public void setOnSingleAppHideChangeListener(OnSingleAppHideChange listener) {
    this.onSingleAppHideChange = listener;
  }

  // =========================================================================
  // 自定义图标
  // =========================================================================

  public void refreshReplaceIcon() {
    // 仅在脏标记为 true 时才重新扫描文件系统
    if (iconCacheDirty) {
      iconReplaceMap.clear();

      if (getContext().getExternalCacheDir() != null && !config.isShowCustomIcon()) {
        File iconFileRoot;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
          iconFileRoot = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
              "E-Ink Launcher" + File.separator + "icon");
        } else {
          iconFileRoot = new File(Environment.getExternalStorageDirectory(),
              "E-Ink Launcher" + File.separator + "icon");
        }

        if (!iconFileRoot.exists()) {
          try {
            iconFileRoot.mkdirs();
          } catch (Exception ignored) {
          }
        }

        File[] files = iconFileRoot.listFiles();
        if (files != null) {
          for (File file : files) {
            String name = file.getName();
            int dotIndex = name.lastIndexOf(".");
            String pkg = dotIndex > 0 ? name.substring(0, dotIndex) : name;
            iconReplaceMap.put(pkg, file);
          }
        }
      }
      iconCacheDirty = false;
    }
    refreshIconData();
  }

  // =========================================================================
  // 数据绑定
  // =========================================================================

  public void setAppList(List<ResolveInfo> appList) {
    dataList.clear();
    dataList.addAll(appList);
    refreshIconData();
  }

  public void updateAppNameLines() {
    int lines = config.getAppNameLines();
    for (int i = 0; i < rowNum * colNum && i < getChildCount(); i++) {
      TextView tvAppName = getChildAt(i).findViewById(R.id.appName);
      tvAppName.setMinLines(lines == 2 ? lines : 0);
      tvAppName.setMaxLines(lines);
    }
  }

  // =========================================================================
  // 布局
  // =========================================================================

  @Override
  protected void onLayout(boolean changed, int l, int t, int r, int b) {
    dragDistance = Math.min(getMeasuredWidth(), getMeasuredHeight()) / 6f;

    for (int i = 0; i < rowNum; i++) {
      for (int j = 0; j < colNum; j++) {
        int index = i * colNum + j;
        if (index >= getChildCount()) break;
        int childLeft = j * getItemWidth();
        int childRight = (j + 1) * getItemWidth();
        int childTop = i * getItemHeight();
        int childBottom = (i + 1) * getItemHeight();
        getChildAt(index).layout(childLeft, childTop, childRight, childBottom);
      }
    }

    if (getChildCount() > 0 && getChildAt(0).findViewById(R.id.appName).getMeasuredWidth() == 0) {
      refreshIconData();
    }
  }

  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    int itemWidthSpec = makeMeasureSpec(getItemWidth(), EXACTLY);
    int itemHeightSpec = makeMeasureSpec(getItemHeight(), EXACTLY);
    for (int i = 0; i < getChildCount(); i++) {
      getChildAt(i).measure(itemWidthSpec, itemHeightSpec);
    }
  }

  private int getItemHeight() {
    return getAdjustedHeight() / rowNum;
  }

  private int getItemWidth() {
    return getAdjustedWidth() / colNum;
  }

  private int getAdjustedHeight() {
    return getHeight() - getPaddingBottom() - getPaddingTop();
  }

  private int getAdjustedWidth() {
    return getWidth() - getPaddingLeft() - getPaddingRight();
  }

  // =========================================================================
  // 内部布局构建
  // =========================================================================

  private void resetIconLayout() {
    int targetCount = rowNum * colNum;
    int currentCount = getChildCount();

    if (currentCount == targetCount) {
      // 子 View 数量未变，仅更新背景即可
      for (int i = 0; i < targetCount; i++) {
        getChildAt(i).setBackgroundResource(getItemBackground(i));
      }
      refreshIconData();
      return;
    }

    // 子 View 数量发生变化，完整重建
    fontSizeObservable.deleteObservers();
    removeAllViews();

    int appNameLines = config.getAppNameLines();
    float currentFontSize = config.getFontSize();

    for (int i = 0; i < targetCount; i++) {
      View itemView = LayoutInflater.from(getContext()).inflate(R.layout.launcher_item, this, false);
      fontSizeObservable.addObserver((Observer) itemView.findViewById(R.id.appName));

      TextView tvAppName = itemView.findViewById(R.id.appName);
      tvAppName.setTextSize(TypedValue.COMPLEX_UNIT_SP, currentFontSize);
      tvAppName.setMinLines(appNameLines == 2 ? appNameLines : 0);
      tvAppName.setMaxLines(appNameLines);
      tvAppName.setEllipsize(TextUtils.TruncateAt.END);

      itemView.setBackgroundResource(getItemBackground(i));
      addView(itemView);
    }
    refreshIconData();
  }

  /**
   * 根据位置返回合适的背景 drawable。
   */
  private int getItemBackground(int index) {
    if (hideDivider || index == rowNum * colNum - 1) {
      return R.drawable.app_item_final;
    } else if (index % colNum == colNum - 1) {
      return R.drawable.app_item_right;
    } else if (index > (rowNum - 1) * colNum - 1) {
      return R.drawable.app_item_bottom;
    } else {
      return R.drawable.app_item_normal;
    }
  }

  private void refreshIconData() {
    WifiControl.bind(null, iconReplaceMap);

    for (int i = 0; i < rowNum; i++) {
      for (int j = 0; j < colNum; j++) {
        int position = i * colNum + j;
        View itemView = getChildAt(position);
        if (itemView == null) return;

        if (position < dataList.size() && position < getChildCount()) {
          bindItemData(itemView, position);
        } else {
          clearItemView(itemView);
        }
      }
    }
    changeItemStatus();
  }

  private void bindItemData(View itemView, int position) {
    String packageName = dataList.get(position).activityInfo.packageName;
    ImageView appImage = itemView.findViewById(R.id.appImage);
    TextView appName = itemView.findViewById(R.id.appName);

    if (AppDataCenter.WIFI_PACKAGE_NAME.equals(packageName)) {
      WifiControl.bind(itemView, iconReplaceMap);
    } else if (AppDataCenter.LOCK_PACKAGE_NAME.equals(packageName)) {
      setIconWithReplacement(appImage, packageName, R.drawable.ic_onekeylock);
      appName.setText(R.string.item_lockscreen);
    } else {
      setIconWithReplacement(appImage, packageName, dataList.get(position));
      // 带缓存的标签加载
      CharSequence label = labelCache.get(packageName);
      if (label == null) {
        label = dataList.get(position).loadLabel(packageManager);
        labelCache.put(packageName, label);
      }
      appName.setText(label);
    }

    // 通过 tag 传递位置，复用单例监听器
    itemView.setTag(position);
    itemView.setOnClickListener(itemClickListener);
    itemView.setOnLongClickListener(itemLongClickListener);
    View menuDelete = itemView.findViewById(R.id.menu_delete);
    menuDelete.setTag(position);
    menuDelete.setOnClickListener(itemClickListener);
    View menuHide = itemView.findViewById(R.id.menu_hide);
    menuHide.setTag(position);
    menuHide.setOnClickListener(hideClickListener);
    itemView.setVisibility(VISIBLE);
    itemView.setAlpha(1);
  }

  private void setIconWithReplacement(ImageView imageView, String packageName, int defaultRes) {
    File replaceFile = iconReplaceMap.get(packageName);
    if (replaceFile != null) {
      imageView.setImageURI(Uri.fromFile(replaceFile));
    } else {
      imageView.setImageResource(defaultRes);
    }
  }

  private void setIconWithReplacement(ImageView imageView, String packageName, ResolveInfo info) {
    File replaceFile = iconReplaceMap.get(packageName);
    if (replaceFile != null) {
      imageView.setImageURI(Uri.fromFile(replaceFile));
    } else {
      // 带缓存的图标加载
      Drawable cached = iconCache.get(packageName);
      if (cached == null) {
        cached = info.loadIcon(packageManager);
        iconCache.put(packageName, cached);
      }
      imageView.setImageDrawable(cached);
    }
  }

  private void clearItemView(View itemView) {
    ((TextView) itemView.findViewById(R.id.appName)).setText("");
    ((ImageView) itemView.findViewById(R.id.appImage)).setImageDrawable(null);
    itemView.setAlpha(0);
  }

  // =========================================================================
  // 删除/隐藏管理模式
  // =========================================================================

  private void changeItemStatus() {
    for (int i = 0; i < getChildCount(); i++) {
      if (i >= dataList.size()) break;

      ViewGroup child = (ViewGroup) getChildAt(i);
      View menuContainer = child.getChildAt(1);

      if (!isDelete) {
        menuContainer.setVisibility(GONE);
      } else {
        menuContainer.setVisibility(VISIBLE);

        String pkgName = dataList.get(i).activityInfo.packageName;
        boolean showDelete = false;
        if (!AppDataCenter.WIFI_PACKAGE_NAME.equals(pkgName)
            && !AppDataCenter.LOCK_PACKAGE_NAME.equals(pkgName)) {
          try {
            showDelete = (packageManager.getPackageInfo(pkgName, 0).applicationInfo.flags
                & ApplicationInfo.FLAG_SYSTEM) == 0;
          } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
          }
        }

        child.findViewById(R.id.menu_delete).setVisibility(showDelete ? VISIBLE : GONE);
        child.findViewById(R.id.menu_hide).setSelected(hideAppPkg.contains(pkgName));
      }
    }
  }

  // =========================================================================
  // 触摸/滑动翻页
  // =========================================================================

  /**
   * 判断当前手势是否为滑动翻页。
   *
   * @return 1=上一页, -1=下一页, 0=无翻页手势
   */
  private int detectSwipeDirection(MotionEvent event) {
    if (touchDown == null) return 0;
    float dx = event.getX() - touchDown.x;
    float dy = event.getY() - touchDown.y;

    if ((dx > 0 && Math.abs(dx) > dragDistance) || (dy > 0 && Math.abs(dy) > dragDistance)) {
      return 1; // toLast (上一页)
    }
    if ((dx < 0 && Math.abs(dx) > dragDistance) || (dy < 0 && Math.abs(dy) > dragDistance)) {
      return -1; // toNext (下一页)
    }
    return 0;
  }

  private boolean handleSwipe(MotionEvent event) {
    if (event.getAction() == MotionEvent.ACTION_DOWN) {
      touchDown = new Point((int) event.getX(), (int) event.getY());
      return false;
    }
    if (event.getAction() == MotionEvent.ACTION_UP && touchListener != null) {
      int direction = detectSwipeDirection(event);
      if (direction == 1) {
        touchListener.toLast();
        return true;
      } else if (direction == -1) {
        touchListener.toNext();
        return true;
      }
    }
    return false;
  }

  @Override
  public boolean dispatchTouchEvent(MotionEvent event) {
    // 记录触摸起点
    if (event.getAction() == MotionEvent.ACTION_DOWN) {
      touchDown = new Point((int) event.getX(), (int) event.getY());
    }
    // 在 UP 时检测滑动
    if (event.getAction() == MotionEvent.ACTION_UP) {
      if (touchListener != null) {
        int direction = detectSwipeDirection(event);
        if (direction == 1) {
          touchListener.toLast();
          return true;
        } else if (direction == -1) {
          touchListener.toNext();
          return true;
        }
      }
    }
    return super.dispatchTouchEvent(event);
  }

  @Override
  public boolean onInterceptTouchEvent(MotionEvent event) {
    if (handleSwipe(event)) return true;
    return super.onInterceptTouchEvent(event);
  }

  @Override
  public boolean onTouchEvent(MotionEvent event) {
    if (handleSwipe(event)) return true;
    return super.onTouchEvent(event);
  }

  // =========================================================================
  // 点击事件处理（单例监听器 + tag 传递位置，避免每次刷新创建新对象）
  // =========================================================================

  private final OnClickListener itemClickListener = new OnClickListener() {
    @Override
    public void onClick(View v) {
      int position = (int) v.getTag();
      if (position >= dataList.size()) return;

      if (isDelete) {
        Intent deleteIntent = new Intent(Intent.ACTION_DELETE,
            Uri.parse("package:" + dataList.get(position).activityInfo.packageName));
        v.getContext().startActivity(deleteIntent);
        return;
      }

      ResolveInfo info = dataList.get(position);
      String pkgName = info.activityInfo.packageName;

      if (AppDataCenter.LOCK_PACKAGE_NAME.equals(pkgName)) {
        ((Launcher) v.getContext()).lockScreen();
      } else if (AppDataCenter.WIFI_PACKAGE_NAME.equals(pkgName)) {
        WifiControl.onClickWifiItem();
      } else {
        ComponentName componentName = new ComponentName(info.activityInfo.packageName, info.activityInfo.name);
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        intent.setComponent(componentName);
        v.getContext().startActivity(intent);
      }
    }
  };

  private final OnLongClickListener itemLongClickListener = new OnLongClickListener() {
    @Override
    public boolean onLongClick(View v) {
      int position = (int) v.getTag();
      if (position >= dataList.size()) return false;

      final String packageName = dataList.get(position).activityInfo.packageName;

      if (AppDataCenter.LOCK_PACKAGE_NAME.equals(packageName)) {
        showPowerMenu(v);
      } else if (AppDataCenter.WIFI_PACKAGE_NAME.equals(packageName)) {
        WifiControl.onLongClickWifiItem();
      } else {
        showAppInfoDialog(v, position, packageName);
      }
      return true;
    }
  };

  private final OnClickListener hideClickListener = new OnClickListener() {
    @Override
    public void onClick(View v) {
      int position = (int) v.getTag();
      if (position >= dataList.size()) return;
      String pkg = dataList.get(position).activityInfo.packageName;
      if (hideAppPkg.contains(pkg)) {
        v.setSelected(false);
        hideAppPkg.remove(pkg);
      } else {
        v.setSelected(true);
        hideAppPkg.add(pkg);
      }
    }
  };

  // =========================================================================
  // 对话框
  // =========================================================================

  private void showPowerMenu(View v) {
    if (!isSystemApp) return;
    new AlertDialog.Builder(v.getContext())
        .setTitle(R.string.power_title)
        .setItems(R.array.power_menu, new DialogInterface.OnClickListener() {
          @Override
          public void onClick(DialogInterface dialog, int which) {
            if (which == 0) {
              Intent intent = new Intent("android.intent.action.ACTION_REQUEST_SHUTDOWN");
              intent.putExtra("android.intent.extra.KEY_CONFIRM", false);
              intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
              getContext().startActivity(intent);
            } else {
              PowerManager pm = (PowerManager) getContext().getSystemService(Context.POWER_SERVICE);
              pm.reboot("重启");
            }
          }
        })
        .setPositiveButton(R.string.dialog_cancel, null)
        .show();
  }

  private void showAppInfoDialog(View v, int position, final String packageName) {
    new AlertDialog.Builder(v.getContext())
        .setIcon(dataList.get(position).loadIcon(packageManager))
        .setTitle(dataList.get(position).loadLabel(packageManager))
        .setMessage(getResources().getString(R.string.dialog_pkg_name, packageName))
        .setPositiveButton(R.string.dialog_cancel, null)
        .setNeutralButton(R.string.dialog_hide, new DialogInterface.OnClickListener() {
          @Override
          public void onClick(DialogInterface dialog, int which) {
            if (onSingleAppHideChange != null) {
              if (!hideAppPkg.add(packageName)) {
                hideAppPkg.remove(packageName);
              }
              onSingleAppHideChange.change(packageName);
            }
          }
        })
        .setNegativeButton(R.string.dialog_uninstall, new DialogInterface.OnClickListener() {
          @Override
          public void onClick(DialogInterface dialog, int which) {
            Intent deleteIntent = new Intent(Intent.ACTION_DELETE,
                Uri.parse("package:" + packageName));
            getContext().startActivity(deleteIntent);
          }
        })
        .show();
  }

  // =========================================================================
  // 回调接口
  // =========================================================================

  public interface TouchListener {
    void toNext();
    void toLast();
  }

  public interface OnSingleAppHideChange {
    void change(String pkg);
  }
}
