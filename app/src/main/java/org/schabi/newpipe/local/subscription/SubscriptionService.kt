package org.schabi.newpipe.local.subscription

import android.content.Context
import android.util.Log
import io.reactivex.*
import io.reactivex.functions.Function
import io.reactivex.schedulers.Schedulers
import org.schabi.newpipe.database.AppDatabase
import org.schabi.newpipe.database.subscription.SubscriptionDAO
import org.schabi.newpipe.database.subscription.SubscriptionEntity
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.util.ExtractorHelper
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Subscription Service singleton:
 * Provides a basis for channel Subscriptions.
 * Provides access to subscription table in database as well as
 * up-to-date observations on the subscribed channels
 */
class SubscriptionService private constructor(context: Context) {

    private val db: AppDatabase = AppDatabase.getDatabase(context.applicationContext)
    /**
     * Provides an observer to the latest update to the subscription table.
     *
     *
     * This observer may be subscribed multiple times, where each subscriber obtains
     * the latest synchronized changes available, effectively share the same data
     * across all subscribers.
     *
     *
     * This observer has a debounce cooldown, meaning if multiple updates are observed
     * in the cooldown interval, only the latest changes are emitted to the subscribers.
     * This reduces the amount of observations caused by frequent updates to the database.
     */
    val subscription: Flowable<List<SubscriptionEntity>>

    private val subscriptionScheduler: Scheduler

    /**
     * Part of subscription observation pipeline
     *
     * @see SubscriptionService.getSubscription
     */
    private// Wait for a period of infrequent updates and return the latest update
    // Share allows multiple subscribers on the same observable
    // Replay synchronizes subscribers to the last emitted result
    fun getSubscriptionInfos(): Flowable<List<SubscriptionEntity>> =
            subscriptionTable().all
                    .debounce(SUBSCRIPTION_DEBOUNCE_INTERVAL.toLong(), TimeUnit.MILLISECONDS)
                    .share()
                    .replay(1)
                    .autoConnect()

    init {
//        db = NewPipeDatabase.getInstance(context.applicationContext)
        subscription = getSubscriptionInfos()

        val subscriptionExecutor = Executors.newFixedThreadPool(SUBSCRIPTION_THREAD_POOL_SIZE)
        subscriptionScheduler = Schedulers.from(subscriptionExecutor)
    }

    fun getChannelInfo(subscriptionEntity: SubscriptionEntity): Maybe<ChannelInfo> {
        Log.d(TAG, "getChannelInfo() called with: subscriptionEntity = [$subscriptionEntity]")

        return Maybe.fromSingle(
                ExtractorHelper.getChannelInfo(subscriptionEntity.serviceId, subscriptionEntity.url!!, false))
                .subscribeOn(subscriptionScheduler)
    }

    /**
     * Returns the database access interface for subscription table.
     */
    fun subscriptionTable(): SubscriptionDAO = db.subscriptionDAO()

    fun updateChannelInfo(info: ChannelInfo): Completable {
        val update = Function<List<SubscriptionEntity>, CompletableSource> { subscriptionEntities ->
            Log.d(TAG, "updateChannelInfo() called with: subscriptionEntities = [$subscriptionEntities]")

            if (subscriptionEntities.size == 1) {
                val subscription = subscriptionEntities[0]

                // Subscriber count changes very often, making this check almost unnecessary.
                // Consider removing it later.
                if (!isSubscriptionUpToDate(info, subscription)) {
                    subscription.setData(info.name, info.avatarUrl, info.description, info.subscriberCount)

                    return@Function Completable.fromRunnable { subscriptionTable().update(subscription) }
                }
            }

            Completable.complete()
        }

        return subscriptionTable().getSubscription(info.serviceId, info.url)
                .firstOrError()
                .flatMapCompletable(update)
    }

    fun upsertAll(infoList: List<ChannelInfo>): List<SubscriptionEntity> {
        val entityList = ArrayList<SubscriptionEntity>()
//        for (info in infoList) entityList.add(SubscriptionEntity.from(info))
        infoList.forEach { info -> entityList.add(SubscriptionEntity.from(info)) }

        return subscriptionTable().upsertAll(entityList)
    }

    private fun isSubscriptionUpToDate(info: ChannelInfo, entity: SubscriptionEntity): Boolean =
            info.url == entity.url &&
                    info.serviceId == entity.serviceId &&
                    info.name == entity.name &&
                    info.avatarUrl == entity.avatarUrl &&
                    info.description == entity.description &&
                    info.subscriberCount == entity.subscriberCount


    companion object {
        private val TAG = "SubscriptionService@" + Integer.toHexString(hashCode())

        @Volatile
        private var instance: SubscriptionService? = null

        fun getInstance(context: Context): SubscriptionService {
            var result = instance
            if (result == null) {
                synchronized(SubscriptionService::class.java) {
                    result = instance
                    if (result == null) {
                        result = SubscriptionService(context)
                        instance = result
                    }
                }
            }

            return result!!
        }

        private const val SUBSCRIPTION_DEBOUNCE_INTERVAL = 500
        private const val SUBSCRIPTION_THREAD_POOL_SIZE = 4
    }
}
