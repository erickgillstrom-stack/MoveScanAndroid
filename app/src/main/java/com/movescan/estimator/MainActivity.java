package com.movescan.estimator;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.speech.RecognizerIntent;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.google.ar.core.ArCoreApk;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int PERMISSION_REQUEST = 200;
    private static final int VIDEO_REQUEST = 300;
    private static final int SPEECH_REQUEST = 301;
    private static final int NAVY = Color.rgb(11, 23, 40);
    private static final int INK = Color.rgb(16, 36, 61);
    private static final int AQUA = Color.rgb(44, 201, 186);
    private static final int PAPER = Color.rgb(245, 248, 251);

    private final List<MoveItem> inventory = new ArrayList<>();
    private final String[] rooms = {"Living room", "Kitchen", "Primary bedroom", "Home office", "Guest bedroom", "Garage", "Other"};
    private final CatalogItem[] catalog = {
            new CatalogItem("3-seat sofa", 82, 280), new CatalogItem("Sleeper sofa", 85, 350),
            new CatalogItem("Upholstered armchair", 21, 75), new CatalogItem("Round side table", 8, 35),
            new CatalogItem("Queen mattress", 34, 85), new CatalogItem("Executive desk", 34, 190),
            new CatalogItem("5-shelf bookcase", 16, 78), new CatalogItem("French-door refrigerator", 53, 310),
            new CatalogItem("Upright piano", 40, 500), new CatalogItem("Flat-screen TV", 10, 45)
    };

    private LinearLayout page;
    private Spinner roomSpinner;
    private TextView inventorySummary;
    private TextView recordingStatus;
    private TextView transcript;
    private EditText customer, origin, destination, packDate, loadDate, mileage;
    private Uri pendingVideoUri;
    private boolean recordAfterPermission;
    private SharedPreferences prefs;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences("movescan", MODE_PRIVATE);
        loadInventory();
        showHome();
    }

    private void showHome() {
        buildShell("Active estimate");
        hero("Johnson Residence", "Grand Rapids, MI → Naples, FL");
        LinearLayout metrics = horizontal();
        metrics.addView(metric("34", "Items"), weight(1));
        metrics.addView(metric("612", "Cu ft"), weight(1));
        metrics.addView(metric("4,218", "Estimated lb"), weight(1));
        page.addView(metrics);
        page.addView(space(16));
        page.addView(cardTitle("Current walkthrough"));
        page.addView(label("4 of 7 rooms captured"));
        page.addView(space(12));
        page.addView(primary("Continue walkthrough", v -> showWalkthrough()));
        page.addView(secondary("Move details", v -> showDetails()));
        page.addView(secondary("Inventory and furniture database", v -> showInventory()));
    }

    private void showDetails() {
        buildShell("Move details");
        customer = field("Customer name", prefs.getString("customer", "Jordan Johnson"));
        origin = field("Origin address", prefs.getString("origin", "1842 Cambridge Drive SE, Grand Rapids, MI 49506"));
        destination = field("Destination address", prefs.getString("destination", "7821 Gulf Shore Boulevard, Naples, FL 34108"));
        packDate = field("Pack Date", prefs.getString("packDate", "October 12, 2026"));
        loadDate = field("Load Date", prefs.getString("loadDate", "October 14, 2026"));
        mileage = field("Route mileage", prefs.getString("mileage", "1,431 miles"));
        mileage.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
        page.addView(secondary("Calculate mileage", v -> calculateMileage()));
        page.addView(primary("Save details", v -> saveDetails()));
        page.addView(secondary("Back", v -> showHome()));
    }

    private void calculateMileage() {
        if (origin.getText().toString().trim().isEmpty() || destination.getText().toString().trim().isEmpty()) {
            toast("Enter both addresses first"); return;
        }
        mileage.setText("1,431 miles");
        toast("Test mileage calculated. Live routing is not connected yet.");
    }

    private void saveDetails() {
        prefs.edit().putString("customer", text(customer)).putString("origin", text(origin))
                .putString("destination", text(destination)).putString("packDate", text(packDate))
                .putString("loadDate", text(loadDate)).putString("mileage", text(mileage)).apply();
        toast("Move details saved on this device");
    }

    private void showWalkthrough() {
        buildShell("Walkthrough recording");
        page.addView(cardTitle("Select room"));
        roomSpinner = new Spinner(this);
        roomSpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, rooms));
        roomSpinner.setPadding(dp(12), dp(10), dp(12), dp(10));
        page.addView(roomSpinner, matchWrap());

        ArCoreApk.Availability availability = ArCoreApk.getInstance().checkAvailability(this);
        TextView ar = label(availability.isSupported()
                ? "ARCore compatible device detected · measurement module ready for production wiring"
                : "ARCore unavailable or still checking · manual measurement fallback will be used");
        ar.setBackground(rounded(availability.isSupported() ? Color.rgb(224, 248, 241) : Color.rgb(255, 244, 216), 12));
        ar.setPadding(dp(14), dp(14), dp(14), dp(14));
        page.addView(ar);
        page.addView(space(14));

        page.addView(primary("● Start video + audio recording", v -> beginRecording()));
        recordingStatus = label("No walkthrough recording captured in this session.");
        page.addView(recordingStatus);
        page.addView(secondary("Record customer voice note", v -> beginSpeech()));
        transcript = label("Voice-note transcript will appear here.");
        transcript.setBackground(rounded(Color.WHITE, 12));
        transcript.setPadding(dp(14), dp(14), dp(14), dp(14));
        page.addView(transcript);
        page.addView(space(12));
        page.addView(primary("Add furniture from database", v -> showCatalogDialog()));
        page.addView(secondary("Add demo camera matches", v -> addDemoMatches()));
        page.addView(secondary("Review inventory", v -> showInventory()));
        page.addView(secondary("Back", v -> showHome()));
    }

    private void beginRecording() {
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED ||
                checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            recordAfterPermission = true;
            requestPermissions(new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO}, PERMISSION_REQUEST);
            return;
        }
        launchRecorder();
    }

    private void launchRecorder() {
        ContentValues values = new ContentValues();
        values.put(MediaStore.Video.Media.DISPLAY_NAME, "MoveScan_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()) + ".mp4");
        values.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4");
        values.put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/MoveScan");
        pendingVideoUri = getContentResolver().insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values);
        Intent intent = new Intent(MediaStore.ACTION_VIDEO_CAPTURE);
        intent.putExtra(MediaStore.EXTRA_OUTPUT, pendingVideoUri);
        intent.putExtra(MediaStore.EXTRA_VIDEO_QUALITY, 1);
        intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
        if (intent.resolveActivity(getPackageManager()) != null) startActivityForResult(intent, VIDEO_REQUEST);
        else toast("No compatible camera recorder was found");
    }

    private void beginSpeech() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Describe what is going and not going");
        try { startActivityForResult(intent, SPEECH_REQUEST); }
        catch (Exception error) { toast("Speech recognition is unavailable on this phone"); }
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == PERMISSION_REQUEST && recordAfterPermission) {
            recordAfterPermission = false;
            boolean granted = true; for (int result : results) granted &= result == PackageManager.PERMISSION_GRANTED;
            if (granted) launchRecorder(); else toast("Camera and microphone permission are required to record");
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == VIDEO_REQUEST) {
            if (resultCode == RESULT_OK && pendingVideoUri != null) {
                if (recordingStatus != null) recordingStatus.setText("Recording saved: " + pendingVideoUri);
                toast("Walkthrough recording saved to Movies/MoveScan");
            } else if (pendingVideoUri != null) {
                getContentResolver().delete(pendingVideoUri, null, null);
                toast("Recording cancelled");
            }
        } else if (requestCode == SPEECH_REQUEST && resultCode == RESULT_OK && data != null) {
            ArrayList<String> results = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (results != null && !results.isEmpty()) {
                String note = results.get(0);
                if (transcript != null) transcript.setText("Customer: “" + note + "”");
                toast(note.toLowerCase(Locale.US).contains("not going") || note.toLowerCase(Locale.US).contains("leave")
                        ? "Not-going language detected" : "Voice note saved");
            }
        }
    }

    private void addDemoMatches() {
        String room = selectedRoom();
        inventory.add(new MoveItem(room, "3-seat sofa", 1, true, 82, 280));
        inventory.add(new MoveItem(room, "Round side table", 1, false, 8, 35));
        saveInventory();
        toast("2 demo matches added with going status");
    }

    private void showCatalogDialog() {
        String[] names = new String[catalog.length];
        for (int i = 0; i < catalog.length; i++) names[i] = catalog[i].name + " · " + catalog[i].cube + " ft³ · " + catalog[i].weight + " lb";
        new AlertDialog.Builder(this).setTitle("Furniture database").setItems(names, (dialog, which) -> {
            CatalogItem item = catalog[which];
            new AlertDialog.Builder(this).setTitle(item.name).setMessage("Is this item going in the move?")
                    .setPositiveButton("Going", (d, w) -> addCatalogItem(item, true))
                    .setNegativeButton("Not going", (d, w) -> addCatalogItem(item, false)).show();
        }).setNegativeButton("Cancel", null).show();
    }

    private void addCatalogItem(CatalogItem item, boolean going) {
        inventory.add(new MoveItem(selectedRoom(), item.name, 1, going, item.cube, item.weight));
        saveInventory(); toast(item.name + " added to " + selectedRoom());
    }

    private String selectedRoom() { return roomSpinner == null ? "Unassigned" : roomSpinner.getSelectedItem().toString(); }

    private void showInventory() {
        buildShell("Room inventory");
        int cube = 0, weight = 0, going = 0;
        for (MoveItem item : inventory) if (item.going) { cube += item.cube * item.quantity; weight += item.weight * item.quantity; going += item.quantity; }
        LinearLayout metrics = horizontal();
        metrics.addView(metric(String.valueOf(going), "Going"), weight(1));
        metrics.addView(metric(String.valueOf(cube), "Cu ft"), weight(1));
        metrics.addView(metric(String.valueOf(weight), "Lb"), weight(1));
        page.addView(metrics);
        page.addView(space(14));
        inventorySummary = new TextView(this);
        inventorySummary.setTextColor(INK); inventorySummary.setTextSize(15); inventorySummary.setLineSpacing(0, 1.25f);
        inventorySummary.setText(renderInventory());
        inventorySummary.setPadding(dp(16), dp(16), dp(16), dp(16));
        inventorySummary.setBackground(rounded(Color.WHITE, 14));
        page.addView(inventorySummary, matchWrap());
        page.addView(primary("Add furniture", v -> { roomSpinner = new Spinner(this); roomSpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, rooms)); showCatalogDialog(); }));
        page.addView(secondary("Clear test inventory", v -> confirmClear()));
        page.addView(secondary("Back", v -> showHome()));
    }

    private String renderInventory() {
        if (inventory.isEmpty()) return "No inventory items yet. Start a walkthrough or add furniture from the database.";
        StringBuilder out = new StringBuilder(); String lastRoom = "";
        for (MoveItem item : inventory) {
            if (!lastRoom.equals(item.room)) { if (out.length() > 0) out.append("\n"); out.append(item.room.toUpperCase(Locale.US)).append("\n"); lastRoom = item.room; }
            out.append("• ").append(item.quantity).append(" × ").append(item.name).append(" — ")
                    .append(item.going ? "GOING" : "NOT GOING").append(" · ")
                    .append(item.cube).append(" ft³ · ").append(item.weight).append(" lb\n");
        }
        return out.toString();
    }

    private void confirmClear() {
        new AlertDialog.Builder(this).setTitle("Clear test inventory?").setMessage("This removes locally saved inventory items.")
                .setPositiveButton("Clear", (d,w) -> { inventory.clear(); saveInventory(); showInventory(); })
                .setNegativeButton("Cancel", null).show();
    }

    private void loadInventory() {
        String raw = prefs.getString("inventory", ""); if (raw.isEmpty()) return;
        for (String row : raw.split("\\n")) {
            String[] p = row.split("\\|", -1); if (p.length != 6) continue;
            try { inventory.add(new MoveItem(p[0], p[1], Integer.parseInt(p[2]), Boolean.parseBoolean(p[3]), Integer.parseInt(p[4]), Integer.parseInt(p[5]))); } catch (Exception ignored) {}
        }
    }

    private void saveInventory() {
        StringBuilder raw = new StringBuilder();
        for (MoveItem item : inventory) raw.append(item.room).append('|').append(item.name).append('|').append(item.quantity).append('|').append(item.going).append('|').append(item.cube).append('|').append(item.weight).append('\n');
        prefs.edit().putString("inventory", raw.toString()).apply();
    }

    private void buildShell(String title) {
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(PAPER);
        TextView header = new TextView(this); header.setText("MOVESCAN\n" + title); header.setTextColor(Color.WHITE); header.setTextSize(20); header.setTypeface(Typeface.DEFAULT, Typeface.BOLD); header.setPadding(dp(20), dp(18), dp(20), dp(18)); header.setBackgroundColor(NAVY);
        root.addView(header, new LinearLayout.LayoutParams(-1, -2));
        ScrollView scroll = new ScrollView(this); page = new LinearLayout(this); page.setOrientation(LinearLayout.VERTICAL); page.setPadding(dp(18), dp(20), dp(18), dp(36)); scroll.addView(page); root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
    }

    private void hero(String title, String sub) { TextView t=cardTitle(title); t.setTextSize(24); page.addView(t); page.addView(label(sub)); page.addView(space(18)); }
    private TextView cardTitle(String value) { TextView t=new TextView(this); t.setText(value); t.setTextColor(INK); t.setTextSize(18); t.setTypeface(Typeface.DEFAULT,Typeface.BOLD); t.setPadding(0,dp(8),0,dp(5)); return t; }
    private TextView label(String value) { TextView t=new TextView(this); t.setText(value); t.setTextColor(Color.rgb(102,117,138)); t.setTextSize(14); t.setPadding(0,dp(4),0,dp(10)); return t; }
    private EditText field(String label, String value) { page.addView(cardTitle(label)); EditText e=new EditText(this); e.setText(value); e.setTextColor(INK); e.setTextSize(16); e.setSingleLine(false); e.setPadding(dp(12),dp(12),dp(12),dp(12)); e.setBackground(rounded(Color.WHITE,10)); page.addView(e,matchWrap()); page.addView(space(8)); return e; }
    private View metric(String number,String label){ LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(12),dp(14),dp(12),dp(14));box.setBackground(rounded(Color.WHITE,12));TextView n=cardTitle(number);TextView l=label(label);box.addView(n);box.addView(l);return box; }
    private Button primary(String text, View.OnClickListener listener){return button(text,AQUA,NAVY,listener);}
    private Button secondary(String text, View.OnClickListener listener){return button(text,Color.WHITE,INK,listener);}
    private Button button(String text,int bg,int fg,View.OnClickListener listener){Button b=new Button(this);b.setText(text);b.setTextColor(fg);b.setTextSize(15);b.setAllCaps(false);b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);b.setBackground(rounded(bg,11));b.setOnClickListener(listener);LinearLayout.LayoutParams lp=matchWrap();lp.topMargin=dp(10);b.setLayoutParams(lp);return b;}
    private LinearLayout horizontal(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.HORIZONTAL);l.setGravity(Gravity.CENTER);l.setWeightSum(3);l.setPadding(0,0,0,0);return l;}
    private LinearLayout.LayoutParams weight(float w){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,w);p.setMargins(dp(4),0,dp(4),0);return p;}
    private LinearLayout.LayoutParams matchWrap(){return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);}
    private GradientDrawable rounded(int color,int radius){GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp(radius));return g;}
    private View space(int h){View v=new View(this);v.setLayoutParams(new LinearLayout.LayoutParams(1,dp(h)));return v;}
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
    private String text(EditText e){return e.getText().toString().trim();}
    private void toast(String message){Toast.makeText(this,message,Toast.LENGTH_LONG).show();}

    private static class CatalogItem { final String name; final int cube,weight; CatalogItem(String n,int c,int w){name=n;cube=c;weight=w;} }
    private static class MoveItem { final String room,name; final int quantity,cube,weight; final boolean going; MoveItem(String r,String n,int q,boolean g,int c,int w){room=r;name=n;quantity=q;going=g;cube=c;weight=w;} }
}
