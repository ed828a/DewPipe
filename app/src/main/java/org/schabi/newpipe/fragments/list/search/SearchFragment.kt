package org.schabi.newpipe.fragments.list.search

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.preference.PreferenceManager
import android.support.v7.app.AlertDialog
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.TooltipCompat
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.util.Log
import android.view.*
import android.view.animation.DecelerateInterpolator
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import icepick.State
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.functions.BiFunction
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.PublishSubject
import org.schabi.newpipe.R
import org.schabi.newpipe.ReCaptchaActivity
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.ListExtractor
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.StreamingService
import org.schabi.newpipe.extractor.exceptions.ParsingException
import org.schabi.newpipe.extractor.search.SearchExtractor
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.fragments.BackPressable
import org.schabi.newpipe.fragments.list.BaseListFragment
import org.schabi.newpipe.local.history.HistoryRecordManager
import org.schabi.newpipe.report.ErrorActivity
import org.schabi.newpipe.report.ErrorInfo
import org.schabi.newpipe.report.UserAction
import org.schabi.newpipe.util.*
import org.schabi.newpipe.util.AnimationUtils.animateView
import java.io.IOException
import java.io.InterruptedIOException
import java.net.SocketException
import java.util.*
import java.util.Arrays.asList
import java.util.concurrent.TimeUnit

class SearchFragment : BaseListFragment<SearchInfo, ListExtractor.InfoItemsPage<*>>(), BackPressable {

    @State
    @JvmField
    protected var filterItemCheckedId = -1

    @State
    @JvmField
    protected var serviceId = Constants.NO_SERVICE_ID

    // these three represet the current search query
    @State
    @JvmField
    protected var searchString: String? = null
    @State
    @JvmField
    protected var contentFilter: Array<String> = emptyArray()
    @State
    @JvmField
    protected var sortFilter: String? = null

    // these two represtent the last search
    @State
    @JvmField
    protected var lastSearchedString: String? = null

    @State
    @JvmField
    protected var wasSearchFocused = false

    private val menuItemToFilterName: MutableMap<Int, String> = HashMap()
    private var service: StreamingService? = null
    private var currentPageUrl: String? = null   // StateSaver takes care it
    private var nextPageUrl: String? = null      // SateSave takes care it
    private lateinit var contentCountry: String
    private var isSuggestionsEnabled = true
    private var isSearchHistoryEnabled = true

    private val suggestionPublisher = PublishSubject.create<String>()
    private var searchDisposable: Disposable? = null
    private var suggestionDisposable: Disposable? = null
//    private val disposables = CompositeDisposable()

    private lateinit var suggestionListAdapter: SuggestionListAdapter
    private lateinit var historyRecordManager: HistoryRecordManager

    private lateinit var preferences: SharedPreferences
    ///////////////////////////////////////////////////////////////////////////
    // Views
    ///////////////////////////////////////////////////////////////////////////

    private lateinit var searchToolbarContainer: View
    private lateinit var searchEditText: EditText
    private lateinit var searchClear: View

    private lateinit var suggestionsPanel: View
    private lateinit var suggestionsRecyclerView: RecyclerView

    ///////////////////////////////////////////////////////////////////////////
    // Search
    ///////////////////////////////////////////////////////////////////////////

    private var textWatcher: TextWatcher? = null

    /**
     * Set wasLoading to true so when the fragment onResume is called, the initial search is done.
     */
    private fun setSearchOnResume() {
        wasLoading.set(true)
    }

    ///////////////////////////////////////////////////////////////////////////
    // Fragment's LifeCycle
    ///////////////////////////////////////////////////////////////////////////

    override fun onAttach(context: Context?) {
        super.onAttach(context)

        suggestionListAdapter = SuggestionListAdapter(activity!!)
        preferences = PreferenceManager.getDefaultSharedPreferences(activity)
        isSearchHistoryEnabled = preferences.getBoolean(getString(R.string.enable_search_history_key), true)
        suggestionListAdapter.setShowSuggestionHistory(isSearchHistoryEnabled)

        historyRecordManager = HistoryRecordManager(context!!)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

//        val preferences = PreferenceManager.getDefaultSharedPreferences(activity)
        isSuggestionsEnabled = preferences.getBoolean(getString(R.string.show_search_suggestions_key), true)
        contentCountry = preferences.getString(getString(R.string.content_country_key), getString(R.string.default_country_value))
                ?: getString(R.string.default_country_value)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_search, container, false)
    }

    override fun onViewCreated(rootView: View, savedInstanceState: Bundle?) {
        super.onViewCreated(rootView, savedInstanceState)
        showSearchOnStart()
        initSearchListeners()
    }

    override fun onPause() {
        super.onPause()

        wasSearchFocused = searchEditText!!.hasFocus()

        searchDisposable?.dispose()
        suggestionDisposable?.dispose()
        compositeDisposable.clear()
        hideKeyboardSearch()
    }

    override fun onResume() {
        Log.d(TAG, "onResume() called")
        super.onResume()

        try {
            service = NewPipe.getService(serviceId)
        } catch (exception: Exception) {
            ErrorActivity.reportError(activity!!, exception, activity!!.javaClass,
                    activity!!.findViewById(android.R.id.content),
                    ErrorInfo.make(UserAction.UI_ERROR,
                            "",
                            "",
                            R.string.general_error))
        }

        if (!TextUtils.isEmpty(searchString)) {
            if (wasLoading.getAndSet(false)) {
                search(searchString, contentFilter, sortFilter!!)
            } else if (infoListAdapter!!.itemsList.size == 0) {
                if (savedState == null) {
                    search(searchString, contentFilter, sortFilter!!)
                } else if (!isLoading.get() && !wasSearchFocused) {
                    infoListAdapter!!.clearStreamItemList()
                    showEmptyState()
                }
            }
        }

        if (suggestionDisposable == null || suggestionDisposable!!.isDisposed) initSuggestionObserver()

        if (TextUtils.isEmpty(searchString) || wasSearchFocused) {
            showKeyboardSearch()
            showSuggestionsPanel()
        } else {
            hideKeyboardSearch()
            hideSuggestionsPanel()
        }
        wasSearchFocused = false
    }

    override fun onDestroyView() {
        Log.d(TAG, "onDestroyView() called")
        unsetSearchListeners()
        super.onDestroyView()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (searchDisposable != null) searchDisposable!!.dispose()
        if (suggestionDisposable != null) suggestionDisposable!!.dispose()
        if (!compositeDisposable.isDisposed) compositeDisposable.dispose()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            ReCaptchaActivity.RECAPTCHA_REQUEST -> if (resultCode == Activity.RESULT_OK && !TextUtils.isEmpty(searchString)) {
                search(searchString, contentFilter, sortFilter!!)
            } else
                Log.e(TAG, "ReCaptcha failed")

            else -> Log.e(TAG, "Request code getTabFrom activity not supported [$requestCode]")
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    // Init
    ///////////////////////////////////////////////////////////////////////////

    override fun initViews(rootView: View, savedInstanceState: Bundle?) {
        super.initViews(rootView, savedInstanceState)
        suggestionsPanel = rootView.findViewById(R.id.suggestions_panel)
        suggestionsRecyclerView = rootView.findViewById(R.id.suggestions_list)
        suggestionsRecyclerView.adapter = suggestionListAdapter
        suggestionsRecyclerView.layoutManager = LayoutManagerSmoothScroller(activity!!)

        searchToolbarContainer = activity!!.findViewById(R.id.toolbar_search_container)
        searchEditText = searchToolbarContainer.findViewById(R.id.toolbar_search_edit_text)
        searchClear = searchToolbarContainer.findViewById(R.id.toolbar_search_clear)
    }

    ///////////////////////////////////////////////////////////////////////////
    // State Saving
    ///////////////////////////////////////////////////////////////////////////

    override fun writeTo(objectsToSave: Queue<Any>) {
        super.writeTo(objectsToSave)
        objectsToSave.add(currentPageUrl)
        objectsToSave.add(nextPageUrl)
    }

    @Throws(Exception::class)
    override fun readFrom(savedObjects: Queue<Any>) {
        super.readFrom(savedObjects)
        currentPageUrl = savedObjects.poll() as String
        nextPageUrl = savedObjects.poll() as String
    }

    override fun onSaveInstanceState(bundle: Bundle) {
//        searchString = if (searchEditText != null)
//            searchEditText.text.toString()
//        else
//            searchString
        searchString = searchEditText.text.toString()
        super.onSaveInstanceState(bundle)
    }

    ///////////////////////////////////////////////////////////////////////////
    // Init's
    ///////////////////////////////////////////////////////////////////////////

    override fun reloadContent() {
        if (!TextUtils.isEmpty(searchString) && !TextUtils.isEmpty(searchEditText.text)) {
            val searchText = if (!TextUtils.isEmpty(searchString)) searchString!!
            else searchEditText.text.toString()

            search(searchText, arrayOf<String>(), "")
        } else {
//            if (searchEditText != null) {
            searchEditText.setText("")
            showKeyboardSearch()
//            }
            animateView(errorPanelRoot, false, 200)
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    // Menu
    ///////////////////////////////////////////////////////////////////////////

    override fun onCreateOptionsMenu(menu: Menu?, inflater: MenuInflater?) {
        super.onCreateOptionsMenu(menu, inflater)

        val supportActionBar = activity!!.supportActionBar?.apply {
            setDisplayShowTitleEnabled(true)
            setDisplayHomeAsUpEnabled(true)
        }
//        if (supportActionBar != null) {
//            supportActionBar.setDisplayShowTitleEnabled(false)
//            supportActionBar.setDisplayHomeAsUpEnabled(true)
//        }

//        menuItemToFilterName = HashMap()

        var itemId = 0
        var isFirstItem = true
        val context = context
        if (context != null && menu != null) {
            for (filter in service!!.searchQHFactory.availableContentFilter) {
                menuItemToFilterName[itemId] = filter
                val item = menu.add(1,
                        itemId++,
                        0,
                        ServiceHelper.getTranslatedFilterString(filter, context))
                if (isFirstItem) {
                    item.isChecked = true
                    isFirstItem = false
                }
            }
            menu.setGroupCheckable(1, true, true)
            restoreFilterChecked(menu, filterItemCheckedId)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {

        val contentFilter = ArrayList<String>(1)
        if (item != null) {
            contentFilter.add(menuItemToFilterName[item.itemId]!!)
            changeContentFilter(item, contentFilter)
        }
        return true
    }

    private fun restoreFilterChecked(menu: Menu, itemId: Int) {
        if (itemId != -1) {
            val item = menu.findItem(itemId) ?: return

            item.isChecked = true
        }
    }

    private fun showSearchOnStart() {
        Log.d(TAG, "showSearchOnStart(): searchQuery = $searchString, lastSearchedQuery = $lastSearchedString")
        searchEditText.setText(searchString)

        if (TextUtils.isEmpty(searchString) || TextUtils.isEmpty(searchEditText.text)) {
//            searchToolbarContainer!!.translationX = 100f
//            searchToolbarContainer!!.alpha = 0f
//            searchToolbarContainer!!.visibility = View.VISIBLE
//            searchToolbarContainer!!.animate()
//                    .translationX(0f)
//                    .alpha(1f)
//                    .setDuration(200)
//                    .setInterpolator(DecelerateInterpolator()).start()
            searchToolbarContainer.apply {
                translationX = 100f
                alpha = 0f
                visibility = View.VISIBLE
                animate()
                        .translationX(0f)
                        .alpha(1f)
                        .setDuration(200)
                        .setInterpolator(DecelerateInterpolator()).start()
            }
        } else {
            searchToolbarContainer.apply {
                translationX = 0f
                alpha = 1f
                visibility = View.VISIBLE
            }
//            searchToolbarContainer!!.translationX = 0f
//            searchToolbarContainer!!.alpha = 1f
//            searchToolbarContainer!!.visibility = View.VISIBLE
        }
    }

    private fun initSearchListeners() {
        Log.d(TAG, "initSearchListeners() called")
        searchClear.setOnClickListener { view ->
            Log.d(TAG, "searchClear.onClick() : view = [$view]")
            if (TextUtils.isEmpty(searchEditText.text)) {
                NavigationHelper.gotoMainFragment(fragmentManager!!)
                return@setOnClickListener
            }

            searchEditText.setText("")
            suggestionListAdapter.setItems(ArrayList())
            showKeyboardSearch()
        }

        TooltipCompat.setTooltipText(searchClear, getString(R.string.clear))

        searchEditText.setOnClickListener { v ->
            Log.d(TAG, "searchEditText.onClick(): view = [$v]")
            if (isSuggestionsEnabled && errorPanelRoot.visibility != View.VISIBLE) {
                showSuggestionsPanel()
            }
        }

        searchEditText.setOnFocusChangeListener { v: View, hasFocus: Boolean ->
            Log.d(TAG, "searchEditText.onFocusChange(): v = [$v], hasFocus = [$hasFocus]")
            if (isSuggestionsEnabled && hasFocus && errorPanelRoot.visibility != View.VISIBLE) {
                showSuggestionsPanel()
            }
        }

        suggestionListAdapter.setListener(object : SuggestionListAdapter.OnSuggestionItemSelected {
            override fun onSuggestionItemSelected(item: SuggestionItem) {
                search(item.query, arrayOf(), "")
                searchEditText.setText(item.query)
            }

            override fun onSuggestionItemInserted(item: SuggestionItem) {
                searchEditText.setText(item.query)
                searchEditText.setSelection(searchEditText.text.length)
            }

            override fun onSuggestionItemLongClick(item: SuggestionItem) {
                if (item.fromHistory) showDeleteSuggestionDialog(item)
            }
        })

        // this part can use RxTextView.textChanges(searchEditText)
        if (textWatcher != null) searchEditText.removeTextChangedListener(textWatcher)
        textWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable) {
                val newText = searchEditText.text.toString()
                suggestionPublisher.onNext(newText)
            }
        }
        searchEditText.addTextChangedListener(textWatcher)

        searchEditText.setOnEditorActionListener { v, actionId, event ->

            Log.d(TAG, "searchEditText.onEditorAction() : v = [$v], actionId = [$actionId], event = [$event]")

            if (event != null && (event.keyCode == KeyEvent.KEYCODE_ENTER || event.action == EditorInfo.IME_ACTION_SEARCH)) {
                search(searchEditText.text.toString(), arrayOf(), "")
                return@setOnEditorActionListener true
            }
            false
        }

        if (suggestionDisposable == null || suggestionDisposable!!.isDisposed)
            initSuggestionObserver()
    }

    private fun unsetSearchListeners() {
        Log.d(TAG, "SearchFragment.unsetSearchListeners() called")
        searchClear.setOnClickListener(null)
        searchClear.setOnLongClickListener(null)
        searchEditText.setOnClickListener(null)
        searchEditText.onFocusChangeListener = null
        searchEditText.setOnEditorActionListener(null)

        if (textWatcher != null) searchEditText.removeTextChangedListener(textWatcher)
        textWatcher = null
    }

    private fun showSuggestionsPanel() {
        Log.d(TAG, "showSuggestionsPanel() called")
        animateView(suggestionsPanel, AnimationUtils.Type.LIGHT_SLIDE_AND_ALPHA, true, 200)
    }

    private fun hideSuggestionsPanel() {
        Log.d(TAG, "hideSuggestionsPanel() called")
        animateView(suggestionsPanel, AnimationUtils.Type.LIGHT_SLIDE_AND_ALPHA, false, 200)
    }

    private fun showKeyboardSearch() {
        Log.d(TAG, "showKeyboardSearch() called")

        if (searchEditText.requestFocus()) {
            val inputMethodManager = activity!!.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            inputMethodManager.showSoftInput(searchEditText, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun hideKeyboardSearch() {
        Log.d(TAG, "hideKeyboardSearch() called")

        val inputMethodManager = activity!!.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        inputMethodManager.hideSoftInputFromWindow(searchEditText.windowToken, InputMethodManager.HIDE_NOT_ALWAYS)

        searchEditText.clearFocus()
    }

    private fun showDeleteSuggestionDialog(item: SuggestionItem) {
        if (activity == null) return

        val query = item.query
        AlertDialog.Builder(activity!!)
                .setTitle(query)
                .setMessage(R.string.delete_item_search_history)
                .setCancelable(true)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.delete) { dialog, which ->
                    val onDelete = historyRecordManager.deleteSearchHistory(query!!)
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe(
                                    { howManyDeleted ->
                                        suggestionPublisher
                                                .onNext(searchEditText.text.toString())
                                    },
                                    { throwable ->
                                        showSnackBarError(throwable,
                                                UserAction.DELETE_FROM_HISTORY, "none",
                                                "Deleting item failed", R.string.general_error)
                                    })
                    compositeDisposable.add(onDelete)
                }
                .show()
    }

    override fun onBackPressed(): Boolean {
        if (suggestionsPanel.visibility == View.VISIBLE
                && infoListAdapter!!.itemsList.size > 0
                && !isLoading.get()) {
            hideSuggestionsPanel()
            hideKeyboardSearch()
            searchEditText.setText(lastSearchedString)
            return true
        }
        return false
    }

    fun giveSearchEditTextFocus() {
        showKeyboardSearch()
    }

    private fun initSuggestionObserver() {
        Log.d(TAG, "initSuggestionObserver() called")
        if (suggestionDisposable != null && !suggestionDisposable!!.isDisposed) suggestionDisposable!!.dispose()

        val observable = suggestionPublisher
                .debounce(SUGGESTIONS_DEBOUNCE.toLong(), TimeUnit.MILLISECONDS)
                .startWith(if (searchString != null)
                    searchString
                else
                    "")
                .filter { searchString -> isSuggestionsEnabled }

        suggestionDisposable = observable
                .switchMap { query ->
                    val flowable = historyRecordManager.getRelatedSearches(query, 5, 25)

                    val local = flowable.toObservable()
                            .map<List<SuggestionItem>> { searchHistoryEntries ->
                                val result = ArrayList<SuggestionItem>()
                                for (entry in searchHistoryEntries)
                                    result.add(SuggestionItem(true, entry.search))
                                result
                            }

                    if (query.length < THRESHOLD_NETWORK_SUGGESTION) {
                        // Only pass through if the query length is equal or greater than THRESHOLD_NETWORK_SUGGESTION

                        return@switchMap local.materialize()

                    }

                    val network = ExtractorHelper.suggestionsFor(serviceId, query)
                            .toObservable()
                            .map<List<SuggestionItem>> { strings ->
                                val result = ArrayList<SuggestionItem>()
                                for (entry in strings) {
                                    result.add(SuggestionItem(false, entry))
                                }
                                result
                            }

                    Observable.zip<List<SuggestionItem>, List<SuggestionItem>, List<SuggestionItem>>(
                            local,
                            network,
                            BiFunction { localResult, networkResult ->
                                val result = ArrayList<SuggestionItem>()
                                if (localResult.isNotEmpty()) result.addAll(localResult)

                                // Remove duplicates
                                val iterator = networkResult.iterator() as MutableIterator<SuggestionItem>
                                while (iterator.hasNext() && localResult.isNotEmpty()) {
                                    val next = iterator.next()
                                    for (item in localResult) {
                                        if (item.query == next.query) {
                                            iterator.remove()
                                            break
                                        }
                                    }
                                }

                                if (networkResult.isNotEmpty()) result.addAll(networkResult)
                                result
                            }).materialize()
                }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe { listNotification ->
                    when {
                        listNotification.isOnNext -> handleSuggestions(listNotification.value!!)
                        listNotification.isOnError -> {
                            val error = listNotification.error
                            error?.let { throwable ->
                                if (!ExtractorHelper.hasAssignableCauseThrowable(throwable,
                                                IOException::class.java, SocketException::class.java,
                                                InterruptedException::class.java, InterruptedIOException::class.java)) {
                                    onSuggestionError(throwable)
                                }
                            }

                        }
                    }
                }
    }

    override fun doInitialLoadLogic() {
        // no-op
    }

    private fun search(searchString: String?, contentFilter: Array<String>, sortFilter: String) {
        Log.d(TAG, "search() called with: query = [$searchString]")
        if (TextUtils.isEmpty(searchString)) return

        try {
            val service = NewPipe.getServiceByUrl(searchString)
            val context = activity ?: return
            if (service != null) {
                showLoading()
                val d = Observable
                        .fromCallable { NavigationHelper.getIntentByLink(context, service, searchString) }
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(
                                { intent ->
                                    fragmentManager?.popBackStackImmediate()
                                    activity?.startActivity(intent)
                                },
                                { throwable ->
                                    showError(getString(R.string.url_not_supported_toast), false)
                                })

                compositeDisposable.add(d)
                return
            }
        } catch (e: Exception) {
            // Exception occurred, it's not a url
            Log.d(TAG, "Exception occurred, because searchString is not a url")
        }

        lastSearchedString = this.searchString
        this.searchString = searchString
        infoListAdapter?.clearStreamItemList()
        hideSuggestionsPanel()
        hideKeyboardSearch()

        val d2 = historyRecordManager.onSearched(serviceId, searchString!!)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        { ignored -> },
                        { error ->
                            showSnackBarError(error, UserAction.SEARCHED,
                                    NewPipe.getNameOfService(serviceId), searchString, 0)
                        }
                )
        suggestionPublisher.onNext(searchString)
        startLoading(false)   // start loading search result
        compositeDisposable.add(d2)
    }

    public override fun startLoading(forceLoad: Boolean) {
        super.startLoading(forceLoad)
        compositeDisposable.clear()
        if (searchDisposable != null && !searchDisposable!!.isDisposed) searchDisposable!!.dispose()
        searchDisposable = ExtractorHelper.searchFor(serviceId,
                searchString!!,
                Arrays.asList(*contentFilter),
                sortFilter!!)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .doOnEvent { searchResult, throwable -> isLoading.set(false) }
                .subscribe({result -> this.handleResult(result) },
                        {error -> this.onError(error) })

    }

    override fun loadMoreItems() {
        isLoading.set(true)
        showListFooter(true)
        if (searchDisposable != null && !searchDisposable!!.isDisposed) searchDisposable!!.dispose()
        searchDisposable = ExtractorHelper.getMoreSearchItems(
                serviceId,
                searchString!!,
                asList(*contentFilter),
                sortFilter!!,
                nextPageUrl!!)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .doOnEvent { nextItemsResult, throwable -> isLoading.set(false) }
                .subscribe({ this.handleNextItems(it) }, { this.onError(it) })
    }

    override fun hasMoreItems(): Boolean {
        // TODO: No way to tell if search has more items in the moment
        return true
    }

    override fun onItemSelected(selectedItem: InfoItem) {
        super.onItemSelected(selectedItem)
        hideKeyboardSearch()
    }

    ///////////////////////////////////////////////////////////////////////////
    // Utils
    ///////////////////////////////////////////////////////////////////////////

    private fun changeContentFilter(item: MenuItem, contentFilter: List<String>) {
        this.filterItemCheckedId = item.itemId
        item.isChecked = true

        this.contentFilter = arrayOf(contentFilter[0])

        if (!TextUtils.isEmpty(searchString)) {
            search(searchString, this.contentFilter, sortFilter!!)
        }
    }

    private fun setQuery(serviceId: Int, searchString: String, contentfilter: Array<String>, sortFilter: String) {
        this.serviceId = serviceId
        this.searchString = searchString
        this.contentFilter = contentfilter
        this.sortFilter = sortFilter
    }

    ///////////////////////////////////////////////////////////////////////////
    // Suggestion Results
    ///////////////////////////////////////////////////////////////////////////

    private fun handleSuggestions(suggestions: List<SuggestionItem>) {
        Log.d(TAG, "handleSuggestions(): suggestions = [$suggestions]")
        suggestionsRecyclerView.smoothScrollToPosition(0)
        suggestionsRecyclerView.post { suggestionListAdapter.setItems(suggestions) }

        if (errorPanelRoot.visibility == View.VISIBLE) {
            hideLoading()
        }
    }

    private fun onSuggestionError(exception: Throwable) {
        Log.d(TAG, "onSuggestionError(): exception = [$exception]")
        if (super.onError(exception)) return

        val errorId = if (exception is ParsingException)
            R.string.parsing_error
        else
            R.string.general_error
        onUnrecoverableError(exception, UserAction.GET_SUGGESTIONS,
                NewPipe.getNameOfService(serviceId), searchString!!, errorId)
    }

    ///////////////////////////////////////////////////////////////////////////
    // Contract
    ///////////////////////////////////////////////////////////////////////////

    override fun hideLoading() {
        super.hideLoading()
        showListFooter(false)
    }

    override fun showError(message: String, showRetryButton: Boolean) {
        super.showError(message, showRetryButton)
        hideSuggestionsPanel()
        hideKeyboardSearch()
    }

    ///////////////////////////////////////////////////////////////////////////
    // Search Results
    ///////////////////////////////////////////////////////////////////////////

    override fun handleResult(result: SearchInfo) {
        Log.d(TAG, "handleResult(result = $result): result.url = ${result.url}, result.nextPageUrl = ${result.nextPageUrl},  result.relatedItems = ${result.relatedItems}")
        val exceptions = result.errors
        if (exceptions != null && !exceptions.isEmpty() && !(exceptions.size == 1 && exceptions[0] is SearchExtractor.NothingFoundException)) {
            showSnackBarError(result.errors, UserAction.SEARCHED,
                    NewPipe.getNameOfService(serviceId), searchString!!, 0)
        }

        lastSearchedString = searchString
        nextPageUrl = result.nextPageUrl
        currentPageUrl = result.url

        if (infoListAdapter != null && infoListAdapter!!.itemsList.size == 0) {
            if (!result.relatedItems.isEmpty()) {
                infoListAdapter!!.addInfoItemList(result.relatedItems)
            } else {
                infoListAdapter!!.clearStreamItemList()
                showEmptyState()
                return
            }
        }

        super.handleResult(result)
    }

    override fun handleNextItems(result: ListExtractor.InfoItemsPage<*>) {
        showListFooter(false)
        currentPageUrl = result.nextPageUrl
        infoListAdapter!!.addInfoItemList(result.items)
        nextPageUrl = result.nextPageUrl

        if (!result.errors.isEmpty()) {
            showSnackBarError(result.errors, UserAction.SEARCHED,
                    NewPipe.getNameOfService(serviceId), "\"$searchString\" → page: $nextPageUrl", 0)
        }
        super.handleNextItems(result)
    }

    override fun onError(exception: Throwable): Boolean {
        if (super.onError(exception)) return true

        if (exception is SearchExtractor.NothingFoundException) {
            infoListAdapter!!.clearStreamItemList()
            showEmptyState()
        } else {
            val errorId = if (exception is ParsingException)
                R.string.parsing_error
            else
                R.string.general_error
            onUnrecoverableError(exception, UserAction.SEARCHED,
                    NewPipe.getNameOfService(serviceId), searchString!!, errorId)
        }

        return true
    }

    companion object {

        ///////////////////////////////////////////////////////////////////////////
        // Search
        ///////////////////////////////////////////////////////////////////////////

        /**
         * The suggestions will only be fetched getTabFrom network if the query meet this threshold (>=).
         * (local ones will be fetched regardless of the length)
         */
        private const val THRESHOLD_NETWORK_SUGGESTION = 1

        /**
         * How much time have to pass without emitting a item (i.e. the user stop typing) to fetch/show the suggestions, in milliseconds.
         */
        private const val SUGGESTIONS_DEBOUNCE = 120 //ms

        //////////////////////////////////////////////////////////////////////////

        fun getInstance(serviceId: Int, searchString: String): SearchFragment {
            val searchFragment = SearchFragment()
            searchFragment.setQuery(serviceId, searchString, arrayOf(), "")

            if (!TextUtils.isEmpty(searchString)) {
                searchFragment.setSearchOnResume()
            }

            return searchFragment
        }
    }
}
