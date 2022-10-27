package com.hmdp;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.entity.Shop;
import com.hmdp.service.IShopService;
import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisIdWorker;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@SpringBootTest
class HmDianPingApplicationTests {
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private ShopServiceImpl shopService;

    @Autowired
    private RedisTemplate redisTemplate;

    @Resource
    private RedisIdWorker redisIdWorker;

    private ExecutorService es = Executors.newFixedThreadPool(500);

    @Test
    public void delete(){
        stringRedisTemplate.expire("shoptype",1, TimeUnit.SECONDS);
        System.out.println("成功");
    }

    @Test
    public void shopservice(){
        shopService.saveShop2Redis(1L,10L);
    }

    @Test
    void testidwork() throws Exception {
        CountDownLatch latch = new CountDownLatch(300);

        Runnable task = () -> {
            for (int i = 0; i < 100; i++) {
                long id = redisIdWorker.nextid("order");
                System.out.println(id);
            }
            latch.countDown();
        };

        for (int i = 0; i < 300; i++) {
            es.submit(task);
        }
        latch.await();
    }


    //将店铺坐标分类存入Redis
    @Test
    void loadShopData(){

        List<Shop> list = shopService.list();

        Map<Long,List<Shop>> map = list.stream().collect(Collectors.groupingBy(Shop::getTypeId));

        for(Map.Entry<Long,List<Shop>> entry : map.entrySet()){
            Long typeId = entry.getKey();
            List<Shop> value = entry.getValue();

            String key = "shop:geo:" + typeId;
            List<RedisGeoCommands.GeoLocation<String>> locations = new ArrayList<>();

            for(Shop shop : value){
                //stringRedisTemplate.opsForGeo().add(key,new Point(shop.getX(), shop.getY()),shop.getId().toString());
                locations.add(new RedisGeoCommands.GeoLocation<>(shop.getId().toString(),
                        new Point(shop.getX(), shop.getY())));
            }

            stringRedisTemplate.opsForGeo().add(key,locations);
        }

    }

    //UV统计
    @Test
    void testHyperLogLog(){
        String[] values = new String[1000];
        int j = 0;
        for(int i = 0;i < 1000000;i++){
            j = i % 1000;
            values[j] = "user_" + i;
            if(j == 999){
                stringRedisTemplate.opsForHyperLogLog().add("hl2",values);
            }
        }

        Long count = stringRedisTemplate.opsForHyperLogLog().size("hl2");
        System.out.println(count);
    }
}

