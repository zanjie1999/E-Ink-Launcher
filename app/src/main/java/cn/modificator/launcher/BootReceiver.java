package cn.modificator.launcher;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * 开机启动
 */
public final class BootReceiver extends BroadcastReceiver {

  @Override
  public void onReceive(Context context, Intent intent) {
    if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())
        || !new Config(context).isStartAtBoot()) {
      return;
    }

    Intent launchIntent = new Intent(context, Launcher.class);
    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
    context.startActivity(launchIntent);
  }
}
