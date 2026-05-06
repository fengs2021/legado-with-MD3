package io.legado.app.model

import io.legado.app.constant.AppLog
import io.legado.app.constant.EventBus
import io.legado.app.constant.PageAnim.scrollPageAnim
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.BookProgress
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.readRecord.ReadRecord
import io.legado.app.data.entities.readRecord.ReadRecordSession
import io.legado.app.data.repository.ReadRecordRepository
import io.legado.app.help.AppWebDav
import io.legado.app.help.book.BookHelp
import io.legado.app.help.book.AIContentCorrector
import io.legado.app.ui.main.my.aiCorrection.AICorrectionConfig
import io.legado.app.help.book.ContentProcessor
import io.legado.app.help.book.BookContent
import io.legado.app.help.book.isImage
import io.legado.app.help.book.isLocal
import io.legado.app.help.book.isPdf
import io.legado.app.help.book.isSameNameAuthor
import io.legado.app.help.book.readSimulating
import io.legado.app.help.book.simulatedTotalChapterNum
import io.legado.app.help.book.update
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.help.coroutine.Coroutine
import io.legado.app.help.globalExecutor
import io.legado.app.model.localBook.TextFile
import io.legado.app.model.webBook.WebBook
import io.legado.app.service.BaseReadAloudService
import io.legado.app.service.CacheBookService
import io.legado.app.ui.book.read.page.entities.TextChapter
import io.legado.app.ui.book.read.page.provider.ChapterProvider
import io.legado.app.ui.book.read.page.provider.LayoutProgressListener
import io.legado.app.utils.postEvent
import io.legado.app.utils.stackTraceStr
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import splitties.init.appCtx
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.max
import kotlin.math.min


@Suppress("MemberVisibilityCanBePrivate")
object ReadBook : CoroutineScope by MainScope(), KoinComponent {
    var book: Book? = null
    var callBack: CallBack? = null
    var inBookshelf = false
    var chapterSize = 0
    var simulatedChapterSize = 0
    var durChapterIndex = 0
    var durChapterPos = 0
    var isLocalBook = true
    var chapterChanged = false
    var prevTextChapter: TextChapter? = null
    var curTextChapter: TextChapter? = null
    var nextTextChapter: TextChapter? = null
    var bookSource: BookSource? = null
    var msg: String? = null
    private val readRecordRepository: ReadRecordRepository by inject()
    private var lastReadLength: Long = 0
    private val loadingChapters = arrayListOf<Int>()
    private val readRecord = ReadRecord()
    private val chapterLoadingJobs = ConcurrentHashMap<Int, Coroutine<*>>()
    private val prevChapterLoadingLock = Mutex()
    private val curChapterLoadingLock = Mutex()
    private val nextChapterLoadingLock = Mutex()
    private val aiCorrectionMutex = Mutex()
    private val aiCorrectionScope = CoroutineScope(SupervisorJob() + IO)
    internal val correctedChapterCache = hashMapOf<String, Long>()  // bookUrl#chapterIndex -> timestamp of when correction was added

    /** 清除AI修正缓存，下一次读时会重新修正 */
    fun clearCorrectionCache() {
        correctedChapterCache.clear()
    }
    var readStartTime: Long = System.currentTimeMillis()

    /* 跳转进度前进度记录 */
    var lastBookProgress: BookProgress? = null

    /* web端阅读进度记录 */
    var webBookProgress: BookProgress? = null

    var preDownloadTask: Job? = null
    val downloadedChapters = hashSetOf<Int>()
    val downloadFailChapters = hashMapOf<Int, Int>()
    var contentProcessor: ContentProcessor? = null
    val downloadScope = CoroutineScope(SupervisorJob() + IO)
    val preDownloadSemaphore = Semaphore(2)

    val executor = globalExecutor

    private val ioScope = CoroutineScope(IO)

    private var autoSaveJob: Job? = null

    private var currentActiveSession: ReadRecordSession? = null
    //占位
    private var currentReadLength: Long = 10L
    private const val AUTO_SAVE_INTERVAL = 120 * 1000L

    private const val MIN_READ_DURATION = 10 * 1000L

    fun resetData(book: Book) {
        ReadBook.book = book
        readRecord.bookName = book.name
        readRecord.bookAuthor = book.author
        readRecord.readTime = appDb.readRecordDao.getReadTime("", book.name, book.author) ?: 0
        chapterSize = appDb.bookChapterDao.getChapterCount(book.bookUrl)
        simulatedChapterSize = if (book.readSimulating()) {
            book.simulatedTotalChapterNum()
        } else {
            chapterSize
        }
        contentProcessor = ContentProcessor.get(book)
        durChapterIndex = book.durChapterIndex
        durChapterPos = book.durChapterPos
        isLocalBook = book.isLocal
        clearTextChapter()
        callBack?.upContent()
        callBack?.upMenuView()
        callBack?.upPageAnim()
        upWebBook(book)
        lastBookProgress = null
        webBookProgress = null
        TextFile.clear()
        synchronized(this) {
            loadingChapters.clear()
            downloadedChapters.clear()
            downloadFailChapters.clear()
        }
    }

    fun upData(book: Book) {
        ReadBook.book = book
        chapterSize = appDb.bookChapterDao.getChapterCount(book.bookUrl)
        simulatedChapterSize = if (book.readSimulating()) {
            book.simulatedTotalChapterNum()
        } else {
            chapterSize
        }
        if (durChapterIndex != book.durChapterIndex) {
            durChapterIndex = book.durChapterIndex
            durChapterPos = book.durChapterPos
            clearTextChapter()
        }
        if (curTextChapter?.isCompleted == false) {
            curTextChapter = null
        }
        if (nextTextChapter?.isCompleted == false) {
            nextTextChapter = null
        }
        if (prevTextChapter?.isCompleted == false) {
            prevTextChapter = null
        }
        callBack?.upMenuView()
        upWebBook(book)
        synchronized(this) {
            loadingChapters.clear()
            downloadedChapters.clear()
            downloadFailChapters.clear()
        }
    }

    fun upWebBook(book: Book) {
        if (book.isLocal) {
            bookSource = null
            if (book.getImageStyle().isNullOrBlank() && (book.isImage || book.isPdf)) {
                book.setImageStyle(Book.imgStyleFull)
            }
        } else {
            appDb.bookSourceDao.getBookSource(book.origin)?.let {
                bookSource = it
                if (book.getImageStyle().isNullOrBlank()) {
                    var imageStyle = it.getContentRule().imageStyle
                    if (imageStyle.isNullOrBlank() && (book.isImage || book.isPdf)) {
                        imageStyle = Book.imgStyleFull
                    }
                    book.setImageStyle(imageStyle)
                    if (imageStyle.equals(Book.imgStyleSingle, true)) {
                        book.setPageAnim(0)
                    }
                }
            } ?: let {
                bookSource = null
            }
        }
    }

    fun upReadBookConfig(book: Book) {
        val oldIndex = ReadBookConfig.styleSelect
        ReadBookConfig.isComic = book.isImage
        if (oldIndex != ReadBookConfig.styleSelect) {
            postEvent(EventBus.UP_CONFIG, arrayListOf(1, 2, 5))
            if (AppConfig.readBarStyleFollowPage) {
                postEvent(EventBus.UPDATE_READ_ACTION_BAR, true)
            }
        }
    }

    fun setProgress(progress: BookProgress) {
        if (progress.durChapterIndex < chapterSize &&
            (durChapterIndex != progress.durChapterIndex
                    || durChapterPos != progress.durChapterPos)
        ) {
            durChapterIndex = progress.durChapterIndex
            durChapterPos = progress.durChapterPos
            saveRead()
            clearTextChapter()
            callBack?.upContent()
            loadContent(resetPageOffset = true)
        }
    }

    //暂时保存跳转前进度
    fun saveCurrentBookProgress() {
        if (lastBookProgress != null) return //避免进度条连续跳转不能覆盖最初的进度记录
        lastBookProgress = book?.let { BookProgress(it) }
    }

    //恢复跳转前进度
    fun restoreLastBookProgress() {
        lastBookProgress?.let {
            setProgress(it)
            lastBookProgress = null
        }
    }

    fun clearTextChapter() {
        clearExpiredChapterLoadingJob(true)
        prevTextChapter = null
        curTextChapter = null
        nextTextChapter = null
    }

    fun clearSearchResult() {
        curTextChapter?.clearSearchResult()
        prevTextChapter?.clearSearchResult()
        nextTextChapter?.clearSearchResult()
    }

    fun uploadProgress(toast: Boolean = false, successAction: (() -> Unit)? = null) {
        book?.let {
            launch(IO) {
                AppWebDav.uploadBookProgress(it, toast) {
                    successAction?.invoke()
                }
                ensureActive()
                it.update()
            }
        }
    }

    /**
     * 同步阅读进度
     * 如果当前进度快于服务器进度或者没有进度进行上传，如果慢与服务器进度则执行传入动作
     */
    fun syncProgress(
        newProgressAction: ((progress: BookProgress) -> Unit)? = null,
        uploadSuccessAction: (() -> Unit)? = null,
        syncSuccessAction: (() -> Unit)? = null
    ) {
        if (!AppConfig.syncBookProgress) return
        val book = book ?: return
        Coroutine.async {
            AppWebDav.getBookProgress(book)
        }.onError {
            AppLog.put("拉取阅读进度失败", it)
        }.onSuccess { progress ->
            if (progress == null || progress.durChapterIndex < book.durChapterIndex ||
                (progress.durChapterIndex == book.durChapterIndex
                        && progress.durChapterPos < book.durChapterPos)
            ) {
                // 服务器没有进度或者进度比服务器快，上传现有进度
                Coroutine.async {
                    AppWebDav.uploadBookProgress(BookProgress(book), uploadSuccessAction)
                    book.update()
                }
            } else if (progress.durChapterIndex > book.durChapterIndex ||
                progress.durChapterPos > book.durChapterPos
            ) {
                // 进度比服务器慢，执行传入动作
                newProgressAction?.invoke(progress)
            } else {
                syncSuccessAction?.invoke()
            }
        }
    }

    fun initReadTime() {
        val currentBookName = book?.name ?: return
        val currentBookAuthor = book?.author ?: ""
        if (currentActiveSession != null &&
            (currentActiveSession!!.bookName != currentBookName || currentActiveSession!!.bookAuthor != currentBookAuthor)
        ) {
            commitReadSession()
        }

        if (currentActiveSession == null) {
            lastReadLength = currentReadLength
            currentActiveSession = ReadRecordSession(
                deviceId = "",
                bookName = currentBookName,
                bookAuthor = currentBookAuthor,
                startTime = readStartTime,
                endTime = readStartTime,
                words = durChapterIndex.toLong()
            )
        }
    }

    fun upReadTime() {
        val currentLength = currentReadLength
        val currentBookName = book?.name ?: return
        val currentBookAuthor = book?.author ?: ""
        val endTime = System.currentTimeMillis()

        if (currentActiveSession == null ||
            currentActiveSession!!.bookName != currentBookName ||
            currentActiveSession!!.bookAuthor != currentBookAuthor
        ) {
            initReadTime()
            return
        }

        currentActiveSession = currentActiveSession!!.copy(
            endTime = endTime,
            words = durChapterIndex.toLong()
        )

        readStartTime = endTime
        lastReadLength = currentLength
    }

    fun startAutoSaveSession() {
        autoSaveJob?.cancel()
        autoSaveJob = ioScope.launch {
            while (isActive) {
                delay(AUTO_SAVE_INTERVAL)
                commitSessionInternal()
            }
        }
    }

    fun stopAutoSaveSession() {
        autoSaveJob?.cancel()
        autoSaveJob = null
    }

    fun commitReadSession() {
        ioScope.launch {
            commitSessionInternal()
        }
    }

    /**
     * 内部提交逻辑
     */
    private suspend fun commitSessionInternal() {
        val sessionToSave = currentActiveSession ?: return
        val sessionDuration = sessionToSave.endTime - sessionToSave.startTime
        if (sessionDuration < MIN_READ_DURATION) {
            currentActiveSession = null
            return
        }
        try {
            readRecordRepository.saveReadSession(sessionToSave)
        } catch (e: Exception) {
            AppLog.put("保存阅读会话出错: ${sessionToSave.bookName}", e)
        } finally {
            currentActiveSession = null
        }
    }

    fun upMsg(msg: String?) {
        if (ReadBook.msg != msg) {
            ReadBook.msg = msg
            callBack?.upContent()
        }
    }

    fun moveToNextPage(): Boolean {
        var hasNextPage = false
        curTextChapter?.let {
            val nextPagePos = it.getNextPageLength(durChapterPos)
            if (nextPagePos >= 0) {
                hasNextPage = true
                it.getPage(durPageIndex)?.removePageAloudSpan()
                durChapterPos = nextPagePos
                callBack?.cancelSelect()
                callBack?.upContent()
                saveRead(true)
            }
        }
        return hasNextPage
    }

    fun moveToPrevPage(): Boolean {
        var hasPrevPage = false
        curTextChapter?.let {
            val prevPagePos = it.getPrevPageLength(durChapterPos)
            if (prevPagePos >= 0) {
                hasPrevPage = true
                durChapterPos = prevPagePos
                callBack?.upContent()
                saveRead(true)
            }
        }
        return hasPrevPage
    }

    fun moveToNextChapter(upContent: Boolean, upContentInPlace: Boolean = true): Boolean {
        if (durChapterIndex < simulatedChapterSize - 1) {
            durChapterPos = 0
            durChapterIndex++
            clearExpiredChapterLoadingJob()
            prevTextChapter = curTextChapter
            curTextChapter = nextTextChapter
            nextTextChapter = null
            if (curTextChapter == null) {
                AppLog.putDebug("moveToNextChapter-章节未加载,开始加载")
                if (upContentInPlace) callBack?.upContent()
                loadContent(durChapterIndex, upContent, resetPageOffset = false)
            } else if (upContent && upContentInPlace) {
                AppLog.putDebug("moveToNextChapter-章节已加载,刷新视图")
                callBack?.upContent()
            }
            loadContent(durChapterIndex.plus(1), upContent, false)
            saveRead()
            callBack?.upMenuView()
            AppLog.putDebug("moveToNextChapter-curPageChanged()")
            curPageChanged()
            return true
        } else {
            AppLog.putDebug("跳转下一章失败,没有下一章")
            return false
        }
    }

    suspend fun moveToNextChapterAwait(
        upContent: Boolean,
        upContentInPlace: Boolean = true
    ): Boolean {
        if (durChapterIndex < simulatedChapterSize - 1) {
            durChapterPos = 0
            durChapterIndex++
            clearExpiredChapterLoadingJob()
            prevTextChapter = curTextChapter
            curTextChapter = nextTextChapter
            nextTextChapter = null
            if (curTextChapter == null) {
                AppLog.putDebug("moveToNextChapter-章节未加载,开始加载")
                if (upContentInPlace) callBack?.upContentAwait()
                loadContentAwait(durChapterIndex, upContent, resetPageOffset = false)
            } else if (upContent && upContentInPlace) {
                AppLog.putDebug("moveToNextChapter-章节已加载,刷新视图")
                callBack?.upContentAwait()
            }
            loadContent(durChapterIndex.plus(1), upContent, false)
            saveRead()
            callBack?.upMenuView()
            AppLog.putDebug("moveToNextChapter-curPageChanged()")
            curPageChanged()
            return true
        } else {
            AppLog.putDebug("跳转下一章失败,没有下一章")
            return false
        }
    }

    fun moveToPrevChapter(
        upContent: Boolean,
        toLast: Boolean = true,
        upContentInPlace: Boolean = true
    ): Boolean {
        if (durChapterIndex > 0) {
            durChapterPos = if (toLast) prevTextChapter?.lastReadLength ?: Int.MAX_VALUE else 0
            durChapterIndex--
            clearExpiredChapterLoadingJob()
            nextTextChapter = curTextChapter
            curTextChapter = prevTextChapter
            prevTextChapter = null
            if (curTextChapter == null) {
                if (upContentInPlace) callBack?.upContent()
                loadContent(durChapterIndex, upContent, resetPageOffset = false)
            } else if (upContent && upContentInPlace) {
                callBack?.upContent()
            }
            loadContent(durChapterIndex.minus(1), upContent, false)
            saveRead()
            callBack?.upMenuView()
            curPageChanged()
            return true
        } else {
            return false
        }
    }

    fun skipToPage(index: Int, success: (() -> Unit)? = null) {
        durChapterPos = curTextChapter?.getReadLength(index) ?: index
        callBack?.upContent {
            success?.invoke()
        }
        curPageChanged()
        saveRead(true)
    }

    fun setPageIndex(index: Int) {
        recycleRecorders(durPageIndex, index)
        durChapterPos = curTextChapter?.getReadLength(index) ?: index
        saveRead(true)
        curPageChanged(true)
    }

    fun recycleRecorders(beforeIndex: Int, afterIndex: Int) {
        if (!AppConfig.optimizeRender) {
            return
        }
        executor.execute {
            val textChapter = curTextChapter ?: return@execute
            if (afterIndex > beforeIndex) {
                textChapter.getPage(afterIndex - 2)?.recycleRecorders()
            }
            if (afterIndex < beforeIndex) {
                textChapter.getPage(afterIndex + 3)?.recycleRecorders()
            }
        }
    }

    fun openChapter(
        index: Int,
        durChapterPos: Int = 0,
        upContent: Boolean = true,
        success: (() -> Unit)? = null
    ) {
        if (index < chapterSize) {
            clearTextChapter()
            if (upContent) callBack?.upContent()
            durChapterIndex = index
            ReadBook.durChapterPos = durChapterPos
            saveRead()
            loadContent(resetPageOffset = true) {
                success?.invoke()
            }
        }
    }

    /**
     * 当前页面变化
     */
    private fun curPageChanged(pageChanged: Boolean = false) {
        callBack?.pageChanged()
        curTextChapter?.let {
            if (BaseReadAloudService.isRun && it.isCompleted) {
                val scrollPageAnim = pageAnim() == 3
                if (scrollPageAnim && pageChanged) {
                    ReadAloud.pause(appCtx)
                } else {
                    readAloud(!BaseReadAloudService.pause)
                }
            }
        }
        upReadTime()
        preDownload()
    }

    /**
     * 朗读
     */
    fun readAloud(play: Boolean = true, startPos: Int = 0) {
        book ?: return
        val textChapter = curTextChapter ?: return
        if (textChapter.isCompleted) {
            ReadAloud.play(appCtx, play, startPos = startPos)
        }
    }

    /**
     * 当前页数
     */
    val durPageIndex: Int
        get() {
            return curTextChapter?.getPageIndexByCharIndex(durChapterPos) ?: 0
        }

    /**
     * 是否排版到了当前阅读位置
     */
    val isLayoutAvailable inline get() = durPageIndex >= 0

    val isScroll inline get() = pageAnim() == scrollPageAnim

    val contentLoadFinish get() = curTextChapter != null || msg != null

    /**
     * chapterOnDur: 0为当前页,1为下一页,-1为上一页
     */
    fun textChapter(chapterOnDur: Int = 0): TextChapter? {
        return when (chapterOnDur) {
            0 -> curTextChapter
            1 -> nextTextChapter
            -1 -> prevTextChapter
            else -> null
        }
    }

    /**
     * 加载当前章节和前后一章内容
     * @param resetPageOffset 滚动阅读是否重置滚动位置
     * @param success 当前章节加载完成回调
     */
    fun loadContent(
        resetPageOffset: Boolean,
        success: (() -> Unit)? = null
    ) {
        // 顺序加载：当前 → 下一章 → 上一章
        loadContent(durChapterIndex, resetPageOffset = resetPageOffset) {
            loadContent(durChapterIndex + 1, resetPageOffset = resetPageOffset) {
                loadContent(durChapterIndex - 1, resetPageOffset = resetPageOffset)
            }
            success?.invoke()
        }
    }

    fun loadOrUpContent(success: (() -> Unit)? = null) {
        if (curTextChapter == null) {
            loadContent(durChapterIndex) {
                success?.invoke()
            }
        } else {
            callBack?.upContent()
        }
        if (nextTextChapter == null) {
            loadContent(durChapterIndex + 1)
        }
        if (prevTextChapter == null) {
            loadContent(durChapterIndex - 1)
        }
    }

    /**
     * 加载章节内容
     * @param index 章节序号
     * @param upContent 是否更新视图
     * @param resetPageOffset 滚动阅读是否重置滚动位置
     * @param success 加载完成回调
     */
    fun loadContent(
        index: Int,
        upContent: Boolean = true,
        resetPageOffset: Boolean = false,
        success: (() -> Unit)? = null
    ) {
        Coroutine.async {
            val book = book!!
            val chapter = appDb.bookChapterDao.getChapter(book.bookUrl, index) ?: run {
                if (index == durChapterIndex) {
                    upMsg("章节不存在")
                }
                return@async
            }
            if (addLoading(index)) {
                BookHelp.getContent(book, chapter)?.let {
                    contentLoadFinish(
                        book,
                        chapter,
                        it,
                        upContent,
                        resetPageOffset,
                        success = success
                    )
                } ?: download(
                    downloadScope,
                    chapter,
                    resetPageOffset
                )
            }
        }.onError {
            removeLoading(index)
            if (index == durChapterIndex) {
                upMsg("加载正文出错\n${it.localizedMessage}")
            }
            AppLog.put("加载正文出错\n${it.localizedMessage}", it)
        }
    }

    suspend fun loadContentAwait(
        index: Int,
        upContent: Boolean = true,
        resetPageOffset: Boolean = false,
        success: (() -> Unit)? = null
    ) = withContext(IO) {
        if (addLoading(index)) {
            try {
                val book = book!!
                val chapter = appDb.bookChapterDao.getChapter(book.bookUrl, index)!!
                val dbContent = BookHelp.getContent(book, chapter)
                // 优先读取已修正的内容（如果存在）
                val correctedContent = BookHelp.getCorrectedContent(book, chapter)
                val content = correctedContent ?: dbContent ?: run {
                    AppLog.put("loadContent DB为空，下载章节: ${chapter.title}")
                    downloadAwait(chapter)
                }
                if (dbContent != null) {
                    if (correctedContent != null) {
                        AppLog.put("loadContent 从已修正文件读取: ${chapter.title} len=${correctedContent.length}")
                        // 标记为已修正，避免contentLoadFinishAwait重复修正
                        correctedChapterCache["${book.bookUrl}#${chapter.index}"] = System.currentTimeMillis()
                    } else {
                        AppLog.put("loadContent 从DB读取: ${chapter.title} len=${dbContent.length}")
                    }
                }
                contentLoadFinishAwait(book, chapter, content, upContent, resetPageOffset)
                success?.invoke()
            } catch (e: Exception) {
                AppLog.put("加载正文出错\n${e.localizedMessage}")
            } finally {
                removeLoading(index)
            }
        }
    }

    /**
     * 下载正文
     */
    private suspend fun downloadIndex(index: Int) {
        if (index < 0) return
        if (index > chapterSize - 1) {
            upToc()
            return
        }
        val book = book ?: return
        val chapter = appDb.bookChapterDao.getChapter(book.bookUrl, index) ?: return
        if (BookHelp.hasContent(book, chapter)) {
            downloadedChapters.add(chapter.index)
        } else {
            delay(1000)
            if (addLoading(index)) {
                download(downloadScope, chapter, false, preDownloadSemaphore)
            }
        }
    }

    /**
     * 下载正文
     */
    private fun download(
        scope: CoroutineScope,
        chapter: BookChapter,
        resetPageOffset: Boolean,
        semaphore: Semaphore? = null,
        success: (() -> Unit)? = null
    ) {
        val book = book ?: return removeLoading(chapter.index)
        val bookSource = bookSource
        if (bookSource != null) {
            CacheBook.getOrCreate(bookSource, book).download(scope, chapter, semaphore)
        } else {
            val msg = if (book.isLocal) "无内容" else "没有书源"
            contentLoadFinish(
                book,
                chapter,
                "加载正文失败\n$msg",
                resetPageOffset = resetPageOffset,
                success = success
            )
        }
    }

    private suspend fun downloadAwait(chapter: BookChapter): String {
        val book = book!!
        val bookSource = bookSource
        if (bookSource != null) {
            return CacheBook.getOrCreate(bookSource, book).downloadAwait(chapter)
        } else {
            val msg = if (book.isLocal) "无内容" else "没有书源"
            return "加载正文失败\n$msg"
        }
    }

    @Synchronized
    private fun addLoading(index: Int): Boolean {
        if (loadingChapters.contains(index)) return false
        loadingChapters.add(index)
        return true
    }

    @Synchronized
    fun removeLoading(index: Int) {
        loadingChapters.remove(index)
    }

    /**
     * 内容加载完成
     */
    @Synchronized
    fun contentLoadFinish(
        book: Book,
        chapter: BookChapter,
        content: String,
        upContent: Boolean = true,
        resetPageOffset: Boolean,
        canceled: Boolean = false,
        success: (() -> Unit)? = null
    ) {
        removeLoading(chapter.index)
        if (canceled || chapter.index !in durChapterIndex - 1..durChapterIndex + 1) {
            return
        }
        chapterLoadingJobs[chapter.index]?.cancel()
        val job = Coroutine.async(this, start = CoroutineStart.LAZY) {
            val contentProcessor = ContentProcessor.get(book.name, book.origin)
            val displayTitle = chapter.getDisplayTitle(
                contentProcessor.getTitleReplaceRules(),
                book.getUseReplaceRule()
            )
            val contents = contentProcessor
                .getContent(book, chapter, content, includeTitle = false)
            val cacheKey = "${book.bookUrl}#${chapter.index}"
            // 检查磁盘上是否已有修正文件（跨会话持久化），有则标记缓存
            if (correctedChapterCache[cacheKey] == null && BookHelp.getCorrectedContent(book, chapter) != null) {
                AppLog.put("AI修正跳过(磁盘已有修正文件): ${chapter.title}")
                correctedChapterCache[cacheKey] = System.currentTimeMillis()
            }
            // AI 修正（互斥锁，已修正过则跳过）
            val finalContents = if (AICorrectionConfig.isEffectiveEnabled && correctedChapterCache[cacheKey] == null) {
                AppLog.put("AI修正开始: ${chapter.title}")
                val rawText = contents.textList.joinToString("\n")
                AppLog.put("AI修正原始内容长度: ${rawText.length}")
                try {
                    val corrected = aiCorrectionMutex.withLock {
                        // 双重检查：拿到锁后可能已被另一路径标记
                        if (correctedChapterCache[cacheKey] != null) {
                            return@withLock null
                        }
                        correctedChapterCache[cacheKey] = -1L
                        AIContentCorrector.correct(rawText, chapter.title)
                    }
                    if (corrected == null) {
                        // 已被预下载修正标记，跳过
                        AppLog.put("AI修正跳过(预下载已标记): ${chapter.title}")
                        contents
                    } else {
                        AppLog.put("AI修正完成，结果长度: ${corrected.length}")
                        correctedChapterCache[cacheKey] = System.currentTimeMillis()
                        if (corrected != rawText) {
                            BookHelp.saveCorrectedContent(book, chapter, corrected)
                            BookContent(contents.sameTitleRemoved, corrected.split("\n"), contents.effectiveReplaceRules)
                        } else {
                            AppLog.put("AI修正完成(无变化): ${chapter.title}")
                            contents
                        }
                    }
                } catch (e: Exception) {
                    AppLog.put("AI修正失败: ${chapter.title} ${e.localizedMessage}")
                    correctedChapterCache.remove(cacheKey)
                    contents
                }
            } else {
                if (AICorrectionConfig.enabled && correctedChapterCache[cacheKey] != null) {
                    AppLog.put("AI修正跳过（已修正过）: ${chapter.title}")
                }
                contents
            }
            ensureActive()
            val textChapter = ChapterProvider.getTextChapterAsync(
                this, book, chapter, displayTitle, finalContents, simulatedChapterSize
            )
            when (val offset = chapter.index - durChapterIndex) {
                0 -> curChapterLoadingLock.withLock {
                    withContext(Main) {
                        ensureActive()
                        curTextChapter = textChapter
                    }
                    callBack?.upMenuView()
                    var available = false
                    for (page in textChapter.layoutChannel) {
                        val index = page.index
                        if (!available && page.containPos(durChapterPos)) {
                            if (upContent) {
                                callBack?.upContent(offset, resetPageOffset)
                            }
                            available = true
                        }
                        if (upContent && isScroll) {
                            if (max(index - 3, 0) < durPageIndex) {
                                callBack?.upContent(offset, false)
                            }
                        }
                        callBack?.onLayoutPageCompleted(index, page)
                    }
                    if (upContent) callBack?.upContent(offset, !available && resetPageOffset)
                    curPageChanged()
                    callBack?.contentLoadFinish()
                }

                -1 -> prevChapterLoadingLock.withLock {
                    withContext(Main) {
                        ensureActive()
                        prevTextChapter = textChapter
                    }
                    textChapter.layoutChannel.receiveAsFlow().collect()
                    if (upContent) callBack?.upContent(offset, resetPageOffset)
                }

                1 -> nextChapterLoadingLock.withLock {
                    withContext(Main) {
                        ensureActive()
                        nextTextChapter = textChapter
                    }
                    for (page in textChapter.layoutChannel) {
                        if (page.index > 1) {
                            continue
                        }
                        if (upContent) callBack?.upContent(offset, resetPageOffset)
                    }
                }
            }

            return@async
        }.onError {
            if (it is CancellationException) {
                return@onError
            }
            AppLog.put("ChapterProvider ERROR", it)
            appCtx.toastOnUi("ChapterProvider ERROR:\n${it.stackTraceStr}")
        }.onSuccess {
            success?.invoke()
        }
        chapterLoadingJobs[chapter.index] = job
        job.start()
    }

    suspend fun contentLoadFinishAwait(
        book: Book,
        chapter: BookChapter,
        content: String,
        upContent: Boolean = true,
        resetPageOffset: Boolean
    ) {
        removeLoading(chapter.index)
        if (chapter.index !in durChapterIndex - 1..durChapterIndex + 1) {
            return
        }
        kotlin.runCatching {
            val contentProcessor = ContentProcessor.get(book.name, book.origin)
            val displayTitle = chapter.getDisplayTitle(
                contentProcessor.getTitleReplaceRules(),
                book.getUseReplaceRule()
            )
            val bookContent = contentProcessor
                .getContent(book, chapter, content, includeTitle = false)
            val cacheKey = "${book.bookUrl}#${chapter.index}"
            // 检查磁盘修正文件，有则标记缓存跳过
            if (correctedChapterCache[cacheKey] == null && BookHelp.getCorrectedContent(book, chapter) != null) {
                AppLog.put("contentLoadFinish AI修正跳过(磁盘已有修正文件): ${chapter.title}")
                correctedChapterCache[cacheKey] = System.currentTimeMillis()
            }
            val cacheVal = correctedChapterCache[cacheKey]
            // AI 修正（互斥锁，已修正过则跳过）
            val finalTextList = if (AICorrectionConfig.isEffectiveEnabled && cacheVal == null) {
                AppLog.put("contentLoadFinish AI修正开始: ${chapter.title} contentLen=${content.length}")
                val rawContent = bookContent.textList.joinToString("\n")
                AppLog.put("AI修正原始内容长度: ${rawContent.length}")
                try {
                    val corrected = AIContentCorrector.correct(rawContent, chapter.title)
                    AppLog.put("AI修正完成，结果长度: ${corrected.length}")
                    correctedChapterCache[cacheKey] = System.currentTimeMillis()
                    if (corrected != rawContent) {
                        BookHelp.saveCorrectedContent(book, chapter, corrected)
                        corrected.split("\n")
                    } else {
                        bookContent.textList
                    }
                } catch (e: Exception) {
                    AppLog.put("contentLoadFinish AI修正失败: ${chapter.title} ${e.localizedMessage}")
                    correctedChapterCache.remove(cacheKey)
                    bookContent.textList
                }
            } else {
                if (AICorrectionConfig.enabled) {
                    AppLog.put("contentLoadFinish AI修正跳过: ${chapter.title} cacheVal=$cacheVal contentLen=${content.length}")
                }
                bookContent.textList
            }
            val textChapter = ChapterProvider.getTextChapterAsync(
                this@ReadBook, book, chapter, displayTitle, BookContent(bookContent.sameTitleRemoved, finalTextList, bookContent.effectiveReplaceRules), simulatedChapterSize
            )
            when (val offset = chapter.index - durChapterIndex) {
                0 -> {
                    curTextChapter?.cancelLayout()
                    withContext(Main) {
                        curTextChapter = textChapter
                    }
                    callBack?.upMenuView()
                    var available = false
                    for (page in textChapter.layoutChannel) {
                        val index = page.index
                        if (!available && page.containPos(durChapterPos)) {
                            if (upContent) {
                                callBack?.upContent(offset, resetPageOffset)
                            }
                            available = true
                        }
                        if (upContent && isScroll) {
                            if (max(index - 3, 0) < durPageIndex) {
                                callBack?.upContent(offset, false)
                            }
                        }
                        callBack?.onLayoutPageCompleted(index, page)
                    }
                    if (upContent) callBack?.upContent(offset, !available && resetPageOffset)
                    curPageChanged()
                    callBack?.contentLoadFinish()
                }

                -1 -> {
                    prevTextChapter?.cancelLayout()
                    withContext(Main) {
                        prevTextChapter = textChapter
                    }
                    textChapter.layoutChannel.receiveAsFlow().collect()
                    if (upContent) callBack?.upContent(offset, resetPageOffset)
                }

                1 -> {
                    nextTextChapter?.cancelLayout()
                    withContext(Main) {
                        nextTextChapter = textChapter
                    }
                    for (page in textChapter.layoutChannel) {
                        if (page.index > 1) {
                            continue
                        }
                        if (upContent) callBack?.upContent(offset, resetPageOffset)
                    }
                }
            }

            return
        }.onFailure {
            if (it is CancellationException) {
                return@onFailure
            }
            AppLog.put("ChapterProvider ERROR", it)
            appCtx.toastOnUi("ChapterProvider ERROR:\n${it.stackTraceStr}")
        }
    }

    @Synchronized
    fun upToc() {
        val bookSource = bookSource ?: return
        val book = book ?: return
        if (!book.canUpdate) return
        if (System.currentTimeMillis() - book.lastCheckTime < 600000) return
        book.lastCheckTime = System.currentTimeMillis()
        WebBook.getChapterList(this, bookSource, book).onSuccess(IO) { cList ->
            if (book.bookUrl == ReadBook.book?.bookUrl
                && cList.size > chapterSize
            ) {
                appDb.bookChapterDao.delByBook(book.bookUrl)
                appDb.bookChapterDao.insert(*cList.toTypedArray())
                saveRead()
                chapterSize = cList.size
                simulatedChapterSize = book.simulatedTotalChapterNum()
                nextTextChapter ?: loadContent(durChapterIndex + 1)
            }
        }
    }

    fun pageAnim(): Int {
        return book?.getPageAnim() ?: ReadBookConfig.pageAnim
    }

    fun setCharset(charset: String) {
        book?.let {
            it.charset = charset
            callBack?.loadChapterList(it)
        }
        saveRead()
    }

    fun saveRead(pageChanged: Boolean = false) {
        val book = book ?: return
        executor.execute {
            kotlin.runCatching {
                book.lastCheckCount = 0
                book.durChapterTime = System.currentTimeMillis()
                val chapterChanged = book.durChapterIndex != durChapterIndex
                book.durChapterIndex = durChapterIndex
                book.durChapterPos = durChapterPos
                if (!pageChanged || chapterChanged) {
                    appDb.bookChapterDao.getChapter(book.bookUrl, durChapterIndex)?.let {
                        book.durChapterTitle = it.getDisplayTitle(
                            ContentProcessor.get(book.name, book.origin).getTitleReplaceRules(),
                            book.getUseReplaceRule()
                        )
                        SourceCallBack.callBackBook(SourceCallBack.SAVE_READ, bookSource, book, it)
                    }
                }
                book.update()
            }.onFailure {
                AppLog.put("保存书籍阅读进度信息出错\n$it", it)
            }
        }
    }

    /**
     * 预下载
     */
    private fun preDownload() {
        if (book?.isLocal == true) return
        executor.execute {
            if (AppConfig.preDownloadNum < 2) {
                return@execute
            }
            preDownloadTask?.cancel()
            preDownloadTask = launch(IO) {
                //预下载
                launch {
                    val maxChapterIndex =
                        min(durChapterIndex + AppConfig.preDownloadNum, chapterSize)
                    for (i in durChapterIndex.plus(2)..maxChapterIndex) {
                        if (downloadedChapters.contains(i)) continue
                        if ((downloadFailChapters[i] ?: 0) >= 3) continue
                        downloadIndex(i)
                    }
                }
                launch {
                    val minChapterIndex = durChapterIndex - min(5, AppConfig.preDownloadNum)
                    for (i in durChapterIndex.minus(2) downTo minChapterIndex) {
                        if (downloadedChapters.contains(i)) continue
                        if ((downloadFailChapters[i] ?: 0) >= 3) continue
                        downloadIndex(i)
                    }
                }
                // 预下载完成后AI修正已移除，改由CacheBook预缓存时修正
                // if (AppConfig.preDownloadAiCorrect) {
                //     aiCorrectionScope.launch {
                //         preDownloadAiCorrect()
                //     }
                // }
            }
        }
    }

    /**
     * 预下载完成后，对已下载章节进行AI修正（顺序执行，失败重试2次）
     * 已禁用：改由CacheBook预缓存时修正，阅读时只修当前章节
     */
    @Deprecated("Use CacheBook pre-correction instead")
    private suspend fun preDownloadAiCorrect() {
        val b = book ?: return
        val maxChapterIndex = min(durChapterIndex + AppConfig.preDownloadNum, chapterSize)
        val minChapterIndex = max(0, durChapterIndex - min(5, AppConfig.preDownloadNum))
        for (i in minChapterIndex..maxChapterIndex) {
            if (!isActive) return
            val chapter = appDb.bookChapterDao.getChapter(b.bookUrl, i) ?: continue
            val cacheKey = "${b.bookUrl}#${chapter.index}"
            if (correctedChapterCache[cacheKey] != null) continue
            val originalContent = BookHelp.getContent(b, chapter) ?: continue
            var success = false
            // 重试2次
            repeat(3) { attempt ->
                if (!isActive) return
                try {
                    val corrected = aiCorrectionMutex.withLock {
                        // 双重检查：拿到锁后可能已被contentLoadFinish标记
                        if (correctedChapterCache[cacheKey] != null && correctedChapterCache[cacheKey] != -1L) {
                            return@withLock null
                        }
                        correctedChapterCache[cacheKey] = -1L
                        AIContentCorrector.correct(originalContent, chapter.title)
                    }
                    if (corrected == null) {
                        AppLog.put("预下载AI修正跳过(已被标记): ${chapter.title}")
                        success = true
                    } else {
                        correctedChapterCache[cacheKey] = System.currentTimeMillis()
                        if (corrected != originalContent) {
                            BookHelp.saveCorrectedContent(b, chapter, corrected)
                            AppLog.put("预下载AI修正完成(${attempt + 1}次尝试): ${chapter.title}, 长度${corrected.length}")
                        } else {
                            AppLog.put("预下载AI修正完成(无变化): ${chapter.title}")
                        }
                        success = true
                    }
                } catch (e: Exception) {
                    AppLog.put("预下载AI修正失败第${attempt + 1}次: ${chapter.title} ${e.localizedMessage}")
                    // 重试前先检查是否已被另一方标记完成
                    if (correctedChapterCache[cacheKey] != null && correctedChapterCache[cacheKey] != -1L) {
                        AppLog.put("预下载AI修正跳过(已被标记完成): ${chapter.title}")
                        success = true
                    } else if (attempt < 2) {
                        kotlinx.coroutines.delay(3000)
                    }
                }
            }
            if (!success) {
                AppLog.put("预下载AI修正跳过(重试耗尽): ${chapter.title}")
                correctedChapterCache.remove(cacheKey)
            }
        }
    }

    fun cancelPreDownloadTask() {
        if (contentLoadFinish) {
            preDownloadTask?.cancel()
            downloadScope.coroutineContext.cancelChildren()
        }
    }

    fun onChapterListUpdated(newBook: Book) {
        if (newBook.isSameNameAuthor(book)) {
            book = newBook
            chapterSize = newBook.totalChapterNum
            simulatedChapterSize = newBook.simulatedTotalChapterNum()
            if (simulatedChapterSize > 0 && durChapterIndex > simulatedChapterSize - 1) {
                durChapterIndex = simulatedChapterSize - 1
            }
            if (callBack == null) {
                clearTextChapter()
            } else {
                loadContent(true)
            }
        }
    }

    private fun clearExpiredChapterLoadingJob(clearAll: Boolean = false) {
        val iterator = chapterLoadingJobs.iterator()
        while (iterator.hasNext()) {
            val (index, job) = iterator.next()
            if (clearAll || index !in durChapterIndex - 1..durChapterIndex + 1) {
                job.cancel()
                iterator.remove()
            }
        }
    }

    /**
     * 注册回调
     */
    fun register(cb: CallBack) {
        callBack?.notifyBookChanged()
        callBack = cb
    }

    /**
     * 取消注册回调
     */
    fun unregister(cb: CallBack) {
        if (callBack === cb) {
            callBack = null
        }
        msg = null
        preDownloadTask?.cancel()
        downloadScope.coroutineContext.cancelChildren()
        coroutineContext.cancelChildren()
        ImageProvider.clear()
        clearExpiredChapterLoadingJob(true)
        if (!CacheBookService.isRun) {
            CacheBook.close()
        }
    }

    interface CallBack : LayoutProgressListener {
        fun upMenuView()

        fun loadChapterList(book: Book)

        fun upContent(
            relativePosition: Int = 0,
            resetPageOffset: Boolean = true,
            success: (() -> Unit)? = null
        )

        suspend fun upContentAwait(
            relativePosition: Int = 0,
            resetPageOffset: Boolean = true,
            success: (() -> Unit)? = null
        )

        fun pageChanged()

        fun contentLoadFinish()

        fun upPageAnim(upRecorder: Boolean = false)

        fun notifyBookChanged()

        fun sureNewProgress(progress: BookProgress)

        fun cancelSelect()
    }

}
