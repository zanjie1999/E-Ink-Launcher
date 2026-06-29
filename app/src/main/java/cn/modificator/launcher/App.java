package cn.modificator.launcher;

import androidx.multidex.MultiDexApplication;

public class App extends MultiDexApplication {
  @Override
  public void onCreate() {
    super.onCreate();
    CrashCapture.getInstance().init(this, 1, Launcher.class);
  }


}
