package code.ponfee.commons.jedis;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;

import code.ponfee.commons.math.Numbers;
import code.ponfee.commons.util.Bytes;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.Transaction;

/**
 * <pre>
 * class X {
 *   public void m() {
 *     Lock lock = new JedisLock(jedisClient, "lockKey", 5);
 *     lock.lock();  // block until acquire lock or timeout
 *     try {
 *       // ... method body
 *     } finally {
 *       lock.unlock()
 *     }
 *   }
 * }
 * 
 * class Y {
 *   public void m() {
 *     Lock lock = new JedisLock(jedisClient, "lockKey", 5);
 *     if (!lock.tryLock()) return;
 *     try {
 *       // ... method body
 *     } finally {
 *       lock.unlock();
 *     }
 *   }
 * }
 * 
 * class Z {
 *   public void m() {
 *     Lock lock = new JedisLock(jedisClient, "lockKey", 5);
 *     // auto timeout release lock
 *     if (!lock.tryLock(100, TimeUnit.MILLISECONDS)) return;
 *     try {
 *       // ... method body
 *     } finally {
 *       lock.unlock();
 *     }
 *   }
 * }
 * </pre>
 * 
 * 基于redis的分布式锁
 * 使用redis transaction功能实现
 * @author fupf
 */
public class JedisLock implements Lock, java.io.Serializable {

    private static final long serialVersionUID = -6209919116306827731L;
    private static Logger logger = LoggerFactory.getLogger(JedisLock.class);

    private static final int MAX_TOMEOUT_SECONDS = 86400; // 最大超 时为1天
    private static final int MIN_TOMEOUT_SECONDS = 1; // 最小超 时为1秒
    private static final int MIN_SLEEP_MILLIS = 9; // 最小休眠时间为9毫秒
    private static final byte[] KEY_PREFIX = "jedis:lock:".getBytes();
    private static final transient ThreadLocal<byte[]> LOCK_VALUE = new ThreadLocal<>();

    private final transient JedisClient jedisClient;
    private final byte[] lockKey;
    private final int timeoutSeconds; // 锁的超时时间，防止死锁
    private final long timeoutMillis;
    private final long sleepMillis;

    public JedisLock(JedisClient jedisClient, String lockKey) {
        this(jedisClient, lockKey, MAX_TOMEOUT_SECONDS);
    }

    public JedisLock(JedisClient jedisClient, String lockKey, int timeoutSeconds) {
        this(jedisClient, lockKey, timeoutSeconds, 9);
    }

    /**
     * 锁对象构造函数
     * @param jedisClient        jedisClient实例
     * @param lockKey            待加锁的键
     * @param timeoutSeconds     锁超时时间（防止死锁）
     * @param sleepMillis        休眠时间（毫秒）
     */
    public JedisLock(JedisClient jedisClient, String lockKey, int timeoutSeconds, int sleepMillis) {
        Preconditions.checkArgument(jedisClient != null, "jedis client cannot be null");
        Preconditions.checkArgument(StringUtils.isNotEmpty(lockKey), "lock key cannot be null");

        this.jedisClient = jedisClient;
        this.lockKey = Bytes.concat(KEY_PREFIX, lockKey.getBytes()); // add prefix key by "jedis:lock:"
        timeoutSeconds = Math.abs(timeoutSeconds);
        if (timeoutSeconds > MAX_TOMEOUT_SECONDS) {
            timeoutSeconds = MAX_TOMEOUT_SECONDS;
        } else if (timeoutSeconds < MIN_TOMEOUT_SECONDS) {
            timeoutSeconds = MIN_TOMEOUT_SECONDS;
        }
        this.timeoutSeconds = timeoutSeconds;
        this.timeoutMillis = TimeUnit.SECONDS.toMillis(timeoutSeconds);
        this.sleepMillis = Numbers.bounds(sleepMillis, MIN_SLEEP_MILLIS, (int) timeoutMillis);
    }

    /**
     * 等待锁直到获取
     */
    public @Override void lock() {
        for (;;) {
            if (tryLock()) {
                break;
            }
            try {
                TimeUnit.MILLISECONDS.sleep(sleepMillis);
            } catch (InterruptedException e) {
                logger.error("jedis lock sleep occur interrupted exception", e);
            }
        }
    }

    /**
     * 等待锁直到获取成功或抛出InterruptedException异常
     */
    public @Override void lockInterruptibly() throws InterruptedException {
        for (;;) {
            if (Thread.interrupted()) {
                throw new InterruptedException();
            }
            if (tryLock()) {
                break;
            }
            TimeUnit.MILLISECONDS.sleep(sleepMillis);
        }
    }

    /**
     * 尝试获取锁，成功返回true，失败返回false
     */
    public @Override boolean tryLock() {
        return jedisClient.call(shardedJedis -> {
            Jedis jedis = shardedJedis.getShard(lockKey);

            // 仅当lockKey不存在才能设置成功并返回1，否则setnx不做任何动作返回0
            Long result = jedis.setnx(lockKey, generateValue());
            if (result != null && result.intValue() == 1) {
                // 成功获取锁后需要设置失效期
                jedis.expire(lockKey, JedisOperations.getActualExpire(timeoutSeconds));
                return true;
            }

            jedis.watch(lockKey); // 监视lockKey
            byte[] value = jedis.get(lockKey); // 获取当前锁值
            if (value == null) {
                jedis.unwatch();
                return tryLock(); // 锁被释放，重新获取
            } else if (System.currentTimeMillis() <= parseValue(value)) {
                jedis.unwatch();
                return false; // 锁未超时
            } else {
                // 锁已超时，争抢锁（事务控制）
                Transaction tx = jedis.multi();
                tx.getSet(lockKey, generateValue());
                tx.expire(lockKey, JedisOperations.getActualExpire(timeoutSeconds));
                List<Object> exec = tx.exec(); // exec执行完后被监控的key会自动unwatch
                return exec != null && !exec.isEmpty() 
                    && Arrays.equals(value, (byte[]) exec.get(0));
            }
        }, false);
    }

    /**
     * 尝试获取锁，成功返回true，失败返回false
     * 线程中断则抛出interrupted异常
     */
    public @Override boolean tryLock(long timeout, TimeUnit unit) 
        throws InterruptedException {
        timeout = unit.toNanos(timeout);
        long startTime = System.nanoTime();
        for (;;) {
            if (Thread.interrupted()) {
                throw new InterruptedException();
            }
            if (tryLock()) {
                return true;
            }
            if (System.nanoTime() - startTime > timeout) {
                return false; // 等待超时则返回
            }
            TimeUnit.MILLISECONDS.sleep(sleepMillis);
        }
    }

    /**
     * 释放锁
     */
    public @Override void unlock() {
        jedisClient.hook(shardedJedis -> {
            // 根据分片获取jedis
            Jedis jedis = shardedJedis.getShard(lockKey);
            jedis.watch(lockKey);
            byte[] value = LOCK_VALUE.get(); // 获取当前线程保存的锁值
            if (value == null || !Arrays.equals(value, jedis.get(lockKey))) {
                // 当前线程未获取过锁或锁已被其它线程获取
                jedis.unwatch();
            } else {
                // 当前线程持有锁，需要释放锁
                Transaction tx = jedis.multi();
                tx.del(lockKey);
                tx.exec(); // 自动unwatch
            }

            LOCK_VALUE.remove(); // 删除
        });
    }

    public @Override Condition newCondition() {
        throw new UnsupportedOperationException();
    }

    /**
     * <pre> 
     *  {@code
     *     class X {
     *       Lock lock = new JedisLock(jedisClient, "lockKey", 5);
     *       // ...
     *       public void m() {
     *         assert !lock.isHeldByCurrentThread();
     *         lock.lock();
     *         try {
     *             // ... method body
     *         } finally {
     *             lock.unlock();
     *         }
     *       }
     *     }
     *  }
     * </pre>
     * 当前线程是否持有锁
     * @return
     */
    public boolean isHeldByCurrentThread() {
        byte[] value = LOCK_VALUE.get();
        return value != null && Arrays.equals(value, jedisClient.valueOps().get(lockKey));
    }

    /**
     * 是否已锁（任何线程）
     * @return
     */
    public boolean isLocked() {
        return jedisClient.valueOps().get(lockKey) != null;
    }

    /**
     * 解析值数据
     * @param value
     * @return
     */
    private long parseValue(byte[] value) {
        return Bytes.toLong(value, 16); // long value after 16 byte uuid
    }

    /**
     * 获取锁值
     * @return
     */
    private byte[] generateValue() {
        UUID uuid = UUID.randomUUID();
        //byte[] value = ByteBuffer
        //    .allocate(24)
        //    .putLong(uuid.getMostSignificantBits()) // 8 byte most bits of uuid
        //    .putLong(uuid.getLeastSignificantBits()) // 8 byte least bits of uuid
        //    .putLong(System.currentTimeMillis() + timeoutMillis) // 8 byte time stamp
        //    .array();

        long most  = uuid.getMostSignificantBits(), 
             least = uuid.getLeastSignificantBits(),
             time  = System.currentTimeMillis() + timeoutMillis;

        byte[] value = {
            (byte) (most  >>> 56), (byte) (most  >>> 48),
            (byte) (most  >>> 40), (byte) (most  >>> 32),
            (byte) (most  >>> 24), (byte) (most  >>> 16),
            (byte) (most  >>>  8), (byte) (most        ),

            (byte) (least >>> 56), (byte) (least >>> 48),
            (byte) (least >>> 40), (byte) (least >>> 32),
            (byte) (least >>> 24), (byte) (least >>> 16),
            (byte) (least >>>  8), (byte) (least       ),

            (byte) (time  >>> 56), (byte) (time  >>> 48),
            (byte) (time  >>> 40), (byte) (time  >>> 32),
            (byte) (time  >>> 24), (byte) (time  >>> 16),
            (byte) (time  >>>  8), (byte) (time        )
        };
        LOCK_VALUE.set(value);
        return value;
    }

}
