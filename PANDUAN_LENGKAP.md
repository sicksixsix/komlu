# Komiklu — Panduan Lengkap Proyek Android

## 📁 Struktur Proyek

```
komiklu/
├── gradle/
│   └── libs.versions.toml              # Version catalog semua dependency
├── build.gradle.kts                    # Root build script
└── app/
    ├── build.gradle.kts                # App dependencies & config
    ├── proguard-rules.pro              # ProGuard untuk release
    └── src/main/
        ├── AndroidManifest.xml
        ├── res/
        │   ├── values/
        │   │   ├── themes.xml          # Dark mode theme #000000 + #00B8CB
        │   │   ├── colors.xml
        │   │   └── strings.xml
        │   ├── navigation/
        │   │   └── nav_graph.xml       # Navigation Component graph
        │   └── xml/
        │       └── network_security_config.xml
        └── java/com/komiklu/app/
            ├── KomikluApp.kt           # Application + Hilt
            ├── MainActivity.kt         # Entry point
            ├── data/
            │   ├── model/
            │   │   └── Models.kt       # ComicDto, Chapter, UiState, dll
            │   ├── remote/
            │   │   └── ApiService.kt   # Retrofit interfaces (CDN JSON)
            │   ├── local/
            │   │   └── dao/
            │   │       └── Daos.kt     # Room DAOs (Favorite, History, Cache)
            │   ├── repository/
            │   │   └── Repositories.kt # ComicRepo, HistoryRepo, AuthRepo
            │   └── session/
            │       └── SessionManager.kt # EncryptedSharedPrefs token
            ├── di/
            │   ├── AppModule.kt        # Gson, DataStore
            │   ├── NetworkModule.kt    # OkHttp, Retrofit, Coil ImageLoader
            │   └── DatabaseModule.kt   # Room DB, AuthInterceptor
            └── ui/
                ├── home/
                │   ├── HomeViewModel.kt
                │   └── HomeFragment.kt
                ├── detail/
                │   └── DetailScreen.kt  # DetailViewModel + DetailFragment
                ├── reader/
                │   ├── ReaderScreen.kt  # ReaderViewModel + ReaderActivity
                │   └── ReaderSettingsBottomSheet.kt
                ├── series/
                │   └── AllSeriesViewModel.kt
                ├── auth/
                │   └── AuthScreens.kt   # AuthViewModel + Login + Register
                ├── profile/
                │   └── ProfileScreens.kt # Profile + Favorites + History
                ├── adapter/
                │   └── Adapters.kt      # Semua RecyclerView adapters
                └── util/
                    └── Extensions.kt    # View extensions
```

---

## 🗂️ Format Data JSON (CDN Statis)

### comics.json
```
https://cdn.komiklu.com/comics.json
```
```json
[
  {
    "title": "Berserk",
    "author": "MIURA Kentaro, MORI Kouji",
    "year": 1989,
    "desc": "Namanya Guts, Pedang Hitam...",
    "cover": "img/berserk_cover.jpg",
    "genres": ["Manga","Action","Adventure"],
    "status": "OnGoing",
    "rating": "9",
    "project": "1",
    "rekomendasi": "0",
    "view": 13270
  }
]
```

### chapters/[title].json
```
https://cdn.komiklu.com/chapters/Berserk.json
```
```json
[
  {
    "chapter": "Chapter 1",
    "url": "https://cdn.komiklu.com/Manga/Berserk/Chapter%201/index.json"
  }
]
```

### index.json (isi chapter)
```
https://cdn.komiklu.com/Manga/Berserk/Chapter%201/index.json
```
```json
["001.webp","002.webp","003.webp"]
```

---

## ☁️ Setup CDN & S3

### Struktur S3 Bucket
```
komiklu-bucket/
├── comics.json                          ← Master data semua manga
├── chapters/
│   ├── Berserk.json
│   ├── Vagabond.json
│   └── ...
└── Manga/
    ├── Berserk/
    │   ├── Chapter 1/
    │   │   ├── index.json
    │   │   ├── 001.webp
    │   │   ├── 002.webp
    │   │   └── ...
    │   └── Chapter 2/
    └── Vagabond/
        └── ...
```

### CloudFront / CDN Cache Rules
```
comics.json          → Cache-Control: max-age=300 (5 menit)
chapters/*.json      → Cache-Control: max-age=300 (5 menit)
Manga/**/*.json      → Cache-Control: max-age=86400 (1 hari)
Manga/**/*.webp      → Cache-Control: max-age=2592000 (30 hari)
```

### Konfigurasi Domain di BuildConfig
```kotlin
// app/build.gradle.kts
buildConfigField("String", "BASE_URL",      "\"https://cdn.komiklu.com/\"")
buildConfigField("String", "API_BASE_URL",  "\"https://api.komiklu.com/\"")
buildConfigField("String", "IMAGE_CDN_URL", "\"https://img.komiklu.com/\"")
```

### Image Optimization CDN (URL Params)
```kotlin
// LOW:    ?q=40&w=480    → hemat bandwidth (mode data saver)
// MEDIUM: ?q=70&w=800    → default
// HIGH:   ?q=90&w=1200   → kualitas penuh
```
Gunakan **CloudFront + Lambda@Edge** atau **Cloudflare Images** untuk resize on-the-fly.

---

## 🏗️ Arsitektur

```
UI Layer (Fragments/Activities)
    ↕ StateFlow / SharedFlow
ViewModel Layer
    ↕ suspend fun / Flow
Repository Layer
    ↕             ↕
Remote (Retrofit)  Local (Room + DataStore)
    ↕
CDN JSON API
```

**Pattern:** MVVM + Clean Architecture + Repository Pattern  
**DI:** Hilt  
**Async:** Kotlin Coroutines + Flow  

---

## ⚡ Strategi Performance

### 1. Cache JSON (OkHttp + Room dua lapis)
```
Request comics.json
    ↓
Room cache ada & < 1 jam? → Return dari Room
    ↓
OkHttp HTTP cache ada? → Return dari disk cache
    ↓
Fetch dari CDN → Simpan ke Room + OkHttp cache
```

### 2. Image Lazy Loading (Coil)
- Memory cache: 50MB (gambar terbaru di RAM)
- Disk cache: 200MB (gambar yang pernah dimuat)
- Crossfade 200ms untuk transisi halus
- Placeholder saat loading

### 3. Preload Chapter Reader
```kotlin
// Saat baca halaman N, preload N+1, N+2, N+3 di background
fun preloadNextImages(pages: List<MangaPage>) {
    val toPreload = pages.drop(currentPage).take(3)
    toPreload.forEach { page -> imageLoader.enqueue(ImageRequest(page.imageUrl)) }
}
```

### 4. Pagination Load More
- Home: tidak ada pagination (semua data kecil dari JSON)
- All Series: 20 item per halaman, load more
- Chapter list: 50 chapter per halaman, load more

### 5. Search Real-time (tanpa API call)
```kotlin
// Semua search dilakukan di memory dari comics.json yang sudah di-cache
val searchResults = _searchQuery
    .debounce(300ms)
    .map { query -> allComics.filter { it.matches(query) } }
```

---

## 🔐 User System

### Auth Flow
```
Login/Register → API ringan (Supabase Auth / Firebase Auth)
    ↓
JWT Token → EncryptedSharedPreferences (AES256)
    ↓
AuthInterceptor → Inject ke setiap request API
```

### Data Lokal (Room)
- **favorites** → disimpan offline, tidak perlu server
- **read_history** → disimpan offline + sinkronisasi opsional
- **comic_cache** → cache JSON, auto-expire 1 jam

### Rekomendasi Auth Backend
- **Supabase** (gratis, PostgreSQL, Auth built-in) ← Direkomendasikan
- **Firebase Auth** (gratis tier)
- **Custom API** minimal (hanya endpoint login/register/refresh)

---

## 📋 Dependencies Utama

| Library | Versi | Kegunaan |
|---------|-------|----------|
| Retrofit | 2.11 | HTTP client JSON |
| OkHttp | 4.12 | Caching HTTP |
| Coil | 2.7 | Image loading + cache |
| Room | 2.6 | Local DB (favorites, history) |
| Hilt | 2.52 | Dependency Injection |
| Navigation | 2.8 | Fragment navigation |
| Paging 3 | 3.3 | Load more pagination |
| DataStore | 1.1 | Preferences ringan |
| ViewPager2 | 1.1 | Banner slideshow |
| Shimmer | 0.5 | Loading skeleton |

---

## 🚀 Cara Build & Run

```bash
# Clone
git clone https://github.com/yourname/komiklu-android.git

# Sesuaikan URL CDN di app/build.gradle.kts:
buildConfigField("String", "BASE_URL", "\"https://cdn-kamu.com/\"")

# Build debug
./gradlew assembleDebug

# Build release (butuh keystore)
./gradlew assembleRelease \
  -Pandroid.injected.signing.store.file=komiklu.keystore \
  -Pandroid.injected.signing.store.password=xxx \
  -Pandroid.injected.signing.key.alias=komiklu \
  -Pandroid.injected.signing.key.password=xxx
```

---

## 📱 Layout XML yang Perlu Dibuat

Semua layout berikut perlu dibuat di `res/layout/`:

```
activity_main.xml           → BottomNavigationView + NavHostFragment
activity_reader.xml         → Fullscreen reader + top/bottom bar
fragment_home.xml           → Shimmer + SwipeRefresh + NestedScrollView
fragment_detail.xml         → CollapsingToolbar + cover + chapter list
fragment_all_series.xml     → Filter chips + RecyclerView + load more
fragment_login.xml          → Logo + fields + buttons
fragment_profile.xml        → Avatar + stats + menu items
fragment_favorites.xml      → RecyclerView favorites
fragment_history.xml        → RecyclerView history + clear button
bottom_sheet_reader_settings.xml → RadioGroups + Switches

item_manga_card.xml         → Horizontal card (cover 90x125dp)
item_latest_chapter.xml     → Row: cover + title + chapter + badge NEW
item_search_result.xml      → Row: cover + title + author + genres
item_chapter_row.xml        → Row: chapter name + date + arrow
item_series.xml             → Row: cover + full info + 2 chapters
item_banner.xml             → Full-width banner dengan overlay
item_genre_chip.xml         → Chip/pill genre
item_favorite.xml           → Row: cover + info + remove button
item_history.xml            → Row: cover + title + progress bar
item_manga_page.xml         → PhotoView/ImageView untuk reader
```

---

## 🎨 Desain Sistem Warna

```xml
Hitam pekat:   #000000  (background utama)
Surface:       #111111  (card, bottom nav)
Surface 2:     #1A1A1A  (input, badge bg)
Border:        #222222  (divider, stroke)
Aksen:         #00B8CB  (tombol, highlight, badge)
Aksen gelap:   #008FA0  (pressed state)
Text utama:    #FFFFFF
Text sekunder: #AAAAAA
Text hint:     #666666
Rating:        #FBBF24  (bintang kuning)
OnGoing:       #22C55E  (hijau)
Finished:      #EF4444  (merah)
```
