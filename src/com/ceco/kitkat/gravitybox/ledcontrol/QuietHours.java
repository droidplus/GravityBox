/*
 * Copyright (C) 2014 Peter Gregus for GravityBox Project (C3C076@xda)
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
package com.ceco.kitkat.gravitybox.ledcontrol;

import java.util.Calendar;
import java.util.GregorianCalendar;

import com.ceco.kitkat.gravitybox.ModLedControl;
import com.ceco.kitkat.gravitybox.Utils;

import de.robv.android.xposed.XSharedPreferences;

public class QuietHours {
    boolean enabled;
    long start;
    long end;
    long startAlt;
    long endAlt;
    boolean muteLED;
    public boolean showStatusbarIcon;

    public QuietHours(XSharedPreferences prefs) {
        enabled = prefs.getBoolean(QuietHoursActivity.PREF_KEY_QH_ENABLED, false);
        start = prefs.getLong(QuietHoursActivity.PREF_KEY_QH_START, 0);
        end = prefs.getLong(QuietHoursActivity.PREF_KEY_QH_END, 0);
        startAlt = prefs.getLong(QuietHoursActivity.PREF_KEY_QH_START_ALT, 0);
        endAlt = prefs.getLong(QuietHoursActivity.PREF_KEY_QH_END_ALT, 0);
        muteLED = prefs.getBoolean(QuietHoursActivity.PREF_KEY_QH_MUTE_LED, false);
        showStatusbarIcon = prefs.getBoolean(QuietHoursActivity.PREF_KEY_QH_STATUSBAR_ICON, true);
    }

    public boolean quietHoursActive() {
        if (!enabled) return false;

        int endMin;
        Calendar c = new GregorianCalendar();
        c.setTimeInMillis(System.currentTimeMillis());
        int curMin = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE);
        int dayOfWeek = c.get(Calendar.DAY_OF_WEEK);
        boolean isFriday = dayOfWeek == Calendar.FRIDAY;
        boolean isSunday = dayOfWeek == Calendar.SUNDAY;
        long s = start; 
        long e = end;
        if (dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY) {
            s = startAlt;
            e = endAlt;
        }

        // special logic for Friday and Sunday
        // we assume people stay up longer on Friday
        // thus when Friday and we are after previous QH let's apply weekend range instead
        if (isFriday) {
            c.setTimeInMillis(end);
            endMin = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE);
            if (curMin > endMin) {
               s = startAlt;
               e = endAlt;
               if (ModLedControl.DEBUG) ModLedControl.log("Applying weekend range for Friday");
            }
        }
        // we assume people go to sleep earlier on Sunday
        // thus when Sunday and we are after previous QH let's apply weekdays range
        if (isSunday) {
            c.setTimeInMillis(endAlt);
            endMin = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE);
            if (curMin > endMin) {
               s = start;
               e = end;
               if (ModLedControl.DEBUG) ModLedControl.log("Applying weekdays range for Sunday");
            }
        }

        return (Utils.isTimeOfDayInRange(System.currentTimeMillis(), s, e));
    }

    public boolean quietHoursActiveIncludingLED() {
        return quietHoursActive() && muteLED;
    }
}