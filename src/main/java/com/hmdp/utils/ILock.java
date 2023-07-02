package com.hmdp.utils;

public interface ILock {
    /**
     * 尝试获取锁
     * @param timeoutSex 锁至于的超时时间，过期后自动释放
     * @returntrue代表获取锁成功，false代表获取锁失败
     */
   boolean tryLock(Long timeoutSex);

    /**
     * 释放锁
     */
   void unlock();

}

