package com.hmdp.service.impl;

import cn.hutool.core.collection.ListUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import cn.hutool.setting.SettingUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

import java.util.List;
import java.util.Set;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryList() {

        String shopJson = stringRedisTemplate.opsForValue().get(RedisConstants.SHOP_TYPE_CACHE) ;
        if (StrUtil.isNotBlank(shopJson)) {
            List<ShopType> shopType = JSONUtil.toList(shopJson,ShopType.class);
            return Result.ok(shopType);
        }

        List<ShopType> shopTypeList = query().orderByAsc("sort").list();

        if(shopTypeList == null){
            return Result.fail("店铺列表不存在");
        }

        stringRedisTemplate.opsForValue().set(RedisConstants.SHOP_TYPE_CACHE,JSONUtil.toJsonStr(shopTypeList));


        return Result.ok(shopTypeList);
    }
}
