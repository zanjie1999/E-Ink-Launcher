package cn.modificator.launcher.widgets;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ViewGroup;
import cn.modificator.launcher.R;

import static android.view.View.MeasureSpec.EXACTLY;
import static android.view.View.MeasureSpec.makeMeasureSpec;
import static androidx.core.content.ContextCompat.getSystemService;

/**
 * E-Ink 桌面网格布局 ViewGroup。
 * <p>
 * 纯布局容器，职责仅限于：
 * <ul>
 *   <li>网格布局（行列排列、尺寸测量）</li>
 *   <li>手势检测（滑动翻页）</li>
 *   <li>向 {@link LauncherAdapter} 请求 ViewHolder 的创建与绑定</li>
 * </ul>
 * 所有数据管理和交互逻辑由 {@link LauncherAdapter} 处理。
 */
public class EInkLauncherView extends ViewGroup {

  // =========================================================================
  // 翻页监听
  // =========================================================================

  /** 翻页手势回调 */
  public interface OnPageChangeListener {
    void onPageNext();
    void onPagePrev();
  }

  // =========================================================================
  // 字段
  // =========================================================================

  // 网格参数
  private int rowNum = 5;
  private int colNum = 5;
  private boolean hideDivider = false;

  // 外部依赖
  private LauncherAdapter adapter;
  private OnPageChangeListener pageChangeListener;

  // 滑动检测
  private float touchDownX;
  private float touchDownY;
  private float swipeThreshold;

  // =========================================================================
  // 构造器
  // =========================================================================

  public EInkLauncherView(Context context) {
    this(context, null);
  }

  public EInkLauncherView(Context context, AttributeSet attrs) {
    this(context, attrs, 0);
  }

  public EInkLauncherView(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
  }

  // =========================================================================
  // Adapter & Listener
  // =========================================================================

  /** 设置数据适配器，触发网格重建 */
  public void setAdapter(LauncherAdapter adapter) {
    if (this.adapter != null) {
      this.adapter.detachView();
    }
    this.adapter = adapter;
    if (adapter != null) {
      adapter.attachView(this);
    }
    resetGrid();
  }

  public LauncherAdapter getAdapter() {
    return adapter;
  }

  /** 设置翻页手势监听 */
  public void setOnPageChangeListener(OnPageChangeListener listener) {
    this.pageChangeListener = listener;
  }

  // =========================================================================
  // 网格配置
  // =========================================================================

  /**
   * 一次性配置所有网格参数，仅触发一次 {@code resetGrid()}。
   */
  public void configure(int colNum, int rowNum, boolean hideDivider) {
    this.colNum = colNum;
    this.rowNum = rowNum;
    this.hideDivider = hideDivider;
    resetGrid();
  }

  public void setGridSize(int colNum, int rowNum) {
    this.colNum = colNum;
    this.rowNum = rowNum;
    resetGrid();
  }

  public void setColNum(int colNum) {
    this.colNum = colNum;
    resetGrid();
  }

  public void setRowNum(int rowNum) {
    this.rowNum = rowNum;
    resetGrid();
  }

  public void setHideDivider(boolean hideDivider) {
    this.hideDivider = hideDivider;
    resetGrid();
  }

  // =========================================================================
  // onLayout / onMeasure
  // =========================================================================

  @Override
  protected void onLayout(boolean changed, int l, int t, int r, int b) {
    int w = getAdjustedWidth();
    int h = getAdjustedHeight();
    if (w <= 0 || h <= 0) return;

    swipeThreshold = Math.min(w, h) / 6f;
    int cellW = w / colNum;
    int cellH = h / rowNum;

    for (int row = 0; row < rowNum; row++) {
      for (int col = 0; col < colNum; col++) {
        int index = row * colNum + col;
        if (index >= getChildCount()) return;
        getChildAt(index).layout(
            col * cellW, row * cellH,
            (col + 1) * cellW, (row + 1) * cellH);
      }
    }
  }

  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    int w = getAdjustedWidth();
    int h = getAdjustedHeight();
    if (w <= 0 || h <= 0) return;

    int cellWSpec = makeMeasureSpec(w / colNum, EXACTLY);
    int cellHSpec = makeMeasureSpec(h / rowNum, EXACTLY);
    for (int i = 0; i < getChildCount(); i++) {
      getChildAt(i).measure(cellWSpec, cellHSpec);
    }
  }

  private int getAdjustedWidth() {
    return getWidth() - getPaddingLeft() - getPaddingRight();
  }

  private int getAdjustedHeight() {
    return getHeight() - getPaddingTop() - getPaddingBottom();
  }

  // =========================================================================
  // 网格构建
  // =========================================================================

  private void resetGrid() {
    if (adapter == null) return;
    int targetCount = rowNum * colNum;

    if (adapter.getHolderCount() == targetCount) {
      // 数量不变，仅刷新背景
      for (int i = 0; i < targetCount; i++) {
        getChildAt(i).setBackgroundResource(getItemBackground(i));
      }
      rebind();
      return;
    }

    // 数量变化，完整重建
    removeAllViews();
    adapter.clearHolders();

    for (int i = 0; i < targetCount; i++) {
      LauncherAdapter.ItemViewHolder holder = adapter.createViewHolder(this);
      holder.itemView.setBackgroundResource(getItemBackground(i));
      addView(holder.itemView);
    }
    rebind();
  }

  /** 请求 adapter 重新绑定所有数据 */
  void rebind() {
    if (adapter != null) {
      adapter.bindAll();
    }
  }

  private int getItemBackground(int index) {
    int total = rowNum * colNum;
    if (hideDivider || index == total - 1) {
      return R.drawable.app_item_final;
    } else if (index % colNum == colNum - 1) {
      return R.drawable.app_item_right;
    } else if (index >= (rowNum - 1) * colNum) {
      return R.drawable.app_item_bottom;
    } else {
      return R.drawable.app_item_normal;
    }
  }

  // =========================================================================
  // 手势检测
  // =========================================================================

  @Override
  public boolean dispatchTouchEvent(MotionEvent event) {
    switch (event.getActionMasked()) {
      case MotionEvent.ACTION_DOWN:
        touchDownX = event.getX();
        touchDownY = event.getY();
        break;
      case MotionEvent.ACTION_UP:
        int dir = detectSwipe(event.getX(), event.getY());
        if (dir != 0 && pageChangeListener != null) {
          if (dir > 0) pageChangeListener.onPagePrev();
          else pageChangeListener.onPageNext();
          return true;
        }
        break;
    }
    return super.dispatchTouchEvent(event);
  }

  /**
   * 检测滑动方向。
   *
   * @return 1 = 上一页, -1 = 下一页, 0 = 无有效滑动
   */
  private int detectSwipe(float upX, float upY) {
    if (swipeThreshold <= 0) return 0;
    float dx = upX - touchDownX;
    float dy = upY - touchDownY;
    if (dx > swipeThreshold || dy > swipeThreshold) return 1;
    if (dx < -swipeThreshold || dy < -swipeThreshold) return -1;
    return 0;
  }
}
