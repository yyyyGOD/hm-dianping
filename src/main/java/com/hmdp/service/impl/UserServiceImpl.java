package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;
import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result sendCode(String phone, HttpSession session) {
        //校验手机号
        if (RegexUtils.isPhoneInvalid(phone)) {
            //不符合返回错误信息
            return Result.fail("手机号格式错误");
        }
        //符合，生成
        String code = RandomUtil.randomNumbers(6);
        //保存验证码到session
        //session.setAttribute("code",code);

        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone, code, LOGIN_CODE_TTL, TimeUnit.MINUTES);

        log.debug("验证码为：{}", code);
        //发送验证码

        //返回ok
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {

        String phone = loginForm.getPhone();

        if (RegexUtils.isPhoneInvalid(phone)) {
            //不符合返回错误信息
            return Result.fail("手机号格式错误");
        }
        //Object cachecode = session.getAttribute("code");
        String cachecode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
        String code = loginForm.getCode();
        if (cachecode == null || !cachecode.equals(code)) {
            return Result.fail("验证码错误");
        }

        User user = query().eq("phone", phone).one();

        if (user == null) {
            user = createUserWithPhone(phone);
        }


        String token = UUID.randomUUID().toString(true);

        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO,new HashMap<>(),
                CopyOptions.create().setIgnoreNullValue(true).setFieldValueEditor((filename,fileVaule) -> fileVaule.toString()));

        stringRedisTemplate.opsForHash().putAll(LOGIN_USER_KEY + token,userMap);
        stringRedisTemplate.expire(LOGIN_USER_KEY + token,LOGIN_USER_TTL,TimeUnit.HOURS);
        //session.setAttribute("user", BeanUtil.copyProperties(user, UserDTO.class));

        return Result.ok(token);
    }

    private User createUserWithPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
        save(user);
        return user;
    }

    //签到
    @Override
    public Result sign() {
        Long userId = UserHolder.getUser().getId();

        LocalDateTime now = LocalDateTime.now();

        String keysuffix = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key = USER_SIGN_KEY + userId + keysuffix;

        int dayOfMonth = now.getDayOfMonth();
        //offet:偏移量
        stringRedisTemplate.opsForValue().setBit(key,dayOfMonth - 1,true);

        return Result.ok();
    }
    //签到统计
    @Override
    public Result signCount() {
        Long userId = UserHolder.getUser().getId();

        LocalDateTime now = LocalDateTime.now();

        String keysuffix = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key = USER_SIGN_KEY + userId + keysuffix;

        int dayOfMonth = now.getDayOfMonth();

        List<Long> result = stringRedisTemplate.opsForValue().bitField(
                key,
                BitFieldSubCommands.create()
                        .get(BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth)).valueAt(0)
        );
        if(result == null || result.isEmpty()){
            return Result.ok(0);
        }

        Long num = result.get(0);
        if(num == null || num == 0){
            return Result.ok(0);
        }

        int count = 0;
        while (true){
            if((num & 1) == 0){
                break;
            }else {
                count++;
            }
            num >>>= 1;
        }

        return Result.ok(count);
    }
}
