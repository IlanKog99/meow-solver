package com.ilan.meowsolver;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** One-screen setup: turn on the accessibility overlay. */
public class MainActivity extends Activity {
    @Override protected void onCreate(Bundle s) {
        super.onCreate(s);
        int p = (int) (24 * getResources().getDisplayMetrics().density);
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(p, p * 2, p, p);

        TextView t = new TextView(this);
        t.setTextSize(16);
        t.setText("Setup (once):\n\n"
                + "1. Tap 'App info' → ⋮ menu (top right) → 'Allow restricted settings'.\n"
                + "2. Tap 'Accessibility' → Installed apps → Meow Solver overlay → On.\n"
                + "3. Open Meowdoku. The panel floats on top.\n\n"
                + "Solve = show answer rings · Cat = place next cat · X = mark X's around every cat.\n"
                + "If a tap gives X instead of a cat, press the 'Tap order' button once.");
        l.addView(t);

        Button info = new Button(this);
        info.setText("App info");
        info.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + getPackageName()))));
        l.addView(info);

        Button acc = new Button(this);
        acc.setText("Accessibility");
        acc.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        l.addView(acc);

        setContentView(l);
    }
}
