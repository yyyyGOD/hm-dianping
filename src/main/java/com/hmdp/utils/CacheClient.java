package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.Shop;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.*;

/*
* redis缓存工具封装
*
* */
@Slf4j
@Component
public class CacheClient {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    public void set(String key, Object valus, Long time, TimeUnit unit){

        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(valus),time,unit);

    }

    public void setWithLogical(String key, Object valus, Long time, TimeUnit unit){

        RedisData redisData = new RedisData();
        redisData.setData(valus);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));

        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(valus),time,unit);

    }

    public <R,ID> R queryWithThrough(String keyPreflix, ID id, Class<R> type, Function<ID,R> dbfallback
                    ,Long time, TimeUnit timeUnit){

        String key = keyPreflix + id;

        String json = stringRedisTemplate.opsForValue().get(key);

        if (StrUtil.isNotBlank(json)) {
            return JSONUtil.toBean(json,type);
        }

        if(json != null){
            return null;
        }

        R r = dbfallback.apply(id);

        if(r == null){
            stringRedisTemplate.opsForValue().set(key,"",CACHE_NULL_TTL,TimeUnit.MINUTES);
            return null;
        }

        this.set(key,r,time,timeUnit);
        //stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(r),CACHE_SHOP_TTL,TimeUnit.MINUTES);

        return r;
    }


    public static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    public <R,ID> R queryWithPassLogicalExpire(String keyPreflix, ID id, Class<R> type, Function<ID,R> dbfallback
            ,Long time, TimeUnit timeUnit){
        String key = keyPreflix + id;

        String Json = stringRedisTemplate.opsForValue().get(key);

        if (StrUtil.isBlank(Json)) {
            return null;
        }

        RedisData redisData = JSONUtil.toBean(Json, RedisData.class);
        R r = JSONUtil.toBean((JSONObject) redisData.getData(), type);
        LocalDateTime expireTime = redisData.getExpireTime();

        if(expireTime.isAfter(LocalDateTime.now())){
            return r;
        }

        boolean isLock = tryLocke(LOCK_SHOP_KEY + id);
        if(isLock){
            CACHE_REBUILD_EXECUTOR.submit(()->{
                R r1 = dbfallback.apply(id);
                this.setWithLogical(key,r1,time,timeUnit);
                unlock(LOCK_SHOP_KEY + id);
            });
        }

        return r;

    }
    private boolean tryLocke(String key){
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    private void unlock(String key){
        stringRedisTemplate.delete(key);
    }
}
