package io.github.coreyh321.timebox;

import android.content.Context;

import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

/** Lets the web app hand its schedule to the home-screen widget. */
@CapacitorPlugin(name = "WidgetBridge")
public class WidgetBridge extends Plugin {
    @PluginMethod
    public void update(PluginCall call) {
        String json = call.getString("data", "{}");
        Context ctx = getContext();
        ctx.getSharedPreferences(TimeBoxWidget.PREFS, Context.MODE_PRIVATE)
                .edit().putString("data", json).apply();
        TimeBoxWidget.refreshAll(ctx);
        call.resolve();
    }
}
