package com.novannews;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends Activity {

    private static final String TXT_SALIN = "Salin Berita";
    private static final String TXT_KEMBALI = "Kembali";
    private static final String TXT_BATAL = "Batal";
    private static final String TXT_TUTUP = "Tutup";

    private static final int MAX_NEWS = 50;
    private static final int MAX_TRY_LIST = 3;
    private static final int MAX_TRY_ARTICLE = 3;
    private static final int TIMEOUT_MS = 8000;
    private static final long CACHE_DURATION_MS = 5 * 60 * 1000; // 5 menit

    private Handler mainHandler;
    private ExecutorService executor;

    // Model Data
    public static class Category {
        public String name;
        public String url;
        public Category(String name, String url) {
            this.name = name;
            this.url = url;
        }
    }

    public static class Portal {
        public String name;
        public String src;
        public List<Category> categories = new ArrayList<>();
        public Portal(String name, String src) {
            this.name = name;
            this.src = src;
        }
    }

    public static class NewsItem {
        public String title = "";
        public String link = "";
        public String desc = "";
        public String date = "";
        public String src = "";
        public String fullContent = "";
    }

    private static class CacheEntry {
        public long timestamp;
        public List<NewsItem> items;
        public CacheEntry(long timestamp, List<NewsItem> items) {
            this.timestamp = timestamp;
            this.items = items;
        }
    }

    private final List<Portal> portals = new ArrayList<>();
    private int currentPortalIndex = 0;
    private int currentCategoryIndex = 0;
    private List<NewsItem> currentNewsList = new ArrayList<>();
    private final Map<String, CacheEntry> listCache = new HashMap<>();
    private int requestCounter = 0;

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);

        try {
            CookieHandler.setDefault(new CookieManager(null, CookiePolicy.ACCEPT_ALL));
        } catch (Exception ignored) {}

        mainHandler = new Handler(Looper.getMainLooper());
        executor = Executors.newCachedThreadPool();

        initPortalsData();

        // Tampilan Layar Utama
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(40, 40, 40, 40);

        TextView title = new TextView(this);
        title.setText("Portal Berita 3 in 1");
        title.setTextSize(24);
        title.setTextColor(Color.BLACK);
        title.setGravity(Gravity.CENTER);
        box.addView(title);

        TextView info = new TextView(this);
        info.setText("CNN Indonesia • Detikcom • Kompas.com\nDibacakan otomatis melalui mesin pembaca layar aktif.");
        info.setTextSize(15);
        info.setTextColor(Color.DKGRAY);
        info.setGravity(Gravity.CENTER);
        info.setPadding(0, 20, 0, 40);
        box.addView(info);

        Button btnBuka = new Button(this);
        btnBuka.setText("Buka Pilihan Berita");
        btnBuka.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showPortalDialog();
            }
        });
        box.addView(btnBuka);

        setContentView(box);

        mainHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!isFinishing()) {
                    showPortalDialog();
                }
            }
        }, 300);
    }

    private void initPortalsData() {
        // 1. CNN Indonesia
        Portal cnn = new Portal("CNN Indonesia", "CNN");
        cnn.categories.add(new Category("Olahraga (Sepak Bola, dll)", "https://www.cnnindonesia.com/olahraga/rss"));
        cnn.categories.add(new Category("Berita Utama / Terkini", "https://www.cnnindonesia.com/rss"));
        cnn.categories.add(new Category("Nasional", "https://www.cnnindonesia.com/nasional/rss"));
        cnn.categories.add(new Category("Internasional", "https://www.cnnindonesia.com/internasional/rss"));
        cnn.categories.add(new Category("Ekonomi & Bisnis", "https://www.cnnindonesia.com/ekonomi/rss"));
        cnn.categories.add(new Category("Teknologi", "https://www.cnnindonesia.com/teknologi/rss"));
        cnn.categories.add(new Category("Hiburan", "https://www.cnnindonesia.com/hiburan/rss"));
        cnn.categories.add(new Category("Gaya Hidup", "https://www.cnnindonesia.com/gaya-hidup/rss"));
        portals.add(cnn);

        // 2. Detikcom
        Portal detik = new Portal("Detikcom", "Detik");
        detik.categories.add(new Category("Olahraga (DetikSport)", "https://sport.detik.com"));
        detik.categories.add(new Category("Berita Utama (DetikNews)", "https://news.detik.com"));
        detik.categories.add(new Category("Ekonomi & Bisnis (DetikFinance)", "https://finance.detik.com"));
        detik.categories.add(new Category("Teknologi (DetikInet)", "https://inet.detik.com"));
        detik.categories.add(new Category("Hiburan & Seleb (DetikHot)", "https://hot.detik.com"));
        detik.categories.add(new Category("Otomotif (DetikOto)", "https://oto.detik.com"));
        portals.add(detik);

        // 3. Kompas.com
        Portal kompas = new Portal("Kompas.com", "Kompas");
        kompas.categories.add(new Category("Olahraga (Bola / Sport)", "https://bola.kompas.com"));
        kompas.categories.add(new Category("Berita Terkini (News)", "https://news.kompas.com"));
        kompas.categories.add(new Category("Nasional", "https://nasional.kompas.com"));
        kompas.categories.add(new Category("Ekonomi & Bisnis (Money)", "https://money.kompas.com"));
        kompas.categories.add(new Category("Teknologi (Tekno)", "https://tekno.kompas.com"));
        kompas.categories.add(new Category("Otomotif", "https://otomotif.kompas.com"));
        kompas.categories.add(new Category("Tren", "https://tren.kompas.com"));
        portals.add(kompas);
    }

    // ------------------------------------------------------------
    // Utilitas Pembaca Layar Native & Toast
    // ------------------------------------------------------------

    private void notifyUser(final String msg) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(MainActivity.this, msg, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void announceToScreenReader(final View targetView, final String text) {
        mainHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (isFinishing()) return;
                AccessibilityManager am = (AccessibilityManager) getSystemService(Context.ACCESSIBILITY_SERVICE);
                if (am != null && am.isEnabled()) {
                    if (targetView != null) {
                        targetView.announceForAccessibility(text);
                    } else if (getWindow() != null && getWindow().getDecorView() != null) {
                        getWindow().getDecorView().announceForAccessibility(text);
                    }
                }
            }
        }, 400);
    }

    private void copyToClipboard(String text) {
        try {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("Berita", text);
            if (cm != null) {
                cm.setPrimaryClip(clip);
                notifyUser("Inti berita berhasil disalin!");
            }
        } catch (Exception e) {
            notifyUser("Gagal menyalin berita.");
        }
    }

    // ------------------------------------------------------------
    // Dialog 1: Pilihan Portal Berita
    // ------------------------------------------------------------
    private void showPortalDialog() {
        if (isFinishing()) return;

        String[] portalNames = new String[portals.size()];
        for (int i = 0; i < portals.size(); i++) {
            portalNames[i] = (i + 1) + ". " + portals.get(i).name;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Pilih Sumber Berita");
        builder.setItems(portalNames, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                currentPortalIndex = which;
                showCategoryDialog(which);
            }
        });
        builder.setNegativeButton(TXT_BATAL, null);
        builder.show();
    }

    // ------------------------------------------------------------
    // Dialog 2: Pilihan Kategori Berita Sesuai Portal
    // ------------------------------------------------------------
    private void showCategoryDialog(final int portalIdx) {
        if (isFinishing()) return;
        currentPortalIndex = portalIdx;
        Portal p = portals.get(portalIdx);

        String[] catNames = new String[p.categories.size()];
        for (int i = 0; i < p.categories.size(); i++) {
            catNames[i] = (i + 1) + ". " + p.categories.get(i).name;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Kategori Berita: " + p.name);
        builder.setItems(catNames, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                fetchNews(portalIdx, which, false);
            }
        });
        builder.setNeutralButton("Kembali ke Portal", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                showPortalDialog();
            }
        });
        builder.setNegativeButton(TXT_BATAL, null);
        builder.show();
    }

    // ------------------------------------------------------------
    // Dialog 3: Daftar Berita
    // ------------------------------------------------------------
    private void showNewsListDialog() {
        if (isFinishing()) return;
        Portal p = portals.get(currentPortalIndex);
        Category c = p.categories.get(currentCategoryIndex);

        List<String> menuItems = new ArrayList<>();
        menuItems.add("[ Ganti Kategori " + p.name + " ]");
        menuItems.add("[ Ganti Portal Berita ]");
        menuItems.add("[ Segarkan Berita ]");

        for (int i = 0; i < currentNewsList.size(); i++) {
            menuItems.add((i + 1) + ". " + currentNewsList.get(i).title);
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(p.name + " - " + c.name + " (" + currentNewsList.size() + " Berita)");
        builder.setItems(menuItems.toArray(new String[0]), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (which == 0) {
                    showCategoryDialog(currentPortalIndex);
                } else if (which == 1) {
                    showPortalDialog();
                } else if (which == 2) {
                    fetchNews(currentPortalIndex, currentCategoryIndex, true);
                } else {
                    int newsIdx = which - 3;
                    if (newsIdx >= 0 && newsIdx < currentNewsList.size()) {
                        loadFullArticleAndShow(currentNewsList.get(newsIdx));
                    }
                }
            }
        });
        builder.setNegativeButton(TXT_TUTUP, null);
        builder.show();
    }

    // ------------------------------------------------------------
    // Dialog 4: Isi Berita Lengkap (Murni Inti Berita Saja)
    // ------------------------------------------------------------
    private void showDetailActionDialog(final NewsItem item) {
        if (isFinishing()) return;

        final String fullText = (item.fullContent != null && !item.fullContent.isEmpty())
                ? item.fullContent
                : (item.desc != null && !item.desc.isEmpty() ? item.desc : "(Konten tidak tersedia.)");

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        // Hapus setTitle agar pembaca layar langsung membaca naskah artikel berita dari kalimat pertama
        builder.setMessage(fullText);

        builder.setPositiveButton(TXT_SALIN, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                // Menyalin murni naskah berita saja tanpa tambahan "Sumber:"
                copyToClipboard(fullText);
            }
        });

        builder.setNegativeButton(TXT_KEMBALI, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                showNewsListDialog();
            }
        });

        final AlertDialog dlg = builder.create();
        dlg.show();

        mainHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                TextView msgView = dlg.findViewById(android.R.id.message);
                if (msgView != null) {
                    msgView.setFocusable(true);
                    msgView.requestFocus();
                    msgView.sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_FOCUSED);
                    announceToScreenReader(msgView, fullText);
                } else if (dlg.getWindow() != null && dlg.getWindow().getDecorView() != null) {
                    announceToScreenReader(dlg.getWindow().getDecorView(), fullText);
                }
            }
        }, 300);
    }

    // ------------------------------------------------------------
    // Jaringan: Pengambil Isi Berita Lengkap
    // ------------------------------------------------------------
    private void loadFullArticleAndShow(final NewsItem item) {
        if (item.fullContent != null && item.fullContent.length() > (item.title.length() + 30)) {
            showDetailActionDialog(item);
            return;
        }

        notifyUser("Mengambil isi berita lengkap...");

        String fetchUrl = item.link;
        if (fetchUrl.contains("kompas.com") && !fetchUrl.contains("page=all")) {
            fetchUrl = fetchUrl + (fetchUrl.contains("?") ? "&page=all" : "?page=all");
        }

        httpFetch(fetchUrl, MAX_TRY_ARTICLE, new HttpCallback() {
            @Override
            public void onResult(boolean ok, String body) {
                if (ok && body != null && !body.isEmpty()) {
                    String extracted = extractFullArticle(body);
                    if (extracted.length() > (item.title.length() + 30)) {
                        item.fullContent = extracted;
                        showDetailActionDialog(item);
                        return;
                    }
                }

                if (!fetchUrl.equals(item.link)) {
                    httpFetch(item.link, 2, new HttpCallback() {
                        @Override
                        public void onResult(boolean ok2, String body2) {
                            if (ok2 && body2 != null && !body2.isEmpty()) {
                                String extracted2 = extractFullArticle(body2);
                                if (extracted2.length() > (item.title.length() + 30)) {
                                    item.fullContent = extracted2;
                                    showDetailActionDialog(item);
                                    return;
                                }
                            }
                            handleArticleFallback(item);
                        }
                    });
                } else {
                    handleArticleFallback(item);
                }
            }
        });
    }

    private void handleArticleFallback(NewsItem item) {
        notifyUser("Menampilkan ringkasan berita.");
        item.fullContent = (item.desc != null && item.desc.length() > item.title.length()) ? cleanPrefix(item.desc) : item.title;
        showDetailActionDialog(item);
    }

    // ------------------------------------------------------------
    // Jaringan: Pengambil Daftar Berita Kategori
    // ------------------------------------------------------------
    private void fetchNews(final int portalIdx, final int catIdx, boolean forceRefresh) {
        currentPortalIndex = portalIdx;
        currentCategoryIndex = catIdx;

        final Portal p = portals.get(portalIdx);
        final Category c = p.categories.get(catIdx);
        final String cacheKey = portalIdx + "_" + catIdx;

        CacheEntry cached = listCache.get(cacheKey);
        if (cached != null && !forceRefresh && (System.currentTimeMillis() - cached.timestamp) < CACHE_DURATION_MS) {
            currentNewsList = cached.items;
            showNewsListDialog();
            return;
        }

        requestCounter++;
        final int myRequest = requestCounter;

        notifyUser("Mengambil " + p.name + " (" + c.name + ")...");

        httpFetch(c.url, MAX_TRY_LIST, new HttpCallback() {
            @Override
            public void onResult(boolean ok, String body) {
                if (myRequest != requestCounter) return;

                if (ok && body != null && !body.isEmpty()) {
                    List<NewsItem> list = parseNews(body, p.src);
                    if (!list.isEmpty()) {
                        listCache.put(cacheKey, new CacheEntry(System.currentTimeMillis(), list));
                        currentNewsList = list;
                        notifyUser("Berhasil memuat " + list.size() + " berita " + c.name + ".");
                        showNewsListDialog();
                        return;
                    }
                    notifyUser("Tidak ada berita yang ditemukan dalam kategori ini.");
                } else {
                    CacheEntry fallback = listCache.get(cacheKey);
                    if (fallback != null) {
                        notifyUser("Koneksi bermasalah. Menampilkan berita tersimpan.");
                        currentNewsList = fallback.items;
                        showNewsListDialog();
                    } else {
                        notifyUser("Gagal mengambil data dari " + p.name + ". Periksa koneksi internet.");
                    }
                }
            }
        });
    }

    // ------------------------------------------------------------
    // Mesin HTTP Koneksi Native
    // ------------------------------------------------------------
    private interface HttpCallback {
        void onResult(boolean ok, String body);
    }

    private void httpFetch(final String targetUrl, final int maxTry, final HttpCallback callback) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                boolean success = false;
                String resultBody = null;

                for (int attempt = 1; attempt <= maxTry; attempt++) {
                    try {
                        String curUrl = targetUrl;
                        int responseCode = 0;
                        String finalHtml = null;

                        for (int hop = 0; hop < 6; hop++) {
                            URL u = new URL(curUrl);
                            HttpURLConnection conn = (HttpURLConnection) u.openConnection();
                            conn.setRequestMethod("GET");
                            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36");
                            conn.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
                            conn.setRequestProperty("Accept-Language", "id-ID,id;q=0.9,en-US;q=0.8,en;q=0.7");
                            conn.setConnectTimeout(TIMEOUT_MS);
                            conn.setReadTimeout(TIMEOUT_MS);
                            conn.setInstanceFollowRedirects(true);

                            responseCode = conn.getResponseCode();

                            if (responseCode == 301 || responseCode == 302 || responseCode == 303 || responseCode == 307 || responseCode == 308) {
                                String newLoc = conn.getHeaderField("Location");
                                if (newLoc != null && !newLoc.isEmpty()) {
                                    try {
                                        curUrl = new URL(new URL(curUrl), newLoc).toString();
                                    } catch (Exception e) {
                                        curUrl = newLoc;
                                    }
                                } else {
                                    break;
                                }
                            } else if (responseCode == 200) {
                                BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
                                StringBuilder sb = new StringBuilder();
                                String line;
                                while ((line = br.readLine()) != null) {
                                    sb.append(line).append("\n");
                                }
                                br.close();
                                finalHtml = sb.toString();
                                break;
                            } else {
                                break;
                            }
                        }

                        if (responseCode == 200 && finalHtml != null && !finalHtml.isEmpty()) {
                            success = true;
                            resultBody = finalHtml;
                            break;
                        }
                    } catch (Exception ignored) {}

                    try {
                        Thread.sleep(600);
                    } catch (InterruptedException ignored) {}
                }

                final boolean finalSuccess = success;
                final String finalResult = resultBody;
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        callback.onResult(finalSuccess, finalResult);
                    }
                });
            }
        });
    }

    // ------------------------------------------------------------
    // Parsing Berita (RSS XML & Scraping HTML)
    // ------------------------------------------------------------
    private List<NewsItem> parseNews(String body, String srcName) {
        List<NewsItem> list = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        if (body == null || body.isEmpty()) return list;

        // 1. Umpan RSS XML
        if (body.contains("<item>") || body.contains("<ITEM>")) {
            Pattern pItem = Pattern.compile("<item[^>]*>(.*?)</item>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
            Matcher mItem = pItem.matcher(body);
            while (mItem.find() && list.size() < MAX_NEWS) {
                String it = mItem.group(1);
                String title = cleanTag(getTag(it, "title"));
                String link = cleanTag(getTag(it, "link"));
                String desc = cleanTag(getTag(it, "description"));
                String date = cleanTag(getTag(it, "pubDate"));

                if (!title.isEmpty() && !link.isEmpty() && !seen.contains(link)) {
                    seen.add(link);
                    NewsItem item = new NewsItem();
                    item.title = title;
                    item.link = link.replace("http://", "https://");
                    item.desc = desc;
                    item.date = date;
                    item.src = srcName;
                    list.add(item);
                }
            }
            if (!list.isEmpty()) return list;
        }

        // 2. Scraping HTML Web Langsung
        if ("Detik".equals(srcName) || body.contains("detik.com")) {
            Pattern pDetik = Pattern.compile("<a[^>]+href=\"(https?://[a-z0-9\\.\\-]+detik\\.com/[^\"]*?/d-\\d+/[^\"]*)\"[^>]*>(.*?)</a>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
            Matcher mDetik = pDetik.matcher(body);
            while (mDetik.find() && list.size() < MAX_NEWS) {
                String href = mDetik.group(1);
                String titleRaw = mDetik.group(2);
                String title = cleanTag(titleRaw);
                String cleanLink = (href.contains("?") ? href.substring(0, href.indexOf("?")) : href).replace("http://", "https://");

                if (title.length() > 15 && !seen.contains(cleanLink) && !isJunkLine(title)) {
                    seen.add(cleanLink);
                    NewsItem item = new NewsItem();
                    item.title = title;
                    item.link = cleanLink;
                    item.desc = title;
                    item.src = "Detik";
                    list.add(item);
                }
            }
        } else if ("Kompas".equals(srcName) || body.contains("kompas.com")) {
            Pattern pKompas = Pattern.compile("<a[^>]+href=\"(https?://[a-z0-9\\.\\-]+kompas\\.com/read/\\d+/[^\"]*)\"[^>]*>(.*?)</a>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
            Matcher mKompas = pKompas.matcher(body);
            while (mKompas.find() && list.size() < MAX_NEWS) {
                String href = mKompas.group(1);
                String titleRaw = mKompas.group(2);
                String title = cleanTag(titleRaw);
                String cleanLink = (href.contains("?") ? href.substring(0, href.indexOf("?")) : href).replace("http://", "https://");

                if (title.length() > 15 && !seen.contains(cleanLink) && !isJunkLine(title)) {
                    seen.add(cleanLink);
                    NewsItem item = new NewsItem();
                    item.title = title;
                    item.link = cleanLink;
                    item.desc = title;
                    item.src = "Kompas";
                    list.add(item);
                }
            }
        }

        return list;
    }

    private String getTag(String block, String name) {
        Pattern p = Pattern.compile("<" + name + "[^>]*>(.*?)</" + name + ">", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(block);
        return m.find() ? m.group(1) : "";
    }

    // ------------------------------------------------------------
    // Parsing MURNI Inti Naskah Berita Saja
    // ------------------------------------------------------------
    private String extractFullArticle(String html) {
        if (html == null || html.isEmpty()) return "";

        String lower = html.toLowerCase(Locale.ROOT);

        // 1. Deteksi letak wadah utama artikel
        String[] containerMarkers = {
            "detail__body-text",
            "itp_bodycontent",
            "read__content",
            "article__content",
            "detail-text",
            "detail_text"
        };

        int startPos = -1;
        for (String m : containerMarkers) {
            int idx = lower.indexOf(m);
            if (idx != -1) {
                int tagStart = html.lastIndexOf('<', idx);
                if (tagStart != -1) {
                    startPos = tagStart;
                    break;
                }
            }
        }

        String contentBlock = (startPos != -1) ? html.substring(startPos) : html;

        // 2. Batasi titik akhir artikel jika wadah utama ditemukan
        if (startPos != -1) {
            String lowerBlock = contentBlock.toLowerCase(Locale.ROOT);
            String[] endMarkers = {
                "class=\"detail__tag",
                "class='detail__tag",
                "class=\"read__tag",
                "class='read__tag",
                "class=\"detail__comment",
                "id=\"comment",
                "<footer"
            };
            int endPos = contentBlock.length();
            for (String em : endMarkers) {
                int eIdx = lowerBlock.indexOf(em);
                if (eIdx != -1 && eIdx > 50 && eIdx < endPos) {
                    endPos = eIdx;
                }
            }
            contentBlock = contentBlock.substring(0, endPos);
        }

        // 3. Ekstrak seluruh naskah paragraf <p> ... </p>
        List<String> paras = new ArrayList<>();
        Pattern pPattern = Pattern.compile("<p[^>]*>(.*?)</p>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
        Matcher m = pPattern.matcher(contentBlock);
        while (m.find()) {
            String cleanP = cleanTag(m.group(1));

            // Berhenti jika mencapai widget belanja / rekomendasi berita
            if (isTerminalJunk(cleanP)) {
                break;
            }

            // Bersihkan awalan nama wartawan / nama portal di paragraf pertama
            if (paras.isEmpty()) {
                cleanP = cleanPrefix(cleanP);
            }

            if (cleanP.length() > 20 && !isJunkLine(cleanP)) {
                paras.add(cleanP);
            }
        }

        // Cadangan darurat jika dalam wadah tidak ditemukan paragraf
        if (paras.isEmpty() && startPos != -1) {
            Matcher mFallback = pPattern.matcher(html);
            while (mFallback.find()) {
                String cleanP = cleanTag(mFallback.group(1));

                if (isTerminalJunk(cleanP)) {
                    break;
                }

                if (paras.isEmpty()) {
                    cleanP = cleanPrefix(cleanP);
                }

                if (cleanP.length() > 25 && !isJunkLine(cleanP)) {
                    paras.add(cleanP);
                }
            }
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < paras.size(); i++) {
            sb.append(paras.get(i));
            if (i < paras.size() - 1) sb.append("\n\n");
        }
        return sb.toString();
    }

    private String cleanPrefix(String txt) {
        if (txt == null) return "";
        // Detik byline: "Kris FW - detikSepakbola  "
        txt = txt.replaceAll("(?i)^[a-z0-9\\s\\.,]+-\\s*detik[a-z]+\\s*", "");
        // Kompas byline: "JAKARTA, KOMPAS.com - "
        txt = txt.replaceAll("(?i)^[a-z0-9\\s\\.,]*kompas\\.com\\s*-\\s*", "");
        // CNN byline: "Jakarta, CNN Indonesia -- "
        txt = txt.replaceAll("(?i)^[a-z0-9\\s\\.,]*cnn\\s+indonesia\\s*--\\s*", "");
        txt = txt.replaceAll("(?i)^[a-z0-9\\s\\.,]*cnn\\s+indonesia\\s*-\\s*", "");
        // Kota prefix umum: "Jakarta - "
        txt = txt.replaceAll("(?i)^jakarta\\s*-\\s*", "");
        return txt.trim();
    }

    private boolean isTerminalJunk(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        return lower.contains("ide belanja")
                || lower.contains("pilihan produk terbaik")
                || lower.contains("anda menyukai artikel ini")
                || lower.contains("dapatkan informasi dan insight")
                || lower.contains("bayangkan seorang anak indonesia")
                || lower.contains("mari hadirkan akses literasi")
                || lower.contains("jagatliterasi")
                || lower.contains("simak video")
                || lower.contains("rekomendasi produk")
                || lower.contains("artikel terkait")
                || lower.contains("berita terkait")
                || lower.contains("berita populer")
                || lower.contains("pilihan redaksi")
                || lower.contains("baca artikel")
                || lower.contains("daftar detikers")
                || lower.contains("copyright")
                || lower.matches("(?i)^#\\d+.*");
    }

    private boolean isJunkLine(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        return text.length() <= 20
                || lower.startsWith("baca juga")
                || lower.startsWith("pilihan redaksi")
                || lower.startsWith("simak video")
                || lower.startsWith("dapatkan update")
                || lower.startsWith("(sumber")
                || lower.startsWith("iklan")
                || lower.startsWith("advertisement")
                || lower.contains("scroll to continue")
                || lower.contains("lanjutkan membaca")
                || lower.startsWith("lihat juga")
                || lower.startsWith("tonton juga")
                || lower.contains("gambas")
                || lower.startsWith("tag:")
                || lower.startsWith("komentar")
                || lower.startsWith("copyright")
                || lower.contains("daftar detikers")
                || lower.startsWith("masuk")
                || lower.contains("userlogin")
                || lower.contains("this.open")
                || lower.contains("@mouseenter")
                || lower.contains("function(")
                || lower.startsWith("var ");
    }

    private String cleanTag(String txt) {
        if (txt == null) return "";
        txt = txt.replaceAll("(?i)<!\\[CDATA\\[(.*?)\\]\\]>", "$1");
        txt = txt.replaceAll("<[^>]*>", "");
        txt = decodeEntities(txt);
        txt = txt.replaceAll("[\\r\\n]+", " ");
        return txt.trim();
    }

    private String decodeEntities(String txt) {
        txt = txt.replace("&nbsp;", " ");
        txt = txt.replace("&quot;", "\"");
        txt = txt.replace("&apos;", "'");
        txt = txt.replace("&#39;", "'");
        txt = txt.replace("&ldquo;", "\"");
        txt = txt.replace("&rdquo;", "\"");
        txt = txt.replace("&lsquo;", "'");
        txt = txt.replace("&rsquo;", "'");
        txt = txt.replace("&hellip;", "...");
        txt = txt.replace("&mdash;", "—");
        txt = txt.replace("&ndash;", "–");
        txt = txt.replace("&lt;", "<");
        txt = txt.replace("&gt;", ">");
        txt = txt.replace("&amp;", "&");
        return txt;
    }

    @Override
    protected void onDestroy() {
        if (executor != null) {
            executor.shutdown();
        }
        super.onDestroy();
    }
}