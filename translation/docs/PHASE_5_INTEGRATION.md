# Phase 5: Integration, Typesetting & Polish

## Overview

Final phase that brings everything together: advanced text rendering, complete Reader UI integration, settings screen, and performance optimization. This transforms the translation system from a working prototype to a production-ready feature.

## Goals

- ✅ Professional-quality text rendering
- ✅ Seamless Reader integration
- ✅ Comprehensive settings UI
- ✅ Performance optimization
- ✅ User testing and refinement
- ✅ Documentation and examples

## Components to Implement

### 1. Advanced Typesetting Engine

**File:** `translation/src/main/java/mihon/feature/translation/data/typesetting/TypesettingEngine.kt`

#### 1.1 Core Interface

```kotlin
interface TypesettingEngine {

    /**
     * Render translated text onto an inpainted image
     */
    suspend fun renderText(
        baseImage: Bitmap,
        translationData: TranslationData,
        style: TypographyStyle
    ): Bitmap

    /**
     * Calculate optimal font size for a bubble
     */
    fun calculateFontSize(
        text: String,
        bounds: RectF,
        style: TypographyStyle
    ): Float

    /**
     * Support text overflow handling
     */
    fun fitTextToBounds(
        text: String,
        bounds: RectF,
        maxFontSize: Float
    ): TextLayout
}

data class TypographyStyle(
    val fontFamily: Typeface,
    val textColor: Int = Color.BLACK,
    val strokeColor: Int = Color.WHITE,
    val strokeWidth: Float = 2f,
    val alignment: Alignment = Alignment.CENTER,
    val verticalText: Boolean = false,
    val lineSpacing: Float = 1.2f,
)

enum class Alignment {
    LEFT, CENTER, RIGHT, TOP, BOTTOM
}

data class TextLayout(
    val lines: List<String>,
    val fontSize: Float,
    val bounds: RectF,
    val overflow: Boolean
)
```

#### 1.2 Implementation

```kotlin
class CanvasTypesettingEngine : TypesettingEngine {

    override suspend fun renderText(
        baseImage: Bitmap,
        translationData: TranslationData,
        style: TypographyStyle
    ): Bitmap = withContext(Dispatchers.Default) {

        val result = baseImage.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)

        translationData.bubbles.forEach { bubble ->
            val text = bubble.translation.translatedText
            val bounds = bubble.bubble.boundingBox

            // Calculate layout
            val layout = fitTextToBounds(text, bounds, 48f)

            // Render text
            if (bubble.bubble.textStyle == TextStyle.VERTICAL) {
                renderVerticalText(canvas, layout, bounds, style)
            } else {
                renderHorizontalText(canvas, layout, bounds, style)
            }
        }

        result
    }

    private fun renderHorizontalText(
        canvas: Canvas,
        layout: TextLayout,
        bounds: RectF,
        style: TypographyStyle
    ) {
        val paint = Paint().apply {
            typeface = style.fontFamily
            textSize = layout.fontSize
            color = style.textColor
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }

        val strokePaint = Paint().apply {
            typeface = style.fontFamily
            textSize = layout.fontSize
            color = style.strokeColor
            style = Paint.Style.STROKE
            strokeWidth = style.strokeWidth
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }

        // Calculate vertical positioning
        val totalHeight = layout.lines.size * layout.fontSize * style.lineSpacing
        var y = bounds.centerY() - totalHeight / 2 + layout.fontSize

        layout.lines.forEach { line ->
            val x = bounds.centerX()

            // Draw stroke first (outline)
            canvas.drawText(line, x, y, strokePaint)

            // Draw fill
            canvas.drawText(line, x, y, paint)

            y += layout.fontSize * style.lineSpacing
        }
    }

    private fun renderVerticalText(
        canvas: Canvas,
        layout: TextLayout,
        bounds: RectF,
        style: TypographyStyle
    ) {
        // Save canvas state
        canvas.save()

        // Rotate 90 degrees for vertical text
        canvas.rotate(-90f, bounds.centerX(), bounds.centerY())

        // Render as horizontal (will appear vertical after rotation)
        val rotatedBounds = RectF(
            bounds.top,
            bounds.left,
            bounds.bottom,
            bounds.right
        )

        renderHorizontalText(canvas, layout, rotatedBounds, style)

        // Restore canvas
        canvas.restore()
    }

    override fun fitTextToBounds(
        text: String,
        bounds: RectF,
        maxFontSize: Float
    ): TextLayout {

        var fontSize = maxFontSize
        var lines: List<String>
        var overflow = false

        val paint = Paint().apply {
            textSize = fontSize
        }

        // Binary search for optimal font size
        while (fontSize > 8f) {
            paint.textSize = fontSize

            // Word wrap
            lines = wrapText(text, bounds.width(), paint)

            // Check if fits vertically
            val totalHeight = lines.size * fontSize * 1.2f
            if (totalHeight <= bounds.height()) {
                return TextLayout(lines, fontSize, bounds, false)
            }

            fontSize -= 2f
        }

        // Text doesn't fit, return with overflow flag
        paint.textSize = fontSize
        lines = wrapText(text, bounds.width(), paint)

        return TextLayout(lines, fontSize, bounds, true)
    }

    private fun wrapText(
        text: String,
        maxWidth: Float,
        paint: Paint
    ): List<String> {

        val words = text.split(" ")
        val lines = mutableListOf<String>()
        var currentLine = ""

        words.forEach { word ->
            val testLine = if (currentLine.isEmpty()) word else "$currentLine $word"
            val width = paint.measureText(testLine)

            if (width <= maxWidth) {
                currentLine = testLine
            } else {
                if (currentLine.isNotEmpty()) {
                    lines.add(currentLine)
                }
                currentLine = word
            }
        }

        if (currentLine.isNotEmpty()) {
            lines.add(currentLine)
        }

        return lines
    }

    override fun calculateFontSize(
        text: String,
        bounds: RectF,
        style: TypographyStyle
    ): Float {
        return fitTextToBounds(text, bounds, 48f).fontSize
    }
}
```

### 2. Reader Activity Integration

**File:** `app/src/main/java/eu/kanade/tachiyomi/ui/reader/ReaderActivity.kt`

Add translation support to the existing Reader:

```kotlin
// In ReaderActivity.kt
class ReaderActivity : BaseActivity() {

    private val translationManager: TranslationManager by injectLazy()
    private val translationPreferences: TranslationPreferences by injectLazy()

    private var translationEnabled by mutableStateOf(false)
    private var showTranslationSettings by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            TachiyomiTheme {
                ReaderContent(
                    // ... existing parameters
                    translationOverlay = {
                        if (translationEnabled) {
                            TranslationOverlay(
                                translationManager = translationManager,
                                onSettingsClick = { showTranslationSettings = true },
                                onDismiss = { translationEnabled = false }
                            )
                        }
                    }
                )

                if (showTranslationSettings) {
                    TranslationSettingsDialog(
                        onDismiss = { showTranslationSettings = false }
                    )
                }
            }
        }
    }
}
```

#### 2.1 Page Loader Decorator

**File:** `app/src/main/java/eu/kanade/tachiyomi/ui/reader/loader/TranslationPageLoader.kt`

```kotlin
class TranslationPageLoaderDecorator(
    private val delegate: PageLoader,
    private val translationManager: TranslationManager,
    private val chapterId: Long
) : PageLoader() {

    override suspend fun loadPage(page: ReaderPage) {
        // Load original page first
        delegate.loadPage(page)

        // Check if translation is enabled
        if (!translationManager.isEnabled.first()) {
            return
        }

        try {
            // Get page stream
            val stream = page.stream?.invoke()
            if (stream != null) {
                val bitmap = BitmapFactory.decodeStream(stream())

                // Translate page
                val translatedBitmap = translationManager.translatePage(
                    pageImage = bitmap,
                    chapterId = chapterId,
                    pageIndex = page.index
                )

                // Update page with translated image
                page.stream = { ByteArrayInputStream(bitmapToByteArray(translatedBitmap)) }
            }
        } catch (e: Exception) {
            Log.e("TranslationPageLoader", "Failed to translate page ${page.index}", e)
            // Fall back to original page
        }
    }

    override fun recycle() {
        delegate.recycle()
    }

    private fun bitmapToByteArray(bitmap: Bitmap): ByteArray {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        return stream.toByteArray()
    }
}
```

#### 2.2 ChapterLoader Integration

**File:** Update `app/src/main/java/eu/kanade/tachiyomi/ui/reader/loader/ChapterLoader.kt`

```kotlin
// In ChapterLoader.getPageLoader()
private fun getPageLoader(chapter: ReaderChapter): PageLoader {
    val baseLoader = when {
        isDownloaded(chapter) -> DownloadPageLoader(chapter, ...)
        isLocal(chapter) -> DirectoryPageLoader(chapter, ...)
        else -> HttpPageLoader(chapter, ...)
    }

    // Wrap with translation decorator if enabled
    val translationPreferences = Injekt.get<TranslationPreferences>()
    val translationManager = Injekt.get<TranslationManager>()

    return if (translationPreferences.autoTranslate().get()) {
        TranslationPageLoaderDecorator(
            delegate = baseLoader,
            translationManager = translationManager,
            chapterId = chapter.chapter.id
        )
    } else {
        baseLoader
    }
}
```

### 3. Settings UI

**File:** `translation/src/main/java/mihon/feature/translation/presentation/TranslationSettingsScreen.kt`

```kotlin
@Composable
fun TranslationSettingsScreen(
    translationManager: TranslationManager,
    translationPreferences: TranslationPreferences
) {
    val preference by translationManager.preference.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp)
    ) {
        // Enable/Disable Toggle
        item {
            SwitchPreferenceWidget(
                title = "Enable Translation",
                subtitle = "Automatically translate comic pages",
                checked = preference.enabled,
                onCheckedChange = { translationManager.setEnabled(it) }
            )
        }

        // Language Settings
        item {
            ListPreference(
                title = "Source Language",
                subtitle = preference.sourceLanguage.displayName,
                entries = Language.entries.map { it.displayName to it.code },
                onValueChange = { code ->
                    Language.fromCode(code)?.let { lang ->
                        translationManager.setLanguages(lang, preference.targetLanguage)
                    }
                }
            )
        }

        item {
            ListPreference(
                title = "Target Language",
                subtitle = preference.targetLanguage.displayName,
                entries = Language.entries.map { it.displayName to it.code },
                onValueChange = { code ->
                    Language.fromCode(code)?.let { lang ->
                        translationManager.setLanguages(preference.sourceLanguage, lang)
                    }
                }
            )
        }

        // API Key Settings
        item {
            CategoryHeader(title = "Translation API")
        }

        item {
            ListPreference(
                title = "Translation Provider",
                subtitle = preference.translatorProvider.name,
                entries = listOf(
                    "Gemini (Free)" to "gemini",
                    "Google Cloud" to "google_cloud",
                    "DeepSeek" to "openai_compat",
                    "Offline (ML Kit)" to "ml_kit"
                ),
                onValueChange = { translationPreferences.translatorProvider().set(it) }
            )
        }

        item {
            EditTextPreference(
                title = "Gemini API Key",
                summary = if (translationPreferences.geminiApiKey().get().isEmpty())
                    "Not configured" else "Configured",
                value = translationPreferences.geminiApiKey().get(),
                onValueChange = { translationPreferences.geminiApiKey().set(it) }
            )
        }

        // Quality Settings
        item {
            CategoryHeader(title = "Quality")
        }

        item {
            ListPreference(
                title = "Processing Quality",
                subtitle = translationPreferences.processingQuality().get(),
                entries = listOf(
                    "Ultra High" to "ultra_high",
                    "High" to "high",
                    "Balanced" to "balanced",
                    "Fast" to "fast"
                ),
                onValueChange = { translationPreferences.processingQuality().set(it) }
            )
        }

        // Cache Settings
        item {
            CategoryHeader(title = "Cache")
        }

        item {
            SwitchPreferenceWidget(
                title = "Enable Caching",
                subtitle = "Store translations to avoid re-processing",
                checked = translationPreferences.cacheEnabled().get(),
                onCheckedChange = { translationPreferences.cacheEnabled().set(it) }
            )
        }

        item {
            ActionPreference(
                title = "Clear Translation Cache",
                subtitle = "Free up storage space",
                onClick = {
                    // Show confirmation dialog
                }
            )
        }

        // Advanced Settings
        item {
            CategoryHeader(title = "Advanced")
        }

        item {
            SwitchPreferenceWidget(
                title = "Show Original Text",
                subtitle = "Display original text alongside translation",
                checked = preference.showOriginalText,
                onCheckedChange = {
                    translationManager.updatePreference(
                        preference.copy(showOriginalText = it)
                    )
                }
            )
        }

        item {
            SwitchPreferenceWidget(
                title = "Auto-Translate",
                subtitle = "Automatically translate when opening chapters",
                checked = preference.autoTranslate,
                onCheckedChange = {
                    translationManager.updatePreference(
                        preference.copy(autoTranslate = it)
                    )
                }
            )
        }
    }
}

@Composable
private fun CategoryHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = 8.dp)
    )
}
```

### 4. Performance Optimization

#### 4.1 Batch Processing

```kotlin
class BatchTranslationProcessor @Inject constructor(
    private val translationManager: TranslationManager
) {

    suspend fun translateChapter(
        pages: List<Bitmap>,
        chapterId: Long
    ): List<Bitmap> = coroutineScope {

        // Process pages in parallel (limited concurrency)
        pages.mapIndexedAsync(concurrency = 3) { index, page ->
            translationManager.translatePage(page, chapterId, index)
        }
    }
}

// Extension for limited parallelism
suspend fun <T, R> List<T>.mapIndexedAsync(
    concurrency: Int,
    transform: suspend (Int, T) -> R
): List<R> = coroutineScope {

    val semaphore = Semaphore(concurrency)
    mapIndexed { index, item ->
        async {
            semaphore.withPermit {
                transform(index, item)
            }
        }
    }.awaitAll()
}
```

#### 4.2 Memory Management

```kotlin
class TranslationMemoryManager @Inject constructor() {

    private val maxCacheSize = 100 * 1024 * 1024 // 100MB

    fun optimizeBitmap(bitmap: Bitmap): Bitmap {
        // Use RGB_565 for non-transparent images (50% memory savings)
        if (!bitmap.hasAlpha()) {
            return bitmap.copy(Bitmap.Config.RGB_565, false).also {
                bitmap.recycle()
            }
        }
        return bitmap
    }

    fun recycleBitmapSafely(bitmap: Bitmap?) {
        bitmap?.let {
            if (!it.isRecycled) {
                it.recycle()
            }
        }
    }
}
```

#### 4.3 Background Pre-translation

```kotlin
class PreTranslationService @Inject constructor(
    private val translationManager: TranslationManager,
    private val downloadManager: DownloadManager
) {

    fun preTranslateDownloadedChapters() {
        // Run in background worker
        CoroutineScope(Dispatchers.Default).launch {
            val downloadedChapters = downloadManager.getDownloadedChapters()

            downloadedChapters.forEach { chapter ->
                if (!hasTranslation(chapter)) {
                    translateChapter(chapter)
                }
            }
        }
    }

    private suspend fun hasTranslation(chapter: Chapter): Boolean {
        // Check if translation cache exists
        return translationManager.hasCachedTranslation(chapter.id, 0)
    }
}
```

### 5. User Experience Enhancements

#### 5.1 Translation Progress

```kotlin
@Composable
fun TranslationProgressDialog(
    currentPage: Int,
    totalPages: Int,
    onCancel: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Translating Chapter") },
        text = {
            Column {
                Text("Processing page $currentPage of $totalPages")
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { currentPage.toFloat() / totalPages },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onCancel) {
                Text("Cancel")
            }
        }
    )
}
```

#### 5.2 Quick Actions

```kotlin
// Add to Reader toolbar
@Composable
fun ReaderTopAppBar(
    // ... existing parameters
    onTranslateClick: () -> Unit
) {
    TopAppBar(
        // ... existing content
        actions = {
            IconButton(onClick = onTranslateClick) {
                Icon(
                    painter = painterResource(R.drawable.ic_translate_24dp),
                    contentDescription = "Translate"
                )
            }
        }
    )
}
```

### 6. Error Handling & Fallbacks

```kotlin
class RobustTranslationManager(
    private val translationManager: TranslationManager
) {

    suspend fun translatePageWithFallback(
        pageImage: Bitmap,
        chapterId: Long,
        pageIndex: Int
    ): Result<Bitmap> = try {

        val result = translationManager.translatePage(pageImage, chapterId, pageIndex)
        Result.success(result)

    } catch (e: NetworkException) {
        Result.failure(TranslationError.NetworkError(e))
    } catch (e: OutOfMemoryError) {
        Result.failure(TranslationError.MemoryError(e))
    } catch (e: Exception) {
        Result.failure(TranslationError.UnknownError(e))
    }
}

sealed class TranslationError : Exception() {
    data class NetworkError(val cause: Throwable) : TranslationError()
    data class MemoryError(val cause: Throwable) : TranslationError()
    data class APIError(val message: String) : TranslationError()
    data class UnknownError(val cause: Throwable) : TranslationError()
}
```

## Testing & Quality Assurance

### 1. Integration Tests

```kotlin
@Test
fun testFullTranslationPipeline() = runTest {
    // Load test image
    val testPage = loadTestImage("manga_page_01.jpg")

    // Run full pipeline
    val result = translationManager.translatePage(
        pageImage = testPage,
        chapterId = 1L,
        pageIndex = 0
    )

    // Verify result
    assertNotNull(result)
    assertTrue(result.width == testPage.width)
    assertTrue(result.height == testPage.height)
}
```

### 2. Performance Benchmarks

```kotlin
@Test
fun benchmarkTranslationSpeed() = runTest {
    val testPages = loadTestPages(count = 10)

    val times = testPages.map { page ->
        measureTimeMillis {
            translationManager.translatePage(page, 1L, 0)
        }
    }

    val averageTime = times.average()
    println("Average translation time: ${averageTime}ms")

    assertTrue(averageTime < 8000, "Translation too slow: ${averageTime}ms")
}
```

### 3. User Acceptance Testing

- [ ] Manga (Japanese vertical text)
- [ ] Manhua (Chinese horizontal text)
- [ ] Manhwa (Korean mixed text)
- [ ] Quality comparison with original
- [ ] Performance on low-end devices
- [ ] Battery impact testing

## Timeline

- **Week 1:** Typesetting engine
- **Week 2:** Reader integration
- **Week 3:** Settings UI
- **Week 4:** Performance optimization
- **Week 5:** Testing and refinement
- **Week 6:** Documentation and release preparation

## Success Metrics

- ✅ Translation quality > 8/10 (user rating)
- ✅ Processing time < 10s per page
- ✅ No crashes or memory leaks
- ✅ 90%+ user satisfaction
- ✅ Works on Android 8.0+
- ✅ Comprehensive documentation

## Final Deliverables

1. **Code:**
   - Fully integrated translation system
   - Comprehensive tests
   - Performance benchmarks

2. **Documentation:**
   - User guide
   - Developer guide
   - API documentation
   - Troubleshooting guide

3. **Assets:**
   - Models (YOLOv10, LaMa, etc.)
   - Sample translations
   - Demo videos

---

**Status:** Ready after Phase 4
**Estimated Duration:** 6 weeks
**Complexity:** High (UI/UX + integration)
