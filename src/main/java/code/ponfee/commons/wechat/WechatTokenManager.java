package code.ponfee.commons.wechat;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;

import code.ponfee.commons.jedis.JedisClient;
import code.ponfee.commons.jedis.JedisLock;

/**
 * wechat global token manager
 * 微信accesstoken刷新平滑过渡，官方给的是5分钟
 * @author Ponfee
 */
public class WechatTokenManager implements DisposableBean {

    /** wechat config of appid and secret */
    private static final Map<String, Wechat> WECHAT_CONFIGS = new HashMap<String, Wechat>() {
        private static final long serialVersionUID = 4891406751053897149L;
        {
            put(new Wechat("wx0a7a9ac2a6e0f7e7", "4516f601af973d42cbfee3b7ca7cff34"));
            //put(new Wechat("appid", "secret"));
        }

        private void put(Wechat wechat) {
            super.put(wechat.appid, wechat);
        }
    };

    /** refresh token lock key */
    private static final Map<String, JedisLock> JEDIS_LOCKS = new ConcurrentHashMap<>();

    /** max of 2000 request from wechat every day */
    private static final int REFRESH_PERIOD_SECONDS = (int) Math.ceil(86400.0D / 2000);

    /** the token effective time 7200 seconds */
    private static final int TOKEN_EXPIRE = 7200 - 60;

    /** refresh from cache of period */
    private static final int CACHE_REFRESH_MILLIS = 15000;

    /** the wechat opendid cache key */
    private static final String WECHAT_OPENID_CACHE = "wechat:openid:cache:";

    private static Logger logger = LoggerFactory.getLogger(WechatTokenManager.class);

    private final JedisClient jedisClient;
    private final ScheduledExecutorService scheduled;

    public WechatTokenManager(JedisClient jedisClient) {
        this.jedisClient = jedisClient;

        // 定时请求微信接口刷新
        scheduled = Executors.newSingleThreadScheduledExecutor();
        scheduled.scheduleAtFixedRate(() -> {
            for (Wechat wechat : WECHAT_CONFIGS.values()) {
                try {
                    refreshToken(wechat);
                } catch (FrequentlyRefreshException e) {
                    logger.error(e.getMessage());
                }
            }
        }, 0, TOKEN_EXPIRE / 2, TimeUnit.SECONDS);

        // 定期从缓存中加载
        new Thread(() -> {
            while (true) {

                try { // 加异常捕获为防止Redis挂掉后退出循环
                      // load token and ticket from redis cache
                    for (Wechat wx : WECHAT_CONFIGS.values()) {
                        String accessToken = jedisClient.valueOps().get(wx.accessTokenKey);
                        if (StringUtils.isNotEmpty(accessToken)) {
                            wx.accessToken = accessToken;
                        }

                        String jsapiTicket = jedisClient.valueOps().get(wx.jsapiTicketKey);
                        if (StringUtils.isNotEmpty(jsapiTicket)) {
                            wx.jsapiTicket = jsapiTicket;
                        }
                    }
                } catch (Throwable t) {
                    logger.error("load token from cache occur error", t);
                }

                try {
                    // to sleep for prevent endless loop
                    Thread.sleep(CACHE_REFRESH_MILLIS);
                } catch (InterruptedException e) {
                    logger.error("thread sleep occur interrupted exception", e);
                }

            }
        }).start();
    }

    /**
     * 获取accessToken
     * @param appid
     * @return token
     */
    public final String getAccessToken(String appid) {
        return getWechat(appid).accessToken;
    }

    /**
     * 获取jsapiTicket
     * @param appid
     * @return ticket
     */
    public final String getJsapiTicket(String appid) {
        return getWechat(appid).jsapiTicket;
    }

    /**
     * 手动刷新accessToken
     * @param appid
     * @return
     */
    public String refreshAndGetAccessToken(String appid)
        throws FrequentlyRefreshException {
        Wechat wechat = getWechat(appid);
        refreshToken(wechat);
        return wechat.accessToken;
    }

    /**
     * 手动刷新jsapiTicket
     * @param appid
     * @return
     */
    public String refreshAndGetJsapiTicket(String appid)
        throws FrequentlyRefreshException {
        Wechat wechat = getWechat(appid);
        refreshToken(wechat);
        return wechat.jsapiTicket;
    }

    /**
     * 缓存openId：主要是解决获取openid时若网络慢会同时出现多次请求，
     * 导致错误：{"errcode":40029,"errmsg":"invalid code, hints: [ req_id: raY0187ns82 ]"}
     * 当调用{@link Wechats#getOAuth2(String, String, String)}时，如果返回此错误则从缓存获取
     * 如果获取成功则缓存到此缓存
     * @param code
     * @param openid
     */
    public void cacheOpenIdByCode(String code, String openid) {
        jedisClient.valueOps().set(WECHAT_OPENID_CACHE + code, openid, 30);
    }

    /**
     * 加载openId
     * @param code
     * @return the cache of openid
     */
    public String loadOpenIdByCode(String code) {
        return jedisClient.valueOps().get(WECHAT_OPENID_CACHE + code);
    }

    // -----------------------------------private methods--------------------------------- //
    private Wechat getWechat(String appid) {
        Wechat wechat = WECHAT_CONFIGS.get(appid);
        if (wechat != null) {
            return wechat;
        }
        throw new IllegalArgumentException("invalid wechat appid: " + appid);
    }

    /**
     * 主动刷新token（已限制频率）
     * @param wx
     * @return
     * @throws FrequentlyRefreshException
     */
    private void refreshToken(Wechat wx) throws FrequentlyRefreshException {
        // limit refresh frequency: set the minimum period seconds
        if (getLock(wx.lockRefreshKey, REFRESH_PERIOD_SECONDS).tryLock()) {

            try {
                String accessToken = Wechats.getAccessToken(wx.appid, wx.secret);
                if (StringUtils.isNotEmpty(accessToken)) {
                    wx.accessToken = accessToken;
                    jedisClient.valueOps().set(wx.accessTokenKey, accessToken, TOKEN_EXPIRE);
                }
            } catch (Exception e) {
                logger.error("refresh access token occur error", e);
            }

            try {
                String jsapiTicket = Wechats.getJsapiTicket(wx.accessToken);
                if (StringUtils.isNotEmpty(jsapiTicket)) {
                    wx.jsapiTicket = jsapiTicket;
                    jedisClient.valueOps().set(wx.jsapiTicketKey, jsapiTicket, TOKEN_EXPIRE);
                }
            } catch (Exception e) {
                logger.error("refresh jsapi ticket occur error", e);
            }

            logger.info("－－－ refresh wechat token appid: {} －－－", wx.appid);

        } else {
            throw new FrequentlyRefreshException("微信令牌频繁刷新，请稍后再试！");
        }
    }

    /**
     * 获取分布式锁
     * @param lockKey
     * @param timeout
     * @return
     */
    private JedisLock getLock(String lockKey, int timeout) {
        JedisLock lock = JEDIS_LOCKS.get(lockKey);
        if (lock == null) {
            synchronized (WechatTokenManager.class) {
                lock = JEDIS_LOCKS.get(lockKey);
                if (lock == null) {
                    lock = new JedisLock(jedisClient, lockKey, timeout);
                    JEDIS_LOCKS.put(lockKey, lock);
                }
            }
        }
        return lock;
    }

    /**
     * Wechat
     */
    private static final class Wechat {
        final String appid;
        final String secret;
        final String accessTokenKey;
        final String jsapiTicketKey;
        final String lockRefreshKey;

        volatile String accessToken = null;
        volatile String jsapiTicket = null;

        Wechat(String appid, String secret) {
            this.appid = appid;
            this.secret = secret;
            this.accessTokenKey = "wx:access:token:" + appid;
            this.jsapiTicketKey = "wx:jsapi:ticket:" + appid;
            this.lockRefreshKey = "wx:token:refrsh:" + appid;
        }
    }

    @Override
    public void destroy() throws Exception {
        scheduled.shutdown();
    }

}
