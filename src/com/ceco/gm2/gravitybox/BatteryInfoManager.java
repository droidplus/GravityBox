/*
 * Copyright (C) 2013 Peter Gregus for GravityBox Project (C3C076@xda)
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ceco.gm2.gravitybox;

import com.ceco.gm2.gravitybox.ledcontrol.QuietHours;

import java.util.ArrayList;

import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.BatteryManager;
import android.telephony.TelephonyManager;

public class BatteryInfoManager {
    private BatteryData mBatteryData;
    private ArrayList<BatteryStatusListener> mListeners;
    private Context mContext;
    private Uri[] mSounds;
    private TelephonyManager mTelephonyManager;

    public static final int SOUND_CHARGED = 0;
    public static final int SOUND_PLUGGED = 1;
    public static final int SOUND_UNPLUGGED = 2;

    class BatteryData {
        boolean charging;
        int level;
        int powerSource;
        int temperature;
        int voltage;

        public float getTempCelsius() {
            return ((float)temperature/10f);
        }

        public float getTempFahrenheit() {
            return (((float)temperature/10f)*(9f/5f)+32);
        }
    }

    public interface BatteryStatusListener {
        void onBatteryStatusChanged(BatteryData batteryData);
    }

    public BatteryInfoManager(Context context) {
        mContext = context;
        mBatteryData = new BatteryData();
        mListeners = new ArrayList<BatteryStatusListener>();
        mSounds = new Uri[3];
    }

    public void registerListener(BatteryStatusListener listener) {
        if (!mListeners.contains(listener)) {
            mListeners.add(listener);
            listener.onBatteryStatusChanged(mBatteryData);
        }
    }

    private void notifyListeners() {
        for (BatteryStatusListener listener : mListeners) {
            listener.onBatteryStatusChanged(mBatteryData);
        }
    }

    public void updateBatteryInfo(Intent intent) {
        if (intent == null) return;

        int newLevel = (int)(100f
                * intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0)
                / intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100));
        int newPowerSource = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);
        boolean newCharging = newPowerSource != 0;
        int newTemp = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0);
        int newVoltage = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0);

        if (mBatteryData.level != newLevel || mBatteryData.charging != newCharging ||
                mBatteryData.powerSource != newPowerSource ||
                mBatteryData.temperature != newTemp || 
                mBatteryData.voltage != newVoltage) {
            if (newLevel == 100 && mBatteryData.level < 100 && mBatteryData.level > 0) {
                playSound(SOUND_CHARGED);
            }

            if (mBatteryData.powerSource != newPowerSource) {
                if (newPowerSource == 0) {
                    playSound(SOUND_UNPLUGGED);
                } else if (newPowerSource != BatteryManager.BATTERY_PLUGGED_WIRELESS) {
                    playSound(SOUND_PLUGGED);
                }
            }

            mBatteryData.level = newLevel;
            mBatteryData.charging = newCharging;
            mBatteryData.powerSource = newPowerSource;
            mBatteryData.temperature = newTemp;
            mBatteryData.voltage = newVoltage;

            notifyListeners();
        }
    }

    public BatteryData getCurrentBatteryData() {
        return mBatteryData;
    }

    public void setSound(int type, String uri) {
        if (type < 0 || type > (mSounds.length-1)) return;

        if (uri == null || uri.isEmpty()) {
            mSounds[type] = null;
        } else {
            try {
                mSounds[type] = Uri.parse(uri);
            } catch (Exception e) {
                mSounds[type] = null;
            }
        }
    }

    private void playSound(int type) {
        if (type < 0 || type > (mSounds.length-1) || mSounds[type] == null || 
                !isPhoneIdle() || quietHoursActive()) return;
        try {
            final Ringtone sfx = RingtoneManager.getRingtone(mContext, mSounds[type]);
            if (sfx != null) {
                sfx.setStreamType(AudioManager.STREAM_NOTIFICATION);
                sfx.play();
            }
        } catch (Throwable t) {
            XposedBridge.log(t);
        }
    }

    private boolean isPhoneIdle() {
        if (Utils.isWifiOnly(mContext)) return true;
        try {
            if (mTelephonyManager == null) {
                mTelephonyManager = (TelephonyManager) mContext.getSystemService(Context.TELEPHONY_SERVICE);
            }
            return (mTelephonyManager.getCallState() == TelephonyManager.CALL_STATE_IDLE);
        } catch (Throwable t) {
            XposedBridge.log(t);
            return true;
        }
    }

    private boolean quietHoursActive() {
        QuietHours qh = new QuietHours(
                new XSharedPreferences(GravityBox.PACKAGE_NAME, "ledcontrol"));
        return qh.quietHoursActive();
    }
}
