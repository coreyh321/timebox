package io.github.coreyh321.timebox;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.view.View;
import android.widget.RemoteViews;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/** Home-screen widget: current block, next blocks, top 3 priorities. */
public class TimeBoxWidget extends AppWidgetProvider {
    static final String PREFS = "timebox_widget";
    static final String ACTION_TICK = "io.github.coreyh321.timebox.WIDGET_TICK";
    private static final int[] NEXT_IDS = {R.id.w_next1, R.id.w_next2, R.id.w_next3};
    private static final int[] PRIO_IDS = {R.id.w_p1, R.id.w_p2, R.id.w_p3};

    @Override
    public void onUpdate(Context ctx, AppWidgetManager mgr, int[] ids) {
        RemoteViews v = build(ctx);
        for (int id : ids) mgr.updateAppWidget(id, v);
        scheduleTick(ctx);
    }

    @Override
    public void onReceive(Context ctx, Intent intent) {
        super.onReceive(ctx, intent);
        String a = intent.getAction();
        if (ACTION_TICK.equals(a) || Intent.ACTION_TIME_CHANGED.equals(a)
                || Intent.ACTION_TIMEZONE_CHANGED.equals(a) || Intent.ACTION_DATE_CHANGED.equals(a)) {
            refreshAll(ctx);
        }
    }

    @Override
    public void onDisabled(Context ctx) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am != null) am.cancel(tickIntent(ctx));
    }

    /** Called by the app whenever data changes, and by the half-hour tick. */
    static void refreshAll(Context ctx) {
        AppWidgetManager mgr = AppWidgetManager.getInstance(ctx);
        int[] ids = mgr.getAppWidgetIds(new ComponentName(ctx, TimeBoxWidget.class));
        if (ids == null || ids.length == 0) return;
        RemoteViews v = build(ctx);
        for (int id : ids) mgr.updateAppWidget(id, v);
        scheduleTick(ctx);
    }

    // ---------- data helpers ----------
    private static String dayKey(Calendar c) {
        return String.format(Locale.US, "%04d-%02d-%02d",
                c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH));
    }

    private static String slotKey(int slot) {
        return (slot / 2) + ":" + (slot % 2 == 0 ? "00" : "30");
    }

    private static String txt(JSONObject s, int slot) {
        if (s == null || slot < 0 || slot > 47) return "";
        return s.optString(slotKey(slot), "").trim();
    }

    private static String fmt(int slot) {
        int h = (slot / 2) % 24, m = slot % 2 == 0 ? 0 : 30;
        int h12 = h % 12 == 0 ? 12 : h % 12;
        return String.format(Locale.US, "%d:%02d%s", h12, m, h < 12 ? "a" : "p");
    }

    private static boolean same(String a, String b) {
        return !a.isEmpty() && a.equalsIgnoreCase(b);
    }

    static RemoteViews build(Context ctx) {
        RemoteViews v = new RemoteViews(ctx.getPackageName(), R.layout.timebox_widget);

        Intent open = ctx.getPackageManager().getLaunchIntentForPackage(ctx.getPackageName());
        if (open != null) {
            PendingIntent pi = PendingIntent.getActivity(ctx, 0, open,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            v.setOnClickPendingIntent(R.id.w_root, pi);
        }

        Calendar now = Calendar.getInstance();
        v.setTextViewText(R.id.w_date, String.format(Locale.getDefault(), "%1$ta, %1$tb %1$te", now));

        JSONObject days = null;
        try {
            String raw = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("data", "{}");
            days = new JSONObject(raw).optJSONObject("days");
        } catch (Exception ignored) { }
        if (days == null) days = new JSONObject();

        JSONObject today = days.optJSONObject(dayKey(now));
        JSONObject s = today == null ? null : today.optJSONObject("s");
        Calendar tm = (Calendar) now.clone();
        tm.add(Calendar.DAY_OF_MONTH, 1);
        JSONObject tomorrow = days.optJSONObject(dayKey(tm));
        JSONObject s2 = tomorrow == null ? null : tomorrow.optJSONObject("s");

        int slot = (now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)) / 30;

        // NOW
        String cur = txt(s, slot);
        if (!cur.isEmpty()) {
            int start = slot, end = slot;
            while (start > 0 && same(txt(s, start - 1), cur)) start--;
            while (end < 47 && same(txt(s, end + 1), cur)) end++;
            v.setTextViewText(R.id.w_now, cur);
            v.setTextViewText(R.id.w_now_time, fmt(start) + " – " + fmt(end + 1));
            v.setTextColor(R.id.w_now, 0xFFFFFFFF);
        } else {
            v.setTextViewText(R.id.w_now, "Open time");
            v.setTextViewText(R.id.w_now_time, "Nothing scheduled right now");
            v.setTextColor(R.id.w_now, 0xFFA3ABB8);
        }

        // UP NEXT (rest of today, then tomorrow)
        List<String> next = new ArrayList<>();
        for (int k = slot + 1; k <= 47 && next.size() < 3; k++) {
            String t = txt(s, k);
            if (!t.isEmpty() && !same(t, txt(s, k - 1))) next.add(fmt(k) + "   " + t);
        }
        for (int k = 0; k <= 47 && next.size() < 3; k++) {
            String t = txt(s2, k);
            if (!t.isEmpty() && !same(t, txt(s2, k - 1))) next.add("Tmrw " + fmt(k) + "   " + t);
        }
        for (int i = 0; i < NEXT_IDS.length; i++) {
            if (i < next.size()) {
                v.setTextViewText(NEXT_IDS[i], next.get(i));
                v.setViewVisibility(NEXT_IDS[i], View.VISIBLE);
            } else {
                v.setViewVisibility(NEXT_IDS[i], View.GONE);
            }
        }
        v.setViewVisibility(R.id.w_next_empty, next.isEmpty() ? View.VISIBLE : View.GONE);

        // TOP PRIORITIES
        JSONArray p = today == null ? null : today.optJSONArray("p");
        JSONArray pd = today == null ? null : today.optJSONArray("pd");
        int shown = 0;
        for (int i = 0; i < PRIO_IDS.length; i++) {
            String t = p == null ? "" : p.optString(i, "").trim();
            boolean done = pd != null && pd.optBoolean(i, false);
            if (t.isEmpty()) {
                v.setViewVisibility(PRIO_IDS[i], View.GONE);
            } else {
                v.setTextViewText(PRIO_IDS[i], (done ? "✓ " : (i + 1) + ".  ") + t);
                v.setTextColor(PRIO_IDS[i], done ? 0xFF6B7280 : 0xFFFFFFFF);
                v.setViewVisibility(PRIO_IDS[i], View.VISIBLE);
                shown++;
            }
        }
        v.setViewVisibility(R.id.w_p_empty, shown == 0 ? View.VISIBLE : View.GONE);
        return v;
    }

    // ---------- refresh at every :00 and :30 ----------
    private static PendingIntent tickIntent(Context ctx) {
        Intent i = new Intent(ctx, TimeBoxWidget.class).setAction(ACTION_TICK);
        return PendingIntent.getBroadcast(ctx, 7, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static void scheduleTick(Context ctx) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        Calendar c = Calendar.getInstance();
        c.set(Calendar.SECOND, 2);
        c.set(Calendar.MILLISECOND, 0);
        int m = c.get(Calendar.MINUTE);
        if (m < 30) c.set(Calendar.MINUTE, 30);
        else { c.set(Calendar.MINUTE, 0); c.add(Calendar.HOUR_OF_DAY, 1); }
        PendingIntent pi = tickIntent(ctx);
        try {
            if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
                am.setAndAllowWhileIdle(AlarmManager.RTC, c.getTimeInMillis(), pi);
            } else {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC, c.getTimeInMillis(), pi);
            }
        } catch (SecurityException e) {
            am.set(AlarmManager.RTC, c.getTimeInMillis(), pi);
        }
    }
}
