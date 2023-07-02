package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {
@Resource
private StringRedisTemplate stringRedisTemplate;
    @Override
    public Result sendCode(String phone, HttpSession session) {
//        1.验证手机号
        if(RegexUtils.isPhoneInvalid(phone)){
//        2.如果不符合，返回错误信息
            return Result.fail("手机好格式错误");
        }
//        3.符合生成验证码
        String code= RandomUtil.randomNumbers(6);
//        4.保存验证吗到redis(并设置有效期）
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY+phone,code,LOGIN_CODE_TTL, TimeUnit.MINUTES);
//        session.setAttribute("code",code);
//        5.生成燕正码
        log.debug("仿宋短信验证码成功，验证码：{}");
//        返回ok
        return  Result.ok();

    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        String phone = loginForm.getPhone();
//        1.验证手机号
        if(RegexUtils.isPhoneInvalid(phone)){
//           2.如果不符合，返回错误信息
            return Result.fail("手机好格式错误");
        }
//        2.从redis中获取校验验证码
        Object cacheCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY+phone);
        String code = loginForm.getCode();
        if(cacheCode==null||!cacheCode.toString().equals(equals(code))){

//        3.不一致，报错
            return Result.fail("验证码错误");
        }
//        4.一致，根据手机号查询用户
        User user = query().eq("phone", phone).one();
//        5.判断用户是否存在
        if(user==null){

//        6.不存在，创建新用户并保存
           user= createUserWithPhone(phone);
        }
//      todo  7.保存用户到redis中
//        7.1随机生成token,作为登陆令牌
        String token = UUID.randomUUID().toString();
//        7.2将User对象转为hash存储
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO,new HashMap<>(), CopyOptions.create()
                .setIgnoreNullValue(true)
                .setFieldValueEditor((fieldName,fieldValue)->fieldValue.toString()));
//        7.3存储
        String tokenKey=LOGIN_USER_KEY+token;
        stringRedisTemplate.opsForHash().putAll("login:token"+token,userMap);
//        7.4设置token有效期
        stringRedisTemplate.expire(tokenKey, LOGIN_USER_TTL,TimeUnit.MINUTES);
//        短信验证码，隐藏用户敏感信息
//        session.setAttribute("user", BeanUtil.copyProperties(user, UserDTO.class));
//        8.返回token
        return Result.ok(token);
    }

    @Override
    public Result sign() {
        //获取当前登陆用户
        Long id = UserHolder.getUser().getId();
        //获取日期
        LocalDateTime now = LocalDateTime.now();
        //拼接key
        String yyyyMM = now.format(DateTimeFormatter.ofPattern("yyyy:MM:"));
        String key = USER_SIGN_KEY +yyyyMM+ id;
        //获取今天是本月的第几天
        int dayOfMonth = now.getDayOfMonth();
        //写了redis
        stringRedisTemplate.opsForValue().setBit(key,dayOfMonth-1,true);
        return Result.ok();
    }

    @Override
    public Result signCount() {
        //获取当前登陆用户
        Long id = UserHolder.getUser().getId();
        //获取日期
        LocalDateTime now = LocalDateTime.now();
        //拼接key
        String yyyyMM = now.format(DateTimeFormatter.ofPattern("yyyy:MM:"));
        String key = USER_SIGN_KEY +yyyyMM+ id;
        //获取今天是本月的第几天
        int dayOfMonth = now.getDayOfMonth();
        //获取截至本月今天的所有签到记录
        List<Long> result = stringRedisTemplate.opsForValue().bitField(key
                , BitFieldSubCommands
                        .create()
                        .get(BitFieldSubCommands.BitFieldType
                                .unsigned(dayOfMonth))
                        .valueAt(0)
        );
        if (result==null||result.isEmpty()){
            return Result.ok(0);
        }
        Long num = result.get(0);
        if (num==null||num==0){
            return Result.ok(0);
        }
        //转二进制字符串
        String binaryString = Long.toBinaryString(num);
        //计算连续签到天数
        int count=0;
        for (int i = binaryString.length()-1; i >=0; i--) {
            if (binaryString.charAt(i)=='1'){
                count++;
            }
            else {
                break;
            }
        }
        //返回
        return Result.ok(count);
    }

    private User createUserWithPhone(String phone) {
//1.创建用户
        User user = new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX+RandomUtil.randomString(10));
//        保存
        save(user);
    return null;

    }
}
