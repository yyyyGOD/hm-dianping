package com.hmdp;

import com.hmdp.service.IShopService;
import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.utils.RedisConstants;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

@SpringBootTest
class HmDianPingApplicationTests {
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private ShopServiceImpl shopService;

    @Autowired
    private RedisTemplate redisTemplate;
    @Test
    public void delete(){
        stringRedisTemplate.expire("shoptype",1, TimeUnit.SECONDS);
        System.out.println("成功");
    }

    @Test
    public void shopservice(){
        shopService.saveShop2Redis(1L,10L);
    }

}

