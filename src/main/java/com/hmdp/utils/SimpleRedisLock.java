package com.hmdp.utils;


import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class SimpleRedisLock implements ILock{
    private String name;

    public SimpleRedisLock(String name, StringRedisTemplate stringRedisTemplate) {
        this.name = name;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    private StringRedisTemplate stringRedisTemplate;
    private static  final String KEY_PREFIX="lock:";
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;
    static {
        UNLOCK_SCRIPT=new DefaultRedisScript<>();
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("unlock.lua"));
        UNLOCK_SCRIPT.setResultType(Long.class);
    }

    @Override
    public boolean tryLock(Long timeoutSex) {
//        获取线程标识
        long threadId = Long.parseLong(ID_PREFIX+Thread.currentThread().getId());
//        获取锁
        Boolean success = stringRedisTemplate.opsForValue()
                .setIfAbsent(KEY_PREFIX + name, String.valueOf(threadId), timeoutSex, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(success);
    }
private static final String ID_PREFIX= UUID.randomUUID().toString()+"";

//
//    @Override
//    public void unlock() {
//        //        获取线程标识
//        String threadId = String.valueOf(Long.parseLong(ID_PREFIX+Thread.currentThread().getId()));
////        获取锁中的标识
//        String id = stringRedisTemplate.opsForValue().get(KEY_PREFIX + name);
////判断标识是否一样
//        if(threadId.equals(id)){
////        释放锁
//            stringRedisTemplate.delete(KEY_PREFIX+name);
//
//        }
//
//    }

    @Override
public void unlock() {
//        调用lua脚本
        stringRedisTemplate.execute(
                UNLOCK_SCRIPT,
                Collections.singletonList(KEY_PREFIX + name),
                ID_PREFIX + Thread.currentThread().getId());
    }

}