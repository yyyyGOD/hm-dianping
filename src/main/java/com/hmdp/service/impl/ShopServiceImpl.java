package com.hmdp.service.impl;

import cn.hutool.cache.Cache;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisData;
import com.hmdp.utils.SystemConstants;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    public static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    CacheClient cacheClient;
    @Override
    public Result queryById(Long id) {
        //穿透封装
        Shop shop = cacheClient.queryWithThrough(CACHE_SHOP_KEY, id, Shop.class,this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES);
        //过期封装
        //Shop shop = cacheClient.queryWithPassLogicalExpire(CACHE_SHOP_KEY,id,Shop.class,this::getById,20L,TimeUnit.MINUTES);

        //---------------------------------------------------------------
        //穿透
        //Shop shop = queryWithPassThrough(id);

        //击穿
        //Shop shop = queryWithPassMutex(id);

        //逻辑过期
        //Shop shop = queryWithPassLogicalExpire(id);

        if(shop == null){
            return Result.fail("店铺不存在");
        }

        return Result.ok(shop);
    }


    public Shop queryWithThrough(Long id){

        String key = CACHE_SHOP_KEY + id;

        String shopjson = stringRedisTemplate.opsForValue().get(key);

        if (StrUtil.isNotBlank(shopjson)) {
            return JSONUtil.toBean(shopjson,Shop.class);
        }

        if(shopjson != null){
            return null;
        }

        Shop shop = getById(id);

        if(shop == null){
            stringRedisTemplate.opsForValue().set(key,"",CACHE_NULL_TTL,TimeUnit.MINUTES);
            return null;
        }

        stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(shop),CACHE_SHOP_TTL,TimeUnit.MINUTES);

        return shop;
    }
    //逻辑过期
    public Shop queryWithPassLogicalExpire(Long id){

        String shopJson = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);

        if (StrUtil.isBlank(shopJson)) {
            return null;
        }

        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
        Shop shop = JSONUtil.toBean((JSONObject) redisData.getData(), Shop.class);
        LocalDateTime expireTime = redisData.getExpireTime();

        if(expireTime.isAfter(LocalDateTime.now())){
            return shop;
        }

        boolean isLock = tryLocke(LOCK_SHOP_KEY + id);
        if(isLock){
            CACHE_REBUILD_EXECUTOR.submit(()->{
                this.saveShop2Redis(id,20L);
                unlock(LOCK_SHOP_KEY + id);
            });
        }

        return shop;

    }


    //击穿
    public Shop queryWithPassMutex(Long id){

        String shopJson = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);

        if (StrUtil.isNotBlank(shopJson)) {
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return shop;
        }

        if(shopJson != null){
            return null;
        }
        Shop shop = null;

        try {
            boolean isLocke = tryLocke(LOCK_SHOP_KEY + id);
            if(!isLocke){
                Thread.sleep(30);
                return queryWithPassMutex(id);
            }

            shop = getById(id);

            Thread.sleep(200);

            if(shop == null){
                stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id,"", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);

                return null;
            }

            stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id,JSONUtil.toJsonStr(shop), RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
        }catch (InterruptedException e){
            throw new RuntimeException(e);
        }finally {
            unlock(LOCK_SHOP_KEY + id);
        }

        return shop;
    }

    @Override
    @Transactional
    public Result update(Shop shop) {
        Long id = shop.getId();
        if(id == null){
            return Result.fail("店铺id不能为空");
        }
        updateById(shop);

        stringRedisTemplate.delete(CACHE_SHOP_KEY + id);

        return Result.ok();
    }

    private boolean tryLocke(String key){
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    private void unlock(String key){
        stringRedisTemplate.delete(key);
    }

    public void saveShop2Redis(Long id,Long exs){

        Shop shop = getById(id);

        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(exs));

        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id,JSONUtil.toJsonStr(redisData));

    }

    //附近商铺排序
    @Override
    public Result queryShopByType(Integer typeId, Integer current, Double x, Double y) {

        if(x == null || y == null){
            // 根据类型分页查询
            Page<Shop> page = query()
                    .eq("type_id", typeId)
                    .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
            // 返回数据
            return Result.ok(page.getRecords());
        }

        String key = SHOP_GEO_KEY + typeId;
        int from = (current - 1) * SystemConstants.DEFAULT_PAGE_SIZE;
        int end = current * SystemConstants.DEFAULT_PAGE_SIZE;

        GeoResults<RedisGeoCommands.GeoLocation<String>> results = stringRedisTemplate.opsForGeo()
                .search(key,
                        GeoReference.fromCoordinate(x, y),
                        new Distance(10000),
                        RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs().includeDistance().limit(end)
                );
        if(results == null){
            return Result.ok(Collections.emptyList());
        }
        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> list = results.getContent();
        if(list.size() <= from){
            return Result.ok(Collections.emptyList());
        }

        List<Long> ids = new ArrayList<>(list.size());
        Map<String,Distance> distanceMap = new HashMap<>(list.size());

        list.stream().skip(from).forEach(result -> {
            String shopIdStr = result.getContent().getName();
            ids.add(Long.valueOf(shopIdStr));

            Distance distance = result.getDistance();
            distanceMap.put(shopIdStr,distance);
        });

        String idStr = StrUtil.join(",", ids);
        List<Shop> shops = query().in("id", ids).last("ORDER BY FIELD (id," + idStr + ")").list();

        for (Shop shop : shops) {
            shop.setDistance(distanceMap.get(shop.getId().toString()).getValue());
        }

        return Result.ok(shops);
    }
}
