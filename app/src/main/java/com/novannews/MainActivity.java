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
                notifyUser("Isi berita lengkap berhasil disalin!");
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
    // Dialog 4: Isi Berita Lengkap (Dibacakan Pembaca Layar)
    // ------------------------------------------------------------
    private void showDetailActionDialog(final NewsItem item) {
        if (isFinishing()) return;

        final String fullText = (item.fullContent != null && !item.fullContent.isEmpty())
                ? item.fullContent
                : (item.desc != null && !item.desc.isEmpty() ? item.desc : "(Konten tidak tersedia.)");

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Isi Berita Lengkap");
        builder.setMessage(fullText);

        builder.setPositiveButton(TXT_SALIN, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                copyToClipboard(fullText + "\n\nSumber: " + item.link);
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

        // Fokuskan teks isi berita dan instruksikan mesin suara pembaca layar untuk membacakannya
        mainHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                TextView msgView = dlg.findViewById(android.R.id.message);
                if (msgView != null) {
                    msgView.setFocusable(true);
                    msgView.requestFocus();
                    msgView.sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_FOCUSED);
                    announceToScreenReader(msgView, fullText);
                } else {
                    announceToScreenReader(dlg.getWindow().getDecorView(), fullText);
                }
            }
        }, 300);
    }

    // ------------------------------------------------------------
    // Jaringan: Pengambil Isi Berita Lengkap
    // ------------------------------------------------------------
    private void loadFullArticleAndShow(final NewsItem item) {
        if (item.fullContent != null && !item.fullContent.isEmpty()) {
            showDetailActionDialog(item);
            return;
        }

        notifyUser("Mengambil isi berita lengkap...");

        String fetchUrl = prepareArticleUrl(item.link);

        httpFetch(fetchUrl, MAX_TRY_ARTICLE, new HttpCallback() {
            @Override
            public void onResult(boolean ok, String body) {
                if (ok && body != null) {
                    String extracted = extractFullArticle(item.link, body);
                    if (extracted.length() < item.desc.length()) {
                        extracted = item.desc;
                    }
                    if (!extracted.isEmpty()) {
                        item.fullContent = extracted;
                    }
                } else {
                    notifyUser("Gagal memuat berita lengkap. Menampilkan ringkasan.");
                    item.fullContent = item.desc;
                }
                showDetailActionDialog(item);
            }
        });
    }

    private String prepareArticleUrl(String url) {
        if (url == null) return "";
        if (url.contains("detik.com")) {
            if (!url.contains("single=1")) {
                return url + (url.contains("?") ? "&single=1" : "?single=1");
            }
        } else if (url.contains("kompas.com")) {
            if (!url.contains("page=all")) {
                return url + (url.contains("?") ? "&page=all" : "?page=all");
            }
        }
        return url;
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

                if (ok && body != null) {
                    List<NewsItem> list = parseNews(body, p.src);
                    if (!list.isEmpty()) {
                        listCache.put(cacheKey, new CacheEntry(System.currentTimeMillis(), list));
                        currentNewsList = list;
                        notifyUser("Berhasil memuat " + list.size() + " berita " + c.name + ".");
                        showNewsListDialog() ;
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

                        for (int hop = 0; hop < 5; hop++) {
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
                                    curUrl = newLoc;
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

                        if (responseCode == 200 && finalHtml != null) {
                            success = true;
                            resultBody = finalHtml;
                            break;
                        }
                    } catch (Exception ignored) {
                    }

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
                    item.link = link;
                    item.desc = desc;
                    item.date = date;
                    item.src = srcName;
                    list.add(item);
                }
            }
            if (!list.isEmpty()) return list;
        }

        if ("Detik".equals(srcName) || body.contains("detik.com")) {
            Pattern pDetik = Pattern.compile("<a[^>]+href=\"(https?://[a-z0-9\\.\\-]+detik\\.com/[^\"]*?/d-\\d+/[^\"]*)\"[^>]*>(.*?)</a>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
            Matcher mDetik = pDetik.matcher(body);
            while (mDetik.find() && list.size() < MAX_NEWS) {
                String href = mDetik.group(1);
                String titleRaw = mDetik.group(2);
                String title = cleanTag(titleRaw);
                String cleanLink = href.contains("?") ? href.substring(0, href.indexOf("?")) : href;

                if (title.length() > 15 && !seen.contains(cleanLink) && !isJunkParagraph(title)) {
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
                String cleanLink = href.contains("?") ? href.substring(0, href.indexOf("?")) : href;

                if (title.length() > 15 && !seen.contains(cleanLink) && !isJunkParagraph(title)) {
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
    // Parsing Isi Artikel Bersih
    // ------------------------------------------------------------
    private String extractFullArticle(String url, String html) {
        if (html == null || html.isEmpty()) return "";

        html = html.replaceAll("(?i)<script[\\s\\S]*?</script>", "");
        html = html.replaceAll("(?i)<style[\\s\\S]*?</style>", "");

        String contentBlock = html;

        if (url.contains("cnnindonesia.com")) {
            int start = indexOfPattern(html, "(?i)<div[^>]*class=\"[^\"]*detail[-_]text[^\"]*\"");
            if (start != -1) {
                String sub = html.substring(start);
                int end = indexOfPattern(sub, "(?i)class=\"[^\"]*tag[-_]|class=\"[^\"]*author|class=\"[^\"]*share|<footer");
                contentBlock = (end != -1) ? sub.substring(0, end) : sub;
            }
        } else if (url.contains("detik.com")) {
            int start = indexOfPattern(html, "(?i)class=\"[^\"]*(detail__body-text|itp_bodycontent|detail[-_]text|detail__body)[^\"]*\"");
            if (start != -1) {
                String sub = html.substring(start);
                int end = indexOfPattern(sub, "(?i)class=\"[^\"]*(detail__tag|detail__comment|tag[-_])|<footer");
                contentBlock = (end != -1) ? sub.substring(0, end) : sub;
            }
        } else if (url.contains("kompas.com")) {
            int start = indexOfPattern(html, "(?i)class=\"[^\"]*(read__content|article__content|detail[-_]text)[^\"]*\"");
            if (start != -1) {
                String sub = html.substring(start);
                int end = indexOfPattern(sub, "(?i)class=\"[^\"]*(read__tag|read__author|read__comment|tag[-_])|<footer");
                contentBlock = (end != -1) ? sub.substring(0, end) : sub;
            }
        }

        List<String> paras = collectParagraphs(contentBlock);
        if (paras.isEmpty()) {
            paras = collectParagraphs(html);
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < paras.size(); i++) {
            sb.append(paras.get(i));
            if (i < paras.size() - 1) sb.append("\n\n");
        }
        return sb.toString();
    }

    private int indexOfPattern(String text, String regex) {
        Matcher m = Pattern.compile(regex).matcher(text);
        return m.find() ? m.start() : -1;
    }

    private List<String> collectParagraphs(String block) {
        List<String> list = new ArrayList<>();
        Pattern p = Pattern.compile("<p[^>]*>(.*?)</p>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(block);
        while (m.find()) {
            String inner = m.group(1);
            inner = cleanTag(inner);
            if (!isJunkParagraph(inner) && inner.length() > 20) {
                list.add(inner);
            }
        }
        return list;
    }

    private boolean isJunkParagraph(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        return text.length() <= 15
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
                || lower.startsWith("komentar");
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