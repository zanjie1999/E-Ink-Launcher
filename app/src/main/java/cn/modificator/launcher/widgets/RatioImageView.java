package cn.modificator.launcher.widgets;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;

import cn.modificator.launcher.R;

/**
 * 按照宽高比例自适应尺寸的 ImageView。
 * 以 {@link ReferenceType} 指定的边为基准，另一边按比例计算。
 */
public class RatioImageView extends ImageView {

  private ReferenceType reference = ReferenceType.WIDTH;
  private double ratioWidth = 1;
  private double ratioHeight = 1;

  public enum ReferenceType {
    WIDTH,
    HEIGHT
  }

  public RatioImageView(Context context) {
    super(context);
  }

  public RatioImageView(Context context, AttributeSet attrs) {
    super(context, attrs);
    initAttrs(context, attrs, 0);
  }

  public RatioImageView(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    initAttrs(context, attrs, defStyleAttr);
  }

  private void initAttrs(Context context, AttributeSet attrs, int defStyleAttr) {
    TypedArray ta = context.obtainStyledAttributes(attrs, R.styleable.RatioLayout, defStyleAttr, 0);
    reference = ta.getInt(R.styleable.RatioLayout_reference, 0) == 0
        ? ReferenceType.WIDTH : ReferenceType.HEIGHT;
    ratioHeight = ta.getFloat(R.styleable.RatioLayout_ratioHeight, 1);
    ratioWidth = ta.getFloat(R.styleable.RatioLayout_ratioWidth, 1);
    ta.recycle();
  }

  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    boolean widthBased = (reference == ReferenceType.WIDTH);

    setMeasuredDimension(
        View.getDefaultSize(0, widthBased ? widthMeasureSpec
            : (int) (heightMeasureSpec / ratioHeight * ratioWidth)),
        View.getDefaultSize(0, !widthBased ? heightMeasureSpec
            : (int) (widthMeasureSpec / ratioWidth * ratioHeight))
    );

    int baseSize = widthBased ? getMeasuredWidth() : getMeasuredHeight();
    int otherSpec = widthBased
        ? View.MeasureSpec.makeMeasureSpec((int) (baseSize / ratioWidth * ratioHeight), View.MeasureSpec.EXACTLY)
        : View.MeasureSpec.makeMeasureSpec((int) (baseSize / ratioHeight * ratioWidth), View.MeasureSpec.EXACTLY);

    super.onMeasure(
        widthBased ? widthMeasureSpec : otherSpec,
        widthBased ? otherSpec : heightMeasureSpec
    );
  }
}
