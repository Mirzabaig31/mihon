# Translation Module Testing Guide

## Overview

This guide provides comprehensive testing instructions for the Translation module, covering unit tests, integration tests, manual testing, and build validation.

---

## Table of Contents

1. [Build Testing](#build-testing)
2. [Unit Testing](#unit-testing)
3. [Integration Testing](#integration-testing)
4. [Manual Testing](#manual-testing)
5. [Performance Testing](#performance-testing)
6. [Security Testing](#security-testing)

---

## Build Testing

### Prerequisites

```bash
# Ensure you have:
- Android Studio Hedgehog (2023.1.1) or later
- JDK 17
- Android SDK 34
- Gradle 8.4+
```

### Build Commands

#### 1. Clean Build

```bash
./gradlew clean
./gradlew :translation:assembleDebug
```

**Expected Output:**
```
BUILD SUCCESSFUL in 45s
```

#### 2. Check Module Dependencies

```bash
./gradlew :translation:dependencies
```

**Verify:**
- ✅ All dependencies resolved
- ✅ No version conflicts
- ✅ OkHttp, Coil, Compose present

#### 3. Lint Check

```bash
./gradlew :translation:lintDebug
```

**Expected:**
- 0 errors
- < 10 warnings (acceptable)

#### 4. Full App Build

```bash
./gradlew :app:assembleDebug
```

**Expected:**
```
BUILD SUCCESSFUL in 2m 15s
```

### Build Troubleshooting

#### Issue: "Could not resolve dependency"

**Solution:**
```bash
# Clear Gradle cache
rm -rf ~/.gradle/caches/
./gradlew clean --refresh-dependencies
```

#### Issue: "Duplicate class found"

**Check:**
```bash
# List all dependencies
./gradlew :app:dependencies --configuration debugRuntimeClasspath

# Look for duplicates
grep -i "duplicate" build/reports/lint-results.html
```

#### Issue: Network timeout (Gradle can't download)

**Offline Build:**
```bash
# Use local Maven cache
./gradlew build --offline
```

---

## Unit Testing

### Test Structure

```
translation/src/test/java/
├── domain/
│   └── TranslationModelsTest.kt
├── data/
│   ├── GeminiTranslatorTest.kt
│   ├── TranslationCacheTest.kt
│   └── TranslationManagerImplTest.kt
└── presentation/
    └── TranslationOverlayTest.kt
```

### Sample Unit Tests

#### 1. Domain Models Test

**File:** `domain/TranslationModelsTest.kt`

```kotlin
class TranslationModelsTest {

    @Test
    fun `Language fromCode should return correct language`() {
        val japanese = Language.fromCode("ja")
        assertEquals(Language.JAPANESE, japanese)
    }

    @Test
    fun `Language fromCode should return null for invalid code`() {
        val invalid = Language.fromCode("invalid")
        assertNull(invalid)
    }

    @Test
    fun `TranslationResult should enforce confidence bounds`() {
        // This will fail without validation fix
        assertThrows<IllegalArgumentException> {
            TranslationResult(
                originalText = "test",
                translatedText = "test",
                confidence = 1.5f // Invalid!
            )
        }
    }
}
```

#### 2. Gemini Translator Test

**File:** `data/GeminiTranslatorTest.kt`

```kotlin
class GeminiTranslatorTest {

    private lateinit var translator: GeminiTranslator
    private lateinit var mockClient: OkHttpClient

    @Before
    fun setup() {
        mockClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                // Mock API response
                val mockResponse = """
                {
                  "candidates": [{
                    "content": {
                      "parts": [{"text": "Hello\nWorld"}]
                    }
                  }]
                }
                """.trimIndent()

                Response.Builder()
                    .code(200)
                    .message("OK")
                    .body(mockResponse.toResponseBody("application/json".toMediaType()))
                    .protocol(Protocol.HTTP_1_1)
                    .request(chain.request())
                    .build()
            }
            .build()

        translator = GeminiTranslator(
            apiKey = "test-key",
            client = mockClient
        )
    }

    @Test
    fun `translate should batch texts correctly`() = runTest {
        val texts = List(25) { "Text $it" } // 25 texts

        val results = translator.translate(
            texts,
            Language.JAPANESE,
            Language.ENGLISH
        )

        // Should batch into 3 requests (10, 10, 5)
        assertEquals(25, results.size)
    }

    @Test
    fun `translate should handle network errors gracefully`() = runTest {
        val failingClient = OkHttpClient.Builder()
            .addInterceptor { throw IOException("Network error") }
            .build()

        val failingTranslator = GeminiTranslator("test-key", failingClient)

        val results = failingTranslator.translate(
            listOf("test"),
            Language.JAPANESE,
            Language.ENGLISH
        )

        // Should return original text on error
        assertEquals("test", results[0].translatedText)
        assertNotNull(results[0].error)
    }

    @Test
    fun `translate should return empty API key error`() = runTest {
        val emptyKeyTranslator = GeminiTranslator("", mockClient)

        val results = emptyKeyTranslator.translate(
            listOf("test"),
            Language.JAPANESE,
            Language.ENGLISH
        )

        assertNotNull(results[0].error)
        assertTrue(results[0].error!!.contains("API key"))
    }
}
```

#### 3. Translation Cache Test

**File:** `data/TranslationCacheTest.kt`

```kotlin
@RunWith(RobolectricTestRunner::class)
class TranslationCacheTest {

    private lateinit var cache: TranslationCache
    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        cache = TranslationCache(context)
    }

    @After
    fun teardown() = runTest {
        cache.clearAll()
    }

    @Test
    fun `cache should store and retrieve translation data`() = runTest {
        val data = TranslationData(
            pageIndex = 0,
            chapterId = 123L,
            sourceLanguage = Language.JAPANESE,
            targetLanguage = Language.ENGLISH,
            bubbles = emptyList(),
            processingTimeMs = 1000L
        )

        cache.put(123L, 0, data)

        val retrieved = cache.get(123L, 0)

        assertNotNull(retrieved)
        assertEquals(0, retrieved!!.pageIndex)
        assertEquals(123L, retrieved.chapterId)
    }

    @Test
    fun `cache should expire after 30 days`() = runTest {
        val data = TranslationData(
            pageIndex = 0,
            chapterId = 123L,
            sourceLanguage = Language.JAPANESE,
            targetLanguage = Language.ENGLISH,
            bubbles = emptyList(),
            processingTimeMs = 1000L,
            timestamp = System.currentTimeMillis() - (31L * 24 * 60 * 60 * 1000) // 31 days ago
        )

        cache.put(123L, 0, data)

        val retrieved = cache.get(123L, 0)

        assertNull(retrieved) // Should be expired
    }

    @Test
    fun `cache should handle corrupted data`() = runTest {
        // Manually write corrupted data
        val file = File(context.cacheDir, "translation_cache/123/page_0.json")
        file.parentFile?.mkdirs()
        file.writeText("corrupted json {{{")

        val retrieved = cache.get(123L, 0)

        assertNull(retrieved) // Should handle gracefully
        assertFalse(file.exists()) // Should delete corrupted file
    }
}
```

### Running Unit Tests

```bash
# Run all tests
./gradlew :translation:test

# Run specific test class
./gradlew :translation:testDebugUnitTest --tests GeminiTranslatorTest

# Run with coverage
./gradlew :translation:testDebugUnitTest jacocoTestReport

# View coverage report
open translation/build/reports/jacoco/test/html/index.html
```

---

## Integration Testing

### Integration Test Scenarios

#### 1. Full Translation Pipeline

**File:** `androidTest/TranslationPipelineTest.kt`

```kotlin
@RunWith(AndroidJUnit4::class)
class TranslationPipelineTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var translationManager: TranslationManager

    @Before
    fun setup() {
        // Inject real implementations
        translationManager = TranslationManagerImpl(
            bubbleDetector = StubBubbleDetector(),
            ocrEngine = StubOCREngine(),
            translator = GeminiTranslator(
                apiKey = "test-api-key",
                client = OkHttpClient()
            ),
            inpaintingEngine = StubInpaintingEngine(),
            preferences = TranslationPreferences(PreferenceStore(...)),
            cache = TranslationCache(ApplicationProvider.getApplicationContext())
        )
    }

    @Test
    fun fullTranslationPipeline_completesSuccessfully() = runTest {
        // Load test image
        val testBitmap = loadTestBitmap("manga_page.jpg")

        // Enable translation
        translationManager.setEnabled(true)
        translationManager.setLanguages(
            Language.JAPANESE,
            Language.ENGLISH
        )

        // Translate page
        val result = translationManager.translatePage(
            pageImage = testBitmap,
            chapterId = 1L,
            pageIndex = 0
        )

        // Verify
        assertNotNull(result)
        assertEquals(testBitmap.width, result.width)
        assertEquals(testBitmap.height, result.height)
    }

    @Test
    fun translation_usesCacheOnSecondAttempt() = runTest {
        val testBitmap = loadTestBitmap("manga_page.jpg")

        // First translation
        val start1 = System.currentTimeMillis()
        val result1 = translationManager.translatePage(testBitmap, 1L, 0)
        val time1 = System.currentTimeMillis() - start1

        // Second translation (should use cache)
        val start2 = System.currentTimeMillis()
        val result2 = translationManager.translatePage(testBitmap, 1L, 0)
        val time2 = System.currentTimeMillis() - start2

        // Cache should be significantly faster
        assertTrue(time2 < time1 / 2)
    }
}
```

### Running Integration Tests

```bash
# Run on connected device
./gradlew :translation:connectedAndroidTest

# Run on emulator
./gradlew :translation:connectedDebugAndroidTest

# Run specific test
./gradlew :translation:connectedAndroidTest \
    --tests TranslationPipelineTest.fullTranslationPipeline_completesSuccessfully
```

---

## Manual Testing

### Test Plan Checklist

#### Phase 1 Features

- [ ] **1. Translation Manager Initialization**
  - [ ] Module loads without crash
  - [ ] Dependency injection works
  - [ ] Preferences accessible

- [ ] **2. Gemini API Integration**
  - [ ] Set API key via preferences
  - [ ] Enable translation
  - [ ] Translate sample text
  - [ ] Verify translated output
  - [ ] Check error handling (invalid API key)

- [ ] **3. Caching System**
  - [ ] First translation creates cache
  - [ ] Second translation uses cache
  - [ ] Cache persists across app restarts
  - [ ] Manual cache clear works

- [ ] **4. UI Components**
  - [ ] TranslationOverlay displays correctly
  - [ ] Settings button opens settings
  - [ ] Dismiss button disables translation
  - [ ] Language labels show correctly

### Manual Test Scenarios

#### Scenario 1: First-Time Setup

1. **Install app with translation module**
   ```bash
   ./gradlew :app:installDebug
   ```

2. **Open app and navigate to Reader settings**

3. **Enable translation**
   - Go to Settings → Translation
   - Toggle "Enable Translation"
   - Set Source Language: Japanese
   - Set Target Language: English

4. **Configure API key**
   - Get free Gemini API key from: https://makersuite.google.com/app/apikey
   - Enter in "Gemini API Key" field
   - Save settings

5. **Test translation**
   - Open a manga chapter
   - Translation banner should appear
   - (Phase 1: Will use stub OCR, so translation quality limited)

#### Scenario 2: Translation with Caching

1. **Translate a page** (first time)
   - Note the processing time
   - Check logcat for "Translation completed"

2. **Navigate to next page and back**
   - Should be instant (from cache)
   - Logcat should show "Using cached translation"

3. **Clear cache**
   - Go to Settings → Translation → Clear Cache
   - Translate same page again
   - Should process again (not instant)

#### Scenario 3: Error Handling

1. **Test with invalid API key**
   - Set API key to "invalid-key-test"
   - Try to translate
   - Should show error message (not crash)

2. **Test offline mode**
   - Enable airplane mode
   - Try to translate
   - Should fail gracefully with offline message

3. **Test with very large image**
   - Open high-resolution manga page
   - Should handle without OutOfMemoryError

---

## Performance Testing

### Benchmarks

#### 1. Translation Speed

**Target:** < 10 seconds per page (Phase 1 with stubs)

```kotlin
@Test
fun benchmarkTranslationSpeed() = runTest {
    val testPages = loadTestPages(count = 10)
    val times = mutableListOf<Long>()

    testPages.forEach { page ->
        val time = measureTimeMillis {
            translationManager.translatePage(page, 1L, 0)
        }
        times.add(time)
    }

    val average = times.average()
    val max = times.maxOrNull() ?: 0L
    val min = times.minOrNull() ?: 0L

    println("Average: ${average}ms, Min: ${min}ms, Max: ${max}ms")

    assertTrue(average < 10000, "Average translation too slow: ${average}ms")
}
```

#### 2. Memory Usage

**Target:** < 200MB peak memory

```kotlin
@Test
fun benchmarkMemoryUsage() = runTest {
    val runtime = Runtime.getRuntime()

    // Baseline
    runtime.gc()
    val baseline = runtime.totalMemory() - runtime.freeMemory()

    // Translate 10 pages
    repeat(10) { i ->
        val page = loadTestPage(i)
        translationManager.translatePage(page, 1L, i)
    }

    // Peak memory
    runtime.gc()
    val peak = runtime.totalMemory() - runtime.freeMemory()

    val memoryUsed = (peak - baseline) / (1024 * 1024) // MB

    println("Memory used: ${memoryUsed}MB")

    assertTrue(memoryUsed < 200, "Memory usage too high: ${memoryUsed}MB")
}
```

#### 3. Cache Performance

**Target:** Cache hit < 50ms

```bash
# Use ADB to measure
adb shell am start -n eu.kanade.tachiyomi/.MainActivity

# Monitor logcat for timing
adb logcat | grep "TranslationManager"
```

---

## Security Testing

### Security Checklist

- [ ] **API Key Storage**
  - [ ] Keys not logged
  - [ ] Keys not in plain text in memory dumps
  - [ ] Keys encrypted in SharedPreferences (future improvement)

- [ ] **Network Security**
  - [ ] HTTPS enforced for all API calls
  - [ ] Certificate validation enabled
  - [ ] No certificate pinning (intentional for flexibility)

- [ ] **Input Validation**
  - [ ] Text length limits enforced
  - [ ] No script injection in prompts
  - [ ] File path traversal prevented

### Manual Security Tests

#### Test 1: API Key Exposure

```bash
# Check if API key appears in logs
adb logcat | grep -i "api"

# Should NOT see actual API key value
```

#### Test 2: Network Traffic

```bash
# Use Charles Proxy or Wireshark
# Verify:
# 1. All requests use HTTPS
# 2. API key in header (not URL if possible)
# 3. No sensitive data in query params
```

---

## Test Reports

### Generate Coverage Report

```bash
./gradlew :translation:testDebugUnitTest jacocoTestReport

# View report
open translation/build/reports/jacoco/testDebugUnitTest/html/index.html
```

**Target Coverage:**
- Domain layer: > 90%
- Data layer: > 80%
- Presentation layer: > 70%

### Generate Lint Report

```bash
./gradlew :translation:lintDebug

# View report
open translation/build/reports/lint-results.html
```

---

## Automated Testing (CI/CD)

### GitHub Actions Workflow

**File:** `.github/workflows/translation-tests.yml`

```yaml
name: Translation Module Tests

on: [push, pull_request]

jobs:
  test:
    runs-on: ubuntu-latest

    steps:
      - uses: actions/checkout@v3

      - name: Set up JDK 17
        uses: actions/setup-java@v3
        with:
          java-version: '17'
          distribution: 'temurin'

      - name: Grant execute permission for gradlew
        run: chmod +x gradlew

      - name: Run unit tests
        run: ./gradlew :translation:testDebugUnitTest

      - name: Generate coverage report
        run: ./gradlew :translation:jacocoTestReport

      - name: Upload coverage to Codecov
        uses: codecov/codecov-action@v3
        with:
          files: ./translation/build/reports/jacoco/test/jacocoTestReport.xml
```

---

## Summary

### Phase 1 Testing Status

| Test Type | Status | Coverage |
|-----------|--------|----------|
| Build | ⚠️ Pending | N/A |
| Unit Tests | ❌ Not Implemented | 0% |
| Integration Tests | ❌ Not Implemented | 0% |
| Manual Tests | ✅ Test Plan Ready | N/A |
| Performance | ❌ Not Implemented | N/A |
| Security | ⚠️ Reviewed | N/A |

### Next Steps

1. ✅ Create test infrastructure
2. ✅ Implement unit tests for critical paths
3. ✅ Set up CI/CD pipeline
4. ✅ Perform manual testing with real API key
5. ✅ Generate coverage reports

---

**Last Updated:** November 8, 2025
**Phase:** 1 (Foundation)
**Test Plan Version:** 1.0
