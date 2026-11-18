package com.example.mechanicworkplace

import android.content.Context
import android.util.Log
import com.example.mechanicworkplace.data.auth.AuthGateway
import com.example.mechanicworkplace.data.auth.AuthRepository
import com.example.mechanicworkplace.data.auth.AuthSessionManager
import com.example.mechanicworkplace.data.auth.BasicAuthProvider
import com.example.mechanicworkplace.data.auth.CredentialsStore
import com.example.mechanicworkplace.data.auth.KeystoreCipher
import com.example.mechanicworkplace.data.auth.SyncQueueGate
import com.example.mechanicworkplace.data.local.AppDatabase
import com.example.mechanicworkplace.data.local.RoomCacheCleaner
import com.example.mechanicworkplace.data.photo.AndroidPhotoResolver
import com.example.mechanicworkplace.data.photo.DefaultPhotoRepository
import com.example.mechanicworkplace.data.photo.PhotoRepository
import com.example.mechanicworkplace.data.photo.PhotoResolver
import com.example.mechanicworkplace.data.remote.OneCMobileDataSource
import com.example.mechanicworkplace.data.remote.OrdersDataSource
import com.example.mechanicworkplace.data.remote.onecmobile.LogSanitizer
import com.example.mechanicworkplace.data.remote.onecmobile.OneCMobileApi
import com.example.mechanicworkplace.data.remote.onecmobile.OneCMobileApiClient
import com.example.mechanicworkplace.data.remote.onecmobile.OneCMobileJson
import com.example.mechanicworkplace.repository.CachedOrdersRepository
import com.example.mechanicworkplace.repository.OrdersRepository
import com.example.mechanicworkplace.sync.SyncQueueProcessor
import com.example.mechanicworkplace.sync.SyncQueueRepository
import com.example.mechanicworkplace.sync.SyncScheduler
import java.io.File

class AppContainer private constructor(context: Context) {
    private val appContext = context.applicationContext

    val database: AppDatabase = AppDatabase.build(appContext)

    val basicAuthProvider = BasicAuthProvider()
    val keystoreCipher = KeystoreCipher()
    val credentialsStore = CredentialsStore(appContext, keystoreCipher)
    val syncQueueGate = SyncQueueGate(appContext)

    val authSessionManager = AuthSessionManager(
        authProvider = basicAuthProvider,
        credentialsStore = credentialsStore,
        queueGate = syncQueueGate
    )

    val oneCMobileApi: OneCMobileApi = OneCMobileApiClient(
        baseUrl = BuildConfig.API_BASE_URL,
        authProvider = basicAuthProvider,
        onUnauthorized = { authSessionManager.onUnauthorized() },
        logger = { message ->
            if (BuildConfig.DEBUG) Log.d(TAG, LogSanitizer.redact(message))
        }
    )

    val ordersDataSource: OrdersDataSource = OneCMobileDataSource(oneCMobileApi)
    val syncScheduler = SyncScheduler(appContext)

    val syncQueueRepository = SyncQueueRepository(
        dao = database.syncOperationDao(),
        wake = { syncScheduler.enqueue() },
        json = OneCMobileJson
    )

    val syncQueueProcessor = SyncQueueProcessor(
        dao = database.syncOperationDao(),
        orderCache = database.orderCacheDao(),
        photoCache = database.photoCacheDao(),
        dataSource = ordersDataSource,
        isPaused = { syncQueueGate.state.value is com.example.mechanicworkplace.data.auth.QueueGateState.Paused },
        pauseAuth = { syncQueueGate.pauseForAuth() },
        json = OneCMobileJson
    )

    val ordersRepository: OrdersRepository = CachedOrdersRepository(
        db = database,
        dataSource = ordersDataSource,
        syncQueue = syncQueueRepository
    )

    val photoRepository: PhotoRepository = DefaultPhotoRepository(
        dataSource = ordersDataSource,
        db = database,
        syncQueue = syncQueueRepository,
        cacheDir = File(appContext.cacheDir, "order-photos")
    )

    val photoResolver: PhotoResolver = AndroidPhotoResolver(
        context = appContext,
        workDir = File(appContext.filesDir, "pending-photos")
    )

    val cacheCleaner = RoomCacheCleaner(
        orderDao = database.orderCacheDao(),
        photoDao = database.photoCacheDao(),
        catalogDao = database.catalogCacheDao(),
        syncDao = database.syncOperationDao()
    )

    val authRepository: AuthGateway = AuthRepository(
        api = oneCMobileApi,
        authProvider = basicAuthProvider,
        authSession = authSessionManager,
        syncQueue = syncQueueRepository,
        cacheCleaner = cacheCleaner
    )

    companion object {
        private const val TAG = "OneCMobile"

        @Volatile private var instance: AppContainer? = null

        fun get(context: Context): AppContainer =
            instance ?: synchronized(this) {
                instance ?: AppContainer(context).also { instance = it }
            }
    }
}
