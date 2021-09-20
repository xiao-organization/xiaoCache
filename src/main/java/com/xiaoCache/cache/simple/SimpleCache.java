package com.xiaoCache.cache.simple;

import java.io.Serial;
import java.io.Serializable;
import java.util.Iterator;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import com.xiaoCache.cache.simple.method.CacheFun;


/**
 * [简单缓存](Simple cache)
 * @description zh - 简单缓存
 * @description en - Simple cache
 * @version V1.0
 * @author XiaoXunYao
 * @since 2021-09-20 11:26:51
 */
public class SimpleCache<K, V> implements Iterable<Map.Entry<K, V>>, Serializable {
    
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 池
     */
    private final Map<K, V> cache;

    /**
     * 乐观读写锁
     */
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    /**
     * 写的时候每个key一把锁，降低锁的粒度
     */
    protected final Map<K, Lock> keyLockMap = new ConcurrentHashMap<>();

    /**
     * [构造](structure)
     * @description zh - 构造
     * @description en - structure
     * @version V1.0
     * @author XiaoXunYao
     * @since 2021-09-20 11:22:09
     */
    public SimpleCache() {
        this(new WeakHashMap<>());
    }

    /**
     * [通过自定义Map初始化，可以自定义缓存实现。](Through the custom map initialization, you can customize the cache implementation.)
     * @description: zh - 通过自定义Map初始化，可以自定义缓存实现。
     * @description: en - Through the custom map initialization, you can customize the cache implementation.
     * @version: V1.0
     * @author XiaoXunYao
     * @since 2021-09-20 11:22:50
     * @param initMap: 初始Map，用于定义Map类型
    */
    public SimpleCache(Map<K, V> initMap) {
        this.cache = initMap;
    }

    /**
     * [从缓存池中查找值](Find value from cache pool)
     * @description: zh - 从缓存池中查找值
     * @description: en - Find value from cache pool
     * @version: V1.0
     * @author XiaoXunYao
     * @since 2021-09-20 11:23:03
     * @param key: 键
     * @return V
    */
    public V get(K key){
        lock.readLock().lock();
        try {
            return cache.get(key);
        }finally {
            lock.readLock().unlock();
        }
    }

    /**
     * [从缓存中获得对象，当对象不在缓存中或已经过期返回回调产生的对象](Get the object from the cache. When the object is not in the cache or has expired, return the object generated by the callback)
     * @description: zh - 从缓存中获得对象，当对象不在缓存中或已经过期返回回调产生的对象
     * @description: en - Get the object from the cache. When the object is not in the cache or has expired, return the object generated by the callback
     * @version: V1.0
     * @author XiaoXunYao
     * @since 2021-09-20 11:23:15
     * @param key: 键
     * @param supplier: 如果不存在回调方法，用于生产值对象
     * @return V
    */
    public V get(K key, CacheFun<V> supplier){
        V v = get(key);
        if(null == v && null != supplier) {
            //每个key单独获取一把锁，降低锁的粒度提高并发能力，see pr#1385@Github
            final Lock keyLock = keyLockMap.computeIfAbsent(key, k -> new ReentrantLock());
            keyLock.lock();
            try {
                // 双重检查，防止在竞争锁的过程中已经有其它线程写入
                v = cache.get(key);
                if (null == v) {
                    try {
                        v = supplier.call();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                    put(key, v);
                }
            } finally {
                keyLock.unlock();
                keyLockMap.remove(key);
            }
        }
        return v;
    }

    /**
     * [存入缓存](Cache)
     * @description: zh - 存入缓存
     * @description: en - Cache
     * @version: V1.0
     * @author XiaoXunYao
     * @since 2021-09-20 11:23:28
     * @param key: 键
     * @param value: 值
     * @return V
    */
    public V put(K key, V value) {
        // 独占写锁
        lock.writeLock().lock();
        try {
            cache.put(key, value);
        } finally {
            lock.writeLock().unlock();
        }
        return value;
    }

    /**
     * [移除缓存](Remove cache)
     * @description: zh - 移除缓存
     * @description: en - Remove cache
     * @version: V1.0
     * @author XiaoXunYao
     * @since 2021-09-20 11:23:39
     * @param key: 键
     * @return V
    */
    public V remove(K key) {
        // 独占写锁
        lock.writeLock().lock();
        try {
            return cache.remove(key);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * [清空缓存池](Clear cache pool)
     * @description: zh - 清空缓存池
     * @description: en - Clear cache pool
     * @version: V1.0
     * @author XiaoXunYao
     * @since 2021-09-20 11:23:51
    */
    public void clear() {
        // 独占写锁
        lock.writeLock().lock();
        try {
            this.cache.clear();
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * [重写迭代器](Override iterator)
     * @description: zh - 重写迭代器
     * @description: en - Override iterator
     * @version: V1.0
     * @author XiaoXunYao
     * @since 2021-09-20 11:24:02
     * @return java.util.Iterator<java.util.Map.Entry<K,V>>
    */
    @Override
    public Iterator<Map.Entry<K, V>> iterator() {
        return this.cache.entrySet().iterator();
    }
}
