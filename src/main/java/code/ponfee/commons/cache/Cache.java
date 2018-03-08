package code.ponfee.commons.cache;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import com.google.common.base.Preconditions;

import code.ponfee.commons.jce.digest.DigestUtils;

/**
 * 缓存类
 * @author fupf
 * @param <T>
 */
public class Cache<T> {

    public static final long KEEPALIVE_FOREVER = 0; // 为0表示不失效

    private final boolean caseSensitiveKey; // 是否忽略大小写（只针对String）
    private final boolean compressKey; // 是否压缩key（只针对String）
    private final long keepAliveInMillis; // 默认的数据保存的时间
    private final Map<Comparable<?>, CacheValue<T>> cache = new ConcurrentHashMap<>(); // 缓存容器

    private volatile boolean isDestroy = false; // 是否被销毁
    private DateProvider dateProvider = DateProvider.SYSTEM;
    private final Lock lock = new ReentrantLock(); // 定时清理加锁
    private ScheduledExecutorService executor;

    Cache(boolean caseSensitiveKey, boolean compressKey, long keepAliveInMillis, int autoReleaseInSeconds) {
        Preconditions.checkArgument(keepAliveInMillis >= 0);
        Preconditions.checkArgument(autoReleaseInSeconds >= 0);

        this.caseSensitiveKey = caseSensitiveKey;
        this.compressKey = compressKey;
        this.keepAliveInMillis = keepAliveInMillis;

        if (autoReleaseInSeconds > 0) {
            // 定时清理
            (executor = Executors.newSingleThreadScheduledExecutor()).scheduleAtFixedRate(() -> {
                if (!lock.tryLock()) {
                    return;
                }
                try {
                    long now = now();
                    Entry<Comparable<?>, CacheValue<T>> entry;
                    for (Iterator<Entry<Comparable<?>, CacheValue<T>>> t = cache.entrySet().iterator(); t.hasNext();) {
                        entry = t.next();
                        if (entry.getValue().isExpire(now)) {
                            t.remove();
                        }
                    }
                } finally {
                    lock.unlock();
                }
            }, autoReleaseInSeconds, autoReleaseInSeconds, TimeUnit.SECONDS);
        }
    }

    public boolean isCaseSensitiveKey() {
        return caseSensitiveKey;
    }

    public boolean isCompressKey() {
        return compressKey;
    }

    public long getKeepAliveInMillis() {
        return keepAliveInMillis;
    }

    public DateProvider getDateProvider() {
        return dateProvider;
    }

    private long now() {
        return dateProvider.now();
    }

    protected void setDateProvider(DateProvider dateProvider) {
        this.dateProvider = dateProvider;
    }

    // --------------------------------cache value-------------------------------
    public void set(Comparable<?> key) {
        set(key, null);
    }

    public void set(Comparable<?> key, T value) {
        long expireTimeMillis;
        if (keepAliveInMillis > 0) {
            expireTimeMillis = now() + keepAliveInMillis;
        } else {
            expireTimeMillis = KEEPALIVE_FOREVER;
        }

        this.set(key, value, expireTimeMillis);
    }

    public void setWithAliveInMillis(Comparable<?> key, T value, int aliveInMillis) {
        this.set(key, value, now() + aliveInMillis);
    }

    public void setWithNull(Comparable<?> key, long expireTimeMillis) {
        set(key, null, expireTimeMillis);
    }

    public void set(Comparable<?> key, T value, long expireTimeMillis) {
        Preconditions.checkState(!isDestroy);

        if (expireTimeMillis == KEEPALIVE_FOREVER || expireTimeMillis > now()) {
            cache.put(getEffectiveKey(key), new CacheValue<>(value, expireTimeMillis));
        }
    }

    /**
     * 获取
     * @param key
     * @return
     */
    public T get(Comparable<?> key) {
        if (isDestroy) {
            return null;
        }

        key = getEffectiveKey(key);
        CacheValue<T> cacheValue = cache.get(key);
        if (cacheValue == null) {
            return null;
        } else if (cacheValue.isExpire(now())) {
            cache.remove(key);
            return null;
        } else {
            return cacheValue.getValue();
        }
    }

    /**
     * get value and remove it
     * @param key
     */
    public T getAndRemove(Comparable<?> key) {
        if (isDestroy) {
            return null;
        }

        CacheValue<T> cacheValue = cache.remove(getEffectiveKey(key));
        return cacheValue == null ? null : cacheValue.getValue();
    }

    /**
     * @param key
     * @return
     */
    public boolean containsKey(Comparable<?> key) {
        if (isDestroy) {
            return false;
        }

        key = getEffectiveKey(key);
        CacheValue<T> cacheValue = cache.get(key);
        if (cacheValue == null) {
            return false;
        } else if (cacheValue.isExpire(now())) {
            cache.remove(key);
            return false;
        } else {
            return true;
        }
    }

    /**
     * 是否包含指定的value
     * @param value
     * @return
     */
    public boolean containsValue(T value) {
        if (isDestroy) {
            return false;
        }

        Entry<Comparable<?>, CacheValue<T>> entry;
        for (Iterator<Entry<Comparable<?>, CacheValue<T>>> iter = cache.entrySet().iterator(); iter.hasNext();) {
            entry = iter.next();
            if (entry.getValue().isAlive(now())) {
                if ((value == null && entry.getValue().getValue() == null) ||
                    (value != null && value.equals(entry.getValue().getValue()))) {
                    return true;
                }
            } else {
                iter.remove();
            }
        }
        return false;
    }

    /**
     * get for value collection
     * @return  the collection of values
     */
    public Collection<T> values() {
        if (isDestroy) {
            return Collections.emptyList();
        }

        Collection<T> values = new ArrayList<>();
        Entry<Comparable<?>, CacheValue<T>> entry;
        for (Iterator<Entry<Comparable<?>, CacheValue<T>>> iter = cache.entrySet().iterator(); iter.hasNext();) {
            entry = iter.next();
            if (entry.getValue().isAlive(now())) {
                values.add(entry.getValue().getValue());
            } else {
                iter.remove();
            }
        }
        return values;
    }

    /**
     * get size of the cache keys
     * @return
     */
    public int size() {
        return cache.size();
    }

    /**
     * check is empty
     * @return
     */
    public boolean isEmpty() {
        return size() == 0;
    }

    /**
     * clear all
     */
    public void clear() {
        Preconditions.checkState(!isDestroy);

        cache.clear();
    }

    /**
     * destory the cache self
     */
    public void destroy() {
        isDestroy = true;
        if (executor != null) try {
            executor.shutdown();
        } catch (Exception ignored) {
            ignored.printStackTrace();
        }
        cache.clear();
    }

    public boolean isDestroy() {
        return isDestroy;
    }

    /**
     * get effective key
     * @param key
     * @return
     */
    private Comparable<?> getEffectiveKey(Comparable<?> key) {
        if (key instanceof CharSequence) {
            if (!caseSensitiveKey) {
                key = key.toString().toLowerCase(); // 不区分大小写（转小写）
            }
            if (compressKey) {
                key = DigestUtils.sha1Hex(key.toString()); // 压缩key
            }
        }
        return key;
    }

}
