package com.hmdp.utils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.UserDTO;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 登录验证功能
 */
public class RefreshTokenInterceptor implements HandlerInterceptor {
   private StringRedisTemplate stringRedisTemplate;
   public RefreshTokenInterceptor(StringRedisTemplate stringRedisTemplate){
       this.stringRedisTemplate=stringRedisTemplate;
   }
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
//        todo 1.获取请求头中的token
//        HttpSession session = request.getSession();
        String token = request.getHeader("authorization");
        if(StrUtil.isBlank(token)){
//
            return true;
        }
//       todo  2.基于token获取session中的用户
//        Object user = session.getAttribute("user");
        String key=RedisConstants.LOGIN_USER_KEY + token;
        Map<Object, Object> userMap= stringRedisTemplate.opsForHash().entries(key);
//        3.判断用户是否存在
        if(userMap.isEmpty()){
            return true;
        }
//        todo 将查询到的Hash数据转为UserDto对象
        UserDTO userDTO = BeanUtil.fillBeanWithMap(userMap, new UserDTO(), false);
//      todo   5.存在，保存用户信息到ThreadLocal
        UserHolder.saveUser(userDTO);
//        todo 7.刷新token的有效期
        stringRedisTemplate.expire(key, RedisConstants.LOGIN_USER_TTL ,TimeUnit.MINUTES);

//        6.放行
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
//       移除用户
        UserHolder.removeUser();
    }
}
