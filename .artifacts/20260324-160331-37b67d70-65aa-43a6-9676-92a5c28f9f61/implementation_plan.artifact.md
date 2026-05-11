# Network Initialization Flow Implementation Plan

## Problem Description

Implement a 3-step network initialization sequence for the CopyManga app:
1. **GET** `https://www.2026copy.com/h5` → obtain cookies
2. **GET** `https://api.2026copy.com/api/v3/system/network21?platform=1` → get config
3. **GET** `https://api.2026copy.com/api/v3/h5/homeIndex` → get home page information

## Progress

### ✅ Completed:
1. **DTO Updates** - [ComicDto.kt](file:///C:/Users/RICHARD/AndroidStudioProjects/CopyManga_Neo/app/src/main/java/com/rchdliang/copymanga_neo/data/remote/dto/ComicDto.kt) updated with correct fields (females, males, theme, popular, author_alias) and added `CharacterDto`, `ThemeDto` classes
2. **@WwwRetrofit Qualifier** - Added to [ApiRetrofit.kt](file:///C:/Users/RICHARD/AndroidStudioProjects/CopyManga_Neo/app/src/main/java/com/rchdliang/copymanga_neo/di/ApiRetrofit.kt)
3. **WwwApiService Interface** - Added to [SystemApiService.kt](file:///C:/Users/RICHARD/AndroidStudioProjects/CopyManga_Neo/app/src/main/java/com/rchdliang/copymanga_neo/data/remote/api/SystemApiService.kt)

### ⏳ Remaining:

## Proposed Changes

### 1. [NetworkModule.kt](file:///C:/Users/RICHARD/AndroidStudioProjects/CopyManga_Neo/app/src/main/java/com/rchdliang/copymanga_neo/di/NetworkModule.kt)

**Add import:**
```kotlin
import com.rchdliang.copymanga_neo.data.remote.api.WwwApiService
```

**Add provider methods after `provideSystemApiService()`:**
```kotlin
/**
 * 提供 WWW 专用的 Retrofit 实例（用于 www.2026copy.com）
 */
@Provides
@Singleton
@WwwRetrofit
fun provideWwwRetrofit(okHttpClient: OkHttpClient): Retrofit {
    val gson = GsonBuilder()
        .setStrictness(Strictness.LENIENT)
        .create()

    return Retrofit.Builder()
        .baseUrl(WWW_BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create(gson))
        .build()
}

/**
 * 提供 WwwApiService（使用 WWW 域名）
 */
@Provides
@Singleton
fun provideWwwApiService(@WwwRetrofit retrofit: Retrofit): WwwApiService {
    return retrofit.create(WwwApiService::class.java)
}
```

### 2. [MangaRepository.kt](file:///C:/Users/RICHARD/AndroidStudioProjects/CopyManga_Neo/app/src/main/java/com/rchdliang/copymanga_neo/domain/repository/MangaRepository.kt)

**Add method after `searchComics()`:**
```kotlin
/**
 * 初始化网络请求（获取 cookies 和配置）
 * 1. 访问 www.2026copy.com/h5 获取 cookies
 * 2. 访问 api.2026copy.com/api/v3/system/network21 获取配置
 */
suspend fun initialize(): Result<Unit>
```

### 3. [MangaRepositoryImpl.kt](file:///C:/Users/RICHARD/AndroidStudioProjects/CopyManga_Neo/app/src/main/java/com/rchdliang/copymanga_neo/data/repository/MangaRepositoryImpl.kt)

**Update constructor to inject new services:**
```kotlin
@Singleton
class MangaRepositoryImpl @Inject constructor(
    private val apiService: MangaApiService,
    private val wwwApiService: WwwApiService,
    private val systemApiService: SystemApiService
) : MangaRepository
```

**Add initialize() method after constructor:**
```kotlin
override suspend fun initialize(): Result<Unit> {
    return try {
        android.util.Log.d("MangaRepository", "Step 1: Fetching cookies from www.2026copy.com/h5")
        val wwwResponse = wwwApiService.getH5Page()
        if (!wwwResponse.isSuccessful) {
            android.util.Log.e("MangaRepository", "Failed to fetch cookies: HTTP ${wwwResponse.code()}")
            return Result.failure(Exception("Failed to fetch cookies: HTTP ${wwwResponse.code()}"))
        }
        android.util.Log.d("MangaRepository", "Step 1 complete: Cookies fetched successfully")

        android.util.Log.d("MangaRepository", "Step 2: Fetching config from api.2026copy.com/api/v3/system/network21")
        val configResponse = systemApiService.getNetworkConfig(platform = 1)
        if (!configResponse.isSuccessful) {
            android.util.Log.e("MangaRepository", "Failed to fetch config: HTTP ${configResponse.code()}")
            return Result.failure(Exception("Failed to fetch config: HTTP ${configResponse.code()}"))
        }
        val configBody = configResponse.body()
        if (configBody == null || configBody.code != 200) {
            android.util.Log.e("MangaRepository", "Failed to fetch config: ${configBody?.message}")
            return Result.failure(Exception("Failed to fetch config: ${configBody?.message}"))
        }
        android.util.Log.d("MangaRepository", "Step 2 complete: Config fetched successfully")

        android.util.Log.d("MangaRepository", "Initialization complete: All steps successful")
        Result.success(Unit)
    } catch (e: Exception) {
        android.util.Log.e("MangaRepository", "Initialization failed: ${e.message}", e)
        Result.failure(e)
    }
}
```

### 4. [CopyMangaApplication.kt](file:///C:/Users/RICHARD/AndroidStudioProjects/CopyManga_Neo/app/src/main/java/com/rchdliang/copymanga_neo/CopyMangaApplication.kt)

**Replace entire file content:**
```kotlin
package com.rchdliang.copymanga_neo

import android.app.Application
import com.rchdliang.copymanga_neo.domain.repository.MangaRepository
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Application 类，用于初始化 Hilt 依赖注入框架
 */
@HiltAndroidApp
class CopyMangaApplication : Application() {

    @Inject
    lateinit var mangaRepository: MangaRepository

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()

        // Initialize network (fetch cookies and config) on app startup
        applicationScope.launch {
            mangaRepository.initialize()
        }
    }
}
```

---

## Verification Plan

### Build Verification

**Command:**
```bash
cd C:/Users/RICHARD/AndroidStudioProjects/CopyManga_Neo
./gradlew app:assembleDebug
```

**Expected Result:** Build completes successfully with no compilation errors

### Manual Testing Steps

**Step 1: Deploy app to device or emulator**
- In Android Studio, click the green Run button (▶) in the toolbar
- OR use command line: `./gradlew app:installDebug`
- Select a connected Android device or emulator
- Wait for app to install and launch

**Step 2: Open Logcat window**
- In Android Studio, go to bottom panel and click "Logcat" tab
- OR use menu: View → Tool Windows → Logcat
- In the logcat filter dropdown, select the device and `com.rchdliang.copymanga_neo` package

**Step 3: Filter logs by MangaRepository tag**
- In the logcat search/filter box, type: `MangaRepository`
- Set log level to: `Debug` (or `Verbose` to see all logs)

**Step 4: Verify initialization sequence in logcat**
After app launches, you should see these log messages in order:
```
D/MangaRepository: Step 1: Fetching cookies from www.2026copy.com/h5
D/MangaRepository: Step 1 complete: Cookies fetched successfully
D/MangaRepository: Step 2: Fetching config from api.2026copy.com/api/v3/system/network21
D/MangaRepository: Step 2 complete: Config fetched successfully
D/MangaRepository: Initialization complete: All steps successful
```

**Step 5: Verify home screen loads correctly**
- App should launch to home screen automatically
- Comic list should display with visible items showing:
  - Comic name (e.g., "梅之助君是男子宿舍的女孩子")
  - Cover image (visible thumbnail)
  - Author name
  - Popularity count (number)
- No error messages, crash dialogs, or loading spinners stuck indefinitely

**Step 6: Check HomeViewModel logs**
- In Logcat, change the filter to: `HomeViewModel`
- Should see log message: `Successfully loaded X comics` (where X is number of comics loaded)
- No exception stack traces should appear

**Step 7: Verify no errors**
- Clear logcat (click trash icon)
- Restart the app
- Watch for any ERROR or WARN level logs
- No network errors, null pointer exceptions, or crashes should occur

### Success Criteria

- ✅ Build completes without errors (`./gradlew app:assembleDebug` returns success)
- ✅ App launches without crashes
- ✅ Logcat shows successful 3-step initialization sequence (all 5 log messages appear in order)
- ✅ Home screen displays comic list with all fields (name, cover image, author, popularity count)
- ✅ No null pointer exceptions or network errors in logcat
- ✅ `HomeViewModel` logs "Successfully loaded X comics"

### Troubleshooting

**If build fails:**
- Check for missing imports in modified files
- Ensure all new classes (`WwwApiService`, `CharacterDto`, `ThemeDto`) are properly imported

**If initialization fails:**
- Check network connectivity on device/emulator
- Verify device can access www.2026copy.com and api.2026copy.com
- Look for HTTP error codes in logcat error messages
- Check for SSL/TLS or CORS issues

**If home screen doesn't load:**
- Check `HomeViewModel` logs for errors
- Verify `getHomeComics()` is called after initialization completes
- Check for any exceptions in the repository layer
