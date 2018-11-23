package org.schabi.newpipe.local.history

/*
 * Copyright (C) Mauricio Colli 2018
 * HistoryRecordManager.java is part of NewPipe.
 *
 * NewPipe is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * NewPipe is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with NewPipe.  If not, see <http://www.gnu.org/licenses/>.
 */

import android.content.Context
import android.content.SharedPreferences
import android.preference.PreferenceManager

import org.schabi.newpipe.NewPipeDatabase
import org.schabi.newpipe.R
import org.schabi.newpipe.database.AppDatabase
import org.schabi.newpipe.database.history.dao.SearchHistoryDAO
import org.schabi.newpipe.database.history.dao.StreamHistoryDAO
import org.schabi.newpipe.database.history.model.SearchHistoryEntry
import org.schabi.newpipe.database.history.model.StreamHistoryEntity
import org.schabi.newpipe.database.history.model.StreamHistoryEntry
import org.schabi.newpipe.database.stream.StreamStatisticsEntry
import org.schabi.newpipe.database.stream.dao.StreamDAO
import org.schabi.newpipe.database.stream.dao.StreamStateDAO
import org.schabi.newpipe.database.stream.model.StreamEntity
import org.schabi.newpipe.database.stream.model.StreamStateEntity
import org.schabi.newpipe.extractor.stream.StreamInfo

import java.util.ArrayList
import java.util.Date

import io.reactivex.Flowable
import io.reactivex.Maybe
import io.reactivex.Single
import io.reactivex.schedulers.Schedulers

class HistoryRecordManager(context: Context) {

    private val database: AppDatabase
    private val streamTable: StreamDAO
    private val streamHistoryTable: StreamHistoryDAO
    private val searchHistoryTable: SearchHistoryDAO
    private val streamStateTable: StreamStateDAO
    private val sharedPreferences: SharedPreferences
    private val searchHistoryKey: String
    private val streamHistoryKey: String

    val streamHistory: Flowable<List<StreamHistoryEntry>>
        get() = streamHistoryTable.history.subscribeOn(Schedulers.io())

    val streamStatistics: Flowable<List<StreamStatisticsEntry>>
        get() = streamHistoryTable.statistics.subscribeOn(Schedulers.io())

    private val isStreamHistoryEnabled: Boolean
        get() = sharedPreferences.getBoolean(streamHistoryKey, false)

    private val isSearchHistoryEnabled: Boolean
        get() = sharedPreferences.getBoolean(searchHistoryKey, false)

    init {
        database = NewPipeDatabase.getInstance(context)
        streamTable = database.streamDAO()
        streamHistoryTable = database.streamHistoryDAO()
        searchHistoryTable = database.searchHistoryDAO()
        streamStateTable = database.streamStateDAO()
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        searchHistoryKey = context.getString(R.string.enable_search_history_key)
        streamHistoryKey = context.getString(R.string.enable_watch_history_key)
    }

    ///////////////////////////////////////////////////////
    // Watch History
    ///////////////////////////////////////////////////////

    fun onViewed(info: StreamInfo): Maybe<Long> {
        if (!isStreamHistoryEnabled) return Maybe.empty()

        val currentTime = Date()
        return Maybe.fromCallable {
            database.runInTransaction<Long> {
                val streamId = streamTable.upsert(StreamEntity(info))
                val latestEntry = streamHistoryTable.latestEntry

                if (latestEntry != null && latestEntry.streamUid == streamId) {
                    streamHistoryTable.delete(latestEntry)
                    latestEntry.setAccessDate(currentTime)
                    latestEntry.repeatCount = latestEntry.repeatCount + 1
                    return@runInTransaction streamHistoryTable.insert(latestEntry)
                } else {
                    return@runInTransaction streamHistoryTable.insert(StreamHistoryEntity(streamId, currentTime))
                }
            }
        }.subscribeOn(Schedulers.io())
    }

    fun deleteStreamHistory(streamId: Long): Single<Int> {
        return Single.fromCallable { streamHistoryTable.deleteStreamHistory(streamId) }
                .subscribeOn(Schedulers.io())
    }

    fun deleteWholeStreamHistory(): Single<Int> {
        return Single.fromCallable { streamHistoryTable.deleteAll() }
                .subscribeOn(Schedulers.io())
    }

    fun insertStreamHistory(entries: Collection<StreamHistoryEntry>): Single<List<Long>> {
        val entities = ArrayList<StreamHistoryEntity>(entries.size)
        for (entry in entries) {
            entities.add(entry.toStreamHistoryEntity())
        }
        return Single.fromCallable { streamHistoryTable.insertAll(entities) }
                .subscribeOn(Schedulers.io())
    }

    fun deleteStreamHistory(entries: Collection<StreamHistoryEntry>): Single<Int> {
        val entities = ArrayList<StreamHistoryEntity>(entries.size)
        for (entry in entries) {
            entities.add(entry.toStreamHistoryEntity())
        }
        return Single.fromCallable { streamHistoryTable.delete(entities) }
                .subscribeOn(Schedulers.io())
    }

    ///////////////////////////////////////////////////////
    // Search History
    ///////////////////////////////////////////////////////

    fun onSearched(serviceId: Int, search: String): Maybe<Long> {
        if (!isSearchHistoryEnabled) return Maybe.empty()

        val currentTime = Date()
        val newEntry = SearchHistoryEntry(currentTime, serviceId, search)

        return Maybe.fromCallable {
            database.runInTransaction<Long> {
                val latestEntry = searchHistoryTable.latestEntry
                if (latestEntry != null && latestEntry.hasEqualValues(newEntry)) {
                    latestEntry.creationDate = currentTime
                    return@runInTransaction searchHistoryTable.update(latestEntry).toLong()
                } else {
                    return@runInTransaction searchHistoryTable.insert(newEntry)
                }
            }
        }.subscribeOn(Schedulers.io())
    }

    fun deleteSearchHistory(search: String): Single<Int> {
        return Single.fromCallable { searchHistoryTable.deleteAllWhereQuery(search) }
                .subscribeOn(Schedulers.io())
    }

    fun deleteWholeSearchHistory(): Single<Int> {
        return Single.fromCallable { searchHistoryTable.deleteAll() }
                .subscribeOn(Schedulers.io())
    }

    fun getRelatedSearches(query: String,
                           similarQueryLimit: Int,
                           uniqueQueryLimit: Int): Flowable<List<SearchHistoryEntry>> {
        return if (query.length > 0)
            searchHistoryTable.getSimilarEntries(query, similarQueryLimit)
        else
            searchHistoryTable.getUniqueEntries(uniqueQueryLimit)
    }

    ///////////////////////////////////////////////////////
    // Stream State History
    ///////////////////////////////////////////////////////

    fun loadStreamState(info: StreamInfo): Maybe<StreamStateEntity> {
        return Maybe.fromCallable { streamTable.upsert(StreamEntity(info)) }
                .flatMap { streamId -> streamStateTable.getState(streamId).firstElement() }
                .flatMap { states -> if (states.isEmpty()) Maybe.empty() else Maybe.just(states[0]) }
                .subscribeOn(Schedulers.io())
    }

    fun saveStreamState(info: StreamInfo, progressTime: Long): Maybe<Long> {
        return Maybe.fromCallable {
            database.runInTransaction<Long> {
                val streamId = streamTable.upsert(StreamEntity(info))
                streamStateTable.upsert(StreamStateEntity(streamId, progressTime))
            }
        }.subscribeOn(Schedulers.io())
    }

    ///////////////////////////////////////////////////////
    // Utility
    ///////////////////////////////////////////////////////

    fun removeOrphanedRecords(): Single<Int> {
        return Single.fromCallable{ streamTable.deleteOrphans() }
                .subscribeOn(Schedulers.io())
    }
}
