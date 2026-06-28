package cn.modificator.launcher;

import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 从工作咩闹钟抄过来的时钟模式
 */
public class ClockActivity extends AppCompatActivity {

  private TextView tvTime;
  private TextView tvDate;
  private Config config;
  private final Handler timeHandler = new Handler(Looper.getMainLooper());

  private final Runnable timeTicker = new Runnable() {
    @Override
    public void run() {
      updateTime();
      timeHandler.postDelayed(this, getNextTickDelay());
    }
  };

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    config = new Config(this);
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    setFullscreen();
    setContentView(R.layout.activity_clock);
    applyThemeBackground();

    tvTime = findViewById(R.id.tv_time);
    tvDate = findViewById(R.id.tv_date);

    tvTime.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        backToLauncher();
      }
    });
    tvDate.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        rotateScreen();
      }
    });

    updateTime();
  }

  @Override
  protected void onResume() {
    super.onResume();
    setFullscreen();
    startTicker();
  }

  @Override
  protected void onPause() {
    super.onPause();
    stopTicker();
  }

  @Override
  public void onWindowFocusChanged(boolean hasFocus) {
    super.onWindowFocusChanged(hasFocus);
    if (hasFocus) {
      setFullscreen();
    }
  }

  @Override
  public void onConfigurationChanged(Configuration newConfig) {
    super.onConfigurationChanged(newConfig);
    setFullscreen();
    applyThemeBackground();
    updateTime();
  }

  @Override
  protected void onDestroy() {
    stopTicker();
    super.onDestroy();
  }

  private void updateTime() {
    if (tvTime == null || tvDate == null) return;

    Date now = new Date();
    boolean is24Hour = DateFormat.is24HourFormat(this);
    boolean showSeconds = config.isClockShowSeconds();
    String timePattern = is24Hour ? "H:mm" : "h:mm";
    if (showSeconds) {
      timePattern += ":ss";
    }

    tvTime.setText(new SimpleDateFormat(timePattern, Locale.getDefault()).format(now));
    tvDate.setText(new SimpleDateFormat("M月d日 E", Locale.getDefault()).format(now));
  }

  private void backToLauncher() {
    Intent intent = new Intent(this, Launcher.class);
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
    startActivity(intent);
    finish();
  }

  private void startTicker() {
    stopTicker();
    timeTicker.run();
  }

  private void stopTicker() {
    timeHandler.removeCallbacks(timeTicker);
  }

  private long getNextTickDelay() {
    if (config.isClockShowSeconds()) {
      return 1000 - System.currentTimeMillis() % 1000;
    }
    long millisInMinute = 60 * 1000;
    return millisInMinute - System.currentTimeMillis() % millisInMinute;
  }

  private void rotateScreen() {
    int orientation = getResources().getConfiguration().orientation;
    int currentRequested = getRequestedOrientation();
    if (orientation == Configuration.ORIENTATION_PORTRAIT
        && currentRequested != ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT) {
      setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
    } else if (orientation == Configuration.ORIENTATION_LANDSCAPE
        && currentRequested != ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE) {
      setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT);
    } else if (orientation == Configuration.ORIENTATION_PORTRAIT) {
      setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE);
    } else {
      setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
    }
  }

  private void applyThemeBackground() {
    int themeMode = config.getThemeMode();
    int backgroundColor = getResources().getColor(R.color.mainBgColor);
    if (themeMode == 3) {
      backgroundColor = 0xffffffff;
    } else if (themeMode == 4) {
      backgroundColor = 0xff000000;
    }

    findViewById(R.id.clockRoot).setBackgroundColor(backgroundColor);
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      getWindow().setStatusBarColor(backgroundColor);
      getWindow().setNavigationBarColor(backgroundColor);
    }
  }

  private void setFullscreen() {
    getWindow().getDecorView().setSystemUiVisibility(
        View.SYSTEM_UI_FLAG_FULLSCREEN
            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
      getWindow().getAttributes().layoutInDisplayCutoutMode =
          WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
    }
  }

  @Override
  public boolean dispatchKeyEvent(KeyEvent event) {
    if (event.getAction() == KeyEvent.ACTION_DOWN) {
      int keyCode = event.getKeyCode();
      Log.d("zyyme dispatchKeyEvent", String.valueOf(keyCode));
      if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT
          || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT
          || keyCode == KeyEvent.KEYCODE_DPAD_UP
          || keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
        rotateScreen();
        return true;
      } else if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER
          || keyCode == KeyEvent.KEYCODE_ENTER
          || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER) {
        backToLauncher();
        return true;
      }
    }
    return super.dispatchKeyEvent(event);
  }
}
