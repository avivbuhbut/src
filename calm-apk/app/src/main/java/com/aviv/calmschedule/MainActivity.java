package com.aviv.calmschedule;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.CalendarContract;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class MainActivity extends Activity {
    private static final int REQ_CALENDAR = 4101;
    private static final int BG = Color.rgb(246, 245, 241);
    private static final int CARD = Color.WHITE;
    private static final int TEXT = Color.rgb(39, 46, 42);
    private static final int MUTED = Color.rgb(102, 111, 105);
    private static final int ACCENT = Color.rgb(71, 103, 84);
    private static final int ACCENT_SOFT = Color.rgb(229, 238, 232);
    private static final int BORDER = Color.rgb(224, 226, 221);

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final AtomicInteger loadGeneration = new AtomicInteger();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private LinearLayout root;
    private LinearLayout content;
    private TextView dateTitle;
    private TextView statusText;
    private Button todayButton;
    private LocalDate selectedDay;
    private boolean followToday = true;
    private SharedPreferences prefs;
    private boolean observerRegistered = false;
    private boolean receiverRegistered = false;

    private final ContentObserver calendarObserver = new ContentObserver(mainHandler) {
        @Override public void onChange(boolean selfChange) {
            scheduleReload();
        }
        @Override public void onChange(boolean selfChange, Uri uri) {
            scheduleReload();
        }
    };

    private final Runnable delayedReload = new Runnable() {
        @Override public void run() {
            reload();
        }
    };

    private final BroadcastReceiver timeReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_DATE_CHANGED.equals(intent.getAction()) && followToday) {
                selectedDay = LocalDate.now();
                saveSelection();
            }
            updateHeader();
            reload();
        }
    };

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        getWindow().getDecorView().setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        prefs = getSharedPreferences("calm_schedule", MODE_PRIVATE);
        restoreSelection(state);
        buildUi();

        if (!hasCalendarPermission()) {
            renderPermissionState(false);
        } else {
            renderLoading();
        }
    }

    @Override protected void onStart() {
        super.onStart();
        if (followToday && !selectedDay.equals(LocalDate.now())) {
            selectedDay = LocalDate.now();
            saveSelection();
        }
        updateHeader();
        registerTimeReceiver();
        registerCalendarObserverIfPossible();
        if (hasCalendarPermission()) reload();
    }

    @Override protected void onResume() {
        super.onResume();
        if (hasCalendarPermission()) {
            registerCalendarObserverIfPossible();
            reload();
        } else {
            renderPermissionState(false);
        }
    }

    @Override protected void onStop() {
        super.onStop();
        mainHandler.removeCallbacks(delayedReload);
        unregisterCalendarObserver();
        unregisterTimeReceiver();
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putString("selectedDay", selectedDay.toString());
        out.putBoolean("followToday", followToday);
    }

    private void restoreSelection(Bundle state) {
        LocalDate today = LocalDate.now();
        if (state != null) {
            followToday = state.getBoolean("followToday", true);
            String saved = state.getString("selectedDay");
            selectedDay = safeDate(saved, today);
            if (followToday) selectedDay = today;
            return;
        }
        followToday = prefs.getBoolean("followToday", true);
        selectedDay = safeDate(prefs.getString("selectedDay", today.toString()), today);
        if (followToday) selectedDay = today;
    }

    private LocalDate safeDate(String value, LocalDate fallback) {
        try { return value == null ? fallback : LocalDate.parse(value); }
        catch (Exception ignored) { return fallback; }
    }

    private void saveSelection() {
        prefs.edit()
                .putString("selectedDay", selectedDay.toString())
                .putBoolean("followToday", followToday)
                .apply();
    }

    private void buildUi() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        root.setPadding(dp(18), dp(14), dp(18), dp(14));
        setContentView(root);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        TextView appTitle = text("Calm Schedule", 25, TEXT, true);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        top.addView(appTitle, titleParams);

        Button refresh = smallButton("↻");
        refresh.setContentDescription("רענון");
        refresh.setOnClickListener(v -> reload());
        top.addView(refresh);

        Button add = smallButton("+");
        add.setContentDescription("אירוע חדש");
        add.setOnClickListener(v -> addEvent());
        top.addView(add);
        root.addView(top);

        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setGravity(Gravity.CENTER_VERTICAL);
        nav.setPadding(0, dp(12), 0, dp(2));

        Button next = smallButton("‹");
        next.setContentDescription("היום הבא");
        next.setOnClickListener(v -> changeDay(1));
        nav.addView(next);

        dateTitle = text("", 20, TEXT, true);
        dateTitle.setGravity(Gravity.CENTER);
        dateTitle.setOnClickListener(v -> showDatePicker());
        nav.addView(dateTitle, new LinearLayout.LayoutParams(0, dp(48), 1f));

        Button prev = smallButton("›");
        prev.setContentDescription("היום הקודם");
        prev.setOnClickListener(v -> changeDay(-1));
        nav.addView(prev);
        root.addView(nav);

        todayButton = button("היום");
        todayButton.setOnClickListener(v -> goToday());
        root.addView(todayButton, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)));

        statusText = text("", 13, MUTED, false);
        statusText.setGravity(Gravity.CENTER);
        statusText.setPadding(0, dp(8), 0, dp(8));
        statusText.setOnClickListener(v -> showSyncExplanation());
        root.addView(statusText);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, dp(2), 0, dp(22));
        scroll.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        updateHeader();
    }

    private void updateHeader() {
        if (dateTitle == null) return;
        Locale he = new Locale("he", "IL");
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("EEEE, d בMMMM", he);
        dateTitle.setText(selectedDay.format(formatter));
        boolean isToday = selectedDay.equals(LocalDate.now());
        todayButton.setVisibility(isToday ? View.GONE : View.VISIBLE);
    }

    private void changeDay(long delta) {
        selectedDay = selectedDay.plusDays(delta);
        followToday = selectedDay.equals(LocalDate.now());
        saveSelection();
        updateHeader();
        reload();
    }

    private void goToday() {
        selectedDay = LocalDate.now();
        followToday = true;
        saveSelection();
        updateHeader();
        reload();
    }

    private void showDatePicker() {
        DatePickerDialog dialog = new DatePickerDialog(
                this,
                (DatePicker view, int year, int month, int day) -> {
                    selectedDay = LocalDate.of(year, month + 1, day);
                    followToday = selectedDay.equals(LocalDate.now());
                    saveSelection();
                    updateHeader();
                    reload();
                },
                selectedDay.getYear(),
                selectedDay.getMonthValue() - 1,
                selectedDay.getDayOfMonth());
        dialog.show();
    }

    private boolean hasCalendarPermission() {
        return checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestCalendarPermission() {
        requestPermissions(new String[]{Manifest.permission.READ_CALENDAR}, REQ_CALENDAR);
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode != REQ_CALENDAR) return;
        if (hasCalendarPermission()) {
            registerCalendarObserverIfPossible();
            reload();
        } else {
            renderPermissionState(true);
        }
    }

    private void renderPermissionState(boolean denied) {
        if (content == null) return;
        content.removeAllViews();
        content.addView(spacer(24));
        TextView title = text("כדי להציג את הלו״ז צריך גישה ליומן", 20, TEXT, true);
        title.setGravity(Gravity.CENTER);
        content.addView(title);
        TextView body = text(
                "Calm Schedule קוראת את האירועים שכבר מסונכרנים לטלפון. היא לא מוחקת או משנה את היומנים שלך.",
                15, MUTED, false);
        body.setGravity(Gravity.CENTER);
        body.setPadding(dp(8), dp(12), dp(8), dp(18));
        content.addView(body);

        Button grant = button(denied ? "נסה לתת הרשאה שוב" : "אפשר גישה ליומן");
        grant.setOnClickListener(v -> requestCalendarPermission());
        content.addView(grant, matchWrapMargins(0, 4, 0, 6));

        if (denied && !shouldShowRequestPermissionRationale(Manifest.permission.READ_CALENDAR)) {
            Button settings = button("פתח הגדרות הרשאות");
            settings.setOnClickListener(v -> {
                Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", getPackageName(), null));
                startActivity(i);
            });
            content.addView(settings, matchWrapMargins(0, 6, 0, 0));
        }
        statusText.setText("אין כרגע הרשאת יומן");
    }

    private void renderLoading() {
        content.removeAllViews();
        TextView t = text("טוען את היום…", 17, MUTED, false);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, dp(36), 0, dp(18));
        content.addView(t);
    }

    private void reload() {
        if (!hasCalendarPermission()) {
            renderPermissionState(false);
            return;
        }
        final int generation = loadGeneration.incrementAndGet();
        final LocalDate day = selectedDay;
        statusText.setText("מרענן…");

        executor.execute(() -> {
            LoadResult result;
            try {
                result = loadDay(day);
            } catch (SecurityException e) {
                mainHandler.post(() -> {
                    if (generation == loadGeneration.get()) renderPermissionState(true);
                });
                return;
            } catch (Throwable e) {
                mainHandler.post(() -> {
                    if (generation == loadGeneration.get()) renderError(e);
                });
                return;
            }

            mainHandler.post(() -> {
                if (generation != loadGeneration.get() || !day.equals(selectedDay)) return;
                renderResult(result);
            });
        });
    }

    private LoadResult loadDay(LocalDate day) {
        Map<Long, CalendarMeta> calendars = readCalendars();
        if (calendars.isEmpty()) return new LoadResult(new ArrayList<>(), 0, 0);

        ZoneId zone = ZoneId.systemDefault();
        long dayStart = day.atStartOfDay(zone).toInstant().toEpochMilli();
        long dayEnd = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli();

        long queryStart = day.minusDays(1).atStartOfDay(zone).toInstant().toEpochMilli();
        long queryEnd = day.plusDays(2).atStartOfDay(zone).toInstant().toEpochMilli();

        Uri.Builder builder = CalendarContract.Instances.CONTENT_URI.buildUpon();
        ContentUris.appendId(builder, queryStart);
        ContentUris.appendId(builder, queryEnd);

        String[] projection = {
                CalendarContract.Instances.EVENT_ID,
                CalendarContract.Instances.BEGIN,
                CalendarContract.Instances.END,
                CalendarContract.Instances.TITLE,
                CalendarContract.Instances.ALL_DAY,
                CalendarContract.Instances.CALENDAR_ID,
                CalendarContract.Instances.EVENT_LOCATION
        };

        LinkedHashMap<String, EventItem> unique = new LinkedHashMap<>();
        try (Cursor c = getContentResolver().query(
                builder.build(), projection, null, null,
                CalendarContract.Instances.BEGIN + " ASC")) {
            if (c != null) {
                int idIx = c.getColumnIndexOrThrow(CalendarContract.Instances.EVENT_ID);
                int beginIx = c.getColumnIndexOrThrow(CalendarContract.Instances.BEGIN);
                int endIx = c.getColumnIndexOrThrow(CalendarContract.Instances.END);
                int titleIx = c.getColumnIndexOrThrow(CalendarContract.Instances.TITLE);
                int allDayIx = c.getColumnIndexOrThrow(CalendarContract.Instances.ALL_DAY);
                int calendarIx = c.getColumnIndexOrThrow(CalendarContract.Instances.CALENDAR_ID);
                int locationIx = c.getColumnIndexOrThrow(CalendarContract.Instances.EVENT_LOCATION);

                while (c.moveToNext()) {
                    long calendarId = c.getLong(calendarIx);
                    CalendarMeta meta = calendars.get(calendarId);
                    if (meta == null) continue;

                    long eventId = c.getLong(idIx);
                    long begin = c.getLong(beginIx);
                    long end = c.getLong(endIx);
                    boolean allDay = c.getInt(allDayIx) != 0;
                    if (end <= begin) end = begin + 1;

                    if (allDay) {
                        LocalDate b = Instant.ofEpochMilli(begin).atZone(ZoneOffset.UTC).toLocalDate();
                        LocalDate e = Instant.ofEpochMilli(end).atZone(ZoneOffset.UTC).toLocalDate();
                        if (day.isBefore(b) || !day.isBefore(e)) continue;
                    } else {
                        if (!(end > dayStart && begin < dayEnd)) continue;
                    }

                    String title = c.isNull(titleIx) ? "" : c.getString(titleIx);
                    if (title == null || title.trim().isEmpty()) title = "ללא כותרת";
                    String location = c.isNull(locationIx) ? "" : c.getString(locationIx);

                    EventItem item = new EventItem(eventId, begin, end, title, allDay,
                            calendarId, meta.name, meta.account, location);
                    unique.put(eventId + ":" + begin + ":" + end, item);
                }
            }
        }

        ArrayList<EventItem> events = new ArrayList<>(unique.values());
        Collections.sort(events, (a, b) -> {
            if (a.allDay != b.allDay) return a.allDay ? -1 : 1;
            int byBegin = Long.compare(a.begin, b.begin);
            if (byBegin != 0) return byBegin;
            return Long.compare(a.eventId, b.eventId);
        });

        long busyMillis = computeBusyMillis(events, dayStart, dayEnd);
        return new LoadResult(events, calendars.size(), busyMillis);
    }

    private Map<Long, CalendarMeta> readCalendars() {
        Map<Long, CalendarMeta> map = new HashMap<>();
        String[] projection = {
                CalendarContract.Calendars._ID,
                CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
                CalendarContract.Calendars.ACCOUNT_NAME,
                CalendarContract.Calendars.VISIBLE,
                CalendarContract.Calendars.SYNC_EVENTS
        };

        try (Cursor c = getContentResolver().query(
                CalendarContract.Calendars.CONTENT_URI,
                projection, null, null, null)) {
            if (c == null) return map;
            int idIx = c.getColumnIndexOrThrow(CalendarContract.Calendars._ID);
            int nameIx = c.getColumnIndexOrThrow(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME);
            int accountIx = c.getColumnIndexOrThrow(CalendarContract.Calendars.ACCOUNT_NAME);
            int visibleIx = c.getColumnIndexOrThrow(CalendarContract.Calendars.VISIBLE);
            int syncIx = c.getColumnIndexOrThrow(CalendarContract.Calendars.SYNC_EVENTS);

            while (c.moveToNext()) {
                boolean visible = c.getInt(visibleIx) != 0;
                boolean synced = c.getInt(syncIx) != 0;
                if (!visible && !synced) continue;
                long id = c.getLong(idIx);
                String name = c.isNull(nameIx) ? "יומן" : c.getString(nameIx);
                String account = c.isNull(accountIx) ? "" : c.getString(accountIx);
                map.put(id, new CalendarMeta(name, account));
            }
        }
        return map;
    }

    private long computeBusyMillis(List<EventItem> events, long dayStart, long dayEnd) {
        List<long[]> spans = new ArrayList<>();
        for (EventItem e : events) {
            if (e.allDay) continue;
            long s = Math.max(dayStart, e.begin);
            long t = Math.min(dayEnd, e.end);
            if (t > s) spans.add(new long[]{s, t});
        }
        spans.sort(Comparator.comparingLong(a -> a[0]));
        long total = 0, s = -1, e = -1;
        for (long[] span : spans) {
            if (s < 0) {
                s = span[0]; e = span[1];
            } else if (span[0] <= e) {
                e = Math.max(e, span[1]);
            } else {
                total += e - s;
                s = span[0]; e = span[1];
            }
        }
        if (s >= 0) total += e - s;
        return total;
    }

    private void renderResult(LoadResult result) {
        content.removeAllViews();
        updateHeader();

        String busy = formatDuration(result.busyMillis);
        String summary = result.events.size() + " אירועים";
        if (result.busyMillis > 0) summary += " • " + busy + " תפוס";
        TextView daySummary = text(summary, 15, MUTED, false);
        daySummary.setGravity(Gravity.CENTER);
        daySummary.setPadding(0, dp(4), 0, dp(10));
        content.addView(daySummary);

        EventItem focus = findFocusEvent(result.events);
        if (focus != null) content.addView(focusCard(focus), matchWrapMargins(0, 0, 0, 12));

        if (result.events.isEmpty()) {
            TextView empty = text(
                    selectedDay.equals(LocalDate.now()) ? "אין אירועים היום" : "אין אירועים ביום הזה",
                    20, TEXT, true);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(8), dp(54), dp(8), dp(8));
            content.addView(empty);

            TextView calm = text("היום פנוי. אפשר להשאיר קצת מרווח.", 15, MUTED, false);
            calm.setGravity(Gravity.CENTER);
            content.addView(calm);
        } else {
            for (EventItem event : result.events) {
                content.addView(eventCard(event), matchWrapMargins(0, 0, 0, 10));
            }
        }

        String updated = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));
        statusText.setText("עודכן " + updated + " • " + result.calendarCount + " יומנים מסונכרנים במכשיר");
    }

    private EventItem findFocusEvent(List<EventItem> events) {
        if (!selectedDay.equals(LocalDate.now())) return null;
        long now = System.currentTimeMillis();
        EventItem future = null;
        for (EventItem e : events) {
            if (e.allDay) continue;
            if (e.begin <= now && now < e.end) return e;
            if (e.begin > now && (future == null || e.begin < future.begin)) future = e;
        }
        return future;
    }

    private View focusCard(EventItem event) {
        LinearLayout card = card(ACCENT_SOFT);
        long now = System.currentTimeMillis();
        boolean current = event.begin <= now && now < event.end;
        TextView label = text(current ? "קורה עכשיו" : "האירוע הבא", 13, ACCENT, true);
        card.addView(label);
        TextView title = text(event.title, 21, TEXT, true);
        title.setPadding(0, dp(4), 0, dp(3));
        card.addView(title);
        TextView time = text(formatRange(event), 16, ACCENT, true);
        card.addView(time);
        String meta = event.calendarName;
        if (!event.location.isEmpty()) meta += " • " + event.location;
        TextView info = text(meta, 13, MUTED, false);
        info.setPadding(0, dp(4), 0, 0);
        card.addView(info);
        card.setOnClickListener(v -> openEvent(event));
        return card;
    }

    private View eventCard(EventItem event) {
        LinearLayout card = card(CARD);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.TOP);
        row.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        LinearLayout details = new LinearLayout(this);
        details.setOrientation(LinearLayout.VERTICAL);
        TextView title = text(event.title, 18, TEXT, true);
        title.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG);
        details.addView(title);

        String source = event.calendarName;
        if (!event.account.isEmpty() && !event.account.equals(event.calendarName)) {
            source += " • " + event.account;
        }
        TextView meta = text(source, 12, MUTED, false);
        meta.setPadding(0, dp(5), 0, 0);
        details.addView(meta);

        if (!event.location.isEmpty()) {
            TextView location = text(event.location, 12, MUTED, false);
            location.setPadding(0, dp(3), 0, 0);
            details.addView(location);
        }
        row.addView(details, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView time = text(event.allDay ? "כל היום" : formatRange(event), 14, ACCENT, true);
        time.setGravity(Gravity.END);
        time.setPadding(dp(10), 0, 0, 0);
        row.addView(time);
        card.addView(row);

        card.setOnClickListener(v -> openEvent(event));
        card.setOnLongClickListener(v -> {
            openEvent(event);
            return true;
        });
        return card;
    }

    private void openEvent(EventItem event) {
        Uri uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, event.eventId);
        Intent intent = new Intent(Intent.ACTION_VIEW, uri);
        intent.putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, event.begin);
        intent.putExtra(CalendarContract.EXTRA_EVENT_END_TIME, event.end);
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "לא נמצאה אפליקציית יומן לפתיחת האירוע", Toast.LENGTH_LONG).show();
        }
    }

    private void addEvent() {
        ZoneId zone = ZoneId.systemDefault();
        LocalDateTime start;
        if (selectedDay.equals(LocalDate.now())) {
            LocalDateTime now = LocalDateTime.now().plusHours(1).withMinute(0).withSecond(0).withNano(0);
            start = now;
        } else {
            start = selectedDay.atTime(9, 0);
        }
        long begin = start.atZone(zone).toInstant().toEpochMilli();
        long end = start.plusHours(1).atZone(zone).toInstant().toEpochMilli();

        Intent intent = new Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI);
        intent.putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, begin);
        intent.putExtra(CalendarContract.EXTRA_EVENT_END_TIME, end);
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "לא נמצאה אפליקציית יומן להוספת אירוע", Toast.LENGTH_LONG).show();
        }
    }

    private void renderError(Throwable error) {
        content.removeAllViews();
        TextView title = text("לא הצלחתי לקרוא את היומן", 20, TEXT, true);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, dp(34), 0, dp(8));
        content.addView(title);
        TextView body = text("אפשר לנסות שוב. האירועים שלך לא שונו.", 14, MUTED, false);
        body.setGravity(Gravity.CENTER);
        content.addView(body);
        Button retry = button("נסה שוב");
        retry.setOnClickListener(v -> reload());
        content.addView(retry, matchWrapMargins(0, 18, 0, 0));
        statusText.setText("שגיאה בקריאת היומן");
    }

    private void showSyncExplanation() {
        new AlertDialog.Builder(this)
                .setTitle("איך הסנכרון עובד?")
                .setMessage("האפליקציה מציגה יומנים שכבר זמינים ומסונכרנים במכשיר Android. יומני Google ראשיים, משניים ומשותפים יופיעו כאשר סנכרון היומן שלהם פעיל בטלפון. Calm Schedule לא משנה את הגדרות הסנכרון של Google Calendar.")
                .setPositiveButton("הבנתי", null)
                .show();
    }

    private void scheduleReload() {
        mainHandler.removeCallbacks(delayedReload);
        mainHandler.postDelayed(delayedReload, 350);
    }

    private void registerCalendarObserverIfPossible() {
        if (observerRegistered || !hasCalendarPermission()) return;
        try {
            getContentResolver().registerContentObserver(
                    CalendarContract.CONTENT_URI, true, calendarObserver);
            observerRegistered = true;
        } catch (Throwable ignored) {
            observerRegistered = false;
        }
    }

    private void unregisterCalendarObserver() {
        if (!observerRegistered) return;
        try { getContentResolver().unregisterContentObserver(calendarObserver); }
        catch (Throwable ignored) {}
        observerRegistered = false;
    }

    private void registerTimeReceiver() {
        if (receiverRegistered) return;
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_DATE_CHANGED);
        filter.addAction(Intent.ACTION_TIME_CHANGED);
        filter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        registerReceiver(timeReceiver, filter);
        receiverRegistered = true;
    }

    private void unregisterTimeReceiver() {
        if (!receiverRegistered) return;
        try { unregisterReceiver(timeReceiver); } catch (Throwable ignored) {}
        receiverRegistered = false;
    }

    private String formatRange(EventItem event) {
        if (event.allDay) return "כל היום";
        ZoneId zone = ZoneId.systemDefault();
        DateTimeFormatter f = DateTimeFormatter.ofPattern("HH:mm");
        String start = Instant.ofEpochMilli(event.begin).atZone(zone).format(f);
        String end = Instant.ofEpochMilli(event.end).atZone(zone).format(f);
        return start + "–" + end;
    }

    private String formatDuration(long millis) {
        long minutes = millis / 60000L;
        long hours = minutes / 60;
        long mins = minutes % 60;
        if (hours > 0 && mins > 0) return hours + " ש׳ " + mins + " דק׳";
        if (hours > 0) return hours + " ש׳";
        return mins + " דק׳";
    }

    private LinearLayout card(int color) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(15), dp(16), dp(15));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color);
        bg.setCornerRadius(dp(18));
        bg.setStroke(dp(1), BORDER);
        box.setBackground(bg);
        box.setElevation(dp(1));
        return box;
    }

    private TextView text(String value, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setGravity(Gravity.RIGHT);
        t.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG_RTL);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    private Button button(String value) {
        Button b = new Button(this);
        b.setText(value);
        b.setTextSize(15);
        b.setTextColor(TEXT);
        b.setAllCaps(false);
        b.setMinHeight(dp(44));
        b.setBackground(tintedButtonDrawable(Color.WHITE));
        return b;
    }

    private Button smallButton(String value) {
        Button b = button(value);
        b.setTextSize(20);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(48), dp(48));
        lp.setMargins(dp(4), 0, dp(4), 0);
        b.setLayoutParams(lp);
        b.setPadding(0, 0, 0, 0);
        return b;
    }

    private GradientDrawable tintedButtonDrawable(int color) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color);
        bg.setCornerRadius(dp(14));
        bg.setStroke(dp(1), BORDER);
        return bg;
    }

    private View spacer(int heightDp) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(1, dp(heightDp)));
        return v;
    }

    private LinearLayout.LayoutParams matchWrapMargins(int l, int t, int r, int b) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(dp(l), dp(t), dp(r), dp(b));
        return lp;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static final class CalendarMeta {
        final String name;
        final String account;
        CalendarMeta(String name, String account) {
            this.name = name == null ? "יומן" : name;
            this.account = account == null ? "" : account;
        }
    }

    private static final class EventItem {
        final long eventId;
        final long begin;
        final long end;
        final String title;
        final boolean allDay;
        final long calendarId;
        final String calendarName;
        final String account;
        final String location;

        EventItem(long eventId, long begin, long end, String title, boolean allDay,
                  long calendarId, String calendarName, String account, String location) {
            this.eventId = eventId;
            this.begin = begin;
            this.end = end;
            this.title = title;
            this.allDay = allDay;
            this.calendarId = calendarId;
            this.calendarName = calendarName == null ? "יומן" : calendarName;
            this.account = account == null ? "" : account;
            this.location = location == null ? "" : location;
        }
    }

    private static final class LoadResult {
        final List<EventItem> events;
        final int calendarCount;
        final long busyMillis;
        LoadResult(List<EventItem> events, int calendarCount, long busyMillis) {
            this.events = events;
            this.calendarCount = calendarCount;
            this.busyMillis = busyMillis;
        }
    }
}
