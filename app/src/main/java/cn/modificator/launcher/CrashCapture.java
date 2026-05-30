package cn.modificator.launcher;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Environment;
import android.text.TextUtils;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 全局异常捕获处理器。
 * 捕获未处理异常后记录日志并跳转到崩溃详情页。
 */
public class CrashCapture implements Thread.UncaughtExceptionHandler {

  private static final String TAG = "CrashCapture";
  private static final CrashCapture INSTANCE = new CrashCapture();
  private static final SimpleDateFormat DATE_FORMAT =
      new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.getDefault());

  private Thread.UncaughtExceptionHandler defaultHandler;
  private Context appContext;
  private final Map<String, String> deviceInfo = new HashMap<>();

  private CrashCapture() {
  }

  public static CrashCapture getInstance() {
    return INSTANCE;
  }

  /**
   * 初始化崩溃捕获。
   *
   * @param context         Application Context
   * @param restartTime     重启延迟（保留参数，当前不使用）
   * @param restartActivity 重启目标 Activity（保留参数，当前不使用）
   */
  public void init(Context context, long restartTime, Class<?> restartActivity) {
    appContext = context.getApplicationContext();
    defaultHandler = Thread.getDefaultUncaughtExceptionHandler();
    Thread.setDefaultUncaughtExceptionHandler(this);
  }

  @Override
  public void uncaughtException(Thread thread, Throwable ex) {
    ex.printStackTrace();

    if (!handleException(ex) && defaultHandler != null) {
      defaultHandler.uncaughtException(thread, ex);
      return;
    }

    try {
      Thread.sleep(2000);
    } catch (InterruptedException ignored) {
    }

    String logFile = saveCrashInfo(ex);

    Intent crashIntent = new Intent(appContext, CrashDetailPage.class);
    crashIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
    if (!TextUtils.isEmpty(logFile)) {
      crashIntent.putExtra("crashFile", logFile);
    }
    appContext.startActivity(crashIntent);

    android.os.Process.killProcess(android.os.Process.myPid());
    System.exit(10);
  }

  private boolean handleException(Throwable ex) {
    if (ex == null) return false;
    collectDeviceInfo();
    return true;
  }

  private void collectDeviceInfo() {
    try {
      PackageManager pm = appContext.getPackageManager();
      PackageInfo pi = pm.getPackageInfo(appContext.getPackageName(), PackageManager.GET_ACTIVITIES);
      if (pi != null) {
        deviceInfo.put("versionName", pi.versionName != null ? pi.versionName : "null");
        deviceInfo.put("versionCode", String.valueOf(pi.versionCode));
      }
    } catch (PackageManager.NameNotFoundException e) {
      e.printStackTrace();
    }
    deviceInfo.put("osVersion", Build.VERSION.RELEASE);
    deviceInfo.put("sdkCode", String.valueOf(Build.VERSION.SDK_INT));
    deviceInfo.put("FINGERPRINT", Build.FINGERPRINT);
    deviceInfo.put("DISPLAY", Build.DISPLAY);
  }

  private String saveCrashInfo(Throwable ex) {
    StringBuilder sb = new StringBuilder();

    // 设备信息
    for (Map.Entry<String, String> entry : deviceInfo.entrySet()) {
      sb.append(entry.getKey()).append("=").append(entry.getValue()).append("\r\n");
    }

    // 异常堆栈
    StringWriter writer = new StringWriter();
    PrintWriter pw = new PrintWriter(writer);
    ex.printStackTrace(pw);
    Throwable cause = ex.getCause();
    while (cause != null) {
      cause.printStackTrace(pw);
      cause = cause.getCause();
    }
    pw.close();
    sb.append(writer.toString());

    // 写入文件
    String fileName = "crash-" + BuildConfig.VERSION_NAME
        + "-" + Build.DEVICE
        + "-" + Build.PRODUCT
        + "-" + Build.TYPE
        + "-" + DATE_FORMAT.format(new Date())
        + "-" + System.currentTimeMillis() + ".log";

    if (!Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())) {
      return null;
    }

    File dir = appContext.getExternalFilesDir("crash");
    if (dir == null) return null;

    Log.i(TAG, "Crash log dir: " + dir);
    if (!dir.exists()) {
      dir.mkdir();
    }

    try (FileOutputStream fos = new FileOutputStream(new File(dir, fileName))) {
      fos.write(sb.toString().getBytes());
      return fileName;
    } catch (IOException e) {
      e.printStackTrace();
    }
    return null;
  }
}