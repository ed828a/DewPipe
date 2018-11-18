package org.schabi.newpipe.fragments.list.search

import android.app.Activity
import android.content.Context
import android.content.Intent
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
import android.widget.TextView
import icepick.State
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
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
    var filterItemCheckedId = -1

    @State
    var serviceId = Constants.NO_SERVICE_ID

    // this three represet the current search query
    @State
    var searchString: String? = null

    @State
    lateinit var contentFilter: Array<String?>

    @State
    lateinit var sortFilter: String

    // these represtent the last search
    @State
    var lastSearchedString: String? = null

    @State
    var wasSearchFocused = false

    private var menuItemToFilterName: MutableMap<Int, String>? = null
    private var service: StreamingService? = null
    private var currentPageUrl: String? = null
    private var nextPageUrl: String? = null
    private var contentCountry: String? = null
    private var isSuggestionsEnabled = true

    private val suggestionPublisher = PublishSubject.create<String>()
    private var searchDisposable: Disposable? = null
    private var suggestionDisposable: Disposable? = null
    private val disposables = CompositeDisposable()

    private var suggestionListAdapter: SuggestionListAdapter? = null
    private var historyRecordManager: HistoryRecordManager? = null

    ///////////////////////////////////////////////////////////////////////////
    // Views
    ///////////////////////////////////////////////////////////////////////////

    private var searchToolbarContainer: View? = null
    private var searchEditText: EditText? = null
    private var searchClear: View? = null

    private var suggestionsPanel: View? = null
    private var suggestionsRecyclerView: RecyclerView? = null

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

        suggestionListAdapter = SuggestionListAdapter(activity)
        val preferences = PreferenceManager.getDefaultSharedPreferences(activity)
        val isSearchHistoryEnabled = preferences.getBoolean(getString(R.string.enable_search_history_key), true)
        suggestionListAdapter!!.setShowSuggestionHistory(isSearchHistoryEnabled)

        historyRecordManager = HistoryRecordManager(context)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val preferences = PreferenceManager.getDefaultSharedPreferences(activity)
        isSuggestionsEnabled = preferences.getBoolean(getString(R.string.show_search_suggestions_key), true)
        contentCountry = preferences.getString(getString(R.string.content_country_key), getString(R.string.default_country_value))
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

        if (searchDisposable != null) searchDisposable!!.dispose()
        if (suggestionDisposable != null) suggestionDisposable!!.dispose()
        disposables?.clear()
        hideKeyboardSearch()
    }

    override fun onResume() {
        if (DEBUG) Log.d(TAG, "onResume() called")
        super.onResume()

        try {
            service = NewPipe.getService(serviceId)
        } catch (e: Exception) {
            ErrorActivity.reportError(getActivity(), e, getActivity()!!.javaClass,
                    getActivity()!!.findViewById(android.R.id.content),
                    ErrorActivity.ErrorInfo.make(UserAction.UI_ERROR,
                            "",
                            "", R.string.general_error))
        }

        if (!TextUtils.isEmpty(searchString)) {
            if (wasLoading.getAndSet(false)) {
                search(searchString, contentFilter, sortFilter)
            } else if (infoListAdapter!!.itemsList.size == 0) {
                if (savedState == null) {
                    search(searchString, contentFilter, sortFilter)
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
        if (DEBUG) Log.d(TAG, "onDestroyView() called")
        unsetSearchListeners()
        super.onDestroyView()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (searchDisposable != null) searchDisposable!!.dispose()
        if (suggestionDisposable != null) suggestionDisposable!!.dispose()
        disposables?.clear()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            ReCaptchaActivity.RECAPTCHA_REQUEST -> if (resultCode == Activity.RESULT_OK && !TextUtils.isEmpty(searchString)) {
                search(searchString, contentFilter, sortFilter)
            } else
                Log.e(TAG, "ReCaptcha failed")

            else -> Log.e(TAG, "Request code from activity not supported [$requestCode]")
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    // Init
    ///////////////////////////////////////////////////////////////////////////

    override fun initViews(rootView: View, savedInstanceState: Bundle?) {
        super.initViews(rootView, savedInstanceState)
        suggestionsPanel = rootView.findViewById(R.id.suggestions_panel)
        suggestionsRecyclerView = rootView.findViewById(R.id.suggestions_list)
        suggestionsRecyclerView!!.adapter = suggestionListAdapter
        suggestionsRecyclerView!!.layoutManager = LayoutManagerSmoothScroller(activity)

        searchToolbarContainer = activity.findViewById(R.id.toolbar_search_container)
        searchEditText = searchToolbarContainer!!.findViewById(R.id.toolbar_search_edit_text)
        searchClear = searchToolbarContainer!!.findViewById(R.id.toolbar_search_clear)
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
        searchString = if (searchEditText != null)
            searchEditText!!.text.toString()
        else
            searchString
        super.onSaveInstanceState(bundle)
    }

    ///////////////////////////////////////////////////////////////////////////
    // Init's
    ///////////////////////////////////////////////////////////////////////////

    override fun reloadContent() {
        if (!TextUtils.isEmpty(searchString) || searchEditText != null && !TextUtils.isEmpty(searchEditText!!.text)) {
            search(
                    if (!TextUtils.isEmpty(searchString))
                        searchString
                    else
                        searchEditText!!.text.toString(),
                    arrayOfNulls(0),
                    "")
        } else {
            if (searchEditText != null) {
                searchEditText!!.setText("")
                showKeyboardSearch()
            }
            animateView(errorPanelRoot, false, 200)
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    // Menu
    ///////////////////////////////////////////////////////////////////////////

    override fun onCreateOptionsMenu(menu: Menu?, inflater: MenuInflater?) {
        super.onCreateOptionsMenu(menu, inflater)

        val supportActionBar = activity.supportActionBar
        if (supportActionBar != null) {
            supportActionBar.setDisplayShowTitleEnabled(false)
            supportActionBar.setDisplayHomeAsUpEnabled(true)
        }

        menuItemToFilterName = HashMap()

        var itemId = 0
        var isFirstItem = true
        val c = context
        for (filter in service!!.searchQHFactory.availableContentFilter) {
            menuItemToFilterName!![itemId] = filter
            val item = menu!!.add(1,
                    itemId++,
                    0,
                    ServiceHelper.getTranslatedFilterString(filter, c))
            if (isFirstItem) {
                item.isChecked = true
                isFirstItem = false
            }
        }
        menu!!.setGroupCheckable(1, true, true)

        restoreFilterChecked(menu, filterItemCheckedId)
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {

        val contentFilter = ArrayList<String>(1)
        contentFilter.add(menuItemToFilterName!![item!!.itemId]!!)
        changeContentFilter(item, contentFilter)

        return true
    }

    private fun restoreFilterChecked(menu: Menu, itemId: Int) {
        if (itemId != -1) {
            val item = menu.findItem(itemId) ?: return

            item.isChecked = true
        }
    }

    private fun showSearchOnStart() {
        if (DEBUG)
            Log.d(TAG, "showSearchOnStart() called, searchQuery → "
                    + searchString
                    + ", lastSearchedQuery → "
                    + lastSearchedString)
        searchEditText!!.setText(searchString)

        if (TextUtils.isEmpty(searchString) || TextUtils.isEmpty(searchEditText!!.text)) {
            searchToolbarContainer!!.translationX = 100f
            searchToolbarContainer!!.alpha = 0f
            searchToolbarContainer!!.visibility = View.VISIBLE
            searchToolbarContainer!!.animate()
                    .translationX(0f)
                    .alpha(1f)
                    .setDuration(200)
                    .setInterpolator(DecelerateInterpolator()).start()
        } else {
            searchToolbarContainer!!.translationX = 0f
            searchToolbarContainer!!.alpha = 1f
            searchToolbarContainer!!.visibility = View.VISIBLE
        }
    }

    private fun initSearchListeners() {
        if (DEBUG) Log.d(TAG, "initSearchListeners() called")
        searchClear!!.setOnClickListener { v ->
            if (DEBUG) Log.d(TAG, "onClick() called with: v = [$v]")
            if (TextUtils.isEmpty(searchEditText!!.text)) {
                NavigationHelper.gotoMainFragment(fragmentManager!!)
                return@setOnClickListener
            }

            searchEditText!!.setText("")
            suggestionListAdapter!!.setItems(ArrayList())
            showKeyboardSearch()
        }

        TooltipCompat.setTooltipText(searchClear!!, getString(R.string.clear))

        searchEditText!!.setOnClickListener { v ->
            if (DEBUG) Log.d(TAG, "onClick() called with: v = [$v]")
            if (isSuggestionsEnabled && errorPanelRoot.visibility != View.VISIBLE) {
                showSuggestionsPanel()
            }
        }

        searchEditText!!.setOnFocusChangeListener { v: View, hasFocus: Boolean ->
            if (DEBUG) Log.d(TAG, "onFocusChange() called with: v = [$v], hasFocus = [$hasFocus]")
            if (isSuggestionsEnabled && hasFocus && errorPanelRoot.visibility != View.VISIBLE) {
                showSuggestionsPanel()
            }
        }

        suggestionListAdapter!!.setListener(object : SuggestionListAdapter.OnSuggestionItemSelected {
            override fun onSuggestionItemSelected(item: SuggestionItem) {
                search(item.query, arrayOfNulls(0), "")
                searchEditText!!.setText(item.query)
            }

            override fun onSuggestionItemInserted(item: SuggestionItem) {
                searchEditText!!.setText(item.query)
                searchEditText!!.setSelection(searchEditText!!.text.length)
            }

            override fun onSuggestionItemLongClick(item: SuggestionItem) {
                if (item.fromHistory) showDeleteSuggestionDialog(item)
            }
        })

        if (textWatcher != null) searchEditText!!.removeTextChangedListener(textWatcher)
        textWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable) {
                val newText = searchEditText!!.text.toString()
                suggestionPublisher.onNext(newText)
            }
        }
        searchEditText!!.addTextChangedListener(textWatcher)
        searchEditText!!.setOnEditorActionListener { v: TextView, actionId: Int, event: KeyEvent ->
            if (DEBUG) {
                Log.d(TAG, "onEditorAction() called with: v = [$v], actionId = [$actionId], event = [$event]")
            }
            if (event != null && (event.keyCode == KeyEvent.KEYCODE_ENTER || event.action == EditorInfo.IME_ACTION_SEARCH)) {
                search(searchEditText!!.text.toString(), arrayOfNulls(0), "")
                return@setOnEditorActionListener true
            }
            false
        }

        if (suggestionDisposable == null || suggestionDisposable!!.isDisposed)
            initSuggestionObserver()
    }

    private fun unsetSearchListeners() {
        if (DEBUG) Log.d(TAG, "unsetSearchListeners() called")
        searchClear!!.setOnClickListener(null)
        searchClear!!.setOnLongClickListener(null)
        searchEditText!!.setOnClickListener(null)
        searchEditText!!.onFocusChangeListener = null
        searchEditText!!.setOnEditorActionListener(null)

        if (textWatcher != null) searchEditText!!.removeTextChangedListener(textWatcher)
        textWatcher = null
    }

    private fun showSuggestionsPanel() {
        if (DEBUG) Log.d(TAG, "showSuggestionsPanel() called")
        animateView(suggestionsPanel, AnimationUtils.Type.LIGHT_SLIDE_AND_ALPHA, true, 200)
    }

    private fun hideSuggestionsPanel() {
        if (DEBUG) Log.d(TAG, "hideSuggestionsPanel() called")
        animateView(suggestionsPanel, AnimationUtils.Type.LIGHT_SLIDE_AND_ALPHA, false, 200)
    }

    private fun showKeyboardSearch() {
        if (DEBUG) Log.d(TAG, "showKeyboardSearch() called")
        if (searchEditText == null) return

        if (searchEditText!!.requestFocus()) {
            val imm = activity.getSystemService(
                    Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(searchEditText, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun hideKeyboardSearch() {
        if (DEBUG) Log.d(TAG, "hideKeyboardSearch() called")
        if (searchEditText == null) return

        val imm = activity.getSystemService(
                Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(searchEditText!!.windowToken,
                InputMethodManager.HIDE_NOT_ALWAYS)

        searchEditText!!.clearFocus()
    }

    private fun showDeleteSuggestionDialog(item: SuggestionItem) {
        if (activity == null || historyRecordManager == null || suggestionPublisher == null ||
                searchEditText == null || disposables == null)
            return
        val query = item.query
        AlertDialog.Builder(activity)
                .setTitle(query)
                .setMessage(R.string.delete_item_search_history)
                .setCancelable(true)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.delete) { dialog, which ->
                    val onDelete = historyRecordManager!!.deleteSearchHistory(query)
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe(
                                    { howManyDeleted ->
                                        suggestionPublisher
                                                .onNext(searchEditText!!.text.toString())
                                    },
                                    { throwable ->
                                        showSnackBarError(throwable,
                                                UserAction.DELETE_FROM_HISTORY, "none",
                                                "Deleting item failed", R.string.general_error)
                                    })
                    disposables.add(onDelete)
                }
                .show()
    }

    override fun onBackPressed(): Boolean {
        if (suggestionsPanel!!.visibility == View.VISIBLE
                && infoListAdapter!!.itemsList.size > 0
                && !isLoading.get()) {
            hideSuggestionsPanel()
            hideKeyboardSearch()
            searchEditText!!.setText(lastSearchedString)
            return true
        }
        return false
    }

    fun giveSearchEditTextFocus() {
        showKeyboardSearch()
    }

    private fun initSuggestionObserver() {
        if (DEBUG) Log.d(TAG, "initSuggestionObserver() called")
        if (suggestionDisposable != null) suggestionDisposable!!.dispose()

        val observable = suggestionPublisher
                .debounce(SUGGESTIONS_DEBOUNCE.toLong(), TimeUnit.MILLISECONDS)
                .startWith(if (searchString != null)
                    searchString
                else
                    "")
                .filter { searchString -> isSuggestionsEnabled }

        suggestionDisposable = observable
                .switchMap { query ->
                    val flowable = historyRecordManager!!
                            .getRelatedSearches(query, 3, 25)
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

                    val network = ExtractorHelper
                            .suggestionsFor(serviceId, query)
                            .toObservable()
                            .map<List<SuggestionItem>> { strings ->
                                val result = ArrayList<SuggestionItem>()
                                for (entry in strings) {
                                    result.add(SuggestionItem(false, entry))
                                }
                                result
                            }

//                    Observable.zip(local, network, {localResult, networkResult ->
//
//                    })

                    Observable.zip<List<SuggestionItem>, List<SuggestionItem>, List<SuggestionItem>>(
                            local,
                            network,
                            BiFunction { localResult, networkResult ->
                                val result = ArrayList<SuggestionItem>()
                                if (localResult.size > 0) result.addAll(localResult)

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
                    if (listNotification.isOnNext()) {
                        handleSuggestions(listNotification.getValue()!!)
                    } else if (listNotification.isOnError()) {
                        val error = listNotification.getError()
                        if (!ExtractorHelper.hasAssignableCauseThrowable(error,
                                        IOException::class.java, SocketException::class.java,
                                        InterruptedException::class.java, InterruptedIOException::class.java)) {
                            onSuggestionError(error!!)
                        }
                    }
                }
    }

    override fun doInitialLoadLogic() {
        // no-op
    }

    private fun search(searchString: String?, contentFilter: Array<String?>, sortFilter: String) {
        if (DEBUG) Log.d(TAG, "search() called with: query = [$searchString]")
        if (searchString!!.isEmpty()) return

        try {
            val service = NewPipe.getServiceByUrl(searchString)
            if (service != null) {
                showLoading()
                disposables.add(Observable
                        .fromCallable { NavigationHelper.getIntentByLink(activity, service, searchString) }
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe({ intent ->
                            fragmentManager!!.popBackStackImmediate()
                            activity.startActivity(intent)
                        }, { throwable -> showError(getString(R.string.url_not_supported_toast), false) }))
                return
            }
        } catch (e: Exception) {
            // Exception occurred, it's not a url
        }

        lastSearchedString = this.searchString
        this.searchString = searchString
        infoListAdapter!!.clearStreamItemList()
        hideSuggestionsPanel()
        hideKeyboardSearch()

        historyRecordManager!!.onSearched(serviceId, searchString)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        { ignored -> },
                        { error ->
                            showSnackBarError(error, UserAction.SEARCHED,
                                    NewPipe.getNameOfService(serviceId), searchString, 0)
                        }
                )
        suggestionPublisher.onNext(searchString)
        startLoading(false)
    }

    public override fun startLoading(forceLoad: Boolean) {
        super.startLoading(forceLoad)
        disposables?.clear()
        if (searchDisposable != null) searchDisposable!!.dispose()
        searchDisposable = ExtractorHelper.searchFor(serviceId,
                searchString,
                Arrays.asList(*contentFilter),
                sortFilter)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .doOnEvent { searchResult, throwable -> isLoading.set(false) }
                .subscribe({ this.handleResult(it) }, { this.onError(it) })

    }

    override fun loadMoreItems() {
        isLoading.set(true)
        showListFooter(true)
        if (searchDisposable != null) searchDisposable!!.dispose()
        searchDisposable = ExtractorHelper.getMoreSearchItems(
                serviceId,
                searchString,
                asList(*contentFilter),
                sortFilter,
                nextPageUrl)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .doOnEvent { nextItemsResult, throwable -> isLoading.set(false) }
                .subscribe( { this.handleNextItems(it) },  { this.onError(it) })
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
            search(searchString, this.contentFilter, sortFilter)
        }
    }

    private fun setQuery(serviceId: Int, searchString: String, contentfilter: Array<String?>, sortFilter: String) {
        this.serviceId = serviceId
        this.searchString = searchString
        this.contentFilter = contentfilter as Array<String?>
        this.sortFilter = sortFilter
    }

    ///////////////////////////////////////////////////////////////////////////
    // Suggestion Results
    ///////////////////////////////////////////////////////////////////////////

    fun handleSuggestions(suggestions: List<SuggestionItem>) {
        if (DEBUG) Log.d(TAG, "handleSuggestions() called with: suggestions = [$suggestions]")
        suggestionsRecyclerView!!.smoothScrollToPosition(0)
        suggestionsRecyclerView!!.post { suggestionListAdapter!!.setItems(suggestions) }

        if (errorPanelRoot.visibility == View.VISIBLE) {
            hideLoading()
        }
    }

    fun onSuggestionError(exception: Throwable) {
        if (DEBUG) Log.d(TAG, "onSuggestionError() called with: exception = [$exception]")
        if (super.onError(exception)) return

        val errorId = if (exception is ParsingException)
            R.string.parsing_error
        else
            R.string.general_error
        onUnrecoverableError(exception, UserAction.GET_SUGGESTIONS,
                NewPipe.getNameOfService(serviceId), searchString, errorId)
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
        val exceptions = result.errors
        if (!exceptions.isEmpty() && !(exceptions.size == 1 && exceptions[0] is SearchExtractor.NothingFoundException)) {
            showSnackBarError(result.errors, UserAction.SEARCHED,
                    NewPipe.getNameOfService(serviceId), searchString, 0)
        }

        lastSearchedString = searchString
        nextPageUrl = result.nextPageUrl
        currentPageUrl = result.url

        if (infoListAdapter!!.itemsList.size == 0) {
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
                    NewPipe.getNameOfService(serviceId), searchString, errorId)
        }

        return true
    }

    companion object {

        ///////////////////////////////////////////////////////////////////////////
        // Search
        ///////////////////////////////////////////////////////////////////////////

        /**
         * The suggestions will only be fetched from network if the query meet this threshold (>=).
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
            searchFragment.setQuery(serviceId, searchString, arrayOfNulls(0), "")

            if (!TextUtils.isEmpty(searchString)) {
                searchFragment.setSearchOnResume()
            }

            return searchFragment
        }
    }
}
