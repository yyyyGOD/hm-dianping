package com.hmdp.utils;

import cn.hutool.core.lang.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

public class SimpleRedisLock implements ILock{

    private String name;

    private StringRedisTemplate stringRedisTemplate;

    public static final String KEY_PERFIX = "lock";
    public static final String ID_PERFIX = UUID.randomUUID().toString(true) + "-";

    public SimpleRedisLock(String name,StringRedisTemplate stringRedisTemplate){
        this.name = name;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean tryLock(long timeoutSec) {

        String id = ID_PERFIX + Thread.currentThread().getId();

        Boolean success = stringRedisTemplate.opsForValue().setIfAbsent(KEY_PERFIX + name,id +"",timeoutSec, TimeUnit.SECONDS);

        return Boolean.TRUE.equals(success);
    }

    @Override
    public void unLock() {

        String ThreadId = ID_PERFIX + Thread.currentThread().getId();

        String id = stringRedisTemplate.opsForValue().get(KEY_PERFIX + name);

        if(ThreadId.equals(id)){
            stringRedisTemplate.delete(KEY_PERFIX + name);
        }
    }
}
