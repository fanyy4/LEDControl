/*          Copyright (c) 2015 Krishanu Dutta Bhuyan
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package com.kdb.ledcontrol;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.TaskStackBuilder;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * Created by KDB on 17/12/2014.
 */
public class LEDManager {
    private static final String pathToLED = "/sys/class/leds/charging/trigger";
    private static final String pathToLEDdir = "/sys/class/leds/";
    private static String devicePathToLED = null;
    private static String devicePathToBrightness = null;
    private static final String getPathToLEDdirAlter = "/sys/class/pm8xx-led/leds";
    private static final String pathToBrightness = "/sys/class/leds/charging/max_brightness";
    private static final String TAG = "LED Control";
    private String SHARED_PREF;
    public SharedPreferences prefs;
    public SharedPreferences.Editor prefsEditor;
    public String cmd;
    public boolean rooted;
    private Context context;
    private Process process;
    private DataOutputStream deviceInput;
    private BufferedReader deviceOutput;
    private String DEVICE_MOTO_X = "ghost";
    private String DEVICE_MOTO_G = "falcon";
    private String DEVICE_MOTO_E = "condor";
    private String DEVICE_NEXUS_6 = "shamu";
    private String DEVICE_MAXX = "obake-maxx";
    private String DEVICE_ULTRA = "obake";
    public final int TRIGGER_BATTERY_CHARGING = 1;
    public final int TRIGGER_BATTERY_FULL = 2;
    public final int TRIGGER_BATTERY_CHARGING_OR_FULL = 3;
    public final int TRIGGER_BATTERY_CHARGING_BLINKING_OR_FULL = 4;
    public final int TRIGGER_USB = 5;
    public final int TRIGGER_BKL_or_MOTOX_ADAPTER = 6;
    public final int TRIGGER_DISK_IO = 7;
    public final int TRIGGER_EXT_IO = 8;
    public final int TRIGGER_BLUETOOTH = 9;
    public final int TRIGGER_TORCH = 10;
    public final int TRIGGER_FLASH = 11;
    public final int TRIGGER_ALWAYS = 12;
    private File chargingDir;
    private File brightnessDir;

    public LEDManager(Context context) {
        this.context = context;
        process = null;
        rooted = true;
        SHARED_PREF = context.getPackageName() + ".prefs";
        prefs = context.getSharedPreferences(SHARED_PREF, 0);
        prefsEditor = prefs.edit();
        //check for root
        try {
            process = Runtime.getRuntime().exec("su");
        } catch (IOException e) {
            e.printStackTrace();
            if (process != null) process.destroy();
        }
        //confirming root only if the command "id" has "root"
        if (process != null) {
            deviceOutput = new BufferedReader(new InputStreamReader(process.getInputStream()));
            deviceInput = new DataOutputStream(process.getOutputStream());
            String user = getUser();
            if ((user == null) || (!user.contains("root"))) {
                rooted = false;
            }
        } else {
            rooted = false;
        }
        chargingDir = new File(pathToLEDdir + "charging");
        brightnessDir = new File(pathToBrightness);
        try {
            devicePathToBrightness = brightnessDir.getCanonicalPath();
            devicePathToLED = chargingDir.getCanonicalPath() + "/trigger";
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /*
     *  Returns output from executing command "id"
     */
    private String getUser() {
        if (deviceInput == null || deviceOutput == null) {
            return null;
        }
        String user = null;
        try {
            deviceInput.writeBytes("id\n");
            deviceInput.flush();
            user = deviceOutput.readLine();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return user;
    }

    /*
     *  Dynamically sets the value of cmd based on user's choice
     */
    public void setChoice(int Choice) {
        //Don't proceed further if no root access:
        if (!rooted)
            return;
        if (Choice == -2) {   //for (int) lastSelected in MainActivity
            cmd = null;
        } else if (Choice == 0) {
            cmd = "none";
        } else if (Choice == R.id.rBtn_Charging) {
            cmd = "battery-charging";
        } else if (Choice == R.id.rBtn_Full) {
            cmd = "battery-full";
        } else if (Choice == R.id.rBtn_Charging_or_full) {
            cmd = "battery-charging-or-full";
        } else if (Choice == R.id.rBtn_Charging_or_full_blink) {
            if (getDevice().equals(DEVICE_MOTO_X)) {
                cmd = "heartbeat";
            } else {
                cmd = "battery-charging-blink-full-solid";
            }
        } else if (Choice == R.id.rBtn_USB) {
            cmd = "usb-online";
        } else if (Choice == R.id.rBtn_Display) {
            if (getDevice().equals(DEVICE_NEXUS_6)) {
                cmd = "backlight";
            } else {
                cmd = "bkl-trigger";
            }
        } else if (Choice == R.id.rBtn_Disk_IO) {
            cmd = "mmc0";
        } else if (Choice == R.id.rBtn_Ext_IO) {
            cmd = "mmc1";
        } else if (Choice == R.id.rBtn_Charging_adapter) {
            if (getDevice().equals(DEVICE_NEXUS_6)) {
                cmd = "dc-online";
            } else {
                cmd = "pm8921-dc-online";
            }
        } else if (Choice == R.id.rBtn_Bluetooth) {
            if (getDevice().equals(DEVICE_NEXUS_6)) {
                cmd = "rkfill0";
            } else cmd = "rkfill1";
        } else if (Choice == R.id.rBtn_Always_on) {
            cmd = "default-on";
        } else if (Choice == R.id.rBtn_Cam_flash) {
            cmd = "flash0_trigger";
        } else if (Choice == R.id.rBtn_Torch) {
            cmd = "torch_trigger";
        }
    }

    /*
     * Returns true if a directory called `charging` exists in /sys/class/leds/, to determine if the device is supported
     */
    public boolean isDeviceSupported() {
        return  devicePathToLED != null;
    }

    /*
     *  Returns an integer value depending on currently set trigger
     */
    public int checkState() {
        //not aborting if not rooted here, but the directory needs to exist if we wish to 'cat' it
        if (!isDeviceSupported()){
            return -1;
        }
        String output = null;
        int val = 0;
        try {
            deviceInput.writeBytes("cat " + devicePathToLED + "\n");
            deviceInput.flush();
            output = this.deviceOutput.readLine();
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (output == null)
            return -1;
        else if (output.contains("[battery-charging]"))
            val = TRIGGER_BATTERY_CHARGING;
        else if (output.contains("[battery-full]"))
            val = TRIGGER_BATTERY_FULL;
        else if (output.contains("[battery-charging-or-full]"))
            val = TRIGGER_BATTERY_CHARGING_OR_FULL;
        else if (output.contains("[battery-charging-blink-full-solid]") || output.contains("[heartbeat]"))
            val = TRIGGER_BATTERY_CHARGING_BLINKING_OR_FULL;
        else if (output.contains("[usb-online]"))
            val = TRIGGER_USB;
        else if (output.contains("[bkl-trigger]") || output.contains("[pm8921-dc-online]") || output.contains("[backlight]"))
            val = TRIGGER_BKL_or_MOTOX_ADAPTER;
        else if (output.contains("[mmc0]"))
            val = TRIGGER_DISK_IO;
        else if (output.contains("[mmc1]"))
            val = TRIGGER_EXT_IO;
        else if (output.contains("[rkfill0]") || output.contains("[rkfill1]"))
            val = TRIGGER_BLUETOOTH;
        else if (output.contains("[torch_trigger]"))
            val = TRIGGER_TORCH;
        else if (output.contains("[flash0_trigger]"))
            val = TRIGGER_FLASH;
        else if (output.contains("[default-on]"))
            val = TRIGGER_ALWAYS;
        return val;
    }

    /*
     *  Returns an integer value depending on currently set brightness
     */
    public int checkBrightness() {
        int brightness = 0;
        if (!isDeviceSupported() || devicePathToBrightness == null){
            return 0;
        }
        try {
            deviceInput.writeBytes("cat " + devicePathToBrightness + "\n");
            deviceInput.flush();
            brightness = Integer.parseInt(deviceOutput.readLine());
        } catch (IOException e) {
            e.printStackTrace();
        }
        return brightness / 25;
    }

    /*
     *  Brings the app to life; the core methods - Apply() and ApplyBrightness()
     */
    public void Apply() {
        //again, stop if root not found:
        if (!rooted || cmd == null || !isDeviceSupported())
            return;
        try {
            deviceInput.writeBytes("echo " + cmd + " > " + devicePathToLED + "\n");
            deviceInput.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
        prefsEditor.putString("last_cmd", cmd).apply();
        Log.i(TAG, "Successfully set trigger: " + cmd);
    }

    public void ApplyBrightness(int brightness) {
        if (!rooted || !isDeviceSupported() || devicePathToBrightness == null) return;
        if (brightness != 10) brightness *= 25;
        else brightness = 255;
        try {
            deviceInput.writeBytes("echo " + Integer.toString(brightness) + " > " + devicePathToBrightness + "\n");
            deviceInput.flush();
        } catch (IOException e) {
            e.printStackTrace();
    }
        prefsEditor.putInt("last_brightness", brightness).apply();
    }

    /*
     *  Returns a String value based on user's device.
     *  Since the app only runs on Moto G, E and X, we need to check if the device is correct
     */
    public String getDevice() {
        if (deviceInput == null || deviceOutput == null) return null;
        String device = null;
        String output = Build.DEVICE;
        if (output.contains(DEVICE_MOTO_E)) device = DEVICE_MOTO_E;
        else if (output.contains(DEVICE_MOTO_G)) device = DEVICE_MOTO_G;
        else if (output.contains(DEVICE_MOTO_X)) device = DEVICE_MOTO_X;
        else if (output.contains(DEVICE_NEXUS_6)) device = DEVICE_NEXUS_6;
        else if (output.contains(DEVICE_MAXX)) device = DEVICE_MAXX;
        else if (output.contains(DEVICE_ULTRA)) device = DEVICE_ULTRA;
        return device;
    }

    public void setOnBoot() {
        String cmd = prefs.getString("last_cmd", null);
        int brightness = prefs.getInt("last_brightness", -1);
        if (brightness != -1 && devicePathToBrightness != null) {
            brightness /= 25;
            ApplyBrightness(brightness);
        }
        if (cmd != null && devicePathToLED != null) {
            try {
                deviceInput.writeBytes("echo " + cmd + " > " + devicePathToLED + "\n");
                deviceInput.flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        if (checkState() != -1 && prefs.getBoolean("show_notif", true)) {
            Log.i(TAG, "LED trigger applied on boot");
            Bitmap largeIcon = BitmapFactory.decodeResource(context.getResources(), R.drawable.ic_launcher);
            NotificationCompat.Builder builder = new NotificationCompat.Builder(context)
                    .setSmallIcon(R.drawable.ic_stat_notification)
                    .setLargeIcon(largeIcon)
                    .setContentTitle(context.getString(R.string.app_name_full))
                    .setContentText(context.getString(R.string.notification_content));
            Intent resultIntent = new Intent(context, MainActivity.class);
            TaskStackBuilder stackBuilder = TaskStackBuilder.create(context);
            stackBuilder.addParentStack(MainActivity.class);
            stackBuilder.addNextIntent(resultIntent);
            PendingIntent resultPendingIntent = stackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT);
            builder.setContentIntent(resultPendingIntent);
            NotificationManager mNotificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            mNotificationManager.notify(0, builder.build());
        }
    }
}