package com.hmdp.utils;

public class RedisConstants {
    
    //Redis Key前缀
    static String HEAD = "hmdp:";
    public static final String LOGIN_CODE_KEY = HEAD + "login:code:";
    public static final Long LOGIN_CODE_TTL = 2L;
    public static final String LOGIN_USER_KEY = HEAD +"login:token:";
    public static final Long LOGIN_USER_TTL = 36000L;

    public static final Long CACHE_NULL_TTL = 2L;

    public static final Long CACHE_SHOP_TTL = 30L;
    public static final String CACHE_SHOP_KEY = HEAD +"cache:shop:";

    public static final String LOCK_SHOP_KEY = HEAD + "lock:shop:";
    public static final Long LOCK_SHOP_TTL = 10L;

    public static final String SECKILL_STOCK_KEY = HEAD + "seckill:stock:";
    public static final String BLOG_LIKED_KEY = HEAD + "blog:liked:";
    public static final String FEED_KEY = HEAD + "feed:";
    public static final String SHOP_GEO_KEY = HEAD + "shop:geo:";
    public static final String USER_SIGN_KEY = HEAD + "sign:";
}
