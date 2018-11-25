package org.schabi.newpipe.fragments

interface ViewContract<I> {
    fun showLoading()
    fun hideLoading()
    fun showEmptyState()
    fun showError(message: String, showRetryButton: Boolean)

    fun handleResult(result: I)
}
